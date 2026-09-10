package pe.edu.pucp.paqrap.planner.sa;

import pe.edu.pucp.paqrap.planner.domain.Order;
import pe.edu.pucp.paqrap.planner.route.PlanEvaluator;
import pe.edu.pucp.paqrap.planner.route.RoutePlan;

import java.util.Collection;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** SA core using Metropolis acceptance and the agreed geometric cooling schedule. */
public final class SimulatedAnnealingPlanner {
    private final PlanEvaluator evaluator;
    private final NeighborGenerator neighborGenerator;
    private final RandomGenerator random;

    public SimulatedAnnealingPlanner(PlanEvaluator evaluator, NeighborGenerator neighborGenerator,
                                     RandomGenerator random) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator is required");
        this.neighborGenerator = Objects.requireNonNull(neighborGenerator, "neighborGenerator is required");
        this.random = Objects.requireNonNull(random, "random is required");
    }

    public RoutePlan optimize(RoutePlan initial, Collection<Order> requiredOrders, AnnealingConfig config) {
        if (!evaluator.isFeasible(initial, requiredOrders)) {
            throw new IllegalArgumentException("SA requires a feasible initial plan");
        }

        RoutePlan current = initial.copy();
        RoutePlan best = initial.copy();
        double currentCost = evaluator.cost(current);
        double bestCost = currentCost;
        double temperature = config.initialTemperature();
        int totalIterations = 0;
        int withoutImprovement = 0;

        while (temperature >= config.minimumTemperature()
                && totalIterations < config.maximumIterations()
                && withoutImprovement < config.maximumIterationsWithoutImprovement()) {
            for (int i = 0; i < config.iterationsPerTemperature()
                    && totalIterations < config.maximumIterations(); i++) {
                var maybeNeighbor = neighborGenerator.generate(current, random);
                totalIterations++;
                if (maybeNeighbor.isEmpty() || !evaluator.isFeasible(maybeNeighbor.get(), requiredOrders)) {
                    continue;
                }

                RoutePlan neighbor = maybeNeighbor.get();
                double neighborCost = evaluator.cost(neighbor);
                double delta = neighborCost - currentCost;
                if (delta <= 0 || random.nextDouble() < Math.exp(-delta / temperature)) {
                    current = neighbor;
                    currentCost = neighborCost;
                }
                if (currentCost < bestCost) {
                    best = current.copy();
                    bestCost = currentCost;
                    withoutImprovement = 0;
                } else {
                    withoutImprovement++;
                }
            }
            temperature *= config.coolingFactor();
        }
        return best;
    }
}
