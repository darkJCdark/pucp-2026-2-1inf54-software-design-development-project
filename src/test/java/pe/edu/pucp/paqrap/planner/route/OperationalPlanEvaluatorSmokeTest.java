package pe.edu.pucp.paqrap.planner.route;

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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalPlanEvaluatorSmokeTest {
    @Test
    void validatesHardConstraintsAndBuildsInitialPlan() {
        Instant start = Instant.parse("2026-09-09T12:00:00Z");
        Warehouse central = Warehouse.central("CENTRAL", PaqRapNetwork.CENTRAL_LOCATION);
        Vehicle vehicle = new Vehicle("TA01", VehicleType.CAR, true);
        OperationalSnapshot snapshot = new OperationalSnapshot(start, FleetProfile.defaults(),
                InventorySnapshot.from(List.of(central)),
                Map.of(vehicle.id(), new VehicleOperationalState(vehicle, VehicleStatus.AVAILABLE,
                        central.location(), start)),
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()), ShiftSchedule.defaultSchedule(), List.of());
        Order order = new Order("P-1", new Location(28, 14), 10, start, start.plus(Duration.ofHours(4)));
        OperationalPlanEvaluator evaluator = new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()));
        DeliveryRoute route = DeliveryRoute.startScenarioAtCentral("R-1", vehicle, central, 6, start)
                .withAppendedStop(new DeliveryStop(order, 2))
                .withAppendedStop(new WarehouseVisit(central, 4))
                .withAppendedStop(new DeliveryStop(order, 8))
                .returningTo(central);
        PlanEvaluation evaluation = evaluator.evaluate(OperationalPlan.empty().withRoute(route), snapshot,
                List.of(order), List.of());
        assertTrue(evaluation.isFeasible());
        assertEquals(Integer.MAX_VALUE, evaluation.remainingInventory().availableStock("CENTRAL"));

        DeliveryRoute overCapacity = DeliveryRoute.startScenarioAtCentral("R-2", vehicle, central, 6, start)
                .withAppendedStop(new WarehouseVisit(central, 20))
                .returningTo(central);
        PlanEvaluation invalid = evaluator.evaluate(OperationalPlan.empty().withRoute(overCapacity), snapshot,
                List.of(), List.of());
        assertTrue(invalid.violations().stream().anyMatch(v -> v.type() == PlanViolationType.VEHICLE_CAPACITY));

        InitialPlanBuilder builder = new InitialPlanBuilder(evaluator);
        assertTrue(builder.build(snapshot, List.of(new Order("P-2", new Location(28, 14), 4, start,
                start.plus(Duration.ofHours(4)))), central, List.of()).isPresent());
    }
}
