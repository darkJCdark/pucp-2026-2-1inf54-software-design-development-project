package pe.edu.pucp.paqrap.planner.replan;

import pe.edu.pucp.paqrap.planner.domain.VehicleType;

import java.time.Instant;
import java.util.Objects;

public record SpeedChangeEvent(Instant occurredAt, VehicleType vehicleType, double speedKmPerHour)
        implements ReplanningEvent {
    public SpeedChangeEvent {
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(vehicleType, "vehicleType is required");
        if (speedKmPerHour <= 0) {
            throw new IllegalArgumentException("Speed must be positive");
        }
    }
}
