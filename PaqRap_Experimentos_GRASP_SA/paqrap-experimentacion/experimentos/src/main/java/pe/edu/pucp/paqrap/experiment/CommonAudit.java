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
                "provably_unservable_order_ids",List.copyOf(unservable),"servable_orders_fully_served",servableComplete,"full_servable_feasible",fullServable,
                "provably_unservable_rule","Ninguna ruta directa desde el central, saliendo en el instante de planificacion, con cualquier vehiculo disponible, entrega a tiempo (mismo scheduler: camino, 80 km, refrigerio, plazo, mantenimiento). Es cota inferior de llegada para cualquier plan con las reglas del dominio comun.",
                "planned_cost_raw",evaluation.totalCost(),"planned_cost_if_complete_feasible",full?evaluation.totalCost():null,
                "distance_km",distance,"violations",evaluation.violations().stream().map(v->obj("type",v.type(),"route_id",v.routeId(),"detail",v.detail())).toList(),
                "routes",routeDetails,"interpretation","Planning output, not observed delivery execution. Missing orders are NOT a mathematical infeasibility certificate.");
        return new AuditResult(full,structural,complete,covered,total,missing.size(),evaluation.totalCost(),distance,plan.routes().size(),
                unservable.size(),servableComplete,fullServable,details);
    }

    /**
     * Pedidos que ningun plan puede entregar a tiempo con las reglas del dominio comun: para cada
     * vehiculo disponible, una ruta directa central -&gt; pedido -&gt; central que sale al planificar
     * (llegada mas temprana posible: cualquier parada previa o salida posterior solo la retrasa) no
     * llega en plazo o viola una regla dura por ruta. Independiente de los algoritmos y fuera del
     * tiempo medido. No depende del plan evaluado.
     */
    static Set<String> provablyUnservable(ProblemInstance p){
        RouteScheduler scheduler=new RouteScheduler(new RoadNetwork());
        OperationalSnapshot s=p.snapshot();Warehouse central=p.central();
        List<Vehicle> available=s.vehiclesById().values().stream().map(VehicleOperationalState::vehicle)
                .filter(v->s.isVehiclePlannableAt(v.id(),s.planningTime())).toList();
        Set<String> unservable=new TreeSet<>();
        for(Order o:p.orders()){
            boolean servable=false;
            for(Vehicle v:available){
                int quantity=Math.min(o.packages(),Math.min(s.fleetProfile().parametersFor(v.type()).capacity(),v.type().capacity()));
                DeliveryRoute direct=DeliveryRoute.startScenarioAtCentral("CERT-"+v.id(),v,central,quantity,s.planningTime())
                        .withAppendedStop(new DeliveryStop(o,quantity)).returningTo(central);
                try{
                    ScheduledDeliveryRoute r=scheduler.schedule(direct,s,p.blocks());
                    boolean ok=r.scheduledStops().stream().allMatch(st->st.approach().distanceKm()<=80
                            && !(st.stop() instanceof DeliveryStop && st.arrivedAt().isAfter(o.deadline())))
                            && !s.hasVehicleDisruptionDuring(v.id(),direct.departureAt(),r.completedAt());
                    if(ok){servable=true;break;}
                }catch(IllegalStateException noPath){/* sin camino con este vehiculo */}
            }
            if(!servable)unservable.add(o.id());
        }
        return unservable;
    }

    public record AuditResult(boolean fullFeasible,boolean routeValid,int completeOrders,int coveredPackages,int totalPackages,
                              int missingOrders,double rawCost,double distanceKm,int routes,
                              int provablyUnservable,int servableComplete,boolean fullServableFeasible,Map<String,Object> details){}
}
