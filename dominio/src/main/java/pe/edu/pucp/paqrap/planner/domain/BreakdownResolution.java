package pe.edu.pucp.paqrap.planner.domain;

import java.time.Instant;

/** Consequences calculated from a breakdown according to the course rules. */
public record BreakdownResolution(Instant unavailableUntil, Instant returnsToCentralAt) {
}
