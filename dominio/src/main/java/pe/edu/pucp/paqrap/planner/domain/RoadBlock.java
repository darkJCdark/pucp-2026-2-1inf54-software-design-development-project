package pe.edu.pucp.paqrap.planner.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A planned open polyline of blocked street segments, active during a defined interval. */
public record RoadBlock(Instant startsAt, Instant endsAt, List<Location> nodes) {
    public RoadBlock {
        Objects.requireNonNull(startsAt, "startsAt is required");
        Objects.requireNonNull(endsAt, "endsAt is required");
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes are required"));
        if (!endsAt.isAfter(startsAt) || nodes.size() < 2) {
            throw new IllegalArgumentException("A road block needs an interval and at least one blocked segment");
        }
        for (int index = 1; index < nodes.size(); index++) {
            Location previous = nodes.get(index - 1);
            Location next = nodes.get(index);
            if (previous.x() != next.x() && previous.y() != next.y()) {
                throw new IllegalArgumentException("Blocked polyline segments must be horizontal or vertical");
            }
            if (previous.equals(next)) {
                throw new IllegalArgumentException("Blocked polyline points cannot be repeated consecutively");
            }
        }
    }

    public boolean isActiveAt(Instant instant) {
        return !instant.isBefore(startsAt) && instant.isBefore(endsAt);
    }

    public boolean overlaps(Instant fromInclusive, Instant toExclusive) {
        Objects.requireNonNull(fromInclusive, "fromInclusive is required");
        Objects.requireNonNull(toExclusive, "toExclusive is required");
        if (!toExclusive.isAfter(fromInclusive)) {
            throw new IllegalArgumentException("An interval must have positive duration");
        }
        return fromInclusive.isBefore(endsAt) && toExclusive.isAfter(startsAt);
    }

    /** Expands endpoint notation such as (31,21)-(34,21) into every blocked grid node. */
    public Set<Location> blockedNodes() {
        Set<Location> expanded = new LinkedHashSet<>();
        for (int index = 1; index < nodes.size(); index++) {
            Location current = nodes.get(index - 1);
            Location target = nodes.get(index);
            expanded.add(current);
            int dx = Integer.compare(target.x(), current.x());
            int dy = Integer.compare(target.y(), current.y());
            while (!current.equals(target)) {
                Location next = new Location(current.x() + dx, current.y() + dy);
                expanded.add(next);
                current = next;
            }
        }
        return Set.copyOf(expanded);
    }

    public Set<StreetSegment> blockedSegments() {
        Set<StreetSegment> expanded = new LinkedHashSet<>();
        for (int index = 1; index < nodes.size(); index++) {
            Location current = nodes.get(index - 1);
            Location target = nodes.get(index);
            int dx = Integer.compare(target.x(), current.x());
            int dy = Integer.compare(target.y(), current.y());
            while (!current.equals(target)) {
                Location next = new Location(current.x() + dx, current.y() + dy);
                expanded.add(new StreetSegment(current, next));
                current = next;
            }
        }
        return Set.copyOf(expanded);
    }
}
