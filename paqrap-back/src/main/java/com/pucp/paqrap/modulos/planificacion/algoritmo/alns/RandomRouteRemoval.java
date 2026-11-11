package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;

/**
 * Elimina aleatoriamente rutas completas.
 *
 * Es un destroy barato y útil para diversificación.
 */
final class RandomRouteRemoval
        implements DestroyOperator {

    @Override
    public String name() {
        return "random-route-removal";
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

        shuffle(
                candidates,
                random
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

    /**
     * Fisher-Yates usando directamente RandomGenerator.
     */
    private void shuffle(
            List<DeliveryRoute> routes,
            RandomGenerator random
    ) {

        for (
                int i =
                        routes.size() - 1;
                i > 0;
                i--
        ) {

            int j =
                    random.nextInt(
                            i + 1
                    );

            DeliveryRoute tmp =
                    routes.get(i);

            routes.set(
                    i,
                    routes.get(j)
            );

            routes.set(
                    j,
                    tmp
            );
        }
    }
}