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
                "planned_cost_raw",evaluation.totalCost(),"planned_cost_if_complete_feasible",full?evaluation.totalCost():null,
                "distance_km",distance,"violations",evaluation.violations().stream().map(v->obj("type",v.type(),"route_id",v.routeId(),"detail",v.detail())).toList(),
                "routes",routeDetails,"interpretation","Planning output, not observed delivery execution. Missing orders are NOT a mathematical infeasibility certificate.");
        return new AuditResult(full,structural,complete,covered,total,missing.size(),evaluation.totalCost(),distance,plan.routes().size(),details);
    }
    public record AuditResult(boolean fullFeasible,boolean routeValid,int completeOrders,int coveredPackages,int totalPackages,
                              int missingOrders,double rawCost,double distanceKm,int routes,Map<String,Object> details){}
}
