package pe.edu.pucp.paqrap.planner.sa;

import pe.edu.pucp.paqrap.planner.route.OperationalPlan;
import pe.edu.pucp.paqrap.planner.route.PlanEvaluation;

import java.util.Objects;

public record OperationalAnnealingResult(OperationalPlan bestPlan, PlanEvaluation bestEvaluation,
                                         double initialCost, int iterations, int evaluatedNeighbors,
                                         int acceptedNeighbors, double finalTemperature) {
    public OperationalAnnealingResult {
        Objects.requireNonNull(bestPlan, "bestPlan is required");
        Objects.requireNonNull(bestEvaluation, "bestEvaluation is required");
    }
}
