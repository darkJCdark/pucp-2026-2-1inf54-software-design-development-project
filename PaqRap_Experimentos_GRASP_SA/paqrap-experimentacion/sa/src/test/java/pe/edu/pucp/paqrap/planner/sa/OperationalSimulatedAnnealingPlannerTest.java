package pe.edu.pucp.paqrap.planner.sa;

import org.junit.jupiter.api.Test;
import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.route.*;
import pe.edu.pucp.paqrap.planner.search.SearchControl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OperationalSimulatedAnnealingPlannerTest {
    private static final Instant START = Instant.parse("2026-09-09T12:00:00Z");
    private static final Warehouse CENTRAL = Warehouse.central("CENTRAL", PaqRapNetwork.CENTRAL_LOCATION);
    private static final Vehicle CAR = new Vehicle("TA01", VehicleType.CAR, true);
    private static final Vehicle MOTORCYCLE = new Vehicle("TM01", VehicleType.MOTORCYCLE, true);
    private static final Order ORDER = new Order("P1", new Location(28, 14), 2, START,
            START.plus(Duration.ofHours(8)));

    @Test
    void distinguishesIterationsAttemptsEvaluationsAndAcceptances() {
        OperationalSnapshot snapshot = snapshot(CAR);
        OperationalPlan initial = plan(CAR, ORDER);
        AtomicInteger calls = new AtomicInteger();
        OperationalNeighborGenerator generator = (current, state, random) ->
                calls.incrementAndGet() == 1 ? Optional.empty() : Optional.of(current);
        OperationalAnnealingResult result;
        SearchControl control = SearchControl.install(0);
        try (control) {
            result = planner(generator, new Random(7)).optimize(initial, snapshot, List.of(ORDER), List.of(),
                    config(2, 2, 20));
        }
        assertEquals(2, result.iterations());
        assertEquals(1, result.evaluatedNeighbors());
        assertEquals(1, result.acceptedNeighbors());
        assertEquals(2, control.iterations());
        assertEquals(2, control.attempts());
        assertEquals(2, control.evaluations()); // Initial plan plus one nonempty neighbor.
        assertEquals(1, control.accepted());
    }

    @Test
    void appliesMetropolisProbabilityToWorseNeighborAndAcceptsBetterNeighbor() {
        OperationalSnapshot snapshot = snapshot(CAR, MOTORCYCLE);
        OperationalPlan cheaper = plan(MOTORCYCLE, ORDER);
        OperationalPlan dearer = plan(CAR, ORDER);
        double delta = evaluator().evaluate(dearer, snapshot, List.of(ORDER), List.of()).totalCost()
                - evaluator().evaluate(cheaper, snapshot, List.of(ORDER), List.of()).totalCost();
        assertTrue(delta > 0);
        double probability = Math.exp(-delta / 100.0);
        assertTrue(probability > 0 && probability < 1);

        OperationalNeighborGenerator worse = (current, state, random) -> Optional.of(dearer);
        var rejected = planner(worse, randomReturning((probability + 1) / 2)).optimize(
                cheaper, snapshot, List.of(ORDER), List.of(), config(1, 1, 10));
        var accepted = planner(worse, randomReturning(probability / 2)).optimize(
                cheaper, snapshot, List.of(ORDER), List.of(), config(1, 1, 10));
        var better = planner((current, state, random) -> Optional.of(cheaper), randomReturning(1)).optimize(
                dearer, snapshot, List.of(ORDER), List.of(), config(1, 1, 10));

        assertEquals(0, rejected.acceptedNeighbors());
        assertEquals(1, accepted.acceptedNeighbors());
        assertEquals(1, better.acceptedNeighbors());
        assertEquals(cheaper, accepted.bestPlan()); // Best remains cheaper even after accepting worse.
        assertEquals(cheaper, better.bestPlan());
    }

    @Test
    void coolingAndBothIterationLimitsAreReportedExactly() {
        OperationalSnapshot snapshot = snapshot(CAR);
        OperationalPlan initial = plan(CAR, ORDER);
        OperationalNeighborGenerator same = (current, state, random) -> Optional.of(current);
        var maxIterations = planner(same, new Random(1)).optimize(initial, snapshot, List.of(ORDER), List.of(),
                new AnnealingConfig(100, 81, .9, 1, 2, 20));
        var minimumTemperature = planner(same, new Random(1)).optimize(initial, snapshot, List.of(ORDER), List.of(),
                new AnnealingConfig(100, 90, .9, 1, 20, 20));
        var noImprovement = planner(same, new Random(1)).optimize(initial, snapshot, List.of(ORDER), List.of(),
                new AnnealingConfig(100, 1, .9, 1, 20, 2));

        assertEquals(2, maxIterations.iterations());
        assertEquals(81, maxIterations.finalTemperature(), 1e-10);
        assertEquals(2, minimumTemperature.iterations());
        assertEquals(81, minimumTemperature.finalTemperature(), 1e-10);
        assertEquals(2, noImprovement.iterations());
        assertEquals(81, noImprovement.finalTemperature(), 1e-10);
        assertEquals(noImprovement.bestEvaluation().totalCost(), noImprovement.initialCost(), 1e-10);
    }

    @Test
    void rejectsNegativeLoadAndPreservesFeasibleBest() {
        OperationalSnapshot snapshot = snapshot(CAR);
        OperationalPlan initial = plan(CAR, ORDER);
        OperationalNeighborGenerator invalid = (current, state, random) -> {
            DeliveryRoute original = current.routeForVehicle(CAR.id()).orElseThrow();
            DeliveryRoute negative = original.withReplacedStops(List.of(
                    new DeliveryStop(ORDER, 2), new DeliveryStop(ORDER, 2), new WarehouseVisit(CENTRAL, 0)));
            return Optional.of(current.withRoute(negative));
        };
        OperationalAnnealingResult result;
        SearchControl control = SearchControl.install(0);
        try (control) {
            result = planner(invalid, new Random(1)).optimize(initial, snapshot, List.of(ORDER), List.of(),
                    config(1, 1, 10));
        }
        assertEquals(1, result.evaluatedNeighbors());
        assertEquals(0, result.acceptedNeighbors());
        assertEquals(1, control.invalid());
        assertSame(initial, result.bestPlan());
        assertTrue(result.bestEvaluation().isFeasible());
    }

    @Test
    void fixedSeedReproducesPlanCostAndDeterministicCounters() {
        OperationalSnapshot snapshot = snapshot(CAR, MOTORCYCLE);
        OperationalPlan initial = plan(CAR, ORDER);
        OperationalAnnealingResult first = planner(new OperationalRouteNeighborGenerator(), new Random(73))
                .optimize(initial, snapshot, List.of(ORDER), List.of(), config(25, 5, 25));
        OperationalAnnealingResult second = planner(new OperationalRouteNeighborGenerator(), new Random(73))
                .optimize(initial, snapshot, List.of(ORDER), List.of(), config(25, 5, 25));
        assertEquals(signature(first.bestPlan()), signature(second.bestPlan()));
        assertEquals(first.bestEvaluation().totalCost(), second.bestEvaluation().totalCost());
        assertEquals(first.iterations(), second.iterations());
        assertEquals(first.evaluatedNeighbors(), second.evaluatedNeighbors());
        assertEquals(first.acceptedNeighbors(), second.acceptedNeighbors());
        assertTrue(first.acceptedNeighbors() <= first.evaluatedNeighbors());
    }

    @Test
    void cooperativeDeadlineReturnsValidatedBestWithoutBusinessViolation() {
        OperationalSnapshot snapshot = snapshot(CAR);
        OperationalPlan initial = plan(CAR, ORDER);
        OperationalNeighborGenerator slow = (current, state, random) -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return Optional.of(current);
        };
        OperationalAnnealingResult result;
        SearchControl control = SearchControl.install(500);
        try (control) {
            result = planner(slow, new Random(1)).optimize(initial, snapshot, List.of(ORDER), List.of(),
                    config(5, 5, 5));
        }
        assertTrue(control.timedOut());
        assertSame(initial, result.bestPlan());
        assertTrue(result.bestEvaluation().isFeasible());
        assertTrue(result.bestEvaluation().violations().isEmpty());
        assertEquals(0, result.evaluatedNeighbors()); // Evaluation was interrupted before completion.
    }

    private static OperationalSnapshot snapshot(Vehicle... vehicles) {
        Map<String, VehicleOperationalState> states = new java.util.TreeMap<>();
        for (Vehicle vehicle : vehicles) {
            states.put(vehicle.id(), new VehicleOperationalState(vehicle, VehicleStatus.AVAILABLE,
                    CENTRAL.location(), START));
        }
        return new OperationalSnapshot(START, FleetProfile.defaults(), InventorySnapshot.from(List.of(CENTRAL)),
                states, new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()),
                ShiftSchedule.defaultSchedule(), List.of());
    }

    private static OperationalPlan plan(Vehicle vehicle, Order order) {
        return OperationalPlan.empty().withRoute(DeliveryRoute.startScenarioAtCentral(
                "R-" + vehicle.id(), vehicle, CENTRAL, order.packages(), START)
                .withAppendedStop(new DeliveryStop(order, order.packages())).returningTo(CENTRAL));
    }

    private static OperationalPlanEvaluator evaluator() {
        return new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()));
    }

    private static OperationalSimulatedAnnealingPlanner planner(OperationalNeighborGenerator neighbors, Random random) {
        return new OperationalSimulatedAnnealingPlanner(evaluator(), neighbors, random);
    }

    private static AnnealingConfig config(int maximumIterations, int perTemperature, int withoutImprovement) {
        return new AnnealingConfig(100, 1, .9, perTemperature, maximumIterations, withoutImprovement);
    }

    private static Random randomReturning(double value) {
        return new Random(0) {
            @Override public double nextDouble() { return value; }
        };
    }

    private static List<String> signature(OperationalPlan plan) {
        return plan.routes().stream().map(route -> route.vehicle().id() + ":" + route.stops()).toList();
    }
}
