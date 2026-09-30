package pe.edu.pucp.paqrap.experiment;

import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.route.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.function.BiConsumer;

/** Executable, dependency-free regressions of the NEW flexible mandatory-rest rule. */
final class FlexibleRestChecks {
    private static final ZoneId Z=ShiftSchedule.DEFAULT_ZONE;
    private static final Instant T=ZonedDateTime.of(2026,9,9,7,0,0,0,Z).toInstant();
    private static final Warehouse C=Warehouse.central("CENTRAL",new Location(27,14));
    private static final Vehicle V=new Vehicle("TA01",VehicleType.CAR,true);
    private static BiConsumer<Boolean,String> check;
    private static void ok(boolean value,String message){check.accept(value,"Flexible rest: "+message);}
    private static OperationalSnapshot snapshot(Instant at,Vehicle... vehicles) {
        Map<String,VehicleOperationalState> states=new TreeMap<>();
        for(Vehicle v:vehicles)states.put(v.id(),new VehicleOperationalState(v,VehicleStatus.AVAILABLE,C.location(),at));
        return new OperationalSnapshot(at,FleetProfile.defaults(),InventorySnapshot.from(List.of(C)),states,
                new MaintenanceCalendar(Z,List.of()),ShiftSchedule.defaultSchedule(),List.of());
    }
    private static Order order(String id,Location destination,Instant at,Instant deadline){return new Order(id,destination,1,at,deadline);}
    private static DeliveryRoute route(Vehicle v,Instant at,List<Order> orders) {
        DeliveryRoute r=DeliveryRoute.startScenarioAtCentral("R-"+v.id(),v,C,orders.size(),at);
        for(Order o:orders)r=r.withAppendedStop(new DeliveryStop(o,1));
        return r.returningTo(C);
    }
    private static List<Order> locals(int n,Instant at){List<Order> orders=new ArrayList<>();for(int i=0;i<n;i++)orders.add(order("P"+i,C.location(),at,at.plus(Duration.ofDays(3))));return orders;}
    private static ScheduledDeliveryRoute schedule(DeliveryRoute route,OperationalSnapshot snapshot,List<RoadBlock> blocks){return new RouteScheduler(new RoadNetwork()).schedule(route,snapshot,blocks);}
    private static ScheduledDeliveryRoute tamper(ScheduledDeliveryRoute s,List<ScheduledMealBreak> meals){return new ScheduledDeliveryRoute(s.route(),s.scheduledStops(),s.completedAt(),s.totalDistanceKm(),s.totalCost(),meals);}
    private static boolean invalid(ScheduledDeliveryRoute s){return !MealBreakAudit.validate(s,ShiftSchedule.defaultSchedule()).isEmpty();}
    static void run(BiConsumer<Boolean,String> assertion) {
        check=assertion;ShiftSchedule shifts=ShiftSchedule.defaultSchedule();
        ok(shifts.mealWindow(T).startsAt().atZone(Z).getHour()==8 && shifts.latestMealStart(T).atZone(Z).getHour()==13,
                "day shift admits starts anywhere from 08:00 to 13:00");
        Instant afternoon=T.plus(Duration.ofHours(8)),night=T.plus(Duration.ofHours(16));
        ok(shifts.mealWindow(afternoon).startsAt().atZone(Z).getHour()==16 && shifts.latestMealStart(afternoon).atZone(Z).getHour()==21,
                "afternoon shift admits starts from 16:00 to 21:00");
        ok(shifts.mealWindow(night).startsAt().atZone(Z).getHour()==0 && shifts.latestMealStart(night).atZone(Z).getHour()==5,
                "night shift admits starts from 00:00 to 05:00 on the next date");
        var snap=snapshot(T,V);
        Order near=order("NEAR",new Location(28,14),T,T.plus(Duration.ofHours(4)));
        var shortRoute=schedule(route(V,T,List.of(near)),snap,List.of());
        ok(shortRoute.mealBreaks().size()==1,"a short early-ending route still contains one mandatory meal");
        ok(shortRoute.mealBreaks().getFirst().placement()==ScheduledMealBreak.Placement.AFTER_ROUTE,
                "meal can be scheduled during idle time after the physical return");
        ok(shortRoute.mealBreaks().getFirst().startsAt().equals(shortRoute.returnedAt()),
                "rest at 08:03 follows this route, not a whole-hour/fixed-clock grid");
        ok(shortRoute.completedAt().equals(shortRoute.returnedAt().plusSeconds(3600)),
                "duty completion includes the mandatory meal even when the vehicle already returned");
        ok(!invalid(shortRoute),"independent meal auditor accepts the generated early-return schedule");

        List<Order> urgent=new ArrayList<>(locals(3,T));
        urgent.add(order("URGENT_AT_TEN",new Location(28,14),T,T.plusSeconds(3*3600+120)));
        var rush=schedule(route(V,T,urgent),snap,List.of());
        ok(!rush.scheduledStops().get(3).arrivedAt().isAfter(urgent.get(3).deadline()),
                "urgent 10:02 delivery is NOT delayed by the former 10-11 fixed meal");
        ok(rush.mealBreaks().getFirst().startsAt().isAfter(T.plus(Duration.ofHours(3))),
                "scheduler postpones rest when an urgent delivery makes that placement preferable");
        ok(rush.scheduledStops().stream().filter(s->s.stop() instanceof DeliveryStop)
                .allMatch(s->Duration.between(s.serviceStartedAt(),s.completedAt()).equals(Duration.ofHours(1))),
                "every delivery keeps its separate uninterrupted hour of conditioning");
        Vehicle other=new Vehicle("TA02",VehicleType.CAR,true);
        var otherSchedule=schedule(route(other,T,urgent),snapshot(T,other),List.of());
        ok(!shortRoute.mealBreaks().getFirst().startsAt().equals(otherSchedule.mealBreaks().getFirst().startsAt()),
                "different routes/vehicles receive different meal times in the same shift");

        var exactShiftEnd=schedule(route(V,T,locals(7,T)),snap,List.of());
        ok(exactShiftEnd.returnedAt().equals(T.plus(Duration.ofHours(8))) && exactShiftEnd.mealBreaks().size()==1,
                "return exactly at 15:00 does not invent an afternoon work shift");
        var longSchedule=schedule(route(V,T,locals(20,T)),snap,List.of());
        ok(longSchedule.mealBreaks().size()==3,"a route spanning day, afternoon and night has three meals");
        ok(longSchedule.mealBreaks().stream().anyMatch(b->b.shiftStart().equals(night)),
                "night meal is attached to the 23:00 shift, including its next-date portion");
        ok(!invalid(longSchedule),"meals remain mandatory across all stops and across midnight");

        RoadBlock waiting=new RoadBlock(T,T.plusSeconds(2*3600),List.of(C.location(),new Location(27,15)));
        var waited=schedule(route(V,T,List.of(near)),snap,List.of(waiting));
        ok(waited.mealBreaks().getFirst().startsAt().equals(T.plusSeconds(3600)) && waited.mealBreaks().getFirst().endsAt().equals(T.plusSeconds(2*3600)),
                "a blocking wait contains the complete 08-09 meal without duplicate waiting");
        ok(waited.scheduledStops().getFirst().arrivedAt().equals(T.plusSeconds(2*3600+90)),
                "vehicle moves immediately after the blocking wait and meal end");

        Instant eleven=T.plusSeconds(4*3600);
        var pre=schedule(route(V,eleven,List.of(order("LATE_BATCH",C.location(),eleven,eleven.plusSeconds(36*3600)))),snapshot(eleven,V),List.of());
        ok(pre.mealBreaks().getFirst().placement()==ScheduledMealBreak.Placement.BEFORE_ROUTE
                        && pre.mealBreaks().getFirst().endsAt().equals(eleven),
                "initial idle-at-Central convention is explicit in a before-route meal record");
        ok(!invalid(pre),"before-route rest obeys the same windows and duration");
        Instant lateStart=T.plusSeconds(7*3600+30*60);
        var replanned=DeliveryRoute.replanFromCurrentLocation("UNKNOWN-HISTORY",V,C.location(),0,lateStart).returningTo(C);
        var missingHistory=new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork())).evaluate(
                OperationalPlan.empty().withRoute(replanned),snapshot(lateStart,V),List.of(),List.of());
        ok(missingHistory.violations().stream().anyMatch(v->v.type()==PlanViolationType.MANDATORY_MEAL_VIOLATION),
                "late replan with unknown rest history is NOT silently credited with a previous meal");

        ScheduledMealBreak good=shortRoute.mealBreaks().getFirst();
        ok(invalid(tamper(shortRoute,List.of())),"auditor rejects a missing mandatory meal");
        ok(invalid(tamper(shortRoute,List.of(good,good))),"auditor rejects a duplicated meal for one shift");
        ok(invalid(tamper(shortRoute,List.of(new ScheduledMealBreak(good.shiftStart(),good.startsAt(),good.startsAt().plusSeconds(1800),good.location(),good.placement())))),
                "auditor rejects a 30-minute break instead of one hour");
        ok(invalid(tamper(shortRoute,List.of(new ScheduledMealBreak(good.shiftStart(),T,T.plusSeconds(3600),C.location(),ScheduledMealBreak.Placement.DURING_ROUTE)))),
                "auditor rejects a meal inside the shift-start safety margin");
        ok(invalid(tamper(shortRoute,List.of(new ScheduledMealBreak(good.shiftStart(),good.startsAt(),good.endsAt(),new Location(1,1),good.placement())))),
                "auditor rejects a rest location inconsistent with the vehicle position");
        var service=shortRoute.scheduledStops().getFirst();
        var duringService=new ScheduledMealBreak(good.shiftStart(),service.serviceStartedAt(),service.completedAt(),service.stop().location(),ScheduledMealBreak.Placement.DURING_ROUTE);
        ok(invalid(tamper(shortRoute,List.of(duringService))),"auditor rejects overlapping meal and conditioning service");
        var lateMeal=new ScheduledMealBreak(good.shiftStart(),T.plusSeconds(7*3600),T.plusSeconds(8*3600),C.location(),ScheduledMealBreak.Placement.AFTER_ROUTE);
        ok(invalid(tamper(shortRoute,List.of(lateMeal))),"auditor rejects a break that ends at the shift change");

        List<Order> impossibleRoute=new ArrayList<>(locals(8,T));
        impossibleRoute.set(7,order("EIGHTH_TOO_EARLY",C.location(),T,T.plusSeconds(7*3600+60)));
        var strict=new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork())).evaluate(
                OperationalPlan.empty().withRoute(route(V,T,impossibleRoute)),snap,impossibleRoute,List.of());
        ok(!strict.isFeasible() && strict.schedulesByRouteId().values().stream().allMatch(s->!s.mealBreaks().isEmpty()),
                "a difficult deadline never disables the mandatory meal");

        Properties props=new Properties();
        ExperimentConfig cfg=new ExperimentConfig(props,Path.of(""));
        for(String family:List.of("NORMAL","BLOCKED","REDUCED","SPLIT")) {
            var instance=InstanceFactory.create(new ScenarioSpec("REST_"+family,family,"SYNTHETIC",2,912,LocalDate.of(2026,9,9),7,8),cfg);
            ok(!instance.blocks().isEmpty(),family+" experiment includes planned road blocks");
            var empty=CommonAudit.evaluate(instance,OperationalPlan.empty());
            ok(((List<?>)empty.details().get("idle_vehicle_meals")).size()==instance.snapshot().vehiclesById().size(),
                    family+" exports an idle first-shift meal for unused vehicles, without fake route cost");
        }
        Order shared=new Order("TWO",new Location(28,14),2,T,T.plusSeconds(36*3600));
        var r1=DeliveryRoute.startScenarioAtCentral("SAME1",V,C,1,T).withAppendedStop(new DeliveryStop(shared,1)).returningTo(C);
        var r2=DeliveryRoute.startScenarioAtCentral("SAME2",other,C,1,T).withAppendedStop(new DeliveryStop(shared,1)).returningTo(C);
        var two=snapshot(T,V,other);
        try(var control=pe.edu.pucp.paqrap.planner.search.SearchControl.install(0)) {
            var evaluated=new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork())).evaluate(
                    OperationalPlan.empty().withRoute(r1).withRoute(r2),two,List.of(shared),List.of());
            ok(evaluated.isFeasible() && control.schedules()==1,"equivalent timing cache reuses only one route schedule for two identical vehicles");
            ok(evaluated.schedulesByRouteId().get("SAME1").route()==r1 && evaluated.schedulesByRouteId().get("SAME2").route()==r2,
                    "timing cache preserves actual vehicle and route identities");
            var fresh=schedule(r2,two,List.of());
            ok(fresh.mealBreaks().equals(evaluated.schedulesByRouteId().get("SAME2").mealBreaks())
                    && fresh.scheduledStops().getFirst().arrivedAt().equals(evaluated.schedulesByRouteId().get("SAME2").scheduledStops().getFirst().arrivedAt())
                    && fresh.scheduledStops().getFirst().approach().legs().equals(evaluated.schedulesByRouteId().get("SAME2").scheduledStops().getFirst().approach().legs()),
                    "cached and freshly scheduled timelines/rests agree exactly");
        }
        // Independent randomized invariant checks on routes, not a claim that the beam is exact.
        Random rng=new Random(20260929);boolean allValid=true,blocksRespected=true;
        for(int caseNo=0;caseNo<60;caseNo++) {
            Instant at=T.plusSeconds((caseNo%3)*8*3600L);
            List<Order> orders=new ArrayList<>();
            int n=3+rng.nextInt(8);
            for(int i=0;i<n;i++)orders.add(order("R"+caseNo+"-"+i,new Location(5+rng.nextInt(61),5+rng.nextInt(41)),at,at.plus(Duration.ofDays(3))));
            RoadBlock block=new RoadBlock(at.plusSeconds(3*3600),at.plusSeconds(8*3600),List.of(new Location(32,10),new Location(32,20)));
            var result=schedule(route(V,at,orders),snapshot(at,V),List.of(block));
            allValid &= MealBreakAudit.validate(result,shifts).isEmpty();
            blocksRespected &= result.scheduledStops().stream().flatMap(s->s.approach().legs().stream())
                    .noneMatch(l->block.isActiveAt(l.arrivesAt()) && block.blockedNodes().contains(l.to()));
        }
        ok(allValid,"60 randomized routes pass independent duration, margins, shift-count, location and non-overlap checks");
        ok(blocksRespected,"60 randomized routes recompute time-dependent paths and do not enter active blocked nodes");
    }
}
