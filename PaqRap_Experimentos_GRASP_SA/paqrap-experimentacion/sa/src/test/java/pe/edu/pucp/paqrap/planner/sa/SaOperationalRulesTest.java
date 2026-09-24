package pe.edu.pucp.paqrap.planner.sa;

import org.junit.jupiter.api.Test;
import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.route.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class SaOperationalRulesTest {
    private static final Instant START = Instant.parse("2026-09-09T12:00:00Z");
    private static final Warehouse CENTRAL = Warehouse.central("CENTRAL", PaqRapNetwork.CENTRAL_LOCATION);

    @Test
    void thirtyPackagesAreCompletelyDeliveredInMultipleOneHourStops() {
        Vehicle car = vehicle(VehicleType.CAR, 1);
        Vehicle motorcycle = vehicle(VehicleType.MOTORCYCLE, 1);
        OperationalSnapshot snapshot = snapshot(List.of(car, motorcycle), List.of());
        Order order = order("P30", 30);
        OperationalPlan plan = new InitialPlanBuilder(evaluator()).build(
                snapshot, List.of(order), CENTRAL, List.of()).plan();
        PlanEvaluation evaluation = evaluator().evaluate(plan, snapshot, List.of(order), List.of());
        var deliveries = plan.routes().stream().flatMap(route -> route.stops().stream())
                .filter(DeliveryStop.class::isInstance).map(DeliveryStop.class::cast).toList();

        assertTrue(evaluation.isFeasible(), () -> evaluation.violations().toString());
        assertTrue(deliveries.size() >= 2);
        assertEquals(30, deliveries.stream().mapToInt(DeliveryStop::deliveredPackages).sum());
        assertTrue(deliveries.stream().allMatch(stop -> stop.order().equals(order)));
        assertEquals(Duration.ofHours(1), DeliveryStop.SERVICE_TIME);
        evaluation.schedulesByRouteId().values().stream()
                .flatMap(route -> route.scheduledStops().stream())
                .filter(stop -> stop.stop() instanceof DeliveryStop)
                .forEach(stop -> assertEquals(Duration.ofHours(1),
                        Duration.between(stop.arrivedAt(), stop.completedAt())));
        assertLoadsValid(evaluation, snapshot);
        assertTrue(evaluation.violations().stream().noneMatch(v ->
                v.type() == PlanViolationType.PARTIAL_DELIVERY_MISMATCH));

        OperationalAnnealingResult result = new OperationalSimulatedAnnealingPlanner(evaluator(),
                new OperationalRouteNeighborGenerator(), new Random(13)).optimize(
                plan, snapshot, List.of(order), List.of(), new AnnealingConfig(100, 1, .9, 2, 10, 10));
        assertTrue(result.bestEvaluation().isFeasible());
        assertLoadsValid(result.bestEvaluation(), snapshot);
    }

    @Test
    void everyFleetTypeRespectsItsDeclaredCapacity() {
        Map<VehicleType, Integer> capacities = Map.of(VehicleType.CAR, 24,
                VehicleType.MOTORCYCLE, 8, VehicleType.BICYCLE, 4);
        for (var entry : capacities.entrySet()) {
            Vehicle vehicle = vehicle(entry.getKey(), 1);
            OperationalSnapshot snapshot = snapshot(List.of(vehicle), List.of());
            Order order = order("CAP-" + entry.getKey(), entry.getValue());
            OperationalPlan plan = new InitialPlanBuilder(evaluator()).build(
                    snapshot, List.of(order), CENTRAL, List.of()).plan();
            PlanEvaluation evaluation = evaluator().evaluate(plan, snapshot, List.of(order), List.of());
            assertEquals(entry.getValue().intValue(), snapshot.fleetProfile()
                    .parametersFor(entry.getKey()).capacity());
            assertTrue(evaluation.isFeasible(), () -> entry.getKey() + ": " + evaluation.violations());
            assertLoadsValid(evaluation, snapshot);
        }
    }

    @Test
    void preventiveMaintenanceExcludesCarMotorcycleAndBicycle() {
        for (VehicleType type : VehicleType.values()) {
            Vehicle unavailable = vehicle(type, 1);
            Vehicle alternative = vehicle(type, 2);
            OperationalSnapshot snapshot = snapshot(List.of(unavailable, alternative),
                    List.of(new MaintenanceDay(unavailable.id(), LocalDate.of(2026, 9, 9))));
            Order order = order("MAINT-" + type, 2);
            OperationalPlan plan = new InitialPlanBuilder(evaluator()).build(
                    snapshot, List.of(order), CENTRAL, List.of()).plan();
            OperationalAnnealingResult result = new OperationalSimulatedAnnealingPlanner(evaluator(),
                    new OperationalRouteNeighborGenerator(), new Random(11)).optimize(
                    plan, snapshot, List.of(order), List.of(), new AnnealingConfig(100, 1, .9, 2, 10, 10));

            assertFalse(snapshot.isVehiclePlannableAt(unavailable.id(), START));
            assertTrue(snapshot.isVehiclePlannableAt(alternative.id(), START));
            assertTrue(result.bestPlan().routeForVehicle(unavailable.id()).isEmpty());
            assertTrue(result.bestPlan().routeForVehicle(alternative.id()).isPresent());
            assertTrue(result.bestEvaluation().isFeasible());
        }
    }

    private static void assertLoadsValid(PlanEvaluation evaluation, OperationalSnapshot snapshot) {
        evaluation.schedulesByRouteId().values().forEach(route -> {
            int capacity = snapshot.fleetProfile().parametersFor(route.route().vehicle().type()).capacity();
            route.scheduledStops().forEach(stop -> {
                assertTrue(stop.loadBefore() >= 0 && stop.loadAfter() >= 0);
                assertTrue(stop.loadBefore() <= capacity && stop.loadAfter() <= capacity);
            });
        });
    }

    private static Vehicle vehicle(VehicleType type, int number) {
        return new Vehicle(type.fleetCode() + String.format("%02d", number), type, true);
    }

    private static Order order(String id, int packages) {
        return new Order(id, new Location(28, 14), packages, START, START.plus(Duration.ofHours(8)));
    }

    private static OperationalSnapshot snapshot(List<Vehicle> vehicles, List<MaintenanceDay> maintenance) {
        Map<String, VehicleOperationalState> states = new TreeMap<>();
        for (Vehicle vehicle : vehicles) {
            states.put(vehicle.id(), new VehicleOperationalState(vehicle, VehicleStatus.AVAILABLE,
                    CENTRAL.location(), START));
        }
        return new OperationalSnapshot(START, FleetProfile.defaults(), InventorySnapshot.from(List.of(CENTRAL)),
                states, new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, maintenance),
                ShiftSchedule.defaultSchedule(), List.of());
    }

    private static OperationalPlanEvaluator evaluator() {
        return new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()));
    }
}
