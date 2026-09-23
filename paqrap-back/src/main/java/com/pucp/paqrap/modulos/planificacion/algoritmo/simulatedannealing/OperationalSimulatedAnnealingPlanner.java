package com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing;

import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Pure-Java simulated annealing over the common PaqRap operational model. */
public final class OperationalSimulatedAnnealingPlanner {
    private final OperationalPlanEvaluator evaluator;
    private final OperationalNeighborGenerator neighborGenerator;
    private final InitialPlanBuilder initialPlanBuilder;

    public OperationalSimulatedAnnealingPlanner(OperationalPlanEvaluator evaluator) {
        this(evaluator, new OperationalRouteNeighborGenerator());
    }

    OperationalSimulatedAnnealingPlanner(OperationalPlanEvaluator evaluator,
                                         OperationalNeighborGenerator neighborGenerator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator is required");
        this.neighborGenerator = Objects.requireNonNull(neighborGenerator, "neighborGenerator is required");
        this.initialPlanBuilder = new InitialPlanBuilder(evaluator);
    }

    
    public ResultadoPlanificacion planificar(OperationalSnapshot snapshot, Collection<Order> orders,
                                             List<RoadBlock> blocks, AnnealingConfig config,
                                             RandomGenerator random) {
        return ejecutar(snapshot, orders, blocks, config, random).resultado();
    }

    
    Ejecucion ejecutar(OperationalSnapshot snapshot, Collection<Order> orders, List<RoadBlock> blocks,
                       AnnealingConfig config, RandomGenerator random) {
        Objects.requireNonNull(snapshot, "snapshot is required");
        Objects.requireNonNull(orders, "orders are required");
        Objects.requireNonNull(blocks, "blocks are required");
        Objects.requireNonNull(config, "config is required");
        Objects.requireNonNull(random, "random is required");

        InitialPlanBuilder.SeedPlan seed = initialPlanBuilder.build(snapshot, orders, blocks);
        if (seed.attended().isEmpty()) {
            PlanEvaluation emptyEvaluation = evaluator.evaluate(OperationalPlan.empty(), snapshot, List.of(), blocks);
            return new Ejecucion(new ResultadoPlanificacion(OperationalPlan.empty(), emptyEvaluation, seed.unattended()),
                    0.0, 0, 0, 0, config.initialTemperature());
        }

        PlanEvaluation initialEvaluation = evaluator.evaluate(seed.plan(), snapshot, seed.attended(), blocks);
        if (!initialEvaluation.isFeasible()) {
            throw new IllegalStateException("Initial SA plan must be feasible: " + initialEvaluation.violations());
        }
        SearchResult search = optimize(seed.plan(), initialEvaluation, snapshot, seed.attended(), blocks, config, random);
        ResultadoPlanificacion result = new ResultadoPlanificacion(search.bestPlan(), search.bestEvaluation(), seed.unattended());
        return new Ejecucion(result, initialEvaluation.totalCost(), search.evaluatedNeighbors(),
                search.acceptedNeighbors(), search.iterations(), search.finalTemperature());
    }

    private SearchResult optimize(OperationalPlan initial, PlanEvaluation initialEvaluation,
                                  OperationalSnapshot snapshot, Collection<Order> requiredOrders,
                                  List<RoadBlock> blocks, AnnealingConfig config, RandomGenerator random) {
        OperationalPlan current = initial;
        OperationalPlan best = initial;
        PlanEvaluation currentEvaluation = initialEvaluation;
        PlanEvaluation bestEvaluation = initialEvaluation;
        double temperature = config.initialTemperature();
        int iterations = 0;
        int evaluated = 0;
        int accepted = 0;
        int withoutImprovement = 0;

        while (temperature >= config.minimumTemperature()
                && iterations < config.maximumIterations()
                && withoutImprovement < config.maximumIterationsWithoutImprovement()) {
            for (int levelIteration = 0; levelIteration < config.iterationsPerTemperature()
                    && iterations < config.maximumIterations(); levelIteration++) {
                iterations++;
                var candidate = neighborGenerator.generate(current, snapshot, random);
                if (candidate.isEmpty()) continue;
                evaluated++;
                PlanEvaluation candidateEvaluation = evaluator.evaluate(candidate.get(), snapshot, requiredOrders, blocks);
                if (!candidateEvaluation.isFeasible()) continue;

                double delta = candidateEvaluation.totalCost() - currentEvaluation.totalCost();
                if (accept(delta, temperature, random)) {
                    current = candidate.get();
                    currentEvaluation = candidateEvaluation;
                    accepted++;
                }
                if (currentEvaluation.totalCost() < bestEvaluation.totalCost()) {
                    best = current;
                    bestEvaluation = currentEvaluation;
                    withoutImprovement = 0;
                } else {
                    withoutImprovement++;
                }
            }
            temperature *= config.coolingFactor();
        }
        return new SearchResult(best, bestEvaluation, evaluated, accepted, iterations, temperature);
    }

    static boolean accept(double delta, double temperature, RandomGenerator random) {
        return delta <= 0 || random.nextDouble() < Math.exp(-delta / temperature);
    }

    record Ejecucion(ResultadoPlanificacion resultado, double initialCost, int evaluatedNeighbors,
                     int acceptedNeighbors, int iterations, double finalTemperature) { }

    private record SearchResult(OperationalPlan bestPlan, PlanEvaluation bestEvaluation, int evaluatedNeighbors,
                                int acceptedNeighbors, int iterations, double finalTemperature) { }
}
