package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

/**
 * Regret-2.
 *
 * Prioriza pedidos cuya segunda mejor alternativa
 * es mucho peor que la primera.
 *
 * Esto evita consumir prematuramente el único
 * vehículo realmente conveniente para un pedido.
 */
final class Regret2InsertionRepair
        implements RepairOperator {

    private final OperationalPlanEvaluator evaluator;
    private final InsertionSupport insertion;

    Regret2InsertionRepair(
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
        return "regret-2-insertion";
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

        List<DeliveryDemand> remaining =
                new ArrayList<>(
                        removedDemands
                );

        List<DeliveryDemand> uninserted =
                new ArrayList<>();

        while (
                !remaining.isEmpty() &&
                !deadlineReached(
                        deadlineNanos
                )
        ) {

            Choice bestChoice =
                    null;

            for (
                    DeliveryDemand demand :
                    remaining
            ) {

                if (
                        deadlineReached(
                                deadlineNanos
                        )
                ) {
                    break;
                }

                List<Order> required =
                        new ArrayList<>(
                                attended
                        );

                required.add(
                        demand.order()
                );

                List<
                        InsertionSupport.InsertionCandidate
                        > options =
                        insertion
                                .validatedInsertions(
                                        current,
                                        demand,
                                        snapshot,
                                        required,
                                        blocks,
                                        deadlineNanos
                                );

                if (
                        options.isEmpty()
                ) {
                    continue;
                }

                double bestCost =
                        options
                                .getFirst()
                                .evaluation()
                                .totalCost();

                /*
                 * Si solo existe una alternativa,
                 * el pedido tiene prioridad máxima.
                 */
                double regret =
                        options.size() == 1
                                ? Double
                                        .POSITIVE_INFINITY
                                : options
                                        .get(1)
                                        .evaluation()
                                        .totalCost() -
                                bestCost;

                Choice choice =
                        new Choice(
                                demand,
                                options.getFirst(),
                                regret
                        );

                if (
                        bestChoice == null ||
                        better(
                                choice,
                                bestChoice
                        )
                ) {

                    bestChoice =
                            choice;
                }
            }

            /*
             * Ninguna demanda restante pudo
             * reinsertarse.
             */
            if (
                    bestChoice == null
            ) {

                uninserted.addAll(
                        remaining
                );

                remaining.clear();

                break;
            }

            current =
                    bestChoice
                            .candidate()
                            .plan();

            attended.add(
                    bestChoice
                            .demand()
                            .order()
            );

            remaining.remove(
                    bestChoice
                            .demand()
            );
        }

        /*
         * Si se agotó presupuesto antes de terminar.
         */
        if (
                !remaining.isEmpty()
        ) {

            uninserted.addAll(
                    remaining
            );
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

    private boolean better(
            Choice left,
            Choice right
    ) {

        int regretCompare =
                Double.compare(
                        left.regret(),
                        right.regret()
                );

        if (
                regretCompare != 0
        ) {
            return regretCompare > 0;
        }

        /*
         * Empate:
         * nos quedamos con el menor coste.
         */
        return left
                .candidate()
                .evaluation()
                .totalCost() <
                right
                        .candidate()
                        .evaluation()
                        .totalCost();
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

    private record Choice(
            DeliveryDemand demand,
            InsertionSupport.InsertionCandidate candidate,
            double regret
    ) {
    }
}