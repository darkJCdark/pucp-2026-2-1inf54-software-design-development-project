package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.util.List;
import java.util.Objects;

import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

/**
 * Operador repair de ALNS.
 */
interface RepairOperator {

    String name();

    RepairResult repair(
            OperationalPlan partialPlan,
            List<DeliveryDemand> removedDemands,
            OperationalSnapshot snapshot,
            List<RoadBlock> blocks,
            long deadlineNanos
    );

    record RepairResult(
            OperationalPlan plan,
            PlanEvaluation evaluation,
            List<Order> attendedOrders,
            List<DeliveryDemand> uninsertedDemands
    ) {

        public RepairResult {

            Objects.requireNonNull(
                    plan,
                    "plan is required"
            );

            Objects.requireNonNull(
                    evaluation,
                    "evaluation is required"
            );

            attendedOrders =
                    List.copyOf(
                            Objects.requireNonNull(
                                    attendedOrders,
                                    "attendedOrders are required"
                            )
                    );

            uninsertedDemands =
                    List.copyOf(
                            Objects.requireNonNull(
                                    uninsertedDemands,
                                    "uninsertedDemands are required"
                            )
                    );
        }

        boolean complete() {
            return uninsertedDemands.isEmpty();
        }
    }
}