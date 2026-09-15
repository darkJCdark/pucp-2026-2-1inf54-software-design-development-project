package pe.edu.pucp.paqrap.planner.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Derives availability and automatic return moments; transfer duration remains intentionally unconfigured. */
public final class BreakdownAvailabilityCalculator {
    private static final Duration AUTOMATIC_RETURN_DELAY = Duration.ofHours(4);

    private final ShiftSchedule shiftSchedule;

    public BreakdownAvailabilityCalculator(ShiftSchedule shiftSchedule) {
        this.shiftSchedule = Objects.requireNonNull(shiftSchedule, "shiftSchedule is required");
    }

    public BreakdownResolution resolve(BreakdownEvent event) {
        Objects.requireNonNull(event, "event is required");
        Instant occurredAt = event.occurredAt();
        return switch (event.type()) {
            case MINOR -> new BreakdownResolution(occurredAt.plus(BreakdownType.MINOR.minimumUnavailable()), null);
            case INTERMEDIATE -> new BreakdownResolution(
                    min(shiftSchedule.endOfNextShift(occurredAt), occurredAt.plus(BreakdownType.INTERMEDIATE.minimumUnavailable())),
                    occurredAt.plus(AUTOMATIC_RETURN_DELAY));
            case MAJOR -> new BreakdownResolution(
                    shiftSchedule.nextAfternoonShiftStartAtOrAfter(occurredAt.plus(BreakdownType.MAJOR.minimumUnavailable())),
                    occurredAt.plus(AUTOMATIC_RETURN_DELAY));
        };
    }

    private Instant min(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }
}
