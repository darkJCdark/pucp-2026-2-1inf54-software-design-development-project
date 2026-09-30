package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.RoadPath;
import java.time.Instant;
import java.util.Objects;

/** Arrival meets the SLA; serviceStart/completion expose service and meal separately. */
public record ScheduledRouteStop(RouteStop stop, RoadPath approach, Instant arrivedAt,
                                 Instant serviceStartedAt, Instant completedAt, int loadBefore, int loadAfter) {
    public ScheduledRouteStop {
        Objects.requireNonNull(stop); Objects.requireNonNull(approach); Objects.requireNonNull(arrivedAt);
        Objects.requireNonNull(serviceStartedAt); Objects.requireNonNull(completedAt);
        if (serviceStartedAt.isBefore(arrivedAt) || completedAt.isBefore(serviceStartedAt))
            throw new IllegalArgumentException("Invalid stop timeline");
    }
    public ScheduledRouteStop(RouteStop stop, RoadPath approach, Instant arrivedAt, Instant completedAt,
                              int loadBefore, int loadAfter) {
        this(stop, approach, arrivedAt, arrivedAt, completedAt, loadBefore, loadAfter);
    }
}
