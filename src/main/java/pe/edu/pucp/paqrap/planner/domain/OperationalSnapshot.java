package pe.edu.pucp.paqrap.planner.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Complete immutable input state consumed by exactly one planner iteration. */
public final class OperationalSnapshot {
    private final Instant planningTime;
    private final FleetProfile fleetProfile;
    private final InventorySnapshot inventory;
    private final Map<String, VehicleOperationalState> vehiclesById;
    private final MaintenanceCalendar maintenanceCalendar;
    private final ShiftSchedule shiftSchedule;
    private final BreakdownAvailabilityCalculator breakdownCalculator;
    private final List<BreakdownEvent> breakdowns;

    public OperationalSnapshot(Instant planningTime, FleetProfile fleetProfile, InventorySnapshot inventory,
                               Map<String, VehicleOperationalState> vehiclesById,
                               MaintenanceCalendar maintenanceCalendar, ShiftSchedule shiftSchedule,
                               List<BreakdownEvent> breakdowns) {
        this.planningTime = Objects.requireNonNull(planningTime, "planningTime is required");
        this.fleetProfile = Objects.requireNonNull(fleetProfile, "fleetProfile is required");
        this.inventory = Objects.requireNonNull(inventory, "inventory is required");
        this.vehiclesById = Map.copyOf(Objects.requireNonNull(vehiclesById, "vehiclesById is required"));
        this.maintenanceCalendar = Objects.requireNonNull(maintenanceCalendar, "maintenanceCalendar is required");
        this.shiftSchedule = Objects.requireNonNull(shiftSchedule, "shiftSchedule is required");
        this.breakdownCalculator = new BreakdownAvailabilityCalculator(this.shiftSchedule);
        this.breakdowns = List.copyOf(Objects.requireNonNull(breakdowns, "breakdowns are required"));
        this.vehiclesById.forEach((id, state) -> {
            if (!id.equals(state.vehicle().id())) {
                throw new IllegalArgumentException("Vehicle state key must match vehicle id");
            }
        });
    }

    public Instant planningTime() { return planningTime; }
    public FleetProfile fleetProfile() { return fleetProfile; }
    public InventorySnapshot inventory() { return inventory; }
    public Map<String, VehicleOperationalState> vehiclesById() { return vehiclesById; }
    public ShiftSchedule shiftSchedule() { return shiftSchedule; }
    public MaintenanceCalendar maintenanceCalendar() { return maintenanceCalendar; }
    public List<BreakdownEvent> breakdowns() { return breakdowns; }

    public boolean isVehiclePlannable(String vehicleId) {
        return isVehiclePlannableAt(vehicleId, planningTime);
    }

    public boolean isVehiclePlannableAt(String vehicleId, Instant instant) {
        Objects.requireNonNull(instant, "instant is required");
        VehicleOperationalState state = vehiclesById.get(vehicleId);
        if (state == null || !state.isPlannableAt(instant)
                || maintenanceCalendar.isUnavailable(vehicleId, instant)) {
            return false;
        }
        return breakdowns.stream()
                .filter(event -> event.vehicleId().equals(vehicleId))
                .noneMatch(event -> !instant.isBefore(event.occurredAt())
                        && instant.isBefore(breakdownCalculator.resolve(event).unavailableUntil()));
    }

    public boolean hasVehicleDisruptionDuring(String vehicleId, Instant fromInclusive, Instant toExclusive) {
        if (maintenanceCalendar.isUnavailableDuring(vehicleId, fromInclusive, toExclusive)) {
            return true;
        }
        return breakdowns.stream().filter(event -> event.vehicleId().equals(vehicleId))
                .anyMatch(event -> fromInclusive.isBefore(breakdownCalculator.resolve(event).unavailableUntil())
                        && toExclusive.isAfter(event.occurredAt()));
    }
}
