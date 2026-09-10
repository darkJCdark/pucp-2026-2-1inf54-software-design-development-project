package pe.edu.pucp.paqrap.planner.replan;

import java.time.Instant;
import java.util.Objects;

/** Signals that the caller has supplied a new active set of planned road blocks. */
public record RoadBlockChangeEvent(Instant occurredAt) implements ReplanningEvent {
    public RoadBlockChangeEvent {
        Objects.requireNonNull(occurredAt, "occurredAt is required");
    }
}
