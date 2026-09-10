package pe.edu.pucp.paqrap.planner.replan;

import org.junit.jupiter.api.Test;
import pe.edu.pucp.paqrap.planner.domain.FleetProfile;
import pe.edu.pucp.paqrap.planner.domain.BreakdownEvent;
import pe.edu.pucp.paqrap.planner.domain.BreakdownType;
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
import pe.edu.pucp.paqrap.planner.route.InitialPlanBuilder;
import pe.edu.pucp.paqrap.planner.route.OperationalPlan;
import pe.edu.pucp.paqrap.planner.route.OperationalPlanEvaluator;
import pe.edu.pucp.paqrap.planner.route.ReplanningPlanBuilder;
import pe.edu.pucp.paqrap.planner.route.RouteProgressProjector;
import pe.edu.pucp.paqrap.planner.route.RouteScheduler;
import pe.edu.pucp.paqrap.planner.sa.AnnealingConfig;
import pe.edu.pucp.paqrap.planner.sa.OperationalSimulatedAnnealingPlanner;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Framework-free end-to-end test: project an in-flight route, change speed and rebuild pending work. */
class DynamicReplanningSmokeTest {
    @Test
    void projectsEventsAndRebuildsOperationalPlans() {
        Instant start = Instant.parse("2026-09-09T12:00:00Z");
        Warehouse central = Warehouse.central("CENTRAL", PaqRapNetwork.CENTRAL_LOCATION);
        Vehicle vehicle = new Vehicle("TA01", VehicleType.CAR, true);
        OperationalSnapshot snapshot = new OperationalSnapshot(start, FleetProfile.defaults(),
                InventorySnapshot.from(List.of(central)),
                Map.of(vehicle.id(), new VehicleOperationalState(vehicle, VehicleStatus.AVAILABLE,
                        central.location(), start)),
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()), ShiftSchedule.defaultSchedule(), List.of());
        Order order = new Order("P-1", new Location(30, 14), 2, start, start.plus(Duration.ofHours(4)));
        OperationalPlan active = OperationalPlan.empty().withRoute(
                DeliveryRoute.startScenarioAtCentral("R-1", vehicle, central, 2, start)
                        .withAppendedStop(new DeliveryStop(order, 2)).returningTo(central));
        RouteScheduler scheduler = new RouteScheduler(new RoadNetwork());
        OperationalPlanEvaluator evaluator = new OperationalPlanEvaluator(scheduler);
        DynamicReplanningService service = new DynamicReplanningService(new RouteProgressProjector(scheduler),
                new ReplanningPlanBuilder(evaluator));

        ReplanningResult result = service.replan(active, snapshot, List.of(order), central, List.of(),
                new SpeedChangeEvent(start.plus(Duration.ofMinutes(1)), VehicleType.CAR, 20.0));
        assertEquals(20.0, result.nextSnapshot().fleetProfile().parametersFor(VehicleType.CAR).speedKmPerHour());
        assertEquals(1, result.pendingOrders().size());
        assertEquals(2, result.pendingOrders().getFirst().packages());
        assertTrue(result.replannedPlan().isPresent());
        assertTrue(evaluator.evaluate(result.replannedPlan().orElseThrow(), result.nextSnapshot(), result.pendingOrders(),
                List.of()).isFeasible());

        OperationalSimulatedAnnealingPlanner noMovePlanner = new OperationalSimulatedAnnealingPlanner(evaluator,
                (plan, state, random) -> Optional.empty(), RandomGenerator.getDefault());
        ReplanningResult optimizedResult = service.replanAndOptimize(active, snapshot, List.of(order), central, List.of(),
                new SpeedChangeEvent(start.plus(Duration.ofMinutes(1)), VehicleType.CAR, 20.0), noMovePlanner,
                new AnnealingConfig(10.0, 1.0, 0.90, 2, 10, 5));
        assertTrue(optimizedResult.replannedPlan().isPresent());

        Order regularOrder = new Order("P-2", new Location(30, 14), 2, start, start.plus(Duration.ofHours(36)));
        ReplanningResult afterBreakdown = service.replan(OperationalPlan.empty(), snapshot, List.of(regularOrder), central,
                List.of(), new VehicleBreakdownEvent(new BreakdownEvent("TA01", BreakdownType.INTERMEDIATE,
                        start.plus(Duration.ofMinutes(1)), central.location())));
        ReplanningResult afterRepair = service.replan(OperationalPlan.empty(), afterBreakdown.nextSnapshot(),
                afterBreakdown.pendingOrders(), central, List.of(),
                new RoadBlockChangeEvent(start.plus(Duration.ofHours(5))));
        assertEquals(VehicleStatus.AVAILABLE, afterRepair.nextSnapshot().vehiclesById().get("TA01").status());
        assertEquals(central.location(), afterRepair.nextSnapshot().vehiclesById().get("TA01").location());
    }
}
