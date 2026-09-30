package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.*;
import java.time.*;
import java.util.*;

/** Independent invariant check; never delegates validation to the meal scheduler/clock. */
public final class MealBreakAudit {
    private MealBreakAudit() {}
    public static List<String> validate(ScheduledDeliveryRoute schedule,ShiftSchedule shifts) {
        List<String> errors=new ArrayList<>();
        Map<Instant,Integer> counts=new HashMap<>();
        List<RoadLeg> legs=schedule.scheduledStops().stream().flatMap(s->s.approach().legs().stream()).toList();
        List<ScheduledMealBreak> sorted=new ArrayList<>(schedule.mealBreaks());
        sorted.sort(Comparator.comparing(ScheduledMealBreak::startsAt));
        Instant previousEnd=null;
        for(ScheduledMealBreak b:sorted) {
            var shift=shifts.shiftAt(b.startsAt());var window=shifts.mealWindow(b.startsAt());
            counts.merge(b.shiftStart(),1,Integer::sum);
            if(!b.shiftStart().equals(shift.startsAt()))errors.add("Meal assigned to wrong shift");
            if(!Duration.between(b.startsAt(),b.endsAt()).equals(ShiftSchedule.MEAL_DURATION))errors.add("Meal must last exactly 60 uninterrupted minutes");
            if(b.startsAt().isBefore(window.startsAt()) || b.endsAt().isAfter(window.endsAt()))errors.add("Meal violates shift margins");
            if(previousEnd!=null && b.startsAt().isBefore(previousEnd))errors.add("Overlapping meals");
            previousEnd=b.endsAt();
            Location expected=schedule.route().startLocation();
            for(RoadLeg leg:legs) {
                if(overlap(b.startsAt(),b.endsAt(),leg.departsAt(),leg.arrivesAt()))errors.add("Driving during a meal");
                if(!leg.arrivesAt().isAfter(b.startsAt()))expected=leg.to();
            }
            if(!expected.equals(b.location()))errors.add("Meal location differs from vehicle position");
            for(ScheduledRouteStop s:schedule.scheduledStops()) {
                if(s.stop() instanceof DeliveryStop && overlap(b.startsAt(),b.endsAt(),s.serviceStartedAt(),s.completedAt()))
                    errors.add("Delivery service during a meal");
                if(s.stop() instanceof WarehouseVisit && s.arrivedAt().isAfter(b.startsAt()) && s.arrivedAt().isBefore(b.endsAt()))
                    errors.add("Warehouse operation during a meal");
            }
            if(b.endsAt().isAfter(schedule.completedAt()))errors.add("Route duty ends before its mandatory meal");
            if(b.placement()==ScheduledMealBreak.Placement.BEFORE_ROUTE && b.endsAt().isAfter(schedule.route().departureAt()))errors.add("Initial meal exceeds departure");
            if(b.placement()==ScheduledMealBreak.Placement.AFTER_ROUTE && b.startsAt().isBefore(schedule.returnedAt()))errors.add("Final idle meal precedes return");
        }
        Instant departure=schedule.route().departureAt();
        Instant last=schedule.completedAt().isAfter(departure)?schedule.completedAt().minusNanos(1):departure;
        Set<Instant> required=new HashSet<>();
        for(Instant shift=shifts.shiftAt(departure).startsAt();!shift.isAfter(last);shift=shifts.shiftAt(shift).endsAt()) {
            required.add(shift);
            if(counts.getOrDefault(shift,0)!=1)errors.add("Exactly one meal required in shift "+shift);
        }
        for(Instant shift:counts.keySet())if(!required.contains(shift))errors.add("Meal outside the route duty horizon");
        for(ScheduledRouteStop s:schedule.scheduledStops()) {
            if(s.stop() instanceof DeliveryStop && !Duration.between(s.serviceStartedAt(),s.completedAt()).equals(DeliveryStop.SERVICE_TIME))
                errors.add("Delivery service must last one hour, separately from rest");
        }
        return List.copyOf(errors);
    }
    private static boolean overlap(Instant a,Instant b,Instant c,Instant d) { return a.isBefore(d)&&c.isBefore(b); }
}
