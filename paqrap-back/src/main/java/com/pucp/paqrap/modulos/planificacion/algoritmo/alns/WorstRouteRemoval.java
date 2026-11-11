package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;

/**
 * Elimina primero las rutas más costosas.
 *
 * Su objetivo es intensificar la búsqueda sobre
 * las partes más caras de la solución.
 */
final class WorstRouteRemoval
        implements DestroyOperator {

    @Override
    public String name() {
        return "worst-route-removal";
    }

    @Override
    public DestroyResult destroy(
            OperationalPlan plan,
            PlanEvaluation evaluation,
            RandomGenerator random,
            int routesToRemove
    ) {

        Objects.requireNonNull(
                plan,
                "plan is required"
        );

        Objects.requireNonNull(
                evaluation,
                "evaluation is required"
        );

        Objects.requireNonNull(
                random,
                "random is required"
        );

        List<DeliveryRoute> candidates =
                new ArrayList<>(
                        AlnsPlanSupport
                                .safelyRemovableRoutes(
                                        plan
                                )
                );

        if (
                candidates.isEmpty() ||
                routesToRemove <= 0
        ) {

            return new DestroyResult(
                    plan,
                    List.of()
            );
        }

        candidates.sort(
                Comparator
                        .comparingDouble(
                                (
                                        DeliveryRoute route
                                ) ->
                                        routeCost(
                                                route,
                                                evaluation
                                        )
                        )
                        .reversed()
        );

        int count =
                Math.min(
                        routesToRemove,
                        candidates.size()
                );

        List<DeliveryRoute> selected =
                List.copyOf(
                        candidates.subList(
                                0,
                                count
                        )
                );

        OperationalPlan partial =
                plan;

        for (
                DeliveryRoute route :
                selected
        ) {

            partial =
                    partial.withoutRoute(
                            route
                                    .vehicle()
                                    .id()
                    );
        }

        return new DestroyResult(
                partial,
                AlnsPlanSupport
                        .demandsFromRoutes(
                                selected
                        )
        );
    }

    private double routeCost(
            DeliveryRoute route,
            PlanEvaluation evaluation
    ) {

        ScheduledDeliveryRoute scheduled =
                evaluation
                        .schedulesByRouteId()
                        .get(
                                route.id()
                        );

        /*
         * El fallback prácticamente solo debería
         * ocurrir ante una evaluación incompleta.
         */
        if (
                scheduled == null
        ) {

            return route
                    .stops()
                    .size();
        }

        return scheduled.totalCost();
    }
}