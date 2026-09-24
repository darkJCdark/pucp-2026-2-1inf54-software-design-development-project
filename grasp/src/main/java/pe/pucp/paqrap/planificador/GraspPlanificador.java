package pe.pucp.paqrap.planificador;

import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.route.*;
import pe.edu.pucp.paqrap.planner.search.*;
import pe.pucp.paqrap.modelo.ResultadoPlanificacion;
import java.util.*;

/** GRASP: repeated randomized greedy construction (alpha RCL) + first-improvement local search.
 * v3 removes the duplicate private feasibility/scheduler/inventory logic. Every candidate is
 * evaluated against ALL original orders with the shared chronological inventory evaluator.
 * A partial feasible incumbent survives interruption, including during construction/local search. */
public final class GraspPlanificador {
    public static final String VERSION = "GRASP-v3 2026-09-24";
    private final OperationalPlanEvaluator evaluator;
    private final FeasibleInsertionService insertions;
    private final Random random;
    private ResultadoPlanificacion best;
    private List<Order> required;

    public GraspPlanificador(RoadNetwork network, RouteScheduler scheduler, OperationalPlanEvaluator evaluator, long seed) {
        Objects.requireNonNull(network); Objects.requireNonNull(scheduler);
        this.evaluator=Objects.requireNonNull(evaluator);
        this.insertions=new FeasibleInsertionService(evaluator);
        this.random=new Random(seed);
    }

    public ResultadoPlanificacion planificar(OperationalSnapshot snapshot, Collection<Order> orders,
                                              List<RoadBlock> blocks, double alpha, int maximumIterations) {
        if (!Double.isFinite(alpha) || alpha<0 || alpha>1 || maximumIterations<=0)
            throw new IllegalArgumentException("alpha must be in [0,1] and iterations > 0");
        required=new ArrayList<>(orders);
        required.sort(Comparator.comparing(Order::deadline).thenComparing(Order::id));
        best=null;
        try {
            retain(OperationalPlan.empty(),evaluator.evaluate(OperationalPlan.empty(),snapshot,required,blocks));
            if (required.isEmpty() || snapshot.vehiclesById().isEmpty()) return best;
            for (int iteration=0; iteration<maximumIterations; iteration++) {
                SearchControl.iteration();
                ResultadoPlanificacion constructed=construct(snapshot,blocks,alpha);
                improve(constructed,snapshot,blocks);
            }
        } catch (SearchStopped exhausted) { /* best is an immutable, already evaluated prefix */ }
        return best;
    }

    private ResultadoPlanificacion construct(OperationalSnapshot snapshot,List<RoadBlock> blocks,double alpha) {
        OperationalPlan current=OperationalPlan.empty();
        PlanEvaluation evaluation=evaluator.evaluate(current,snapshot,required,blocks);
        for (Order order:required) {
            int remaining=order.packages();
            while (remaining>0) {
                SearchControl.checkpoint();
                var candidates=insertions.candidates(current,evaluation,snapshot,required,order,remaining,blocks);
                if (candidates.isEmpty()) break;
                // Prefer making the largest feasible demand progress before optimizing its route cost.
                int largest=candidates.stream().mapToInt(FeasibleInsertionService.Candidate::quantity).max().orElseThrow();
                var eligible=candidates.stream().filter(c->c.quantity()==largest).toList();
                double minimum=eligible.stream().mapToDouble(FeasibleInsertionService.Candidate::addedCost).min().orElseThrow();
                double maximum=eligible.stream().mapToDouble(FeasibleInsertionService.Candidate::addedCost).max().orElseThrow();
                double threshold=minimum+alpha*(maximum-minimum);
                var rcl=eligible.stream().filter(c->c.addedCost()<=threshold+1e-9).toList();
                var chosen=rcl.get(random.nextInt(rcl.size()));
                current=chosen.plan(); evaluation=chosen.evaluation();
                retain(current,evaluation);
                remaining=evaluation.missingPackages().getOrDefault(order.id(),0);
            }
        }
        return result(current,evaluation);
    }

    private ResultadoPlanificacion improve(ResultadoPlanificacion seed,OperationalSnapshot snapshot,List<RoadBlock> blocks) {
        ResultadoPlanificacion current=seed;
        if (!current.noAtendidos().isEmpty()) {
            var repaired=new InitialPlanBuilder(evaluator).buildBestEffort(current.plan(),snapshot,required,blocks);
            if (repaired.evaluation()!=null && PlanQuality.better(repaired.evaluation(),current.evaluacion())) {
                current=result(repaired.plan(),repaired.evaluation()); retain(current.plan(),current.evaluacion());
            }
        }
        while (true) {
            SearchControl.checkpoint();
            ResultadoPlanificacion next=firstImprovement(current,snapshot,blocks);
            if (next==null) return current;
            current=next; retain(current.plan(),current.evaluacion());
        }
    }

    private ResultadoPlanificacion firstImprovement(ResultadoPlanificacion current,OperationalSnapshot snapshot,List<RoadBlock> blocks) {
        List<DeliveryRoute> routes=new ArrayList<>(current.plan().routes());
        // 2-opt over delivery order; warehouse quantities are repaired after the edit.
        for (DeliveryRoute route:routes) {
            List<Integer> indexes=deliveryIndexes(route);
            for (int a=0;a<indexes.size();a++) for (int b=a+1;b<indexes.size();b++) {
                SearchControl.checkpoint();
                List<RouteStop> stops=new ArrayList<>(route.stops());
                for (int left=a,right=b;left<right;left++,right--)
                    Collections.swap(stops,indexes.get(left),indexes.get(right));
                var candidate=tryMove(current,current.plan().withRoute(route.withReplacedStops(stops)),snapshot,blocks);
                if (candidate!=null) return candidate;
            }
        }
        // Relocation also explores idle vehicles: a cheap unit must be able to replace an expensive route.
        List<VehicleOperationalState> vehicles=new ArrayList<>(snapshot.vehiclesById().values());
        vehicles.sort(Comparator.comparingDouble((VehicleOperationalState v)->snapshot.fleetProfile().parametersFor(v.vehicle().type()).costPerKm())
                .thenComparing(v->v.vehicle().id()));
        Warehouse central=snapshot.inventory().warehouses().stream().filter(Warehouse::isCentral).findFirst().orElseThrow();
        for (DeliveryRoute source:routes) for (int index:deliveryIndexes(source)) {
            DeliveryStop delivery=(DeliveryStop)source.stops().get(index);
            for (VehicleOperationalState targetState:vehicles) {
                SearchControl.checkpoint();
                if (source.vehicle().id().equals(targetState.vehicle().id()) || !snapshot.isVehiclePlannable(targetState.vehicle().id())) continue;
                DeliveryRoute target=current.plan().routeForVehicle(targetState.vehicle().id()).orElse(null);
                if (target==null) {
                    target=DeliveryRoute.replanFromCurrentLocation("R-"+targetState.vehicle().id(),targetState.vehicle(),
                            targetState.location(),targetState.carriedPackages(),snapshot.planningTime()).returningTo(central);
                }
                List<RouteStop> from=new ArrayList<>(source.stops()); from.remove(index);
                for (int pos=0;pos<target.stops().size();pos++) {
                    List<RouteStop> to=new ArrayList<>(target.stops()); to.add(pos,delivery);
                    var raw=current.plan().withRoute(source.withReplacedStops(from)).withRoute(target.withReplacedStops(to));
                    var candidate=tryMove(current,raw,snapshot,blocks);
                    if (candidate!=null) return candidate;
                }
            }
        }
        // Cross-route swap.
        for (int a=0;a<routes.size();a++) for (int b=a+1;b<routes.size();b++) {
            DeliveryRoute first=routes.get(a),second=routes.get(b);
            for (int i:deliveryIndexes(first)) for (int j:deliveryIndexes(second)) {
                List<RouteStop> one=new ArrayList<>(first.stops()),two=new ArrayList<>(second.stops());
                RouteStop moved=one.set(i,two.get(j));two.set(j,moved);
                var candidate=tryMove(current,current.plan().withRoute(first.withReplacedStops(one)).withRoute(second.withReplacedStops(two)),snapshot,blocks);
                if(candidate!=null)return candidate;
            }
        }
        return null;
    }

    private ResultadoPlanificacion tryMove(ResultadoPlanificacion current,OperationalPlan raw,
                                            OperationalSnapshot snapshot,List<RoadBlock> blocks) {
        SearchControl.checkpoint(); SearchControl.neighborAttempt();
        OperationalPlan candidate=RouteLoadRepair.repairChanges(current.plan(),raw,snapshot);
        PlanEvaluation evaluated=evaluator.evaluate(candidate,snapshot,required,blocks);
        if(!evaluated.isRouteFeasible()) { SearchControl.invalidNeighbor(); return null; }
        SearchControl.observePlan(candidate,evaluated);
        if(!PlanQuality.better(evaluated,current.evaluacion()))return null;
        SearchControl.acceptedNeighbor();
        retain(candidate,evaluated);
        return result(candidate,evaluated);
    }

    private void retain(OperationalPlan plan,PlanEvaluation evaluation) {
        if (!evaluation.isRouteFeasible()) return;
        SearchControl.observePlan(plan,evaluation);
        if(best==null || PlanQuality.better(evaluation,best.evaluacion()))best=result(plan,evaluation);
    }
    private ResultadoPlanificacion result(OperationalPlan plan,PlanEvaluation evaluation) {
        return new ResultadoPlanificacion(plan,evaluation,required.stream().filter(o->evaluation.missingPackages().containsKey(o.id())).toList());
    }
    private static List<Integer> deliveryIndexes(DeliveryRoute route) {
        List<Integer> result=new ArrayList<>();
        for(int i=0;i<route.stops().size();i++)if(route.stops().get(i) instanceof DeliveryStop)result.add(i);
        return result;
    }
}
