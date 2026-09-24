package pe.edu.pucp.paqrap.planner.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Holds pending speed changes and applies them only when the next iteration begins. */
public final class PlanningIterationCoordinator {
    private OperationalSnapshot current;
    private FleetProfile pendingFleetProfile;

    public PlanningIterationCoordinator(OperationalSnapshot initial) {
        this.current = Objects.requireNonNull(initial, "initial snapshot is required");
    }

    public void requestSpeedChange(VehicleType type, double speedKmPerHour) {
        FleetProfile base = pendingFleetProfile == null ? current.fleetProfile() : pendingFleetProfile;
        pendingFleetProfile = base.withSpeed(type, speedKmPerHour);
    }

    public OperationalSnapshot beginNextIteration(Instant planningTime, InventorySnapshot inventory,
                                                   Map<String, VehicleOperationalState> vehicleStates,
                                                   MaintenanceCalendar maintenanceCalendar,
                                                   ShiftSchedule shiftSchedule,
                                                   List<BreakdownEvent> breakdowns) {
        if (!planningTime.isAfter(current.planningTime())) {
            throw new IllegalArgumentException("Next iteration must advance the planning time");
        }
        FleetProfile activeProfile = pendingFleetProfile == null ? current.fleetProfile() : pendingFleetProfile;
        current = new OperationalSnapshot(planningTime, activeProfile, inventory, vehicleStates,
                maintenanceCalendar, shiftSchedule, breakdowns, Double.isFinite(current.maximumLegDistanceKm()) ? current.maximumLegDistanceKm() : 0);
        pendingFleetProfile = null;
        return current;
    }

    public OperationalSnapshot current() { return current; }
}
