package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.search.SearchControl;
import java.time.Instant;
import java.util.*;

/** Common candidate mechanics, not an optimizer. GRASP uses an RCL; SA's initializer is deterministic.
 * Every returned candidate satisfies the SAME full-plan evaluator, except explicitly missing demand. */
public final class FeasibleInsertionService {
    private final OperationalPlanEvaluator evaluator;
    public FeasibleInsertionService(OperationalPlanEvaluator evaluator) { this.evaluator = Objects.requireNonNull(evaluator); }
    public record Candidate(OperationalPlan plan, PlanEvaluation evaluation, int quantity, double addedCost) {}

    public List<Candidate> candidates(OperationalPlan current, PlanEvaluation currentEvaluation,
            OperationalSnapshot snapshot, Collection<Order> required, Order order, int remaining, List<RoadBlock> blocks) {
        List<Candidate> found = new ArrayList<>();
        Warehouse central = snapshot.inventory().warehouses().stream().filter(Warehouse::isCentral).findFirst().orElseThrow();
        for (VehicleOperationalState state : snapshot.vehiclesById().values()) {
            SearchControl.checkpoint();
            Instant departure = state.availableAt().isAfter(snapshot.planningTime()) ? state.availableAt() : snapshot.planningTime();
            if (!snapshot.isVehiclePlannableAt(state.vehicle().id(), departure)) continue;
            int cap = snapshot.fleetProfile().parametersFor(state.vehicle().type()).capacity();
            int q = Math.min(remaining, cap);
            DeliveryRoute route = current.routeForVehicle(state.vehicle().id()).orElse(null);
            if (route == null) {
                route = state.location().equals(central.location()) && state.carriedPackages() == 0
                        ? DeliveryRoute.startScenarioAtCentral("R-"+state.vehicle().id(),state.vehicle(),central,0,departure).returningTo(central)
                        : DeliveryRoute.replanFromCurrentLocation("R-"+state.vehicle().id(),state.vehicle(),state.location(),state.carriedPackages(),departure).returningTo(central);
            }
            int end = route.stops().size()-1;
            // All insertion positions in the final delivery segment, with exact load repair.
            int start = 0;
            for (int i=0; i<end; i++) if (route.stops().get(i) instanceof WarehouseVisit) start=i+1;
            for (int pos=start; pos<=end; pos++) {
                List<RouteStop> changed = new ArrayList<>(route.stops());
                changed.add(pos, new DeliveryStop(order,q));
                offer(current, currentEvaluation, route.withReplacedStops(changed), snapshot, required, blocks, q, found);
            }
            // Explicit reload choice at ALL warehouses, not merely the nearest one by distance.
            if (!route.stops().stream().noneMatch(DeliveryStop.class::isInstance)) {
                for (Warehouse warehouse : snapshot.inventory().warehouses()) {
                    List<RouteStop> changed = new ArrayList<>(route.stops());
                    changed.add(end, new WarehouseVisit(warehouse,0));
                    changed.add(end+1, new DeliveryStop(order,q));
                    offer(current,currentEvaluation,route.withReplacedStops(changed),snapshot,required,blocks,q,found);
                }
            }
        }
        // Smaller fragments can fit an existing trip before the deadline when a full-capacity chunk cannot.
        if (found.isEmpty() && remaining > 1) return candidates(current,currentEvaluation,snapshot,required,order,1,blocks);
        return found;
    }

    private void offer(OperationalPlan current, PlanEvaluation old, DeliveryRoute route, OperationalSnapshot snapshot,
                       Collection<Order> required, List<RoadBlock> blocks, int quantity, List<Candidate> out) {
        SearchControl.checkpoint();
        DeliveryRoute repaired = RouteLoadRepair.repair(route,snapshot);
        OperationalPlan candidate = current.withRoute(repaired);
        PlanEvaluation evaluation = evaluator.evaluate(candidate,snapshot,required,blocks);
        if (evaluation.isRouteFeasible()) {
            SearchControl.observePlan(candidate,evaluation);
            out.add(new Candidate(candidate,evaluation,quantity,evaluation.totalCost()-old.totalCost()));
        }
    }
}
