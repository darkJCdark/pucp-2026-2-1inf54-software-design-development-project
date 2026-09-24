package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.search.*;
import java.util.*;

/** Deterministic earliest-deadline feasible insertion seed. Independent of GRASP's randomized construction.
 * A failed order never discards other feasible deliveries; timeout retains the last evaluated partial plan. */
public final class InitialPlanBuilder {
    private final OperationalPlanEvaluator evaluator;
    private final FeasibleInsertionService insertions;
    public InitialPlanBuilder(OperationalPlanEvaluator evaluator) {
        this.evaluator=Objects.requireNonNull(evaluator); this.insertions=new FeasibleInsertionService(evaluator);
    }
    public Optional<OperationalPlan> build(OperationalSnapshot snapshot, Collection<Order> orders,
                                           Warehouse central, List<RoadBlock> blocks) {
        BuiltPlan result = buildBestEffort(OperationalPlan.empty(),snapshot,orders,blocks);
        return result.evaluation()!=null && result.evaluation().isFeasible() ? Optional.of(result.plan()) : Optional.empty();
    }
    public record BuiltPlan(OperationalPlan plan, PlanEvaluation evaluation) {}

    public BuiltPlan buildBestEffort(OperationalPlan initial, OperationalSnapshot snapshot,
                                      Collection<Order> orders, List<RoadBlock> blocks) {
        OperationalPlan current=initial;
        PlanEvaluation evaluation=null;
        try {
            evaluation=evaluator.evaluate(current,snapshot,orders,blocks);
            if (!evaluation.isRouteFeasible()) throw new IllegalArgumentException("Invalid initial partial plan");
            SearchControl.observePlan(current,evaluation);
            List<Order> ordered=new ArrayList<>(orders);
            ordered.sort(Comparator.comparing(Order::deadline).thenComparing(Order::id));
            for(Order order:ordered) {
                int remaining=evaluation.missingPackages().getOrDefault(order.id(),0);
                while(remaining>0) {
                    SearchControl.checkpoint();
                    var candidates=insertions.candidates(current,evaluation,snapshot,orders,order,remaining,blocks);
                    if(candidates.isEmpty()) break;
                    var best=candidates.stream().min((a,b)->PlanQuality.compare(a.evaluation(),b.evaluation())).orElseThrow();
                    current=best.plan(); evaluation=best.evaluation();
                    SearchControl.observePlan(current,evaluation);
                    remaining=evaluation.missingPackages().getOrDefault(order.id(),0);
                }
            }
        } catch(SearchStopped exhausted) { /* Return the completed prefix, not a zero-cost fake success. */ }
        return new BuiltPlan(current,evaluation);
    }
}
