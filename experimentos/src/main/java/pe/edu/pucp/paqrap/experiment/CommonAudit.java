package pe.edu.pucp.paqrap.experiment;

import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.route.*;
import java.util.*;
import static pe.edu.pucp.paqrap.experiment.Json.obj;

/** Identical audit on the ORIGINAL, COMPLETE required order set for both solvers. */
public final class CommonAudit {
    private CommonAudit(){}
    public static AuditResult evaluate(ProblemInstance p,OperationalPlan plan){
        // This evaluator is fresh and runs OUTSIDE the search budget; audit time is exported separately.
        PlanEvaluation evaluation=new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()))
                .evaluate(plan,p.snapshot(),p.orders(),p.blocks());
        Map<String,Order> required=new TreeMap<>();p.orders().forEach(o->required.put(o.id(),o));
        Map<String,Integer> onTime=new TreeMap<>(),delivered=new TreeMap<>();
        double distance=0;
        for(var route:evaluation.schedulesByRouteId().values()){
            distance+=route.totalDistanceKm();
            for(var stop:route.scheduledStops())if(stop.stop() instanceof DeliveryStop d){
                delivered.merge(d.order().id(),d.deliveredPackages(),Integer::sum);
                Order o=required.get(d.order().id());
                if(o!=null && o.equals(d.order()) && !stop.arrivedAt().isAfter(o.deadline()) && !stop.arrivedAt().isBefore(o.registeredAt()))
                    onTime.merge(o.id(),d.deliveredPackages(),Integer::sum);
            }
        }
        int complete=0,covered=0,total=p.orders().stream().mapToInt(Order::packages).sum();
        List<String> missing=new ArrayList<>();boolean oversupplied=false;
        for(Order o:p.orders()){
            int q=onTime.getOrDefault(o.id(),0);
            covered+=Math.min(q,o.packages());
            if(q==o.packages())complete++;else missing.add(o.id());
            if(delivered.getOrDefault(o.id(),0)>o.packages())oversupplied=true;
        }
        boolean structural=evaluation.violations().stream().noneMatch(v->v.type()!=PlanViolationType.PARTIAL_DELIVERY_MISMATCH) && !oversupplied;
        boolean full=evaluation.isFeasible() && complete==p.orders().size();
        Set<String> unservable=provablyUnservable(p);
        int servable=p.orders().size()-unservable.size(),servableComplete=0;
        for(Order o:p.orders())if(!unservable.contains(o.id())&&onTime.getOrDefault(o.id(),0)==o.packages())servableComplete++;
        boolean fullServable=structural && servableComplete==servable;
        List<Object> routeDetails=new ArrayList<>();
        for(DeliveryRoute r:plan.routes()){
            List<Object> stops=new ArrayList<>();
            for(RouteStop s:r.stops()){
                if(s instanceof DeliveryStop d)stops.add(obj("type","DELIVERY","order_id",d.order().id(),"packages",d.deliveredPackages(),"x",s.location().x(),"y",s.location().y()));
                else if(s instanceof WarehouseVisit w)stops.add(obj("type","WAREHOUSE","warehouse_id",w.warehouse().id(),"pickup",w.pickupPackages(),"x",s.location().x(),"y",s.location().y()));
            }
            ScheduledDeliveryRoute scheduled=evaluation.schedulesByRouteId().get(r.id());
            Object timetable=scheduled==null?List.of():scheduled.scheduledStops().stream().map(s->obj("arrival",s.arrivedAt(),"completion",s.completedAt(),"load_before",s.loadBefore(),"load_after",s.loadAfter(),"approach_distance_km",s.approach().distanceKm(),"path",s.approach().legs().stream().map(l->obj("from",List.of(l.from().x(),l.from().y()),"to",List.of(l.to().x(),l.to().y()),"departure",l.departsAt(),"arrival",l.arrivesAt())).toList())).toList();
            routeDetails.add(obj("route_id",r.id(),"vehicle_id",r.vehicle().id(),"vehicle_type",r.vehicle().type(),"departure",r.departureAt(),"initial_load",r.initialLoad(),"stops",stops,"timetable",timetable));
        }
        Map<String,Object> details=obj("full_feasible",full,"route_constraints_valid",structural,"orders_total",p.orders().size(),
                "orders_fully_served",complete,"packages_total",total,"packages_covered_on_time",covered,"unserved_order_ids",missing,
                "provably_unservable_order_ids",List.copyOf(unservable),"unruled_out_orders_fully_served",servableComplete,"full_unruled_out_feasible",fullServable,
                "provably_unservable_rule","Optimistic Manhattan travel from actual vehicle states, ignoring blocks, meals, stock, service, return and other orders. Failing for every fixed-fleet vehicle is a necessary-condition certificate. Orders not excluded by this bound are NOT proven servable.",
                "planned_cost_raw",evaluation.totalCost(),"planned_cost_if_complete_feasible",full?evaluation.totalCost():null,
                "distance_km",distance,"violations",evaluation.violations().stream().map(v->obj("type",v.type(),"route_id",v.routeId(),"detail",v.detail())).toList(),
                "routes",routeDetails,"interpretation","Planning output, not observed delivery execution. Missing orders are NOT a mathematical infeasibility certificate.");
        return new AuditResult(full,structural,complete,covered,total,missing.size(),evaluation.totalCost(),distance,plan.routes().size(),
                unservable.size(),servableComplete,fullServable,details);
    }

    /** Safe necessary-condition certificate, NOT a direct-route heuristic.
     * Ignore blocks, meals, stock, other orders, service and return; start from each physical
     * vehicle state. If even that optimistic Manhattan travel bound misses the deadline for
     * every vehicle, full service is impossible in this static fixed-fleet instance.
     * Passing the bound does NOT establish that an order is servable. */
    static Set<String> provablyUnservable(ProblemInstance p){
        Set<String> unservable=new TreeSet<>();
        OperationalSnapshot snapshot=p.snapshot();
        for(Order order:p.orders()){
            boolean possibleUnderBound=false;
            for(VehicleOperationalState state:snapshot.vehiclesById().values()){
                if(!state.vehicle().available() || state.status()==VehicleStatus.OUT_OF_SERVICE)continue;
                double speed=snapshot.fleetProfile().parametersFor(state.vehicle().type()).speedKmPerHour();
                int km=Math.abs(state.location().x()-order.destination().x())+Math.abs(state.location().y()-order.destination().y());
                long perStreet=Math.max(1L,(long)Math.floor(3_600_000_000_000.0/speed));
                java.time.Instant departure=state.availableAt().isAfter(snapshot.planningTime())?state.availableAt():snapshot.planningTime();
                java.time.Instant earliest=departure.plusNanos(Math.multiplyExact(km,perStreet));
                if(!earliest.isAfter(order.deadline())){possibleUnderBound=true;break;}
            }
            if(!possibleUnderBound)unservable.add(order.id());
        }
        return unservable;
    }

    public record AuditResult(boolean fullFeasible,boolean routeValid,int completeOrders,int coveredPackages,int totalPackages,
                              int missingOrders,double rawCost,double distanceKm,int routes,
                              int provablyUnservable,int servableComplete,boolean fullServableFeasible,Map<String,Object> details){}
}
