package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.RoadPath;

import java.time.Instant;
import java.util.Objects;

/** Timing and vehicle load before and after executing one planned stop. */
public record ScheduledRouteStop(RouteStop stop, RoadPath approach, Instant arrivedAt, Instant completedAt,
                                 int loadBefore, int loadAfter) {
    public ScheduledRouteStop {
        Objects.requireNonNull(stop, "stop is required");
        Objects.requireNonNull(approach, "approach is required");
        Objects.requireNonNull(arrivedAt, "arrivedAt is required");
        Objects.requireNonNull(completedAt, "completedAt is required");
        if (completedAt.isBefore(arrivedAt)) {
            throw new IllegalArgumentException("Stop cannot complete before arrival");
        }
    }
}
