package pe.edu.pucp.paqrap.planner.domain;

import java.time.Instant;
import java.util.Objects;

/** Position and operational readiness of one vehicle at a planning instant. */
public record VehicleOperationalState(Vehicle vehicle, VehicleStatus status,
                                      Location location, int carriedPackages, Instant availableAt) {
    public VehicleOperationalState {
        Objects.requireNonNull(vehicle, "vehicle is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(location, "location is required");
        Objects.requireNonNull(availableAt, "availableAt is required");
        if (carriedPackages < 0 || carriedPackages > vehicle.type().capacity()) {
            throw new IllegalArgumentException("Current vehicle load must fit its capacity");
        }
    }

    public VehicleOperationalState(Vehicle vehicle, VehicleStatus status, Location location, Instant availableAt) {
        this(vehicle, status, location, 0, availableAt);
    }

    /** Vehicles in route remain replannable from their current location. */
    public boolean isPlannableAt(Instant instant) {
        return vehicle.available() && status != VehicleStatus.OUT_OF_SERVICE && !instant.isBefore(availableAt);
    }
}
