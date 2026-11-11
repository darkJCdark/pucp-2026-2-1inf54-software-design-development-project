package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

/**
 * Repara insertando cada demanda en el candidato
 * factible de menor coste.
 */
final class CheapestInsertionRepair
        implements RepairOperator {

    private final OperationalPlanEvaluator evaluator;
    private final InsertionSupport insertion;

    CheapestInsertionRepair(
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
    }

    @Override
    public String name() {
        return "cheapest-insertion";
    }

    @Override
    public RepairResult repair(
            OperationalPlan partialPlan,
            List<DeliveryDemand> removedDemands,
            OperationalSnapshot snapshot,
            List<RoadBlock> blocks,
            long deadlineNanos
    ) {

        OperationalPlan current =
                partialPlan;

        List<Order> attended =
                new ArrayList<>(
                        AlnsPlanSupport
                                .ordersInPlan(
                                        partialPlan
                                )
                );

        List<DeliveryDemand> uninserted =
                new ArrayList<>();

        List<DeliveryDemand> ordered =
                new ArrayList<>(
                        removedDemands
                );

        /*
         * Deadline primero.
         */
        ordered.sort(
                Comparator
                        .comparing(
                                (
                                        DeliveryDemand demand
                                ) ->
                                        demand
                                                .order()
                                                .deadline()
                        )
                        .thenComparing(
                                (
                                        DeliveryDemand demand
                                ) ->
                                        demand
                                                .order()
                                                .id()
                        )
        );

        for (
                DeliveryDemand demand :
                ordered
        ) {

            if (
                    deadlineReached(
                            deadlineNanos
                    )
            ) {

                uninserted.add(
                        demand
                );

                continue;
            }

            List<Order> required =
                    new ArrayList<>(
                            attended
                    );

            required.add(
                    demand.order()
            );

            var best =
                    insertion
                            .bestValidatedInsertion(
                                    current,
                                    demand,
                                    snapshot,
                                    required,
                                    blocks,
                                    deadlineNanos
                            );

            if (
                    best.isPresent()
            ) {

                current =
                        best
                                .orElseThrow()
                                .plan();

                attended.add(
                        demand.order()
                );

            } else {

                uninserted.add(
                        demand
                );
            }
        }

        PlanEvaluation evaluation =
                evaluator.evaluate(
                        current,
                        snapshot,
                        attended,
                        blocks
                );

        return new RepairResult(
                current,
                evaluation,
                attended,
                uninserted
        );
    }

    private boolean deadlineReached(
            long deadlineNanos
    ) {

        return
                deadlineNanos !=
                        Long.MAX_VALUE &&
                System.nanoTime() >=
                        deadlineNanos;
    }
}