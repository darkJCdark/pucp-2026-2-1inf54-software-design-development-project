package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.*;
import java.time.*;
import java.util.*;
import static pe.edu.pucp.paqrap.planner.route.ScheduledMealBreak.Placement.*;

/** Copyable state of the driver-shift meals for one scheduling label.
 * Decisions are route-local: not one global lunch time for the fleet.
 * A compulsory latest-start safeguard acts at street nodes and before indivisible service.
 */
final class FlexibleMealClock {
    private final ShiftSchedule shifts;
    private final List<ScheduledMealBreak> breaks;
    FlexibleMealClock(ShiftSchedule shifts, DeliveryRoute route) {
        this.shifts = shifts; this.breaks = new ArrayList<>();
        // Static-batch initial-state convention, EXPLICIT in the instance manifest:
        // a scenario-start driver was idle at Central before this snapshot. If a full legal
        // hour fits before departure, record it rather than assuming a missing meal away.
        // Replans from a moving/current location do NOT receive this credit.
        if (route.initialWarehouse().isPresent()) {
            var window = shifts.mealWindow(route.departureAt());
            Instant end = min(route.departureAt(), window.endsAt());
            if (!end.minus(ShiftSchedule.MEAL_DURATION).isBefore(window.startsAt()))
                take(end.minus(ShiftSchedule.MEAL_DURATION), route.startLocation(), BEFORE_ROUTE);
        }
    }
    private FlexibleMealClock(ShiftSchedule shifts, List<ScheduledMealBreak> breaks) {
        this.shifts=shifts; this.breaks=new ArrayList<>(breaks);
    }
    FlexibleMealClock copy() { return new FlexibleMealClock(shifts,breaks); }
    List<ScheduledMealBreak> breaks() { return List.copyOf(breaks); }
    boolean taken(Instant instant) {
        Instant shift = shifts.shiftAt(instant).startsAt();
        return breaks.stream().anyMatch(b -> b.shiftStart().equals(shift));
    }
    String stateKey(Instant time) { return shifts.shiftAt(time).startsAt()+":"+taken(time); }

    /** Optional placement explored by the beam, not an optional obligation. */
    Optional<Instant> takeNow(Instant time, Location location) {
        if (taken(time)) return Optional.empty();
        Instant start = max(time, shifts.mealWindow(time).startsAt());
        if (start.isAfter(shifts.latestMealStart(time))) return Optional.empty();
        return Optional.of(take(start,location,DURING_ROUTE));
    }

    /** An idle/blocking wait can contain the meal. It is never counted twice as work. */
    Instant idleUntil(Instant from, Instant until, Location location) {
        if (until.isBefore(from)) throw new IllegalArgumentException("Backwards idle interval");
        Instant end=until;
        for (Instant shiftStart=shifts.shiftAt(from).startsAt(); shiftStart.isBefore(end);
             shiftStart=shifts.shiftAt(shiftStart).endsAt()) {
            if (taken(shiftStart)) continue;
            var window=shifts.mealWindow(shiftStart);
            Instant begin=max(from,window.startsAt());
            Instant freeEnd=min(end,window.endsAt());
            if (!begin.plus(ShiftSchedule.MEAL_DURATION).isAfter(freeEnd)) {
                take(begin,location,DURING_ROUTE);
            } else if (end.isAfter(shifts.latestMealStart(shiftStart)) && !begin.isAfter(shifts.latestMealStart(shiftStart))) {
                end=max(end,take(begin,location,DURING_ROUTE));
            } else if (end.isAfter(window.endsAt()) && !taken(shiftStart)) {
                throw new MealSchedulingException("No legal meal before shift limit: "+shiftStart);
            }
        }
        return end;
    }

    /** Earliest admissible start of one uninterrupted work action for this label. */
    Instant workStart(Instant requested, Duration duration, Location location) {
        if (duration.isNegative() || duration.compareTo(Duration.ofHours(1))>0)
            throw new IllegalArgumentException("Work atom must last 0..60 min (one street or one service)");
        if (duration.isZero()) return requested;
        if (taken(requested)) return requested;
        Instant latest=shifts.latestMealStart(requested);
        if (!requested.plus(duration).isAfter(latest)) return requested;
        Instant start=max(requested,shifts.mealWindow(requested).startsAt());
        if (start.isAfter(latest))
            throw new MealSchedulingException("Missing mandatory meal / initial history at "+requested);
        return take(start,location,DURING_ROUTE);
    }

    Instant finish(Instant returnedAt, Instant departureAt, Location location) {
        Instant reference=returnedAt.isAfter(departureAt)?returnedAt.minusNanos(1):departureAt;
        if (taken(reference)) return returnedAt;
        Instant start=max(returnedAt,shifts.mealWindow(reference).startsAt());
        if (start.isAfter(shifts.latestMealStart(reference)))
            throw new MealSchedulingException("Route ends without a legal mandatory meal for "+shifts.shiftAt(reference).startsAt());
        return take(start,location,AFTER_ROUTE);
    }

    private Instant take(Instant start, Location location, ScheduledMealBreak.Placement placement) {
        var window=shifts.mealWindow(start);
        Instant end=start.plus(ShiftSchedule.MEAL_DURATION);
        if (taken(start) || start.isBefore(window.startsAt()) || end.isAfter(window.endsAt()))
            throw new MealSchedulingException("Duplicate/out-of-window meal at "+start);
        breaks.add(new ScheduledMealBreak(shifts.shiftAt(start).startsAt(),start,end,location,placement));
        return end;
    }
    private static Instant min(Instant a,Instant b) { return a.isBefore(b)?a:b; }
    private static Instant max(Instant a,Instant b) { return a.isAfter(b)?a:b; }
}
