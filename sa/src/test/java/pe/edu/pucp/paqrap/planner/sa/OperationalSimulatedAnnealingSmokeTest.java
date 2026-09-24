package pe.edu.pucp.paqrap.planner.sa;

import org.junit.jupiter.api.Test;
import pe.edu.pucp.paqrap.planner.domain.FleetProfile;
import pe.edu.pucp.paqrap.planner.domain.InventorySnapshot;
import pe.edu.pucp.paqrap.planner.domain.Location;
import pe.edu.pucp.paqrap.planner.domain.MaintenanceCalendar;
import pe.edu.pucp.paqrap.planner.domain.OperationalSnapshot;
import pe.edu.pucp.paqrap.planner.domain.Order;
import pe.edu.pucp.paqrap.planner.domain.PaqRapNetwork;
import pe.edu.pucp.paqrap.planner.domain.RoadNetwork;
import pe.edu.pucp.paqrap.planner.domain.ShiftSchedule;
import pe.edu.pucp.paqrap.planner.domain.Vehicle;
import pe.edu.pucp.paqrap.planner.domain.VehicleOperationalState;
import pe.edu.pucp.paqrap.planner.domain.VehicleStatus;
import pe.edu.pucp.paqrap.planner.domain.VehicleType;
import pe.edu.pucp.paqrap.planner.domain.Warehouse;
import pe.edu.pucp.paqrap.planner.route.DeliveryRoute;
import pe.edu.pucp.paqrap.planner.route.DeliveryStop;
import pe.edu.pucp.paqrap.planner.route.OperationalPlan;
import pe.edu.pucp.paqrap.planner.route.OperationalPlanEvaluator;
import pe.edu.pucp.paqrap.planner.route.RouteScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.Map;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Framework-free verification that SA searches only operationally feasible candidates. */
class OperationalSimulatedAnnealingSmokeTest {
    @Test
    void searchesOnlyOperationallyFeasibleCandidates() {
        Instant start = Instant.parse("2026-09-09T12:00:00Z");
        Warehouse central = Warehouse.central("CENTRAL", PaqRapNetwork.CENTRAL_LOCATION);
        Vehicle car = new Vehicle("TA01", VehicleType.CAR, true);
        Vehicle motorcycle = new Vehicle("TM01", VehicleType.MOTORCYCLE, true);
        OperationalSnapshot snapshot = new OperationalSnapshot(start, FleetProfile.defaults(),
                InventorySnapshot.from(List.of(central)),
                Map.of(car.id(), new VehicleOperationalState(car, VehicleStatus.AVAILABLE, central.location(), start),
                        motorcycle.id(), new VehicleOperationalState(motorcycle, VehicleStatus.AVAILABLE,
                                central.location(), start)),
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()), ShiftSchedule.defaultSchedule(), List.of());
        Order first = new Order("P-1", new Location(28, 14), 2, start, start.plus(Duration.ofHours(4)));
        Order second = new Order("P-2", new Location(29, 14), 2, start, start.plus(Duration.ofHours(4)));
        OperationalPlan initial = OperationalPlan.empty()
                .withRoute(DeliveryRoute.startScenarioAtCentral("R-CAR", car, central, 2, start)
                        .withAppendedStop(new DeliveryStop(first, 2)).returningTo(central))
                .withRoute(DeliveryRoute.startScenarioAtCentral("R-MOTO", motorcycle, central, 2, start)
                        .withAppendedStop(new DeliveryStop(second, 2)).returningTo(central));
        OperationalPlanEvaluator evaluator = new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()));
        var random = RandomGeneratorFactory.<RandomGenerator>of("L64X128MixRandom").create(7L);
        OperationalSimulatedAnnealingPlanner planner = new OperationalSimulatedAnnealingPlanner(evaluator,
                new OperationalRouteNeighborGenerator(), random);
        OperationalAnnealingResult result = planner.optimize(initial, snapshot, List.of(first, second), List.of(),
                new AnnealingConfig(100.0, 1.0, 0.90, 10, 100, 30));
        assertTrue(result.bestEvaluation().isFeasible());
        assertTrue(result.bestEvaluation().totalCost() <= evaluator.evaluate(initial, snapshot, List.of(first, second),
                List.of()).totalCost());
    }
}
