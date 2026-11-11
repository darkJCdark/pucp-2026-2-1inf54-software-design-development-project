package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;

/**
 * Operador destroy de ALNS.
 */
interface DestroyOperator {

    String name();

    DestroyResult destroy(
            OperationalPlan plan,
            PlanEvaluation evaluation,
            RandomGenerator random,
            int routesToRemove
    );

    record DestroyResult(
            OperationalPlan plan,
            List<DeliveryDemand> removedDemands
    ) {

        public DestroyResult {

            Objects.requireNonNull(
                    plan,
                    "plan is required"
            );

            removedDemands =
                    List.copyOf(
                            Objects.requireNonNull(
                                    removedDemands,
                                    "removedDemands are required"
                            )
                    );
        }

        boolean changed() {
            return !removedDemands.isEmpty();
        }
    }
}