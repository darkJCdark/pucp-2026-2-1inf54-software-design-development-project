package pe.edu.pucp.paqrap.planner.sa;

import pe.edu.pucp.paqrap.planner.domain.OperationalSnapshot;
import pe.edu.pucp.paqrap.planner.domain.Order;
import pe.edu.pucp.paqrap.planner.domain.RoadBlock;
import pe.edu.pucp.paqrap.planner.route.OperationalPlan;
import pe.edu.pucp.paqrap.planner.route.OperationalPlanEvaluator;
import pe.edu.pucp.paqrap.planner.route.PlanEvaluation;

import pe.edu.pucp.paqrap.planner.search.SearchControl;
import pe.edu.pucp.paqrap.planner.search.SearchStopped;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** SA over OperationalPlan: only plans satisfying every hard business rule may be accepted. */
public final class OperationalSimulatedAnnealingPlanner {
    private final OperationalPlanEvaluator evaluator;
    private final OperationalNeighborGenerator neighborGenerator;
    private final RandomGenerator random;

    public OperationalSimulatedAnnealingPlanner(OperationalPlanEvaluator evaluator,
                                                OperationalNeighborGenerator neighborGenerator,
                                                RandomGenerator random) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator is required");
        this.neighborGenerator = Objects.requireNonNull(neighborGenerator, "neighborGenerator is required");
        this.random = Objects.requireNonNull(random, "random is required");
    }

    public OperationalAnnealingResult optimize(OperationalPlan initial, OperationalSnapshot snapshot,
                                               Collection<Order> requiredOrders, List<RoadBlock> blocks,
                                               AnnealingConfig config) {
        PlanEvaluation currentEvaluation = evaluator.evaluate(initial, snapshot, requiredOrders, blocks);
        if (!currentEvaluation.isFeasible()) {
            throw new IllegalArgumentException("SA requires a feasible initial operational plan");
        }
        OperationalPlan current = initial;
        OperationalPlan best = initial;
        PlanEvaluation bestEvaluation = currentEvaluation;
        double temperature = config.initialTemperature();
        int iterations = 0;
        int accepted = 0;
        int withoutImprovement = 0;

        SearchControl.observe(true, 0, bestEvaluation.totalCost());
        try {
        while (temperature >= config.minimumTemperature()
                && iterations < config.maximumIterations()
                && withoutImprovement < config.maximumIterationsWithoutImprovement()) {
            for (int levelIteration = 0; levelIteration < config.iterationsPerTemperature()
                    && iterations < config.maximumIterations(); levelIteration++) {
                SearchControl.iteration();
                iterations++;
                SearchControl.neighborAttempt();
                var candidate = neighborGenerator.generate(current, snapshot, random);
                if (candidate.isEmpty()) {
                    continue;
                }
                PlanEvaluation candidateEvaluation = evaluator.evaluate(candidate.get(), snapshot, requiredOrders, blocks);
                if (!candidateEvaluation.isFeasible()) {
                    SearchControl.invalidNeighbor();
                    continue;
                }
                double delta = candidateEvaluation.totalCost() - currentEvaluation.totalCost();
                if (delta <= 0 || random.nextDouble() < Math.exp(-delta / temperature)) {
                    current = candidate.get();
                    currentEvaluation = candidateEvaluation;
                    accepted++;
                    SearchControl.acceptedNeighbor();
                }
                if (currentEvaluation.totalCost() < bestEvaluation.totalCost()) {
                    best = current;
                    bestEvaluation = currentEvaluation;
                    withoutImprovement = 0;
                    SearchControl.observe(true, 0, bestEvaluation.totalCost());
                } else {
                    withoutImprovement++;
                }
            }
            temperature *= config.coolingFactor();
        }
        } catch (SearchStopped exhausted) {
            // Return the best feasible plan completed before the deadline.
        }
        return new OperationalAnnealingResult(best, bestEvaluation, iterations, accepted);
    }
}
