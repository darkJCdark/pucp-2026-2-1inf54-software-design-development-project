package pe.edu.pucp.paqrap.experiment;

import pe.edu.pucp.paqrap.planner.route.OperationalPlan;

/** Normalized output, not a claim of global infeasibility when a heuristic fails. */
public record AlgorithmOutput(OperationalPlan plan,String initialization,String termination,
                              double initializationMs,String detail) {
    public static AlgorithmOutput empty(String initialization,String termination,double ms,String detail){
        return new AlgorithmOutput(OperationalPlan.empty(),initialization,termination,ms,detail);
    }
}
