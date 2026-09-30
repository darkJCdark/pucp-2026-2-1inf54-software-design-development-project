package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.Location;
import java.time.Instant;
import java.util.Objects;

/** A real, uninterrupted, stationary one-hour break, assigned to ONE driver shift. */
public record ScheduledMealBreak(Instant shiftStart, Instant startsAt, Instant endsAt,
                                 Location location, Placement placement) {
    public enum Placement { BEFORE_ROUTE, DURING_ROUTE, AFTER_ROUTE }
    public ScheduledMealBreak {
        Objects.requireNonNull(shiftStart); Objects.requireNonNull(startsAt);
        Objects.requireNonNull(endsAt); Objects.requireNonNull(location); Objects.requireNonNull(placement);
        if (!endsAt.isAfter(startsAt)) throw new IllegalArgumentException("Nonpositive meal duration");
    }
}
