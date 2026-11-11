package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.policy.PoliticaPlanificacion;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

/**
 * Constructor inicial escalable para ALNS.
 *
 * Los pedidos ya no se ordenan únicamente por deadline.
 *
 * Ahora utiliza la política operacional:
 *
 * - slack;
 * - urgencia;
 * - espera acumulada;
 * - tiempo mínimo estimado.
 */
final class ScalableInitialSolutionBuilder {

    private final OperationalPlanEvaluator evaluator;

    private final InsertionSupport insertion;

    private final int batchSize;

    private final PoliticaPlanificacion politica =
            new PoliticaPlanificacion();

    ScalableInitialSolutionBuilder(
            OperationalPlanEvaluator evaluator,
            AlnsConfig config
    ) {

        this.evaluator =
                Objects.requireNonNull(
                        evaluator,
                        "evaluator is required"
                );

        Objects.requireNonNull(
                config,
                "config is required"
        );

        this.insertion =
                new InsertionSupport(
                        evaluator,
                        config.candidateVehicles()
                );

        this.batchSize =
                config.initialBatchSize();
    }

    SeedSolution build(
            OperationalSnapshot snapshot,
            Collection<Order> orders,
            List<RoadBlock> blocks,
            long deadlineNanos
    ) {

        /*
         * =================================================
         * PRIORIDAD OPERACIONAL
         * =================================================
         *
         * Además, PoliticaPlanificacion valida que
         * ningún pedido futuro haya entrado por error.
         */

        List<Order> ordered =
                politica
                        .ordenarDesdeCentral(
                                orders,
                                snapshot.planningTime(),
                                snapshot
                        );

        OperationalPlan committedPlan =
                OperationalPlan.empty();

        List<Order> attended =
                new ArrayList<>();

        List<Order> unattended =
                new ArrayList<>();

        for (
                int start = 0;
                start < ordered.size();
        ) {

            if (
                    deadlineReached(
                            deadlineNanos
                    )
            ) {

                unattended.addAll(
                        ordered.subList(
                                start,
                                ordered.size()
                        )
                );

                break;
            }

            int end =
                    Math.min(
                            ordered.size(),
                            start +
                                    batchSize
                    );

            List<Order> batch =
                    ordered.subList(
                            start,
                            end
                    );

            /*
             * =================================================
             * FASE RÁPIDA
             * =================================================
             */

            OperationalPlan tentative =
                    committedPlan;

            List<Order> tentativeRequired =
                    new ArrayList<>(
                            attended
                    );

            for (
                    Order order :
                    batch
            ) {

                tentative =
                        insertion
                                .appendHeuristically(
                                        tentative,

                                        new DeliveryDemand(
                                                order,
                                                order.packages()
                                        ),

                                        snapshot
                                );

                tentativeRequired.add(
                        order
                );
            }

            /*
             * Una evaluación para todo el lote.
             */
            PlanEvaluation batchEvaluation =
                    evaluator.evaluate(
                            tentative,
                            snapshot,
                            tentativeRequired,
                            blocks
                    );

            if (
                    batchEvaluation
                            .isFeasible()
            ) {

                committedPlan =
                        tentative;

                attended.addAll(
                        batch
                );

            } else {

                /*
                 * =================================================
                 * FALLBACK LOCAL
                 * =================================================
                 *
                 * Los pedidos permanecen en el mismo orden de
                 * prioridad calculado anteriormente.
                 */

                for (
                        Order order :
                        batch
                ) {

                    if (
                            deadlineReached(
                                    deadlineNanos
                            )
                    ) {

                        unattended.add(
                                order
                        );

                        continue;
                    }

                    List<Order> required =
                            new ArrayList<>(
                                    attended
                            );

                    required.add(
                            order
                    );

                    var best =
                            insertion
                                    .bestValidatedInsertion(
                                            committedPlan,

                                            new DeliveryDemand(
                                                    order,
                                                    order.packages()
                                            ),

                                            snapshot,

                                            required,

                                            blocks,

                                            deadlineNanos
                                    );

                    if (
                            best.isPresent()
                    ) {

                        committedPlan =
                                best
                                        .orElseThrow()
                                        .plan();

                        attended.add(
                                order
                        );

                    } else {

                        unattended.add(
                                order
                        );
                    }
                }
            }

            start =
                    end;
        }

        /*
         * Validación final obligatoria.
         */
        PlanEvaluation evaluation =
                evaluator.evaluate(
                        committedPlan,
                        snapshot,
                        attended,
                        blocks
                );

        if (
                !evaluation
                        .isFeasible()
        ) {

            throw new IllegalStateException(
                    "ALNS seed must be feasible: "
                            +
                            evaluation
                                    .violations()
            );
        }

        return new SeedSolution(
                committedPlan,
                evaluation,
                attended,
                unattended
        );
    }

    private boolean deadlineReached(
            long deadlineNanos
    ) {

        return deadlineNanos !=
                Long.MAX_VALUE
                &&
                System.nanoTime() >=
                        deadlineNanos;
    }

    record SeedSolution(
            OperationalPlan plan,
            PlanEvaluation evaluation,
            List<Order> attended,
            List<Order> unattended
    ) {

        SeedSolution {

            attended =
                    List.copyOf(
                            attended
                    );

            unattended =
                    List.copyOf(
                            unattended
                    );
        }
    }
}