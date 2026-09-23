package pe.edu.pucp.paqrap.planner.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/** The course-defined 07:00, 15:00 and 23:00 eight-hour shifts. */
public final class ShiftSchedule {
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Lima");

    private final ZoneId zoneId;

    public ShiftSchedule(ZoneId zoneId) {
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId is required");
    }

    public static ShiftSchedule defaultSchedule() {
        return new ShiftSchedule(DEFAULT_ZONE);
    }

    public ZoneId zoneId() { return zoneId; }

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

    /** The 6-hour meal window inside the shift containing `instant`: from
     *  1 hour after shift start to 1 hour before shift end (>=1h margin on
     *  either side of a shift change, per the course-confirmed rule). */
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
