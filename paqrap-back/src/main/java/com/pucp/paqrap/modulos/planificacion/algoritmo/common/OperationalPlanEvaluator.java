package com.pucp.paqrap.modulos.planificacion.algoritmo.common;
import com.pucp.paqrap.modulos.almacenes.entity.*;
import com.pucp.paqrap.modulos.flota.entity.*;
import com.pucp.paqrap.modulos.flota.service.*;
import com.pucp.paqrap.modulos.incidencias.entity.*;
import com.pucp.paqrap.modulos.incidencias.service.*;
import com.pucp.paqrap.modulos.pedidos.entity.*;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.*;
import com.pucp.paqrap.modulos.planificacion.entity.*;
import com.pucp.paqrap.modulos.redvial.entity.*;
import com.pucp.paqrap.modulos.redvial.service.*;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validates all hard business rules and returns the operational cost only for feasible plans. */
public final class OperationalPlanEvaluator {
    /** Hoja "Flota": "Maxima distancia ida: 80 km". Interpretacion adoptada:
     *  ningun tramo individual entre dos paradas consecutivas puede superar
     *  esto -- no la distancia acumulada desde la ultima salida de almacen.
     *  Es una lectura razonable pero no la unica posible; confirmar con el
     *  docente si se requiere. */
    private static final double MAX_LEG_DISTANCE_KM = 80.0;

    private final RouteScheduler scheduler;

    public OperationalPlanEvaluator(RouteScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler is required");
    }

    public PlanEvaluation evaluate(OperationalPlan plan, OperationalSnapshot snapshot,
                                   Collection<Order> requiredOrders, List<RoadBlock> blocks) {
        Objects.requireNonNull(plan, "plan is required");
        Objects.requireNonNull(snapshot, "snapshot is required");
        Objects.requireNonNull(requiredOrders, "requiredOrders are required");
        Objects.requireNonNull(blocks, "blocks are required");
        Map<String, Order> ordersById = indexOrders(requiredOrders);
        List<PlanViolation> violations = new ArrayList<>();
        Map<String, ScheduledDeliveryRoute> schedules = new LinkedHashMap<>();
        List<InventoryEvent> inventoryEvents = new ArrayList<>();
        Map<String, Integer> deliveredByOrderId = new HashMap<>();
        double totalCost = 0;

        for (DeliveryRoute route : plan.routes()) {
            VehicleOperationalState vehicleState = snapshot.vehiclesById().get(route.vehicle().id());
            if (vehicleState == null) {
                violations.add(violation(PlanViolationType.UNKNOWN_VEHICLE, route, "Vehicle is absent from the snapshot"));
                continue;
            }
            validateRouteStart(route, vehicleState, snapshot, violations, inventoryEvents);
            if (!snapshot.isVehiclePlannableAt(route.vehicle().id(), route.departureAt())) {
                violations.add(violation(PlanViolationType.VEHICLE_UNAVAILABLE, route,
                        "Vehicle is not plannable at route departure"));
            }
            try {
                ScheduledDeliveryRoute schedule = scheduler.schedule(route, snapshot, blocks);
                schedules.put(route.id(), schedule);
                totalCost += schedule.totalCost();
                validateSchedule(route, schedule, snapshot, ordersById, deliveredByOrderId, inventoryEvents, violations);
            } catch (IllegalStateException exception) {
                violations.add(violation(PlanViolationType.NO_ROAD_PATH, route, exception.getMessage()));
            }
        }

        validateDeliveredQuantities(ordersById, deliveredByOrderId, violations);
        InventorySnapshot remainingInventory = evaluateInventory(snapshot, inventoryEvents, violations);
        return new PlanEvaluation(schedules, violations, remainingInventory, totalCost);
    }

    private Map<String, Order> indexOrders(Collection<Order> orders) {
        Map<String, Order> indexed = new HashMap<>();
        for (Order order : orders) {
            if (indexed.put(order.id(), order) != null) {
                throw new IllegalArgumentException("Required order ids must be unique");
            }
        }
        return indexed;
    }

    private void validateRouteStart(DeliveryRoute route, VehicleOperationalState state, OperationalSnapshot snapshot,
                                    List<PlanViolation> violations, List<InventoryEvent> inventoryEvents) {
        if (route.departureAt().isBefore(snapshot.planningTime())) {
            violations.add(violation(PlanViolationType.INVALID_ROUTE_START, route,
                    "Route departs before the planning snapshot"));
        }
        route.initialWarehouse().ifPresentOrElse(warehouse -> {
            if (!warehouse.isCentral() || !route.startLocation().equals(warehouse.location())) {
                violations.add(violation(PlanViolationType.INVALID_ROUTE_START, route,
                        "Scenario-start route must start at the central warehouse"));
            }
            inventoryEvents.add(new InventoryEvent(route.departureAt(), route.id(), warehouse, route.initialLoad()));
        }, () -> {
            if (!route.startLocation().equals(state.location())) {
                violations.add(violation(PlanViolationType.INVALID_ROUTE_START, route,
                        "Replanned route must start at the vehicle's current location"));
            }
            if (route.initialLoad() != state.carriedPackages()) {
                violations.add(violation(PlanViolationType.INVALID_INITIAL_LOAD, route,
                        "Replanned route load must equal the vehicle's carried load"));
            }
        });
    }

    private void validateSchedule(DeliveryRoute route, ScheduledDeliveryRoute schedule, OperationalSnapshot snapshot,
                                  Map<String, Order> requiredOrders, Map<String, Integer> deliveredByOrderId,
                                  List<InventoryEvent> inventoryEvents, List<PlanViolation> violations) {
        int capacity = snapshot.fleetProfile().parametersFor(route.vehicle().type()).capacity();
        if (!route.endsAtWarehouse()) {
            violations.add(violation(PlanViolationType.ROUTE_NOT_RETURNED_TO_WAREHOUSE, route,
                    "A completed route must return to a warehouse"));
        }
        if (snapshot.hasVehicleDisruptionDuring(route.vehicle().id(), route.departureAt(), schedule.completedAt())) {
            violations.add(violation(PlanViolationType.MAINTENANCE_OR_BREAKDOWN, route,
                    "A maintenance or breakdown window intersects the route"));
        }
        for (ScheduledRouteStop stop : schedule.scheduledStops()) {
            if (stop.approach().distanceKm() > MAX_LEG_DISTANCE_KM) {
                violations.add(violation(PlanViolationType.LEG_DISTANCE_EXCEEDED, route,
                        "A single leg exceeds the fleet sheet's 80 km maximum: "
                                + stop.approach().distanceKm() + " km"));
            }
            if (stop.loadBefore() > capacity || stop.loadAfter() > capacity) {
                violations.add(violation(PlanViolationType.VEHICLE_CAPACITY, route,
                        "Vehicle load exceeds capacity at a route stop"));
            }
            if (stop.loadBefore() < 0 || stop.loadAfter() < 0) {
                violations.add(violation(PlanViolationType.NEGATIVE_LOAD, route,
                        "A delivery exceeds the vehicle's carried load"));
            }
            if (stop.stop() instanceof DeliveryStop delivery) {
                Order expected = requiredOrders.get(delivery.order().id());
                if (expected == null || !expected.equals(delivery.order())) {
                    violations.add(violation(PlanViolationType.UNKNOWN_ORDER, route,
                            "Delivery does not refer to the required order set"));
                    continue;
                }
                deliveredByOrderId.merge(expected.id(), delivery.deliveredPackages(), Integer::sum);
                if (stop.arrivedAt().isAfter(expected.deadline())) {
                    violations.add(violation(PlanViolationType.SLA_MISSED, route,
                            "Delivery arrival is after the strict deadline for " + expected.id()));
                }
            } else if (stop.stop() instanceof WarehouseVisit visit) {
                inventoryEvents.add(new InventoryEvent(stop.arrivedAt(), route.id(), visit.warehouse(), visit.pickupPackages()));
            }
        }
    }

    private void validateDeliveredQuantities(Map<String, Order> requiredOrders, Map<String, Integer> delivered,
                                             List<PlanViolation> violations) {
        for (Order order : requiredOrders.values()) {
            int deliveredQuantity = delivered.getOrDefault(order.id(), 0);
            if (deliveredQuantity != order.packages()) {
                violations.add(new PlanViolation(PlanViolationType.PARTIAL_DELIVERY_MISMATCH, null,
                        "Order " + order.id() + " requires " + order.packages() + " but has " + deliveredQuantity));
            }
        }
    }

    private InventorySnapshot evaluateInventory(OperationalSnapshot snapshot, List<InventoryEvent> events,
                                                List<PlanViolation> violations) {
        events.sort(Comparator.comparing(InventoryEvent::at).thenComparing(InventoryEvent::routeId));
        InventoryTimeline timeline = new InventoryTimeline(snapshot.inventory(), snapshot.planningTime(),
                snapshot.shiftSchedule().zoneId());
        for (InventoryEvent event : events) {
            if (event.at().isBefore(snapshot.planningTime())) {
                continue;
            }
            if (!timeline.canVisit(event.warehouse().id(), event.pickupPackages(), event.at())) {
                PlanViolationType type = event.pickupPackages() == 0
                        ? PlanViolationType.RETURN_TO_EMPTY_WAREHOUSE : PlanViolationType.INSUFFICIENT_INVENTORY;
                violations.add(new PlanViolation(type, event.routeId(),
                        "Warehouse " + event.warehouse().id() + " lacks stock for the planned visit"));
                continue;
            }
            timeline.withdraw(event.warehouse().id(), event.pickupPackages(), event.at());
        }
        return timeline.inventory();
    }

    private PlanViolation violation(PlanViolationType type, DeliveryRoute route, String detail) {
        return new PlanViolation(type, route.id(), detail);
    }

    private record InventoryEvent(Instant at, String routeId, Warehouse warehouse, int pickupPackages) {
    }
}
