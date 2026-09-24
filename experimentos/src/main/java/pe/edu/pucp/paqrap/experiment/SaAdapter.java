package pe.edu.pucp.paqrap.experiment;

import pe.edu.pucp.paqrap.planner.domain.RoadNetwork;
import pe.edu.pucp.paqrap.planner.route.*;
import pe.edu.pucp.paqrap.planner.sa.*;
import pe.edu.pucp.paqrap.planner.search.*;
import java.util.Random;

/** Initialization is inside the measured budget; no GRASP-generated seed. */
public final class SaAdapter implements UnifiedPlanner {
    @Override public String name(){return "SA";}
    @Override public String version(){return "SA-v3 2026-09-24";}
    @Override public AlgorithmOutput solve(ProblemInstance p,ExperimentConfig c,long seed){
        OperationalPlanEvaluator evaluator=new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()));
        long start=System.nanoTime();
        var built=new InitialPlanBuilder(evaluator).buildBestEffort(OperationalPlan.empty(),p.snapshot(),p.orders(),p.blocks());
        double initializationMs=(System.nanoTime()-start)/1e6;
        OperationalPlan plan=built.plan();
        String initialization=built.evaluation()==null?"TIME_LIMIT":built.evaluation().isFeasible()?"COMPLETE":plan.routes().isEmpty()?"NO_SOLUTION":"PARTIAL";
        try{
            SearchControl.checkpoint();
            if(p.snapshot().vehiclesById().isEmpty() || p.orders().isEmpty())
                return new AlgorithmOutput(plan,initialization,"NO_SEARCH_NEEDED",initializationMs,"Static batch; no collapse certificate");
            AnnealingConfig parameters=new AnnealingConfig(c.decimal("sa.temperature",1000),c.decimal("sa.minimumTemperature",1),
                    c.decimal("sa.cooling",.95),c.integer("sa.iterationsPerTemperature",50),
                    c.integer("sa.maximumIterations",1000000),c.integer("sa.maximumWithoutImprovement",1000000));
            var result=new OperationalSimulatedAnnealingPlanner(evaluator,new OperationalRouteNeighborGenerator(),new Random(seed))
                    .optimize(plan,p.snapshot(),p.orders(),p.blocks(),parameters);
            plan=result.bestPlan();
        }catch(SearchStopped exhausted){/* validated seed or its prefix survives */}
        OperationalPlan incumbent=SearchControl.bestPlan();
        if(incumbent!=null)plan=incumbent;
        return new AlgorithmOutput(plan,initialization,"RETURNED",initializationMs,
                "v3: feasible insertion seed; repaired moves; lexicographic coverage; TIME reheating; same complete-demand evaluator");
    }
}
