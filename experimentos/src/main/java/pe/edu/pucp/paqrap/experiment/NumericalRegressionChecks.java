package pe.edu.pucp.paqrap.experiment;

import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.route.*;
import pe.edu.pucp.paqrap.planner.search.*;
import pe.edu.pucp.paqrap.planner.sa.*;
import java.time.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;

/** Offline regression suite for the numerical model. No third-party testing dependency. */
final class NumericalRegressionChecks {
    private static final ZoneId Z=ShiftSchedule.DEFAULT_ZONE;
    private static final Instant T=ZonedDateTime.of(2026,9,9,7,0,0,0,Z).toInstant();
    private static BiConsumer<Boolean,String> assertion;
    static void run(BiConsumer<Boolean,String> check) throws Exception {
        assertion=check;
        inputPolicies(); deadlineAndLoad(); inventory(); roadAndMeals(); repairAndSearch(); validation();
    }
    private static void ok(boolean condition,String description){assertion.accept(condition,description);}
    private static ExperimentConfig config(String... entries){Properties p=new Properties();for(int i=0;i<entries.length;i+=2)p.setProperty(entries[i],entries[i+1]);ExperimentConfig result=new ExperimentConfig(p,Path.of(""));result.validate();return result;}
    private static ScenarioSpec spec(String family,int n){return new ScenarioSpec("V3_"+family,family,family.equals("REAL")?"REAL":"SYNTHETIC",n,991,LocalDate.of(2026,9,9),7,8);}
    private static Warehouse central(){return Warehouse.central("CENTRAL",new Location(27,14));}
    private static Vehicle car(int n){return new Vehicle("TA0"+n,VehicleType.CAR,true);}
    private static OperationalSnapshot snapshot(Instant time,List<Warehouse> warehouses,List<Vehicle> vehicles,FleetProfile fleet,double maxLeg){
        Warehouse c=warehouses.stream().filter(Warehouse::isCentral).findFirst().orElseThrow();
        Map<String,VehicleOperationalState> states=new TreeMap<>();
        for(Vehicle v:vehicles)states.put(v.id(),new VehicleOperationalState(v,VehicleStatus.AVAILABLE,c.location(),time));
        return new OperationalSnapshot(time,fleet,InventorySnapshot.from(warehouses),states,new MaintenanceCalendar(Z,List.of()),new ShiftSchedule(Z),List.of(),maxLeg);
    }
    private static OperationalSnapshot snapshot(){return snapshot(T,List.of(central()),List.of(car(1),car(2)),FleetProfile.defaults(),0);}
    private static Order order(String id,Location dest,int q,Instant registered,Instant deadline){return new Order(id,dest,q,registered,deadline);}
    private static Order order(String id,int x,int y,int q){return order(id,new Location(x,y),q,T,T.plusSeconds(36*3600));}
    private static DeliveryRoute route(Vehicle vehicle,int q,Instant departure,RouteStop... stops){return DeliveryRoute.startScenarioAtCentral("R_"+vehicle.id(),vehicle,central(),q,departure).withReplacedStops(List.of(stops)).returningTo(central());}
    private static PlanEvaluation evaluate(OperationalSnapshot s,List<Order> orders,DeliveryRoute... routes){return evaluate(s,orders,List.of(),routes);}
    private static PlanEvaluation evaluate(OperationalSnapshot s,List<Order> orders,List<RoadBlock> blocks,DeliveryRoute... routes){OperationalPlan p=OperationalPlan.empty();for(DeliveryRoute r:routes)p=p.withRoute(r);return evaluator().evaluate(p,s,orders,blocks);}
    private static OperationalPlanEvaluator evaluator(){return new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()));}
    private static boolean has(PlanEvaluation e,PlanViolationType type){return e.violations().stream().anyMatch(v->v.type()==type);}
    private static void rejects(Runnable action,String label){boolean rejected=false;try{action.run();}catch(IllegalArgumentException e){rejected=true;}ok(rejected,label);}

    private static void inputPolicies(){
        ProblemInstance full=InstanceFactory.create(spec("NORMAL",2),config());
        ok(full.snapshot().vehiclesById().size()==37,"Case default fleet contains exactly 37 physical vehicles");
        ok(full.snapshot().inventory().warehouse("NORTH_WEST").location().equals(new Location(12,38)) && full.snapshot().inventory().warehouse("EAST").location().equals(new Location(57,27)),"Latest intermediate warehouse coordinates");
        ok(full.snapshot().fleetProfile().parametersFor(VehicleType.CAR).equals(new VehicleParameters(24,40,8)) && full.snapshot().fleetProfile().parametersFor(VehicleType.MOTORCYCLE).equals(new VehicleParameters(8,25,6)) && full.snapshot().fleetProfile().parametersFor(VehicleType.BICYCLE).equals(new VehicleParameters(4,12,3)),"Capacities, speeds and costs exactly match the supplied fleet");
        ProblemInstance reduced=InstanceFactory.create(spec("REDUCED",2),config());
        ok(reduced.snapshot().vehiclesById().size()==19 && reduced.manifest().get("maintenance").equals(List.of()),"REDUCED uses 19 vehicles and no maintenance events");
        ProblemInstance real=InstanceFactory.create(spec("REAL",0),config());
        ok(real.manifest().get("maintenance").equals(List.of()) && real.manifest().get("breakdowns").equals(List.of()) && !Json.encode(real.manifest().get("source_file_sha256")).contains("mantenimiento"),"Real experiments exclude maintenance and automatic breakdown files");
        ok(!real.blocks().isEmpty() && Json.encode(real.manifest().get("source_file_sha256")).contains("bloqueo"),"Real planned blocks are loaded and source-fingerprinted");
        ok(Double.isInfinite(full.snapshot().maximumLegDistanceKm()),"No unsupported hidden 80 km restriction in default experiments");
    }
    private static void deadlineAndLoad(){
        OperationalSnapshot s=snapshot();
        Order exact=order("EXACT",new Location(28,14),1,T,T.plusSeconds(90));
        DeliveryRoute good=route(car(1),1,T,new DeliveryStop(exact,1));
        PlanEvaluation e=evaluate(s,List.of(exact),good);
        ok(e.isFeasible(),"Arrival exactly at deadline is feasible despite service finishing later");
        ScheduledRouteStop stop=e.schedulesByRouteId().get(good.id()).scheduledStops().getFirst();
        ok(stop.completedAt().equals(stop.arrivedAt().plusSeconds(3600)),"Each effective delivery reserves one full service hour");
        Order late=order("LATE",new Location(28,14),1,T,T.plusSeconds(89));
        ok(has(evaluate(s,List.of(late),route(car(1),1,T,new DeliveryStop(late,1))),PlanViolationType.SLA_MISSED),"Arrival one second after deadline is rejected");
        Order future=order("FUTURE",new Location(28,14),1,T.plusSeconds(300),T.plusSeconds(3600));
        ok(has(evaluate(s,List.of(future),route(car(1),1,T,new DeliveryStop(future,1))),PlanViolationType.DELIVERY_BEFORE_REGISTRATION),"Delivery before order registration is rejected");
        Order two=order("TWO",28,14,2);
        PlanEvaluation partial=evaluate(s,List.of(two),route(car(1),1,T,new DeliveryStop(two,1)));
        ok(partial.isRouteFeasible() && !partial.isFeasible() && partial.missingOrders()==1 && partial.missingUnits()==1,"Partial delivery has explicit remaining demand and is not complete");
        ok(has(evaluate(s,List.of(two),route(car(1),3,T,new DeliveryStop(two,2),new DeliveryStop(two,1))),PlanViolationType.ORDER_OVERSUPPLIED),"Overdelivery is a hard violation, not coverage above 100 percent");
        ok(has(evaluate(s,List.of(two),route(car(1),1,T,new DeliveryStop(two,2))),PlanViolationType.NEGATIVE_LOAD),"Vehicle cannot deliver stock that it does not carry");
        ok(has(evaluate(s,List.of(two),route(car(1),2,T,new WarehouseVisit(central(),24),new DeliveryStop(two,2))),PlanViolationType.VEHICLE_CAPACITY),"Reload that exceeds capacity is rejected");
        rejects(()->new Vehicle(car(1).id(),VehicleType.BICYCLE,true),"Vehicle id and type must agree");
        Vehicle forged=new Vehicle(car(1).id(),VehicleType.CAR,false);
        ok(!evaluate(s,List.of(two),route(forged,2,T,new DeliveryStop(two,2))).isRouteFeasible(),"Vehicle definition must match the immutable snapshot");
        Warehouse fake=Warehouse.intermediate("CENTRAL",new Location(28,14),1000);
        ok(has(evaluate(s,List.of(two),route(car(1),2,T,new DeliveryStop(two,2),new WarehouseVisit(fake,0))),PlanViolationType.UNKNOWN_WAREHOUSE),"A warehouse id cannot override its canonical location or type");
        DeliveryRoute teleport=DeliveryRoute.replanFromCurrentLocation("TELEPORT",car(1),new Location(28,14),2,T).withAppendedStop(new DeliveryStop(two,2)).returningTo(central());
        ok(has(evaluate(s,List.of(two),teleport),PlanViolationType.INVALID_ROUTE_START),"Replanned route cannot teleport away from the physical vehicle state");
        ok(PlanQuality.better(e,partial) && !PlanQuality.better(evaluator().evaluate(OperationalPlan.empty(),s,List.of(exact),List.of()),e),"Completeness outranks a cheaper empty or partial plan");
    }
    private static void inventory(){
        Warehouse w=Warehouse.intermediate("W",new Location(28,14),4);
        OperationalSnapshot s=snapshot(T,List.of(central(),w),List.of(car(1),car(2)),FleetProfile.defaults(),0);
        Order a=order("A",29,14,4),b=order("B",29,15,4);
        DeliveryRoute ra=route(car(1),0,T,new WarehouseVisit(w,4),new DeliveryStop(a,4));
        DeliveryRoute rb=route(car(2),0,T,new WarehouseVisit(w,4),new DeliveryStop(b,4));
        ok(has(evaluate(s,List.of(a,b),ra,rb),PlanViolationType.INSUFFICIENT_INVENTORY),"Concurrent routes share one chronological warehouse stock balance");
        ok(s.inventory().availableStock("W")==4,"Inventory audit does not mutate instance stock");
        Warehouse empty=Warehouse.intermediate("W",new Location(28,14),0);
        OperationalSnapshot se=snapshot(T,List.of(central(),empty),List.of(car(1)),FleetProfile.defaults(),0);
        DeliveryRoute ret=DeliveryRoute.startScenarioAtCentral("RETURN",car(1),central(),1,T).withAppendedStop(new DeliveryStop(order("RETURN_ORDER",29,14,1),1)).returningTo(empty);
        ok(evaluate(se,List.of(order("RETURN_ORDER",29,14,1)),ret).isFeasible(),"Returning without pickup to an empty warehouse is permitted");
        Instant reload=ZonedDateTime.of(2026,9,9,23,59,59,0,Z).toInstant();
        Order refill=order("REFILL",new Location(29,14),1,T,T.plusSeconds(48*3600));
        DeliveryRoute before=route(car(1),0,reload.minusSeconds(91),new WarehouseVisit(empty,1),new DeliveryStop(refill,1));
        DeliveryRoute at=route(car(1),0,reload.minusSeconds(90),new WarehouseVisit(empty,1),new DeliveryStop(refill,1));
        ok(has(evaluate(se,List.of(refill),before),PlanViolationType.INSUFFICIENT_INVENTORY),"Empty intermediate warehouse cannot dispatch before 23:59:59 replenishment");
        ok(evaluate(se,List.of(refill),at).isFeasible(),"Inventory becomes available exactly at daily 23:59:59 replenishment");
    }
    private static void roadAndMeals(){
        RoadNetwork roads=new RoadNetwork();Duration step=Duration.ofSeconds(90);
        RoadBlock boundary=new RoadBlock(T.plus(step),T.plusSeconds(3600),List.of(new Location(28,14),new Location(28,15)));
        RoadPath delayed=roads.shortestPath(central().location(),new Location(28,14),T,step,List.of(boundary)).orElseThrow();
        ok(!delayed.arrivesAt().isBefore(boundary.endsAt()),"Entry into a blocked destination exactly at block activation is prohibited");
        RoadPath opened=roads.shortestPath(central().location(),new Location(28,14),boundary.endsAt(),step,List.of(boundary)).orElseThrow();
        ok(opened.arrivesAt().equals(boundary.endsAt().plus(step)),"Block end is exclusive and permits traversal after reopening");
        RoadBlock obstacle=new RoadBlock(T,T.plusSeconds(10*3600),List.of(new Location(28,14),new Location(28,15)));
        RoadPath detour=roads.shortestPath(central().location(),new Location(29,14),T,step,List.of(obstacle)).orElseThrow();
        ok(detour.distanceKm()>2 && detour.legs().stream().noneMatch(l->obstacle.blockedNodes().contains(l.to()) && obstacle.isActiveAt(l.arrivesAt())),"Route detours around active blocked nodes without diagonal shortcuts");
        ShiftSchedule shifts=new ShiftSchedule(Z);
        ok(shifts.nextWorkStart(T.plusSeconds(3*3600),Duration.ofMinutes(1)).equals(T.plusSeconds(4*3600)),"Work requested at fixed 10:00 meal slot resumes at 11:00");
        ok(shifts.nextWorkStart(T.plusSeconds(2*3600),Duration.ofHours(1)).equals(T.plusSeconds(2*3600)),"Service finishing exactly when a meal starts is allowed");
        ok(shifts.nextWorkStart(T.plusSeconds(2*3600+1),Duration.ofHours(1)).equals(T.plusSeconds(4*3600)),"Uninterrupted service cannot overlap a meal slot");
        Instant overnight=ZonedDateTime.of(2026,9,10,2,30,0,0,Z).toInstant();
        ok(shifts.nextWorkStart(overnight,Duration.ofMinutes(1)).equals(overnight.plusSeconds(1800)),"Overnight 23:00-07:00 shift meal is accounted for");
        Instant beforeMeal=T.plusSeconds(3*3600-30);
        OperationalSnapshot s=snapshot(beforeMeal,List.of(central()),List.of(car(1)),FleetProfile.defaults(),0);
        Order nearby=order("MEAL",new Location(28,14),1,beforeMeal,beforeMeal.plusSeconds(7200));
        PlanEvaluation meal=evaluate(s,List.of(nearby),route(car(1),1,beforeMeal,new DeliveryStop(nearby,1)));
        ok(meal.schedulesByRouteId().values().iterator().next().scheduledStops().getFirst().arrivedAt().equals(T.plusSeconds(4*3600+90)),"Travel pauses at a node rather than driving through a meal");
        RoadBlock sameNode=new RoadBlock(T,T.plusSeconds(60),List.of(central().location(),new Location(27,15)));
        Order local=order("LOCAL",27,14,1);
        var localSchedule=new RouteScheduler(new RoadNetwork()).schedule(route(car(1),1,T,new DeliveryStop(local,1)),snapshot(),List.of(sameNode));
        ok(localSchedule.scheduledStops().getFirst().arrivedAt().equals(T.plusSeconds(60)),"Zero-distance stops preserve stationary waiting until a blocked node reopens");
        Warehouse origin=Warehouse.central("CENTRAL",new Location(0,0));
        Order far=order("FAR",70,50,1);
        DeliveryRoute longRoute=DeliveryRoute.startScenarioAtCentral("LONG",car(1),origin,1,T).withAppendedStop(new DeliveryStop(far,1)).returningTo(origin);
        ok(evaluate(snapshot(T,List.of(origin),List.of(car(1)),FleetProfile.defaults(),0),List.of(far),longRoute).isFeasible(),"A 120 km leg is not rejected by an unsupported default distance rule");
        ok(has(evaluate(snapshot(T,List.of(origin),List.of(car(1)),FleetProfile.defaults(),80),List.of(far),longRoute),PlanViolationType.LEG_DISTANCE_EXCEEDED),"An explicitly enabled legacy distance sensitivity limit is enforced");
        rejects(()->new RoadBlock(T,T.plusSeconds(100),List.of(new Location(1,1),new Location(1,2),new Location(1,1))),"Closed block polyline is rejected instead of silently treated as open");
    }
    private static void repairAndSearch() throws Exception {
        Vehicle bike=new Vehicle("TB01",VehicleType.BICYCLE,true);
        OperationalSnapshot s=snapshot(T,List.of(central()),List.of(car(1),bike),FleetProfile.defaults(),0);
        Order five=order("FIVE",28,14,5);
        DeliveryRoute oversized=route(bike,0,T,new DeliveryStop(five,5));
        DeliveryRoute fixed=RouteLoadRepair.repair(oversized,s);
        ok(evaluate(s,List.of(five),fixed).isFeasible() && fixed.stops().stream().filter(DeliveryStop.class::isInstance).mapToInt(x->((DeliveryStop)x).deliveredPackages()).sum()==5,"Load repair splits a five-unit bike delivery into capacity-feasible trips with conserved quantity");
        DeliveryRoute adjacent=route(car(1),5,T,new DeliveryStop(five,2),new DeliveryStop(five,3));
        ok(RouteLoadRepair.repair(adjacent,s).stops().stream().filter(DeliveryStop.class::isInstance).count()==1,"Adjacent same-visit parts are one effective delivery and one service hour");
        ProblemInstance base=InstanceFactory.create(spec("NORMAL",2),config("fleet.cars","1","fleet.motorcycles","0","fleet.bicycles","0","grasp.iterations","2","sa.maximumIterations","50"));
        Instant t=base.snapshot().planningTime();
        Order impossible=order("IMPOSSIBLE",new Location(70,50),2,t,t.plusSeconds(1));
        Order easy=order("EASY",new Location(28,14),1,t,t.plusSeconds(8*3600));
        ProblemInstance mixed=new ProblemInstance(base.spec(),base.snapshot(),List.of(impossible,easy),List.of(),base.manifest(),base.sha256());
        for(UnifiedPlanner solver:List.of(new GraspAdapter(),new SaAdapter())){
            AlgorithmOutput out=solver.solve(mixed,config("grasp.iterations","2","sa.maximumIterations","50"),42);
            var audit=CommonAudit.evaluate(mixed,out.plan());
            ok(audit.routeValid() && audit.missingOrders()==1 && !audit.fullFeasible(),solver.name()+" retains the easy order instead of discarding everything when another order is impossible");
        }
        // Repair many deterministic edits; neighbor generation must never create phantom/missing packages.
        Order three=order("THREE",29,14,3);
        OperationalPlan plan=OperationalPlan.empty().withRoute(route(car(1),5,T,new DeliveryStop(five,5))).withRoute(route(bike,3,T,new DeliveryStop(three,3)));
        var gen=new OperationalRouteNeighborGenerator();Random random=new Random(992);
        int generated=0;boolean conserved=true,validLoads=true;
        for(int i=0;i<150;i++){
            var next=gen.generate(plan,s,random);if(next.isEmpty())continue;generated++;
            var ev=evaluator().evaluate(next.get(),s,List.of(five,three),List.of());
            conserved &= ev.missingUnits()==0 && !has(ev,PlanViolationType.ORDER_OVERSUPPLIED);
            validLoads &= !has(ev,PlanViolationType.NEGATIVE_LOAD) && !has(ev,PlanViolationType.VEHICLE_CAPACITY);
        }
        ok(generated>0 && conserved && validLoads,"150 seeded SA edit attempts conserve demand and repair all vehicle loads");
        OperationalNeighborGenerator emptyNeighbor=(p,sn,rnd)->Optional.empty();
        var sa=new OperationalSimulatedAnnealingPlanner(evaluator(),emptyNeighbor,new Random(5));
        AnnealingConfig rapid=new AnnealingConfig(1,0.9,0.8,1,100,5);
        try(SearchControl c=SearchControl.install(0)){
            var result=sa.optimize(plan,s,List.of(five,three),List.of(),rapid);
            ok(result.evaluatedNeighbors()==1 && c.reheats()==0,"Fixed-iteration SA stops at Tmin without implicit reheating");
        }
        try(SearchControl c=SearchControl.install(1000)){
            var result=sa.optimize(plan,s,List.of(five,three),List.of(),rapid);
            ok(result.evaluatedNeighbors()==5 && c.reheats()==4,"Time-mode SA reheats; empty neighbor attempts still count toward stagnation");
        }
        // Incumbent survives an interrupted next step. This is not a promise about optimality.
        PlanEvaluation ev=evaluator().evaluate(plan,s,List.of(five,three),List.of());
        SearchControl control=SearchControl.install(100);
        try(control){SearchControl.observePlan(plan,ev);Thread.sleep(120);try{SearchControl.checkpoint();}catch(SearchStopped expected){}ok(SearchControl.bestPlan()==plan,"Time limit preserves the last fully evaluated immutable incumbent");}
    }
    private static void validation(){
        rejects(()->config("speed.car","NaN"),"NaN speed rejected before experiments");
        rejects(()->config("maintenance.enabled","true"),"Preventive maintenance cannot be enabled in the numerical protocol");
        rejects(()->config("breakdowns.enabled","true"),"Automatic breakdowns cannot be enabled in the numerical protocol");
        rejects(()->config("resume","perhaps","maintenance.enabled","perhaps"),"Boolean parameters use strict parsing");
        rejects(()->new ScenarioSpec("X","UNKNOWN","SYNTHETIC",1,1,LocalDate.now(),7,8),"Unknown scenario family fails loudly");
        rejects(()->new AnnealingConfig(Double.NaN,1,0.9,1,1,1),"Nonfinite annealing temperature rejected");
        rejects(()->new ShiftSchedule(Z,361),"Meal policy offset must leave the configured end-of-shift margin");
        ok(new ScenarioSpec("MIDNIGHT","REAL","REAL",1,1,LocalDate.of(2026,9,9),23,24).toHour()==24,"Real collection windows may end at midnight (exclusive 24:00)");
    }
}
