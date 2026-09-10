package pe.edu.pucp.paqrap.planner.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalSnapshotSmokeTest {
    @Test
    void maintainsImmutableOperationalState() {
        Instant start = Instant.parse("2026-09-09T12:00:00Z");
        Warehouse central = Warehouse.central("CENTRAL", PaqRapNetwork.CENTRAL_LOCATION);
        Warehouse northWest = Warehouse.intermediate("NORTH_WEST", PaqRapNetwork.NORTH_WEST_LOCATION, 10);
        InventorySnapshot inventory = InventorySnapshot.from(List.of(central, northWest));
        InventorySnapshot afterWithdrawal = inventory.withdraw("NORTH_WEST", 4);
        assertEquals(10, inventory.availableStock("NORTH_WEST"));
        assertEquals(6, afterWithdrawal.availableStock("NORTH_WEST"));
        assertEquals(1_000, afterWithdrawal.reloadIntermediateWarehouses().availableStock("NORTH_WEST"));

        Vehicle vehicle = new Vehicle("TA01", VehicleType.CAR, true);
        VehicleOperationalState state = new VehicleOperationalState(vehicle, VehicleStatus.AVAILABLE,
                central.location(), start);
        MaintenanceCalendar noMaintenance = new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of());
        OperationalSnapshot snapshot = new OperationalSnapshot(start, FleetProfile.defaults(), inventory,
                Map.of(vehicle.id(), state), noMaintenance, ShiftSchedule.defaultSchedule(), List.of());
        PlanningIterationCoordinator coordinator = new PlanningIterationCoordinator(snapshot);
        coordinator.requestSpeedChange(VehicleType.CAR, 30.0);
        assertEquals(40.0, coordinator.current().fleetProfile().parametersFor(VehicleType.CAR).speedKmPerHour());
        OperationalSnapshot next = coordinator.beginNextIteration(start.plusSeconds(60), inventory,
                Map.of(vehicle.id(), state), noMaintenance, ShiftSchedule.defaultSchedule(), List.of());
        assertEquals(30.0, next.fleetProfile().parametersFor(VehicleType.CAR).speedKmPerHour());

        MaintenanceCalendar maintenance = new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE,
                List.of(new MaintenanceDay("TA01", LocalDate.of(2026, 9, 9))));
        assertTrue(maintenance.isUnavailable("TA01", start));
        BreakdownResolution minor = new BreakdownAvailabilityCalculator(ShiftSchedule.defaultSchedule())
                .resolve(new BreakdownEvent("TA01", BreakdownType.MINOR, start, central.location()));
        assertEquals(start.plusSeconds(7_200), minor.unavailableUntil());
    }
}
