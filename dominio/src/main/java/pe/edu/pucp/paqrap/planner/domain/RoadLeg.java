package pe.edu.pucp.paqrap.planner.domain;

import java.time.Instant;
import java.util.Objects;

/** An executed or planned one-kilometre move, including any wait before departure. */
public record RoadLeg(Location from, Location to, Instant departsAt, Instant arrivesAt) {
    public RoadLeg {
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(to, "to is required");
        Objects.requireNonNull(departsAt, "departsAt is required");
        Objects.requireNonNull(arrivesAt, "arrivesAt is required");
        new StreetSegment(from, to);
        if (!arrivesAt.isAfter(departsAt)) {
            throw new IllegalArgumentException("A road leg must have positive travel time");
        }
    }
}
