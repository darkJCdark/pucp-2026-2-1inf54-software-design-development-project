package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

/**
 * Adaptive Large Neighborhood Search sobre el
 * dominio operacional común de PaqRap.
 *
 * Flujo:
 *
 * 1. construir seed escalable;
 * 2. seleccionar destroy;
 * 3. destruir parte de la solución;
 * 4. seleccionar repair;
 * 5. reconstruir;
 * 6. validar;
 * 7. aceptar/rechazar;
 * 8. actualizar pesos adaptativos;
 * 9. conservar mejor solución global.
 */
public final class OperationalAlnsPlanner {

    /**
     * Mismo límite superior que utilizaba SA.
     */
    public static final long PRESUPUESTO_MAXIMO_MS =
            86_400_000L;

    private static final long SIN_LIMITE =
            -1L;

    /*
     * =====================================================
     * SCORES ADAPTATIVOS
     * =====================================================
     */

    /**
     * El operador produjo una nueva mejor
     * solución global.
     */
    private static final double SCORE_NUEVO_MEJOR =
            8.0;

    /**
     * Mejoró la solución actual, aunque no
     * superó el mejor histórico.
     */
    private static final double SCORE_MEJORA_ACTUAL =
            4.0;

    /**
     * Produjo una solución peor, pero fue
     * aceptada por el criterio SA.
     */
    private static final double SCORE_ACEPTADO_PEOR =
            2.0;

    /**
     * Produjo candidato no aceptado.
     */
    private static final double SCORE_RECHAZADO =
            0.5;

    /**
     * El destroy no pudo modificar la solución.
     */
    private static final double SCORE_SIN_CAMBIO =
            0.1;

    private final OperationalPlanEvaluator evaluator;

    public OperationalAlnsPlanner(
            OperationalPlanEvaluator evaluator
    ) {

        this.evaluator =
                Objects.requireNonNull(
                        evaluator,
                        "evaluator is required"
                );
    }

    /*
     * =====================================================
     * API PÚBLICA
     * =====================================================
     */

    public ResultadoPlanificacion planificar(
            OperationalSnapshot snapshot,
            Collection<Order> orders,
            List<RoadBlock> blocks,
            AlnsConfig config,
            RandomGenerator random
    ) {

        return ejecutar(
                snapshot,
                orders,
                blocks,
                config,
                random,
                SIN_LIMITE
        ).resultado();
    }

    public ResultadoPlanificacion planificar(
            OperationalSnapshot snapshot,
            Collection<Order> orders,
            List<RoadBlock> blocks,
            AlnsConfig config,
            RandomGenerator random,
            long presupuestoMs
    ) {

        if (
                presupuestoMs <= 0 ||
                presupuestoMs >
                        PRESUPUESTO_MAXIMO_MS
        ) {

            throw new IllegalArgumentException(
                    "presupuestoMs must be within (0, " +
                            PRESUPUESTO_MAXIMO_MS +
                            "]"
            );
        }

        return ejecutar(
                snapshot,
                orders,
                blocks,
                config,
                random,
                presupuestoMs
        ).resultado();
    }

    /*
     * =====================================================
     * EJECUCIÓN
     * =====================================================
     */

    Ejecucion ejecutar(
            OperationalSnapshot snapshot,
            Collection<Order> orders,
            List<RoadBlock> blocks,
            AlnsConfig config,
            RandomGenerator random,
            long presupuestoMs
    ) {

        Objects.requireNonNull(
                snapshot,
                "snapshot is required"
        );

        Objects.requireNonNull(
                orders,
                "orders are required"
        );

        Objects.requireNonNull(
                blocks,
                "blocks are required"
        );

        Objects.requireNonNull(
                config,
                "config is required"
        );

        Objects.requireNonNull(
                random,
                "random is required"
        );

        /*
         * Deadline absoluto.
         *
         * A diferencia del SA anterior,
         * también la construcción inicial puede
         * respetar el presupuesto.
         */
        long deadlineNanos =
                deadlineNanos(
                        presupuestoMs
                );

        /*
         * =================================================
         * SEED ESCALABLE
         * =================================================
         */

        ScalableInitialSolutionBuilder builder =
                new ScalableInitialSolutionBuilder(
                        evaluator,
                        config
                );

        ScalableInitialSolutionBuilder.SeedSolution seed =
                builder.build(
                        snapshot,
                        orders,
                        blocks,
                        deadlineNanos
                );

        /*
         * No se pudo atender ningún pedido.
         */
        if (
                seed.attended()
                        .isEmpty()
        ) {

            PlanEvaluation emptyEvaluation =
                    evaluator.evaluate(
                            OperationalPlan.empty(),
                            snapshot,
                            List.of(),
                            blocks
                    );

            return new Ejecucion(
                    new ResultadoPlanificacion(
                            OperationalPlan.empty(),
                            emptyEvaluation,
                            seed.unattended()
                    ),
                    0.0,
                    0,
                    0,
                    0,
                    0,
                    config.initialTemperature(),
                    Map.of(),
                    Map.of()
            );
        }

        if (
                !seed
                        .evaluation()
                        .isFeasible()
        ) {

            throw new IllegalStateException(
                    "Initial ALNS plan must be feasible: " +
                            seed
                                    .evaluation()
                                    .violations()
            );
        }

        /*
         * =================================================
         * OPERADORES
         * =================================================
         */

        List<DestroyOperator> destroyOperators =
                List.of(
                        new RandomRouteRemoval(),
                        new WorstRouteRemoval()
                );

        List<RepairOperator> repairOperators =
                List.of(
                        new CheapestInsertionRepair(
                                evaluator,
                                config
                        ),

                        new Regret2InsertionRepair(
                                evaluator,
                                config
                        )
                );

        AdaptiveOperatorPool<DestroyOperator> destroyPool =
                new AdaptiveOperatorPool<>(
                        destroyOperators,
                        DestroyOperator::name,
                        config.reactionFactor()
                );

        AdaptiveOperatorPool<RepairOperator> repairPool =
                new AdaptiveOperatorPool<>(
                        repairOperators,
                        RepairOperator::name,
                        config.reactionFactor()
                );

        /*
         * =================================================
         * BÚSQUEDA
         * =================================================
         */

        SearchResult search =
                optimize(
                        seed.plan(),
                        seed.evaluation(),
                        snapshot,
                        blocks,
                        config,
                        random,
                        deadlineNanos,
                        destroyPool,
                        repairPool
                );

        /*
         * Los pedidos que el seed nunca pudo incluir
         * siguen siendo los únicos no atendidos.
         *
         * Durante ALNS no permitimos perder pedidos.
         */
        ResultadoPlanificacion result =
                new ResultadoPlanificacion(
                        search.bestPlan(),
                        search.bestEvaluation(),
                        seed.unattended()
                );

        return new Ejecucion(
                result,
                seed
                        .evaluation()
                        .totalCost(),
                search.iterations(),
                search.evaluatedCandidates(),
                search.acceptedCandidates(),
                search.globalImprovements(),
                search.finalTemperature(),
                destroyPool.weights(),
                repairPool.weights()
        );
    }

    /*
     * =====================================================
     * LOOP ALNS
     * =====================================================
     */

    private SearchResult optimize(
            OperationalPlan initialPlan,
            PlanEvaluation initialEvaluation,
            OperationalSnapshot snapshot,
            List<RoadBlock> blocks,
            AlnsConfig config,
            RandomGenerator random,
            long deadlineNanos,
            AdaptiveOperatorPool<DestroyOperator> destroyPool,
            AdaptiveOperatorPool<RepairOperator> repairPool
    ) {

        OperationalPlan current =
                initialPlan;

        PlanEvaluation currentEvaluation =
                initialEvaluation;

        OperationalPlan best =
                initialPlan;

        PlanEvaluation bestEvaluation =
                initialEvaluation;

        double temperature =
                config.initialTemperature();

        int iterations =
                0;

        int evaluatedCandidates =
                0;

        int acceptedCandidates =
                0;

        int globalImprovements =
                0;

        int withoutImprovement =
                0;

        while (
                iterations <
                        config.maximumIterations() &&

                withoutImprovement <
                        config.maximumIterationsWithoutImprovement() &&

                temperature >=
                        config.minimumTemperature() &&

                !deadlineReached(
                        deadlineNanos
                )
        ) {

            iterations++;

            /*
             * =============================================
             * SELECCIÓN ADAPTATIVA
             * =============================================
             */

            DestroyOperator destroy =
                    destroyPool.select(
                            random
                    );

            RepairOperator repair =
                    repairPool.select(
                            random
                    );

            int routesToRemove =
                    routesToRemove(
                            config,
                            random
                    );

            /*
             * =============================================
             * DESTROY
             * =============================================
             */

            DestroyOperator.DestroyResult destroyed =
                    destroy.destroy(
                            current,
                            currentEvaluation,
                            random,
                            routesToRemove
                    );

            /*
             * Si el destroy no pudo retirar nada,
             * únicamente penalizamos ese destroy.
             *
             * El repair todavía no participó.
             */
            if (
                    !destroyed.changed()
            ) {

                destroyPool.reward(
                        destroy,
                        SCORE_SIN_CAMBIO
                );

                withoutImprovement++;

                temperature =
                        cool(
                                temperature,
                                config
                        );

                continue;
            }

            /*
             * =============================================
             * REPAIR
             * =============================================
             */

            RepairOperator.RepairResult candidate =
                    repair.repair(
                            destroyed.plan(),
                            destroyed.removedDemands(),
                            snapshot,
                            blocks,
                            deadlineNanos
                    );

            evaluatedCandidates++;

            /*
             * MUY IMPORTANTE:
             *
             * No permitimos que ALNS reduzca el coste
             * simplemente eliminando pedidos.
             *
             * Todas las demandas destruidas deben haber
             * sido reinsertadas.
             */
            if (
                    !candidate.complete() ||
                    !candidate
                            .evaluation()
                            .isFeasible()
            ) {

                destroyPool.reward(
                        destroy,
                        SCORE_RECHAZADO
                );

                repairPool.reward(
                        repair,
                        SCORE_RECHAZADO
                );

                withoutImprovement++;

                temperature =
                        cool(
                                temperature,
                                config
                        );

                continue;
            }

            /*
             * =============================================
             * EVALUACIÓN
             * =============================================
             */

            double candidateCost =
                    candidate
                            .evaluation()
                            .totalCost();

            double currentCost =
                    currentEvaluation
                            .totalCost();

            double bestCost =
                    bestEvaluation
                            .totalCost();

            double delta =
                    candidateCost -
                            currentCost;

            boolean accepted =
                    accept(
                            delta,
                            temperature,
                            random
                    );

            double score;

            /*
             * =============================================
             * NUEVO MEJOR GLOBAL
             * =============================================
             */

            if (
                    candidateCost <
                            bestCost
            ) {

                current =
                        candidate.plan();

                currentEvaluation =
                        candidate.evaluation();

                best =
                        candidate.plan();

                bestEvaluation =
                        candidate.evaluation();

                acceptedCandidates++;

                globalImprovements++;

                withoutImprovement =
                        0;

                score =
                        SCORE_NUEVO_MEJOR;
            }

            /*
             * =============================================
             * ACEPTADO
             * =============================================
             */

            else if (
                    accepted
            ) {

                current =
                        candidate.plan();

                currentEvaluation =
                        candidate.evaluation();

                acceptedCandidates++;

                withoutImprovement++;

                if (
                        candidateCost <
                                currentCost
                ) {

                    score =
                            SCORE_MEJORA_ACTUAL;

                } else {

                    /*
                     * Solución peor aceptada para escapar
                     * de mínimos locales.
                     */
                    score =
                            SCORE_ACEPTADO_PEOR;
                }
            }

            /*
             * =============================================
             * RECHAZADO
             * =============================================
             */

            else {

                withoutImprovement++;

                score =
                        SCORE_RECHAZADO;
            }

            /*
             * =============================================
             * APRENDIZAJE DE OPERADORES
             * =============================================
             */

            destroyPool.reward(
                    destroy,
                    score
            );

            repairPool.reward(
                    repair,
                    score
            );

            /*
             * =============================================
             * ENFRIAMIENTO
             * =============================================
             */

            temperature =
                    cool(
                            temperature,
                            config
                    );
        }

        return new SearchResult(
                best,
                bestEvaluation,
                iterations,
                evaluatedCandidates,
                acceptedCandidates,
                globalImprovements,
                temperature
        );
    }

    /*
     * =====================================================
     * HELPERS
     * =====================================================
     */

    private static int routesToRemove(
            AlnsConfig config,
            RandomGenerator random
    ) {

        int min =
                config.minimumRoutesToRemove();

        int max =
                config.maximumRoutesToRemove();

        if (
                min == max
        ) {
            return min;
        }

        return
                min +
                random.nextInt(
                        max -
                        min +
                        1
                );
    }

    private static double cool(
            double temperature,
            AlnsConfig config
    ) {

        return Math.max(
                config.minimumTemperature(),
                temperature *
                        config.coolingFactor()
        );
    }

    /**
     * Mismo principio de aceptación utilizado por SA.
     */
    static boolean accept(
            double delta,
            double temperature,
            RandomGenerator random
    ) {

        return
                delta <= 0 ||

                random.nextDouble() <
                        Math.exp(
                                -delta /
                                Math.max(
                                        temperature,
                                        1e-12
                                )
                        );
    }

    private static boolean deadlineReached(
            long deadlineNanos
    ) {

        return
                deadlineNanos !=
                        Long.MAX_VALUE &&

                System.nanoTime() >=
                        deadlineNanos;
    }

    private static long deadlineNanos(
            long presupuestoMs
    ) {

        if (
                presupuestoMs ==
                        SIN_LIMITE
        ) {
            return Long.MAX_VALUE;
        }

        /*
         * presupuesto máximo = un día,
         * por lo que no existe riesgo práctico
         * de overflow.
         */
        return
                System.nanoTime() +
                presupuestoMs *
                        1_000_000L;
    }

    /*
     * =====================================================
     * MÉTRICAS
     * =====================================================
     */

    record Ejecucion(
            ResultadoPlanificacion resultado,
            double initialCost,
            int iterations,
            int evaluatedCandidates,
            int acceptedCandidates,
            int globalImprovements,
            double finalTemperature,
            Map<String, Double> destroyWeights,
            Map<String, Double> repairWeights
    ) {
    }

    private record SearchResult(
            OperationalPlan bestPlan,
            PlanEvaluation bestEvaluation,
            int iterations,
            int evaluatedCandidates,
            int acceptedCandidates,
            int globalImprovements,
            double finalTemperature
    ) {
    }
}