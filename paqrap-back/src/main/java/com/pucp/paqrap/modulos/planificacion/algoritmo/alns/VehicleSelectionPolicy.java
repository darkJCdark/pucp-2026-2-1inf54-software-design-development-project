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
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.WarehouseSelectionPolicy;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.TransferStop;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
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

    private final WarehouseSelectionPolicy warehouseSelection =
            new WarehouseSelectionPolicy();

    /** Un vehículo que terminó su ruta y espera >=30 min no debe quedar fuera del top-K. */
    static final Duration UMBRAL_OCIOSIDAD = Duration.ofMinutes(30);
    /** No forzamos candidatos ociosos excesivamente lejos del pedido. */
    static final Duration MARGEN_LLEGADA_OCIOSA = Duration.ofMinutes(45);


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
                        .map(
                                state ->
                                        score(
                                                plan,
                                                order,
                                                snapshot,
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
                    !diversified.contains(candidate)
            ) {
                diversified.add(candidate);
            }
        }

        // Reactivación: abrir al menos una plaza del top-K a una unidad que lleva
        // >=30 min libre en un intermedio, si tiene una ETA competitiva y SLA viable.
        // La primera opción original se conserva. Bajo presión el ranking de SLA
        // ya evalúa TODOS los vehículos, por lo que no hace falta preferencia extra.
        if (!warehouseSelection.urgent(order, snapshot) && diversified.size() > 1) {
            ScoredVehicle mejor = diversified.getFirst();
            ScoredVehicle ocioso = scored.stream()
                    .filter(v -> idleAtIntermediate(v.state(), snapshot))
                    .filter(v -> !v.estimatedSlaMiss())
                    .filter(v -> !v.latestDeliveryArrival().isAfter(
                            mejor.latestDeliveryArrival().plus(MARGEN_LLEGADA_OCIOSA)))
                    .findFirst()
                    .orElse(null);
            if (ocioso != null && !ocioso.equals(mejor)) {
                diversified.remove(ocioso);
                diversified.add(1, ocioso);
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

        return politica.priorizaLlegadaSobreCosto(prioridad)
                || warehouseSelection.urgent(order, snapshot);
    }

    private boolean idleAtIntermediate(VehicleOperationalState state, OperationalSnapshot snapshot) {
        if (state.status() != VehicleStatus.AVAILABLE
                || state.availableAt().isAfter(snapshot.planningTime())
                || Duration.between(state.availableAt(), snapshot.planningTime())
                            .compareTo(UMBRAL_OCIOSIDAD) < 0) {
            return false;
        }
        return snapshot.inventory().warehouses().stream()
                .anyMatch(w -> !w.isCentral() && w.location().equals(state.location()));
    }

    private ScoredVehicle score(
            OperationalPlan plan,
            Order order,
            OperationalSnapshot snapshot,
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

        boolean routeAlreadyOpen =
                plan
                        .routeForVehicle(
                                state
                                        .vehicle()
                                        .id()
                        )
                        .isPresent();

        Instant demandStartsAt;

        Location origin;

        int availableLoad;

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

            origin =
                    routeEndLocation(
                            route
                    );

            availableLoad =
                    Math.max(
                            0,
                            projectedFinalLoad(route)
                    );

        } else {

            demandStartsAt =
                    laterOf(
                            snapshot.planningTime(),
                            state.availableAt()
                    );

            origin =
                    state.location();

            availableLoad =
                    state.carriedPackages();
        }

        boolean firstRouteStartsAtCentral =
                !routeAlreadyOpen
                        &&
                        state
                                .location()
                                .equals(
                                        central.location()
                                );

        double marginalDistanceKm =
                0.0;

        Instant latestDeliveryArrival =
                demandStartsAt;

        Location tripOrigin =
                origin;

        int remaining =
                order.packages();

        for (
                int trip = 0;
                trip < trips;
                trip++
        ) {

            int quantity =
                    Math.min(
                            remaining,
                            parameters.capacity()
                    );

            int suppliedFromVehicle =
                    Math.min(
                            availableLoad,
                            quantity
                    );

            int pickupQuantity =
                    quantity
                            -
                            suppliedFromVehicle;

            Warehouse terminal =
                    warehouseSelection
                            .bestTerminalWarehouse(
                                    order.destination(),
                                    snapshot.inventory()
                            );

            double approachSupplyKm;
            double deliveryKm;

            if (
                    pickupQuantity == 0
            ) {

                /*
                 * La unidad ya lleva suficiente producto P. No la obligamos a
                 * entrar a un almacén únicamente para reconstruir una carga que
                 * ya existe físicamente.
                 */
                approachSupplyKm = 0.0;
                deliveryKm =
                        tripOrigin
                                .manhattanDistanceTo(
                                        order.destination()
                                );

            } else {

                Warehouse supply =
                        trip == 0
                                &&
                                firstRouteStartsAtCentral

                                ? central

                                : warehouseSelection
                                        .bestSupplyWarehouse(
                                                tripOrigin,
                                                order,
                                                pickupQuantity,
                                                snapshot.inventory()
                                        );

                approachSupplyKm =
                        tripOrigin
                                .manhattanDistanceTo(
                                        supply.location()
                                );

                deliveryKm =
                        supply
                                .location()
                                .manhattanDistanceTo(
                                        order.destination()
                                );
            }

            double repositionKm =
                    order
                            .destination()
                            .manhattanDistanceTo(
                                    terminal.location()
                            );

            marginalDistanceKm +=
                    approachSupplyKm
                            +
                            deliveryKm
                            +
                            repositionKm;

            latestDeliveryArrival =
                    plusTravel(
                            latestDeliveryArrival,
                            approachSupplyKm
                                    +
                                    deliveryKm,
                            parameters.speedKmPerHour()
                    );

            if (
                    trip < trips - 1
            ) {

                latestDeliveryArrival =
                        latestDeliveryArrival.plus(
                                DeliveryStop.SERVICE_TIME
                        );
            }

            availableLoad -=
                    suppliedFromVehicle;

            tripOrigin =
                    terminal.location();

            remaining -=
                    quantity;
        }

        double marginalCost =
                marginalDistanceKm
                        *
                        parameters.costPerKm();

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


    private int projectedFinalLoad(
            DeliveryRoute route
    ) {

        int load =
                route.initialLoad();

        for (
                RouteStop stop :
                route.stops()
        ) {

            if (
                    stop instanceof WarehouseVisit visit
            ) {
                load +=
                        visit.pickupPackages();

            } else if (
                    stop instanceof DeliveryStop delivery
            ) {
                load -=
                        delivery.deliveredPackages();

            } else if (
                    stop instanceof TransferStop transfer
            ) {
                load +=
                        transfer.receives()
                                ? transfer.packages()
                                : -transfer.packages();
            }
        }

        return load;
    }

    private Location routeEndLocation(
            DeliveryRoute route
    ) {

        if (
                route.stops().isEmpty()
        ) {
            return route.startLocation();
        }

        return route
                .stops()
                .getLast()
                .location();
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