package com.pucp.paqrap.modulos.planificacion.algoritmo.common;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.TransferDirection;
import com.pucp.paqrap.modulos.planificacion.entity.TransferStop;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

/**
 * Valida todas las reglas duras del plan.
 *
 * La sobrecarga con ContextoProgramacionRuta permite
 * evaluar continuaciones desde checkpoints sin volver
 * a asignar un refrigerio que ya fue tomado.
 */
public final class OperationalPlanEvaluator {

    public static final double HISTORICAL_MAX_LEG_DISTANCE_KM =
            80.0;

    private final RouteScheduler scheduler;

    /**
     * 0 o menor deshabilita el límite.
     */
    private final double maxLegDistanceKm;

    private final MetricasPlanificador metricas;

    public OperationalPlanEvaluator(
            RouteScheduler scheduler
    ) {
        this(
                scheduler,
                0
        );
    }

    public OperationalPlanEvaluator(
            RouteScheduler scheduler,
            double maxLegDistanceKm
    ) {
        this(
                scheduler,
                maxLegDistanceKm,
                MetricasPlanificador.NOOP
        );
    }

    public OperationalPlanEvaluator(
            RouteScheduler scheduler,
            double maxLegDistanceKm,
            MetricasPlanificador metricas
    ) {

        this.scheduler =
                Objects.requireNonNull(
                        scheduler,
                        "scheduler is required"
                );

        if (
                Double.isNaN(
                        maxLegDistanceKm
                )
        ) {
            throw new IllegalArgumentException(
                    "maxLegDistanceKm must be a number"
            );
        }

        this.maxLegDistanceKm =
                maxLegDistanceKm;

        this.metricas =
                Objects.requireNonNull(
                        metricas,
                        "metricas is required"
                );
    }

    /**
     * Evaluación normal.
     */
    public PlanEvaluation evaluate(
            OperationalPlan plan,
            OperationalSnapshot snapshot,
            Collection<Order> requiredOrders,
            List<RoadBlock> blocks
    ) {

        return evaluate(
                plan,
                snapshot,
                requiredOrders,
                blocks,
                Map.of()
        );
    }

    /**
     * Evaluación con contexto temporal por ruta.
     *
     * Principal uso:
     *
     * continuación desde checkpoint donde el
     * conductor ya tomó refrigerio.
     */
    public PlanEvaluation evaluate(
            OperationalPlan plan,
            OperationalSnapshot snapshot,
            Collection<Order> requiredOrders,
            List<RoadBlock> blocks,
            Map<String, ContextoProgramacionRuta> contextosPorRuta
    ) {

        long inicio =
                metricas.inicio();

        try {

            return evaluarPlan(
                    plan,
                    snapshot,
                    requiredOrders,
                    blocks,
                    contextosPorRuta
            );

        } finally {

            metricas.registrarEvaluacion(
                    inicio
            );
        }
    }

    private PlanEvaluation evaluarPlan(
            OperationalPlan plan,
            OperationalSnapshot snapshot,
            Collection<Order> requiredOrders,
            List<RoadBlock> blocks,
            Map<String, ContextoProgramacionRuta> contextosPorRuta
    ) {

        Objects.requireNonNull(
                plan,
                "plan is required"
        );

        Objects.requireNonNull(
                snapshot,
                "snapshot is required"
        );

        Objects.requireNonNull(
                requiredOrders,
                "requiredOrders are required"
        );

        Objects.requireNonNull(
                blocks,
                "blocks are required"
        );

        Objects.requireNonNull(
                contextosPorRuta,
                "route scheduling contexts are required"
        );

        Map<String, Order> ordersById =
                indexOrders(
                        requiredOrders
                );

        List<PlanViolation> violations =
                new ArrayList<>();

        Map<String, ScheduledDeliveryRoute> schedules =
                new LinkedHashMap<>();

        List<InventoryEvent> inventoryEvents =
                new ArrayList<>();

        Map<String, Integer> deliveredByOrderId =
                new HashMap<>();

        double totalCost =
                0;

        for (
                DeliveryRoute route :
                plan.routes()
        ) {

            VehicleOperationalState vehicleState =
                    snapshot
                            .vehiclesById()
                            .get(
                                    route
                                            .vehicle()
                                            .id()
                            );

            if (
                    vehicleState == null
            ) {

                violations.add(
                        violation(
                                PlanViolationType.UNKNOWN_VEHICLE,
                                route,
                                "Vehicle is absent from the snapshot"
                        )
                );

                continue;
            }

            validateRouteStart(
                    route,
                    vehicleState,
                    snapshot,
                    violations,
                    inventoryEvents
            );

            if (
                    !snapshot
                            .isVehiclePlannableAt(
                                    route
                                            .vehicle()
                                            .id(),
                                    route.departureAt()
                            )
            ) {

                violations.add(
                        violation(
                                PlanViolationType.VEHICLE_UNAVAILABLE,
                                route,
                                "Vehicle is not plannable at route departure"
                        )
                );
            }

            try {

                ContextoProgramacionRuta contexto =
                        contextosPorRuta
                                .getOrDefault(
                                        route.id(),
                                        ContextoProgramacionRuta.vacio()
                                );

                ScheduledDeliveryRoute schedule =
                        scheduler.schedule(
                                route,
                                snapshot,
                                blocks,
                                contexto
                        );

                schedules.put(
                        route.id(),
                        schedule
                );

                totalCost +=
                        schedule.totalCost();

                validateSchedule(
                        route,
                        schedule,
                        snapshot,
                        ordersById,
                        deliveredByOrderId,
                        inventoryEvents,
                        violations
                );

            } catch (
                    IllegalStateException exception
            ) {

                violations.add(
                        violation(
                                PlanViolationType.NO_ROAD_PATH,
                                route,
                                exception.getMessage()
                        )
                );
            }
        }

        validateTransferPairs(
                schedules,
                violations
        );

        validateDeliveredQuantities(
                ordersById,
                deliveredByOrderId,
                violations
        );

        InventorySnapshot remainingInventory =
                evaluateInventory(
                        snapshot,
                        inventoryEvents,
                        violations
                );

        return new PlanEvaluation(
                schedules,
                violations,
                remainingInventory,
                totalCost
        );
    }

    private Map<String, Order> indexOrders(
            Collection<Order> orders
    ) {

        Map<String, Order> indexed =
                new HashMap<>();

        for (
                Order order :
                orders
        ) {

            if (
                    indexed.put(
                            order.id(),
                            order
                    ) != null
            ) {

                throw new IllegalArgumentException(
                        "Required order ids must be unique"
                );
            }
        }

        return indexed;
    }

    private void validateRouteStart(
            DeliveryRoute route,
            VehicleOperationalState state,
            OperationalSnapshot snapshot,
            List<PlanViolation> violations,
            List<InventoryEvent> inventoryEvents
    ) {

        if (
                route
                        .departureAt()
                        .isBefore(
                                snapshot.planningTime()
                        )
        ) {

            violations.add(
                    violation(
                            PlanViolationType.INVALID_ROUTE_START,
                            route,
                            "Route departs before the planning snapshot"
                    )
            );
        }

        route
                .initialWarehouse()
                .ifPresentOrElse(

                        warehouse -> {

                            if (
                                    !warehouse.isCentral()
                                    ||
                                    !route
                                            .startLocation()
                                            .equals(
                                                    warehouse.location()
                                            )
                            ) {

                                violations.add(
                                        violation(
                                                PlanViolationType.INVALID_ROUTE_START,
                                                route,
                                                "Scenario-start route must start at the central warehouse"
                                        )
                                );
                            }

                            inventoryEvents.add(
                                    new InventoryEvent(
                                            route.departureAt(),
                                            route.id(),
                                            warehouse,
                                            route.initialLoad()
                                    )
                            );
                        },

                        () -> {

                            if (
                                    !route
                                            .startLocation()
                                            .equals(
                                                    state.location()
                                            )
                            ) {

                                violations.add(
                                        violation(
                                                PlanViolationType.INVALID_ROUTE_START,
                                                route,
                                                "Replanned route must start at the vehicle's current location"
                                        )
                                );
                            }

                            if (
                                    route.initialLoad()
                                            !=
                                            state.carriedPackages()
                            ) {

                                violations.add(
                                        violation(
                                                PlanViolationType.INVALID_INITIAL_LOAD,
                                                route,
                                                "Replanned route load must equal the vehicle's carried load"
                                        )
                                );
                            }
                        }
                );
    }

    private void validateSchedule(
            DeliveryRoute route,
            ScheduledDeliveryRoute schedule,
            OperationalSnapshot snapshot,
            Map<String, Order> requiredOrders,
            Map<String, Integer> deliveredByOrderId,
            List<InventoryEvent> inventoryEvents,
            List<PlanViolation> violations
    ) {

        int capacity =
                snapshot
                        .fleetProfile()
                        .parametersFor(
                                route
                                        .vehicle()
                                        .type()
                        )
                        .capacity();

        if (
                !route.endsAtWarehouse()
        ) {

            violations.add(
                    violation(
                            PlanViolationType.ROUTE_NOT_RETURNED_TO_WAREHOUSE,
                            route,
                            "A completed route must return to a warehouse"
                    )
            );
        }

        if (
                snapshot
                        .hasVehicleDisruptionDuring(
                                route
                                        .vehicle()
                                        .id(),
                                route.departureAt(),
                                schedule.completedAt()
                        )
        ) {

            violations.add(
                    violation(
                            PlanViolationType.MAINTENANCE_OR_BREAKDOWN,
                            route,
                            "A maintenance or breakdown window intersects the route"
                    )
            );
        }

        for (
                ScheduledRouteStop stop :
                schedule.scheduledStops()
        ) {

            if (
                    maxLegDistanceKm > 0
                    &&
                    stop
                            .approach()
                            .distanceKm()
                            >
                            maxLegDistanceKm
            ) {

                violations.add(
                        violation(
                                PlanViolationType.LEG_DISTANCE_EXCEEDED,
                                route,
                                "A single leg exceeds the configured "
                                        +
                                        maxLegDistanceKm
                                        +
                                        " km maximum: "
                                        +
                                        stop
                                                .approach()
                                                .distanceKm()
                                        +
                                        " km"
                        )
                );
            }

            if (
                    stop.loadBefore() > capacity
                    ||
                    stop.loadAfter() > capacity
            ) {

                violations.add(
                        violation(
                                PlanViolationType.VEHICLE_CAPACITY,
                                route,
                                "Vehicle load exceeds capacity at a route stop"
                        )
                );
            }

            if (
                    stop.loadBefore() < 0
                    ||
                    stop.loadAfter() < 0
            ) {

                violations.add(
                        violation(
                                PlanViolationType.NEGATIVE_LOAD,
                                route,
                                "A delivery exceeds the vehicle's carried load"
                        )
                );
            }

            if (
                    stop.stop()
                            instanceof
                            DeliveryStop delivery
            ) {

                Order expected =
                        requiredOrders.get(
                                delivery
                                        .order()
                                        .id()
                        );

                if (
                        expected == null
                        ||
                        !expected.equals(
                                delivery.order()
                        )
                ) {

                    violations.add(
                            violation(
                                    PlanViolationType.UNKNOWN_ORDER,
                                    route,
                                    "Delivery does not refer to the required order set"
                            )
                    );

                    continue;
                }

                deliveredByOrderId.merge(
                        expected.id(),
                        delivery.deliveredPackages(),
                        Integer::sum
                );

                if (
                        stop
                                .arrivedAt()
                                .isAfter(
                                        expected.deadline()
                                )
                ) {

                    violations.add(
                            violation(
                                    PlanViolationType.SLA_MISSED,
                                    route,
                                    "Delivery arrival is after the strict deadline for "
                                            +
                                            expected.id()
                            )
                    );
                }

            } else if (
                    stop.stop()
                            instanceof
                            WarehouseVisit visit
            ) {

                inventoryEvents.add(
                        new InventoryEvent(
                                stop.arrivedAt(),
                                route.id(),
                                visit.warehouse(),
                                visit.pickupPackages()
                        )
                );

            } else if (
                    stop.stop() instanceof TransferStop transfer
            ) {

                Order expected = requiredOrders.get(transfer.order().id());

                if (expected == null || !expected.equals(transfer.order())) {
                    violations.add(
                            violation(
                                    PlanViolationType.UNKNOWN_ORDER,
                                    route,
                                    "Transfer does not refer to the required order set"
                            )
                    );
                }
            }
        }
    }

    /**
     * Valida que cada encuentro tenga exactamente un donante y un receptor,
     * con el mismo pedido, cantidad, nodo e instante. La ruta puede llegar
     * antes y esperar, pero ninguna unidad puede llegar después del encuentro.
     */
    private void validateTransferPairs(
            Map<String, ScheduledDeliveryRoute> schedules,
            List<PlanViolation> violations
    ) {

        Map<String, List<TransferOccurrence>> byId = new LinkedHashMap<>();

        for (ScheduledDeliveryRoute schedule : schedules.values()) {
            for (ScheduledRouteStop scheduled : schedule.scheduledStops()) {
                if (scheduled.stop() instanceof TransferStop transfer) {
                    byId.computeIfAbsent(transfer.transferId(), ignored -> new ArrayList<>())
                            .add(new TransferOccurrence(
                                    schedule.route().id(),
                                    schedule.route().vehicle().id(),
                                    transfer,
                                    scheduled
                            ));
                }
            }
        }

        for (Map.Entry<String, List<TransferOccurrence>> entry : byId.entrySet()) {
            List<TransferOccurrence> occurrences = entry.getValue();
            if (occurrences.size() != 2) {
                for (TransferOccurrence occurrence : occurrences) {
                    violations.add(new PlanViolation(
                            PlanViolationType.INVALID_TRANSFER_PAIR,
                            occurrence.routeId(),
                            "Transfer " + entry.getKey() + " requires exactly two participants"
                    ));
                }
                continue;
            }

            TransferOccurrence first = occurrences.get(0);
            TransferOccurrence second = occurrences.get(1);
            TransferOccurrence donor = first.stop().direction() == TransferDirection.DONATE ? first : second;
            TransferOccurrence receiver = donor == first ? second : first;

            boolean reciprocal = donor.stop().direction() == TransferDirection.DONATE
                    && receiver.stop().direction() == TransferDirection.RECEIVE
                    && donor.stop().order().equals(receiver.stop().order())
                    && donor.stop().packages() == receiver.stop().packages()
                    && donor.stop().location().equals(receiver.stop().location())
                    && donor.stop().meetingAt().equals(receiver.stop().meetingAt())
                    && donor.stop().counterpartyVehicleId().equals(receiver.vehicleId())
                    && receiver.stop().counterpartyVehicleId().equals(donor.vehicleId());

            if (!reciprocal) {
                violations.add(new PlanViolation(
                        PlanViolationType.INVALID_TRANSFER_PAIR,
                        donor.routeId(),
                        "Transfer " + entry.getKey() + " has inconsistent participants or cargo"
                ));
                violations.add(new PlanViolation(
                        PlanViolationType.INVALID_TRANSFER_PAIR,
                        receiver.routeId(),
                        "Transfer " + entry.getKey() + " has inconsistent participants or cargo"
                ));
                continue;
            }

            Instant meeting = donor.stop().meetingAt();
            boolean synchronizedPair = !donor.scheduled().arrivedAt().isAfter(meeting)
                    && !receiver.scheduled().arrivedAt().isAfter(meeting)
                    && donor.scheduled().serviceStartedAt().equals(meeting)
                    && receiver.scheduled().serviceStartedAt().equals(meeting);

            if (!synchronizedPair) {
                violations.add(new PlanViolation(
                        PlanViolationType.TRANSFER_NOT_SYNCHRONIZED,
                        donor.routeId(),
                        "Transfer " + entry.getKey() + " cannot start simultaneously"
                ));
                violations.add(new PlanViolation(
                        PlanViolationType.TRANSFER_NOT_SYNCHRONIZED,
                        receiver.routeId(),
                        "Transfer " + entry.getKey() + " cannot start simultaneously"
                ));
            }
        }
    }

    private record TransferOccurrence(
            String routeId,
            String vehicleId,
            TransferStop stop,
            ScheduledRouteStop scheduled
    ) {
    }

    private void validateDeliveredQuantities(
            Map<String, Order> requiredOrders,
            Map<String, Integer> delivered,
            List<PlanViolation> violations
    ) {

        for (
                Order order :
                requiredOrders.values()
        ) {

            int deliveredQuantity =
                    delivered.getOrDefault(
                            order.id(),
                            0
                    );

            if (
                    deliveredQuantity
                            !=
                            order.packages()
            ) {

                violations.add(
                        new PlanViolation(
                                PlanViolationType.PARTIAL_DELIVERY_MISMATCH,
                                null,
                                "Order "
                                        +
                                        order.id()
                                        +
                                        " requires "
                                        +
                                        order.packages()
                                        +
                                        " but has "
                                        +
                                        deliveredQuantity
                        )
                );
            }
        }
    }

    private InventorySnapshot evaluateInventory(
            OperationalSnapshot snapshot,
            List<InventoryEvent> events,
            List<PlanViolation> violations
    ) {

        events.sort(
                Comparator
                        .comparing(
                                InventoryEvent::at
                        )
                        .thenComparing(
                                InventoryEvent::routeId
                        )
        );

        InventoryTimeline timeline =
                new InventoryTimeline(
                        snapshot.inventory(),
                        snapshot.planningTime(),
                        snapshot
                                .shiftSchedule()
                                .zoneId()
                );

        for (
                InventoryEvent event :
                events
        ) {

            if (
                    event
                            .at()
                            .isBefore(
                                    snapshot.planningTime()
                            )
            ) {
                continue;
            }

            if (
                    !timeline.canVisit(
                            event
                                    .warehouse()
                                    .id(),
                            event.pickupPackages(),
                            event.at()
                    )
            ) {

                PlanViolationType type =
                        event.pickupPackages() == 0

                                ? PlanViolationType.RETURN_TO_EMPTY_WAREHOUSE

                                : PlanViolationType.INSUFFICIENT_INVENTORY;

                violations.add(
                        new PlanViolation(
                                type,
                                event.routeId(),
                                "Warehouse "
                                        +
                                        event
                                                .warehouse()
                                                .id()
                                        +
                                        " lacks stock for the planned visit"
                        )
                );

                continue;
            }

            timeline.withdraw(
                    event
                            .warehouse()
                            .id(),
                    event.pickupPackages(),
                    event.at()
            );
        }

        return timeline.inventory();
    }

    private PlanViolation violation(
            PlanViolationType type,
            DeliveryRoute route,
            String detail
    ) {

        return new PlanViolation(
                type,
                route.id(),
                detail
        );
    }

    private record InventoryEvent(
            Instant at,
            String routeId,
            Warehouse warehouse,
            int pickupPackages
    ) {
    }
}