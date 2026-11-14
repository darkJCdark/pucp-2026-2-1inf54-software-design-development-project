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
 * Regret-3.
 *
 * Prioriza demandas para las que perder la mejor alternativa resulta
 * especialmente costoso frente a la segunda y tercera opción.
 *
 * Si existen menos de tres opciones, la demanda se considera restringida
 * y recibe prioridad. En empates se atiende primero la que tenga menos
 * alternativas factibles.
 */
final class Regret3InsertionRepair
        implements RepairOperator {

    private final OperationalPlanEvaluator evaluator;

    private final InsertionSupport insertion;

    Regret3InsertionRepair(
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
        return "regret-3-insertion";
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
                !remaining.isEmpty()
                &&
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

                double regret;

                if (
                        options.size() < 3
                ) {

                    regret =
                            Double.POSITIVE_INFINITY;

                } else {

                    double second =
                            options
                                    .get(1)
                                    .evaluation()
                                    .totalCost();

                    double third =
                            options
                                    .get(2)
                                    .evaluation()
                                    .totalCost();

                    regret =
                            second
                                    +
                                    third
                                    -
                                    2.0
                                            *
                                            bestCost;
                }

                Choice choice =
                        new Choice(
                                demand,
                                options.getFirst(),
                                regret,
                                options.size()
                        );

                if (
                        bestChoice == null
                        ||
                        better(
                                choice,
                                bestChoice
                        )
                ) {

                    bestChoice =
                            choice;
                }
            }

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
                    bestChoice.demand()
            );
        }

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

        int optionsCompare =
                Integer.compare(
                        left.options(),
                        right.options()
                );

        if (
                optionsCompare != 0
        ) {
            return optionsCompare < 0;
        }

        int costCompare =
                Double.compare(
                        left
                                .candidate()
                                .evaluation()
                                .totalCost(),

                        right
                                .candidate()
                                .evaluation()
                                .totalCost()
                );

        if (
                costCompare != 0
        ) {
            return costCompare < 0;
        }

        return left
                .demand()
                .order()
                .id()
                .compareTo(
                        right
                                .demand()
                                .order()
                                .id()
                )
                <
                0;
    }

    private boolean deadlineReached(
            long deadlineNanos
    ) {

        return deadlineNanos
                !=
                Long.MAX_VALUE
                &&
                System.nanoTime()
                        >=
                        deadlineNanos;
    }

    private record Choice(
            DeliveryDemand demand,
            InsertionSupport.InsertionCandidate candidate,
            double regret,
            int options
    ) {
    }
}
