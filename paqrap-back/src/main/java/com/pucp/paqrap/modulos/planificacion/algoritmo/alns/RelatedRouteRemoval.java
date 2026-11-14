package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;

/**
 * Variante conservadora de Shaw/related removal.
 *
 * La arquitectura actual del destroy retira rutas completas para no romper
 * entregas parciales. Este operador selecciona una ruta semilla y después
 * retira rutas geográfica y temporalmente parecidas:
 *
 * - cercanía entre centroides de pedidos;
 * - semejanza entre deadlines;
 * - semejanza de volumen.
 *
 * Así ALNS puede reconstruir conjuntamente zonas coherentes en vez de
 * limitarse a rutas aleatorias o solamente costosas.
 */
final class RelatedRouteRemoval
        implements DestroyOperator {

    private static final double DEADLINE_HOUR_WEIGHT =
            0.25;

    private static final double PACKAGE_WEIGHT =
            0.05;

    @Override
    public String name() {
        return "related-route-removal";
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
                candidates.isEmpty()
                ||
                routesToRemove <= 0
        ) {

            return new DestroyResult(
                    plan,
                    List.of()
            );
        }

        DeliveryRoute seed =
                candidates.get(
                        random.nextInt(
                                candidates.size()
                        )
                );

        RouteProfile seedProfile =
                profile(
                        seed
                );

        candidates.sort(
                Comparator
                        .comparingDouble(
                                (
                                        DeliveryRoute route
                                ) ->
                                        route == seed
                                                ? Double.NEGATIVE_INFINITY
                                                : relatedness(
                                                        seedProfile,
                                                        profile(
                                                                route
                                                        )
                                                )
                        )
                        .thenComparing(
                                route ->
                                        route
                                                .vehicle()
                                                .id()
                        )
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

    private RouteProfile profile(
            DeliveryRoute route
    ) {

        double sumX =
                0.0;

        double sumY =
                0.0;

        int deliveries =
                0;

        int packages =
                0;

        Instant earliestDeadline =
                Instant.MAX;

        for (
                var stop :
                route.stops()
        ) {

            if (
                    stop instanceof
                            DeliveryStop delivery
            ) {

                sumX +=
                        delivery
                                .order()
                                .destination()
                                .x();

                sumY +=
                        delivery
                                .order()
                                .destination()
                                .y();

                packages +=
                        delivery.deliveredPackages();

                deliveries++;

                if (
                        delivery
                                .order()
                                .deadline()
                                .isBefore(
                                        earliestDeadline
                                )
                ) {

                    earliestDeadline =
                            delivery
                                    .order()
                                    .deadline();
                }
            }
        }

        if (
                deliveries == 0
        ) {

            return new RouteProfile(
                    route
                            .startLocation()
                            .x(),
                    route
                            .startLocation()
                            .y(),
                    Instant.MAX,
                    0
            );
        }

        return new RouteProfile(
                sumX
                        /
                        deliveries,

                sumY
                        /
                        deliveries,

                earliestDeadline,

                packages
        );
    }

    private double relatedness(
            RouteProfile left,
            RouteProfile right
    ) {

        double geographic =
                Math.abs(
                        left.centroidX()
                                -
                                right.centroidX()
                )
                +
                Math.abs(
                        left.centroidY()
                                -
                                right.centroidY()
                );

        double deadlineHours;

        if (
                left.earliestDeadline()
                        .equals(
                                Instant.MAX
                        )
                ||
                right.earliestDeadline()
                        .equals(
                                Instant.MAX
                        )
        ) {

            deadlineHours =
                    0.0;

        } else {

            deadlineHours =
                    Math.abs(
                            Duration
                                    .between(
                                            left.earliestDeadline(),
                                            right.earliestDeadline()
                                    )
                                    .toMinutes()
                    )
                    /
                    60.0;
        }

        double packageDifference =
                Math.abs(
                        left.packages()
                                -
                                right.packages()
                );

        return geographic
                +
                DEADLINE_HOUR_WEIGHT
                        *
                        deadlineHours
                +
                PACKAGE_WEIGHT
                        *
                        packageDifference;
    }

    private record RouteProfile(
            double centroidX,
            double centroidY,
            Instant earliestDeadline,
            int packages
    ) {
    }
}
