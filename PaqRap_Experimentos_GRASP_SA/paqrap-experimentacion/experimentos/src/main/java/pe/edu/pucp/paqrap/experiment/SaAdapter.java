package pe.edu.pucp.paqrap.experiment;

import pe.edu.pucp.paqrap.planner.domain.RoadNetwork;
import pe.edu.pucp.paqrap.planner.route.*;
import pe.edu.pucp.paqrap.planner.sa.*;
import pe.edu.pucp.paqrap.planner.search.SearchControl;
import pe.edu.pucp.paqrap.planner.search.SearchStopped;
import java.util.Random;

/** Includes SA's ORIGINAL deterministic initializer in the measured run; never seeds it with GRASP. */
public final class SaAdapter implements UnifiedPlanner {
    @Override public String name(){return "SA";}
    @Override public String version(){return "SA-operational-v1.1 + shared-domain-v2 (metricas; 2026-09-23)";}
    @Override public AlgorithmOutput solve(ProblemInstance p,ExperimentConfig c,long seed){
        RouteScheduler scheduler=new RouteScheduler(new RoadNetwork());
        OperationalPlanEvaluator evaluator=new OperationalPlanEvaluator(scheduler);
        InitialPlanBuilder builder=new InitialPlanBuilder(evaluator);
        long start=System.nanoTime();
        OperationalPlan initial;
        try {
            var built=builder.build(p.snapshot(),p.orders(),p.central(),p.blocks());
            if(built.isEmpty())return AlgorithmOutput.empty("FAILED","NO_INITIAL_PLAN",(System.nanoTime()-start)/1e6,
                    "Original SA constructor did not find a complete feasible initial plan; NOT proof of infeasibility");
            initial=built.get();
        } catch(SearchStopped exhausted){
            return AlgorithmOutput.empty("TIME_LIMIT","TIME_LIMIT_INITIALIZATION",(System.nanoTime()-start)/1e6,
                    "Budget includes initialization; no feasible seed completed");
        }
        double initializationMs=(System.nanoTime()-start)/1e6;
        // Builder has already performed a full-demand evaluation. The optimizer re-evaluates, as in source.
        AnnealingConfig parameters=new AnnealingConfig(c.decimal("sa.temperature",1000),c.decimal("sa.minimumTemperature",1),
                c.decimal("sa.cooling",.95),c.integer("sa.iterationsPerTemperature",50),
                c.integer("sa.maximumIterations",1000000),c.integer("sa.maximumWithoutImprovement",1000000));
        OperationalSimulatedAnnealingPlanner solver=new OperationalSimulatedAnnealingPlanner(evaluator,
                new OperationalRouteNeighborGenerator(),new Random(seed));
        try {
            var result=solver.optimize(initial,p.snapshot(),p.orders(),p.blocks(),parameters);
            return new AlgorithmOutput(result.bestPlan(),"BUILT","RETURNED",initializationMs,
                    "SA original; initialCost="+result.initialCost()+"; iterations="+result.iterations()
                            +"; evaluatedNeighbors="+result.evaluatedNeighbors()+"; acceptedNeighbors="
                            +result.acceptedNeighbors()+"; finalTemperature="+result.finalTemperature());
        } catch(SearchStopped exhausted){
            // Timeout during optimize's initial re-evaluation: retain the already validated initializer.
            return new AlgorithmOutput(initial,"BUILT","TIME_LIMIT_AFTER_INITIALIZATION",initializationMs,"Returning validated seed");
        }
    }
}
