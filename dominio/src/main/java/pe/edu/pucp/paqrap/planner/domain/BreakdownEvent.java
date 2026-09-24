package pe.edu.pucp.paqrap.planner.domain;

import java.time.Instant;
import java.util.Objects;

/** An event; its effective availability window is calculated in phase 2 from shifts and current time. */
public record BreakdownEvent(String vehicleId, BreakdownType type, Instant occurredAt, Location location) {
    public BreakdownEvent {
        Objects.requireNonNull(vehicleId, "vehicleId is required");
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(location, "location is required");
    }
}
