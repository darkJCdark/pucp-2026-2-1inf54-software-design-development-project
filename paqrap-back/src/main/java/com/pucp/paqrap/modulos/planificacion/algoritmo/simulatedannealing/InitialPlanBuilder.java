package com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing;

import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Builds a deterministic, evaluator-validated seed without depending on GRASP. */
final class InitialPlanBuilder {
    private final OperationalPlanEvaluator evaluator;

    InitialPlanBuilder(OperationalPlanEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    SeedPlan build(OperationalSnapshot snapshot, Collection<Order> orders, List<RoadBlock> blocks) {
        Warehouse central = snapshot.inventory().warehouses().stream()
                .filter(Warehouse::isCentral)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The snapshot requires a central warehouse"));
        List<VehicleOperationalState> vehicles = snapshot.vehiclesById().values().stream()
                .filter(state -> snapshot.isVehiclePlannableAt(state.vehicle().id(), snapshot.planningTime()))
                .filter(state -> state.location().equals(central.location()) && state.carriedPackages() == 0)
                .sorted(Comparator.comparing(state -> state.vehicle().id()))
                .toList();
        List<Order> ordered = new ArrayList<>(orders);
        ordered.sort(Comparator.comparing(Order::deadline).thenComparing(Order::id));

        OperationalPlan plan = OperationalPlan.empty();
        List<Order> attended = new ArrayList<>();
        List<Order> unattended = new ArrayList<>();
        for (Order order : ordered) {
            Candidate best = null;
            for (int startingVehicle = 0; startingVehicle < vehicles.size(); startingVehicle++) {
                OperationalPlan candidatePlan = appendOrderInDeliveries(
                        plan, vehicles, startingVehicle, central, order, snapshot);
                List<Order> candidateOrders = new ArrayList<>(attended);
                candidateOrders.add(order);
                var evaluation = evaluator.evaluate(candidatePlan, snapshot, candidateOrders, blocks);
                if (evaluation.isFeasible() && (best == null || evaluation.totalCost() < best.cost())) {
                    best = new Candidate(candidatePlan, evaluation.totalCost());
                }
            }
            if (best == null) {
                unattended.add(order);
            } else {
                plan = best.plan();
                attended.add(order);
            }
        }
        return new SeedPlan(plan, attended, unattended);
    }

    private OperationalPlan appendOrderInDeliveries(OperationalPlan plan, List<VehicleOperationalState> vehicles,
                                                     int startingVehicle, Warehouse central, Order order,
                                                     OperationalSnapshot snapshot) {
        OperationalPlan candidatePlan = plan;
        int remainingPackages = order.packages();
        int vehicleIndex = startingVehicle;
        while (remainingPackages > 0) {
            VehicleOperationalState vehicleState = vehicles.get(vehicleIndex % vehicles.size());
            int capacity = snapshot.fleetProfile().parametersFor(vehicleState.vehicle().type()).capacity();
            int deliveredPackages = Math.min(remainingPackages, capacity);
            DeliveryRoute route = appendDelivery(candidatePlan, vehicleState, central, order,
                    deliveredPackages, snapshot);
            candidatePlan = candidatePlan.withRoute(route);
            remainingPackages -= deliveredPackages;
            vehicleIndex++;
        }
        return candidatePlan;
    }

    private DeliveryRoute appendDelivery(OperationalPlan plan, VehicleOperationalState state,
                                         Warehouse central, Order order, int deliveredPackages,
                                         OperationalSnapshot snapshot) {
        return plan.routeForVehicle(state.vehicle().id())
                .map(route -> appendAfterReturn(route, central, order, deliveredPackages))
                .orElseGet(() -> DeliveryRoute.startScenarioAtCentral(
                        "SA-" + state.vehicle().id(), state.vehicle(), central, deliveredPackages, snapshot.planningTime())
                        .withAppendedStop(new DeliveryStop(order, deliveredPackages))
                        .returningTo(central));
    }

    private DeliveryRoute appendAfterReturn(DeliveryRoute route, Warehouse central, Order order, int deliveredPackages) {
        List<RouteStop> stops = new ArrayList<>(route.stops());
        if (!stops.isEmpty() && stops.getLast() instanceof WarehouseVisit) stops.removeLast();
        return route.withReplacedStops(stops)
                .withAppendedStop(new WarehouseVisit(central, deliveredPackages))
                .withAppendedStop(new DeliveryStop(order, deliveredPackages))
                .returningTo(central);
    }

    record SeedPlan(OperationalPlan plan, List<Order> attended, List<Order> unattended) {
        SeedPlan {
            attended = List.copyOf(attended);
            unattended = List.copyOf(unattended);
        }
    }

    private record Candidate(OperationalPlan plan, double cost) {
    }
}
