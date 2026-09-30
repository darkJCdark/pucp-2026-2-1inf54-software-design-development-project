package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.search.SearchControl;
import pe.edu.pucp.paqrap.planner.domain.*;
import java.time.*;
import java.util.*;

/** Shared deterministic bounded-label scheduler with flexible, mandatory driver meals.
 * For a fixed route, explores resting before travel / before service / after service
 * (next stop), uses blocking waits, and enforces the latest legal start at street nodes.
 * A bounded beam is a scheduling HEURISTIC, not a proof of optimal break placement.
 * Every returned schedule is checked independently by MealBreakAudit.
 */
public final class RouteScheduler {
    private final RoadNetwork roadNetwork;
    public RouteScheduler(RoadNetwork roadNetwork) { this.roadNetwork=Objects.requireNonNull(roadNetwork); }

    private record Label(Instant time, Location location, FlexibleMealClock clock,
                         List<ScheduledRouteStop> stops, double distance, int late, long latenessSeconds,
                         long arrivalScore) {}

    public ScheduledDeliveryRoute schedule(DeliveryRoute route, OperationalSnapshot snapshot, List<RoadBlock> blocks) {
        SearchControl.routeSchedule(); Objects.requireNonNull(route); Objects.requireNonNull(snapshot); Objects.requireNonNull(blocks);
        double speed=snapshot.fleetProfile().parametersFor(route.vehicle().type()).speedKmPerHour();
        Duration perStreet=Duration.ofNanos(Math.max(1,Math.round(3_600_000_000_000.0/speed)));
        if (perStreet.compareTo(Duration.ofHours(1))>0) throw new IllegalArgumentException("Experimental speed must be >= 1 km/h");
        List<Label> labels=List.of(new Label(route.departureAt(),route.startLocation(),new FlexibleMealClock(snapshot.shiftSchedule(),route),List.of(),0,0,0,0));
        int load=route.initialLoad();
        for (RouteStop stop:route.stops()) {
            SearchControl.checkpoint();
            int after=stop instanceof DeliveryStop d?load-d.deliveredPackages():Math.addExact(load,((WarehouseVisit)stop).pickupPackages());
            List<Label> candidates=new ArrayList<>();
            for (Label label:labels) {
                for (boolean restBeforeTravel:List.of(false,true)) {
                    FlexibleMealClock travelClock=label.clock.copy();
                    Instant departure=label.time;
                    if (restBeforeTravel) {
                        Optional<Instant> rested=travelClock.takeNow(departure,label.location);
                        if (rested.isEmpty()) continue;
                        departure=rested.get();
                    }
                    RoadPath path;
                    try { path=travel(label.location,stop.location(),label.time,departure,perStreet,travelClock,blocks); }
                    catch (IllegalStateException noPath) { continue; }
                    Instant arrived=path.arrivesAt();
                    for (boolean restBeforeService:List.of(false,true)) {
                        if (restBeforeService && !(stop instanceof DeliveryStop)) continue;
                        FlexibleMealClock clock=travelClock.copy();
                        Instant start=arrived;
                        if (restBeforeService) {
                            Optional<Instant> rested=clock.takeNow(start,stop.location());
                            if (rested.isEmpty()) continue;
                            start=rested.get();
                        }
                        Instant completion=start;
                        try {
                            if (stop instanceof DeliveryStop) {
                                start=clock.workStart(start,DeliveryStop.SERVICE_TIME,stop.location());
                                completion=start.plus(DeliveryStop.SERVICE_TIME);
                            }
                        } catch (MealSchedulingException impossible) { continue; }
                        int late=label.late; long tardy=label.latenessSeconds;
                        if (stop instanceof DeliveryStop d && arrived.isAfter(d.order().deadline())) {
                            late++; tardy+=Math.max(1,Duration.between(d.order().deadline(),arrived).getSeconds());
                        }
                        List<ScheduledRouteStop> stops=new ArrayList<>(label.stops);
                        stops.add(new ScheduledRouteStop(stop,path,arrived,start,completion,load,after));
                        candidates.add(new Label(completion,stop.location(),clock,List.copyOf(stops),label.distance+path.distanceKm(),late,tardy,
                                label.arrivalScore+Duration.between(route.departureAt(),arrived).getSeconds()));
                    }
                }
            }
            if (candidates.isEmpty()) throw new MealSchedulingException("No road/rest schedule found for route "+route.id());
            labels=prune(candidates,snapshot.shiftSchedule().mealBeamWidth());
            load=after;
        }
        ScheduledDeliveryRoute best=null; Label bestLabel=null;
        for (Label label:labels) {
            FlexibleMealClock clock=label.clock.copy();
            Instant done;
            try { done=clock.finish(label.time,route.departureAt(),label.location); }
            catch (MealSchedulingException invalid) { continue; }
            var schedule=new ScheduledDeliveryRoute(route,label.stops,done,label.distance,
                    label.distance*snapshot.fleetProfile().parametersFor(route.vehicle().type()).costPerKm(),clock.breaks());
            if (best==null || compareFinal(label,schedule,bestLabel,best)<0) { best=schedule; bestLabel=label; }
        }
        if (best==null) throw new MealSchedulingException("Missing mandatory meal for route "+route.id());
        List<String> errors=MealBreakAudit.validate(best,snapshot.shiftSchedule());
        if (!errors.isEmpty()) throw new MealSchedulingException(String.join("; ",errors));
        return best;
    }

    private static int compareFinal(Label a,ScheduledDeliveryRoute sa,Label b,ScheduledDeliveryRoute sb) {
        int c=Integer.compare(a.late,b.late); if(c!=0)return c;
        c=Long.compare(a.latenessSeconds,b.latenessSeconds); if(c!=0)return c;
        c=Double.compare(sa.totalCost(),sb.totalCost()); if(c!=0)return c;
        c=sa.completedAt().compareTo(sb.completedAt()); if(c!=0)return c;
        return Long.compare(a.arrivalScore,b.arrivalScore);
    }

    /** Preserve pending/taken-shift states; both an early and a cheap representative survive.
     * Capacity is bounded explicitly: pruning can miss a feasible/better timetable, never
     * makes an invalid one valid. Failure is not an infeasibility certificate. */
    private static List<Label> prune(List<Label> candidates,int width) {
        Comparator<Label> timeOrder=Comparator.comparingInt(Label::late).thenComparingLong(Label::latenessSeconds)
                .thenComparing(Label::time).thenComparingDouble(Label::distance).thenComparingLong(Label::arrivalScore);
        Comparator<Label> costOrder=Comparator.comparingInt(Label::late).thenComparingLong(Label::latenessSeconds)
                .thenComparingDouble(Label::distance).thenComparing(Label::time).thenComparingLong(Label::arrivalScore);
        candidates.sort(timeOrder);
        Map<String,List<Label>> groups=new LinkedHashMap<>();
        Set<String> seen=new HashSet<>();
        for (Label l:candidates) {
            String state=l.clock.stateKey(l.time);
            String signature=state+":"+l.time+":"+l.distance+":"+l.late+":"+l.latenessSeconds;
            if(seen.add(signature))groups.computeIfAbsent(state,k->new ArrayList<>()).add(l);
        }
        LinkedHashSet<Label> chosen=new LinkedHashSet<>();
        for(List<Label> group:groups.values()) { if(chosen.size()<width)chosen.add(group.getFirst()); }
        for(List<Label> group:groups.values()) { if(chosen.size()<width)chosen.add(group.stream().min(costOrder).orElseThrow()); }
        for(List<Label> group:groups.values()) for(Label l:group)if(chosen.size()<width)chosen.add(l);
        return List.copyOf(chosen);
    }

    /** Recompute the remaining time-dependent path whenever a break changes passage times. */
    private RoadPath travel(Location origin, Location destination, Instant requested, Instant departure,
                            Duration perStreet, FlexibleMealClock clock, List<RoadBlock> blocks) {
        Location current=origin; Instant time=departure; List<RoadLeg> completed=new ArrayList<>();
        while(true) {
            SearchControl.checkpoint();
            Instant open=roadNetwork.firstUnblockedAt(current,time,blocks);
            time=clock.idleUntil(time,open,current);
            RoadPath suffix=roadNetwork.shortestPath(current,destination,time,perStreet,blocks)
                    .orElseThrow(()->new IllegalStateException("No feasible road path"));
            boolean replan=false;
            for(RoadLeg leg:suffix.legs()) {
                SearchControl.checkpoint();
                Instant available=clock.idleUntil(time,leg.departsAt(),current);
                Instant work=clock.workStart(available,perStreet,current);
                if(!work.equals(leg.departsAt())) { time=work; replan=true; break; }
                completed.add(leg);current=leg.to();time=leg.arrivesAt();
            }
            if(!replan)return new RoadPath(origin,destination,requested,completed,time);
        }
    }
}
