package pe.edu.pucp.paqrap.experiment;

import pe.edu.pucp.paqrap.planner.domain.RoadNetwork;
import pe.edu.pucp.paqrap.planner.route.*;
import pe.pucp.paqrap.planificador.GraspPlanificador;

public final class GraspAdapter implements UnifiedPlanner {
    @Override public String name(){return "GRASP";}
    @Override public String version(){return GraspPlanificador.VERSION;}
    @Override public AlgorithmOutput solve(ProblemInstance p,ExperimentConfig c,long seed){
        RoadNetwork network=new RoadNetwork();
        RouteScheduler scheduler=new RouteScheduler(network);
        OperationalPlanEvaluator evaluator=new OperationalPlanEvaluator(scheduler);
        GraspPlanificador solver=new GraspPlanificador(network,scheduler,evaluator,seed);
        var result=solver.planificar(p.snapshot(),p.orders(),p.blocks(),c.decimal("grasp.alpha",.3),c.integer("grasp.iterations",1000000));
        OperationalPlan incumbent=pe.edu.pucp.paqrap.planner.search.SearchControl.bestPlan();
        if(incumbent!=null)return new AlgorithmOutput(incumbent,"INTEGRATED","RETURNED",Double.NaN,"v3: common full-demand evaluator; validated anytime incumbent");
        if(result==null)return AlgorithmOutput.empty("INTEGRATED","NO_INCUMBENT",Double.NaN,"No evaluated construction completed within the computational budget");
        // esFactible describes route validity; complete coverage is evaluated separately against all original orders.
        return new AlgorithmOutput(result.plan(),"INTEGRATED","RETURNED",Double.NaN,
                "Native unserved orders: "+result.noAtendidos().size()+"; full-demand feasibility is assessed externally");
    }
}
