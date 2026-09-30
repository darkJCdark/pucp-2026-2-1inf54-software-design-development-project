package pe.edu.pucp.paqrap.planner.domain;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/** The course-defined 07:00, 15:00 and 23:00 eight-hour shifts. */
public final class ShiftSchedule {
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Lima");

    public static final Duration MEAL_DURATION = Duration.ofHours(1);
    private final ZoneId zoneId;
    private final int mealBeamWidth;

    public ShiftSchedule(ZoneId zoneId) { this(zoneId, 8, true); }

    private ShiftSchedule(ZoneId zoneId, int mealBeamWidth, boolean flexible) {
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId is required");
        if (mealBeamWidth < 2 || mealBeamWidth > 32)
            throw new IllegalArgumentException("meal.beamWidth must be in 2..32");
        this.mealBeamWidth = mealBeamWidth;
    }

    /** There is deliberately NO fixed-clock meal constructor or optional-meal switch. */
    public static ShiftSchedule flexible(ZoneId zoneId, int beamWidth) {
        return new ShiftSchedule(zoneId, beamWidth, true);
    }
    public static ShiftSchedule defaultSchedule() { return new ShiftSchedule(DEFAULT_ZONE); }
    public ZoneId zoneId() { return zoneId; }
    public int mealBeamWidth() { return mealBeamWidth; }

    /** Latest legal START of an uninterrupted one-hour break in this shift. */
    public Instant latestMealStart(Instant instant) {
        return mealWindow(instant).endsAt().minus(MEAL_DURATION);
    }

    public ShiftWindow shiftAt(Instant instant) {
        Objects.requireNonNull(instant, "instant is required");
        ZonedDateTime local = instant.atZone(zoneId);
        LocalDate date = local.toLocalDate();
        LocalTime time = local.toLocalTime();
        if (!time.isBefore(LocalTime.of(7, 0)) && time.isBefore(LocalTime.of(15, 0))) {
            return window(date, 7, date, 15);
        }
        if (!time.isBefore(LocalTime.of(15, 0)) && time.isBefore(LocalTime.of(23, 0))) {
            return window(date, 15, date, 23);
        }
        if (!time.isBefore(LocalTime.of(23, 0))) {
            return window(date, 23, date.plusDays(1), 7);
        }
        return window(date.minusDays(1), 23, date, 7);
    }

    /** End of the shift immediately following the shift in progress. */
    public Instant endOfNextShift(Instant instant) {
        return shiftAt(instant).endsAt().plusSeconds(8 * 60 * 60L);
    }

    /** Full break must lie inside [shift start + 1h, shift end - 1h].
     * This implements the course wording with one-hour margins at both shift changes.
     * For 07-15, legal STARTS are 08-13, not a mandatory clock time. */
    public ShiftWindow mealWindow(Instant instant) {
        ShiftWindow shift = shiftAt(instant);
        return new ShiftWindow(shift.startsAt().plusSeconds(3600), shift.startsAt().plusSeconds(7 * 3600));
    }

    /** First 15:00 start that is not before the supplied instant. */
    public Instant nextAfternoonShiftStartAtOrAfter(Instant instant) {
        ZonedDateTime local = instant.atZone(zoneId);
        LocalDate targetDate = local.toLocalDate();
        ZonedDateTime todayAtThree = ZonedDateTime.of(LocalDateTime.of(targetDate, LocalTime.of(15, 0)), zoneId);
        if (todayAtThree.toInstant().isBefore(instant)) {
            todayAtThree = todayAtThree.plusDays(1);
        }
        return todayAtThree.toInstant();
    }

    private ShiftWindow window(LocalDate startDate, int startHour, LocalDate endDate, int endHour) {
        Instant starts = ZonedDateTime.of(startDate, LocalTime.of(startHour, 0), zoneId).toInstant();
        Instant ends = ZonedDateTime.of(endDate, LocalTime.of(endHour, 0), zoneId).toInstant();
        return new ShiftWindow(starts, ends);
    }

    public record ShiftWindow(Instant startsAt, Instant endsAt) {
        public ShiftWindow {
            if (!endsAt.isAfter(startsAt)) {
                throw new IllegalArgumentException("Shift end must be after its start");
            }
        }
    }
}
