package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleParameters;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.policy.PoliticaPlanificacion;
import com.pucp.paqrap.modulos.planificacion.policy.PrioridadPedido;
import com.pucp.paqrap.modulos.redvial.entity.Location;

/**
 * Ranking preliminar y barato de vehículos.
 *
 * Regla:
 *
 * CANDIDATOS QUE ESTIMADAMENTE CUMPLEN SLA:
 * 1. menor costo marginal;
 * 2. reutilizar ruta;
 * 3. menor tiempo estimado;
 * 4. id.
 *
 * CANDIDATOS QUE ESTIMADAMENTE NO CUMPLEN SLA:
 * 1. llegada más temprana;
 * 2. menor costo;
 * 3. reutilizar ruta;
 * 4. id.
 *
 * Para URGENTE/CRITICA InsertionSupport podrá revisar
 * todos los vehículos, en vez de candidateVehicles.
 */
final class VehicleSelectionPolicy {

    private final PoliticaPlanificacion politica =
            new PoliticaPlanificacion();

    List<VehicleOperationalState> rank(
            OperationalPlan plan,
            Order order,
            OperationalSnapshot snapshot
    ) {

        Objects.requireNonNull(
                plan,
                "plan is required"
        );

        Objects.requireNonNull(
                order,
                "order is required"
        );

        Objects.requireNonNull(
                snapshot,
                "snapshot is required"
        );

        Warehouse central =
                snapshot
                        .inventory()
                        .warehouses()
                        .stream()
                        .filter(
                                Warehouse::isCentral
                        )
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "The snapshot requires a central warehouse"
                                        )
                        );

        List<ScoredVehicle> scored =
                snapshot
                        .vehiclesById()
                        .values()
                        .stream()

                        .filter(
                                state ->
                                        snapshot.isVehiclePlannableAt(
                                                state.vehicle().id(),
                                                snapshot.planningTime()
                                        )
                        )

                        /*
                         * Las rutas activas con carga física
                         * se manejan por la capa dinámica.
                         */
                        .filter(
                                state ->
                                        state.carriedPackages() == 0
                        )

                        .map(
                                state ->
                                        score(
                                                plan,
                                                order,
                                                snapshot,
                                                central,
                                                state
                                        )
                        )

                        .sorted(
                                scoreComparator()
                        )

                        .toList();

        if (
                scored.size() <= 1
        ) {

            return scored
                    .stream()
                    .map(
                            ScoredVehicle::state
                    )
                    .toList();
        }

        /*
         * Mantener diversidad entre los primeros
         * candidatos cuando candidateVehicles es pequeño.
         */
        List<ScoredVehicle> diversified =
                new ArrayList<>(
                        scored.size()
                );

        EnumSet<VehicleType> represented =
                EnumSet.noneOf(
                        VehicleType.class
                );

        ScoredVehicle best =
                scored.getFirst();

        diversified.add(
                best
        );

        represented.add(
                best
                        .state()
                        .vehicle()
                        .type()
        );

        for (
                ScoredVehicle candidate :
                scored
        ) {

            VehicleType type =
                    candidate
                            .state()
                            .vehicle()
                            .type();

            if (
                    !represented.contains(
                            type
                    )
            ) {

                diversified.add(
                        candidate
                );

                represented.add(
                        type
                );
            }
        }

        for (
                ScoredVehicle candidate :
                scored
        ) {

            if (
                    !diversified.contains(
                            candidate
                    )
            ) {

                diversified.add(
                        candidate
                );
            }
        }

        return diversified
                .stream()
                .map(
                        ScoredVehicle::state
                )
                .toList();
    }

    /**
     * Determina únicamente si debemos ampliar la búsqueda
     * más allá de candidateVehicles.
     */
    boolean isSlaPriority(
            Order order,
            OperationalSnapshot snapshot
    ) {

        PrioridadPedido prioridad =
                politica.prioridadDesdeCentral(
                        order,
                        snapshot.planningTime(),
                        snapshot
                );

        return politica.priorizaLlegadaSobreCosto(
                prioridad
        );
    }

    private ScoredVehicle score(
            OperationalPlan plan,
            Order order,
            OperationalSnapshot snapshot,
            Warehouse central,
            VehicleOperationalState state
    ) {

        VehicleParameters parameters =
                snapshot
                        .fleetProfile()
                        .parametersFor(
                                state
                                        .vehicle()
                                        .type()
                        );

        int trips =
                ceilDiv(
                        order.packages(),
                        parameters.capacity()
                );

        double oneWayKm =
                central
                        .location()
                        .manhattanDistanceTo(
                                order.destination()
                        );

        boolean routeAlreadyOpen =
                plan
                        .routeForVehicle(
                                state
                                        .vehicle()
                                        .id()
                        )
                        .isPresent();

        Instant demandStartsAt;

        double approachCentralKm =
                0.0;

        if (
                routeAlreadyOpen
        ) {

            DeliveryRoute route =
                    plan
                            .routeForVehicle(
                                    state
                                            .vehicle()
                                            .id()
                            )
                            .orElseThrow();

            demandStartsAt =
                    estimatedRouteCompletion(
                            route,
                            parameters.speedKmPerHour()
                    );

        } else {

            demandStartsAt =
                    laterOf(
                            snapshot.planningTime(),
                            state.availableAt()
                    );

            if (
                    !state
                            .location()
                            .equals(
                                    central.location()
                            )
            ) {

                approachCentralKm =
                        state
                                .location()
                                .manhattanDistanceTo(
                                        central.location()
                                );

                demandStartsAt =
                        plusTravel(
                                demandStartsAt,
                                approachCentralKm,
                                parameters.speedKmPerHour()
                        );
            }
        }

        /*
         * =================================================
         * COSTO MARGINAL
         * =================================================
         */

        double marginalDistanceKm =
                approachCentralKm
                        +
                        2.0
                                *
                                oneWayKm
                                *
                                trips;

        double marginalCost =
                marginalDistanceKm
                        *
                        parameters.costPerKm();

        /*
         * =================================================
         * LLEGADA ESTIMADA
         * =================================================
         */

        Instant latestDeliveryArrival =
                demandStartsAt;

        for (
                int trip = 0;
                trip < trips;
                trip++
        ) {

            latestDeliveryArrival =
                    plusTravel(
                            latestDeliveryArrival,
                            oneWayKm,
                            parameters.speedKmPerHour()
                    );

            if (
                    trip < trips - 1
            ) {

                latestDeliveryArrival =
                        latestDeliveryArrival.plus(
                                DeliveryStop.SERVICE_TIME
                        );

                latestDeliveryArrival =
                        plusTravel(
                                latestDeliveryArrival,
                                oneWayKm,
                                parameters.speedKmPerHour()
                        );
            }
        }

        boolean estimatedSlaMiss =
                latestDeliveryArrival.isAfter(
                        order.deadline()
                );

        return new ScoredVehicle(
                state,
                estimatedSlaMiss,
                marginalCost,
                !routeAlreadyOpen,
                latestDeliveryArrival,
                trips
        );
    }

    private Comparator<ScoredVehicle> scoreComparator() {

        return (
                left,
                right
        ) -> {

            /*
             * =================================================
             * 1. FACTIBILIDAD SLA ESTIMADA
             * =================================================
             */

            int sla =
                    Boolean.compare(
                            left.estimatedSlaMiss(),
                            right.estimatedSlaMiss()
                    );

            if (
                    sla != 0
            ) {
                return sla;
            }

            /*
             * =================================================
             * 2A. AMBOS ESTIMADAMENTE CUMPLEN
             * =================================================
             *
             * Ahorrar antes que correr innecesariamente.
             */

            if (
                    !left.estimatedSlaMiss()
            ) {

                int cost =
                        Double.compare(
                                left.marginalCost(),
                                right.marginalCost()
                        );

                if (
                        cost != 0
                ) {
                    return cost;
                }

                int route =
                        Boolean.compare(
                                left.opensNewRoute(),
                                right.opensNewRoute()
                        );

                if (
                        route != 0
                ) {
                    return route;
                }

                int arrival =
                        left
                                .latestDeliveryArrival()
                                .compareTo(
                                        right.latestDeliveryArrival()
                                );

                if (
                        arrival != 0
                ) {
                    return arrival;
                }

            } else {

                /*
                 * =================================================
                 * 2B. AMBOS PARECEN LLEGAR TARDE
                 * =================================================
                 *
                 * Aquí sí tiene sentido intentar rescatar
                 * mediante la llegada más temprana.
                 */

                int arrival =
                        left
                                .latestDeliveryArrival()
                                .compareTo(
                                        right.latestDeliveryArrival()
                                );

                if (
                        arrival != 0
                ) {
                    return arrival;
                }

                int cost =
                        Double.compare(
                                left.marginalCost(),
                                right.marginalCost()
                        );

                if (
                        cost != 0
                ) {
                    return cost;
                }

                int route =
                        Boolean.compare(
                                left.opensNewRoute(),
                                right.opensNewRoute()
                        );

                if (
                        route != 0
                ) {
                    return route;
                }
            }

            return left
                    .state()
                    .vehicle()
                    .id()
                    .compareTo(
                            right
                                    .state()
                                    .vehicle()
                                    .id()
                    );
        };
    }

    private Instant estimatedRouteCompletion(
            DeliveryRoute route,
            double speedKmPerHour
    ) {

        Instant time =
                route.departureAt();

        Location current =
                route.startLocation();

        for (
                RouteStop stop :
                route.stops()
        ) {

            double distance =
                    current.manhattanDistanceTo(
                            stop.location()
                    );

            time =
                    plusTravel(
                            time,
                            distance,
                            speedKmPerHour
                    );

            if (
                    stop instanceof DeliveryStop
            ) {

                time =
                        time.plus(
                                DeliveryStop.SERVICE_TIME
                        );
            }

            current =
                    stop.location();
        }

        return time;
    }

    private Instant plusTravel(
            Instant start,
            double distanceKm,
            double speedKmPerHour
    ) {

        if (
                distanceKm <= 0
        ) {
            return start;
        }

        long millis =
                Math.round(
                        distanceKm
                                /
                                speedKmPerHour
                                *
                                3_600_000.0
                );

        return start.plus(
                Duration.ofMillis(
                        millis
                )
        );
    }

    private Instant laterOf(
            Instant first,
            Instant second
    ) {

        return first.isAfter(
                second
        )
                ? first
                : second;
    }

    private int ceilDiv(
            int numerator,
            int denominator
    ) {

        return (
                numerator
                        +
                        denominator
                        -
                        1
        )
                /
                denominator;
    }

    private record ScoredVehicle(
            VehicleOperationalState state,
            boolean estimatedSlaMiss,
            double marginalCost,
            boolean opensNewRoute,
            Instant latestDeliveryArrival,
            int trips
    ) {
    }
}