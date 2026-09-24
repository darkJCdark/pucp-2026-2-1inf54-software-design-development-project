package pe.edu.pucp.paqrap.planner.sa;

import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.route.*;
import pe.edu.pucp.paqrap.planner.search.*;
import java.util.*;
import java.util.random.RandomGenerator;

/** SA with repaired neighbors and strict hard constraints. Coverage is lexicographic, never
 * a money penalty: improve coverage first; apply Metropolis cost acceptance only in the same tier.
 * TIME mode reheats at Tmin, FIXED mode keeps the finite cooling schedule. */
public final class OperationalSimulatedAnnealingPlanner {
    private final OperationalPlanEvaluator evaluator;
    private final OperationalNeighborGenerator neighborGenerator;
    private final RandomGenerator random;
    public OperationalSimulatedAnnealingPlanner(OperationalPlanEvaluator evaluator,
            OperationalNeighborGenerator neighborGenerator,RandomGenerator random) {
        this.evaluator=Objects.requireNonNull(evaluator);this.neighborGenerator=Objects.requireNonNull(neighborGenerator);this.random=Objects.requireNonNull(random);
    }
    public OperationalAnnealingResult optimize(OperationalPlan initial,OperationalSnapshot snapshot,
            Collection<Order> required,List<RoadBlock> blocks,AnnealingConfig config) {
        PlanEvaluation currentEvaluation=evaluator.evaluate(initial,snapshot,required,blocks);
        if(!currentEvaluation.isRouteFeasible())throw new IllegalArgumentException("SA requires a structurally feasible initial plan");
        OperationalPlan current=initial,best=initial;
        PlanEvaluation bestEvaluation=currentEvaluation;
        double temperature=config.initialTemperature();
        int iterations=0,accepted=0,withoutImprovement=0;
        SearchControl.observePlan(best,bestEvaluation);
        try {
            while(iterations<config.maximumIterations() && withoutImprovement<config.maximumIterationsWithoutImprovement()) {
                if(temperature<config.minimumTemperature()) {
                    if(!SearchControl.isTimeLimited())break;
                    temperature=config.initialTemperature();current=best;currentEvaluation=bestEvaluation;SearchControl.reheat();
                }
                // Periodically try to insert still-unserved demand after route edits free resources.
                if(!currentEvaluation.isFeasible() && iterations>0 && iterations % (config.iterationsPerTemperature()*4L)==0) {
                    var repaired=new InitialPlanBuilder(evaluator).buildBestEffort(current,snapshot,required,blocks);
                    if(repaired.evaluation()!=null && PlanQuality.better(repaired.evaluation(),currentEvaluation)) {
                        current=repaired.plan();currentEvaluation=repaired.evaluation();
                        if(PlanQuality.better(currentEvaluation,bestEvaluation)) {best=current;bestEvaluation=currentEvaluation;withoutImprovement=0;}
                    }
                }
                for(int level=0;level<config.iterationsPerTemperature() && iterations<config.maximumIterations()
                        && withoutImprovement<config.maximumIterationsWithoutImprovement();level++) {
                    SearchControl.iteration();SearchControl.neighborAttempt();iterations++;withoutImprovement++;
                    var generated=neighborGenerator.generate(current,snapshot,random);
                    if(generated.isEmpty())continue;
                    OperationalPlan candidate=generated.get();
                    PlanEvaluation candidateEvaluation=evaluator.evaluate(candidate,snapshot,required,blocks);
                    if(!candidateEvaluation.isRouteFeasible()) {SearchControl.invalidNeighbor();continue;}
                    SearchControl.observePlan(candidate,candidateEvaluation);
                    boolean sameCoverage=PlanQuality.sameCoverage(candidateEvaluation,currentEvaluation);
                    boolean betterCoverage=!sameCoverage && PlanQuality.better(candidateEvaluation,currentEvaluation);
                    double delta=candidateEvaluation.totalCost()-currentEvaluation.totalCost();
                    if(betterCoverage || sameCoverage && (delta<=0 || random.nextDouble()<Math.exp(-delta/temperature))) {
                        current=candidate;currentEvaluation=candidateEvaluation;accepted++;SearchControl.acceptedNeighbor();
                    }
                    if(PlanQuality.better(currentEvaluation,bestEvaluation)) {
                        best=current;bestEvaluation=currentEvaluation;withoutImprovement=0;SearchControl.observePlan(best,bestEvaluation);
                    }
                }
                temperature*=config.coolingFactor();
            }
        } catch(SearchStopped exhausted) { /* Keep the best validated incumbent. */ }
        return new OperationalAnnealingResult(best,bestEvaluation,iterations,accepted);
    }
}
