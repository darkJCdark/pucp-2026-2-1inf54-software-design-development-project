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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryRouteSmokeTest {
    @Test
    void schedulesPartialDeliveriesReloadsAndReturns() {
        Instant start = Instant.parse("2026-09-09T12:00:00Z");
        Warehouse central = Warehouse.central("CENTRAL", PaqRapNetwork.CENTRAL_LOCATION);
        Warehouse east = Warehouse.intermediate("EAST", PaqRapNetwork.EAST_LOCATION, 1_000);
        Vehicle vehicle = new Vehicle("TA01", VehicleType.CAR, true);
        OperationalSnapshot snapshot = new OperationalSnapshot(start, FleetProfile.defaults(),
                InventorySnapshot.from(List.of(central, east)),
                Map.of(vehicle.id(), new VehicleOperationalState(vehicle, VehicleStatus.AVAILABLE,
                        central.location(), start)),
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()), ShiftSchedule.defaultSchedule(), List.of());
        Order order = new Order("P-1", new Location(28, 14), 10, start, start.plus(Duration.ofHours(4)));

        DeliveryRoute route = DeliveryRoute.startScenarioAtCentral("R-1", vehicle, central, 6, start)
                .withAppendedStop(new DeliveryStop(order, 2))
                .withAppendedStop(new WarehouseVisit(east, 4))
                .withAppendedStop(new DeliveryStop(order, 8))
                .returningTo(east);
        ScheduledDeliveryRoute scheduled = new RouteScheduler(new RoadNetwork()).schedule(route, snapshot, List.of());

        assertTrue(route.endsAtWarehouse());
        assertEquals(4, scheduled.scheduledStops().size());
        assertEquals(scheduled.scheduledStops().getFirst().arrivedAt().plus(Duration.ofHours(1)),
                scheduled.scheduledStops().getFirst().completedAt());
        assertEquals(scheduled.scheduledStops().get(1).arrivedAt(), scheduled.scheduledStops().get(1).completedAt());
        assertEquals(0, scheduled.scheduledStops().getLast().loadAfter());
        assertTrue(scheduled.scheduledStops().getFirst().arrivedAt().isBefore(order.deadline()));
        assertThrows(IllegalArgumentException.class,
                () -> DeliveryRoute.startScenarioAtCentral("R-2", vehicle, east, 1, start));
    }
}
