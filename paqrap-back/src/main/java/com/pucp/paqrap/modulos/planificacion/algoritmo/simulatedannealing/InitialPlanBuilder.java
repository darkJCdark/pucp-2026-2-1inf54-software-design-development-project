package com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing;

import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolationType;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a deterministic, evaluator-validated seed plan for the annealing search.
 *
 * <p>Semana 13:
 * <ul>
 *   <li><b>Despacho diferido.</b> Además de los vehículos libres en el central, usa los que todavía están en ruta
 *       pero volverán vacíos al central: su viaje sale cuando regresan ({@code availableAt}). Sin esto, cuando toda la
 *       flota está en la calle los pedidos urgentes esperan a un ciclo en el que haya un vehículo libre y vencen.</li>
 *   <li><b>Consolidación.</b> Un pedido también puede sumarse al último viaje de un vehículo (si cabe) en vez de
 *       abrir otro viaje; así un vehículo lleva varios pedidos cercanos y la flota se satura más tarde.</li>
 *   <li><b>Almacenes intermedios.</b> Un viaje encadenado recarga en el almacén (central o intermedio con stock) que
 *       menos desvía entre la última entrega y el cliente.</li>
 *   <li><b>Evaluación por ruta.</b> Cada candidato solo cambia una o dos rutas; el stock de los intermedios se
 *       reserva entre rutas ({@code reserved}), así que basta evaluar las rutas que cambian: el costo es el mismo que el del plan completo y se
 *       evita reprogramar los otros 36 vehículos en cada intento.</li>
 * </ul>
 */
final class InitialPlanBuilder {
    /** Experimento: ante igual costo, preferir el candidato que termina antes (si no, gana el primero). */
    static boolean DESEMPATE_POR_TERMINO = Boolean.parseBoolean(System.getProperty("paqrap.sa.desempate", "true"));
    /** Experimento: permitir sumar un pedido al último viaje de un vehículo. */
    static boolean CONSOLIDAR = Boolean.parseBoolean(System.getProperty("paqrap.sa.consolidar", "true"));

    private final OperationalPlanEvaluator evaluator;

    InitialPlanBuilder(OperationalPlanEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    SeedPlan build(OperationalSnapshot snapshot, Collection<Order> orders, List<RoadBlock> blocks) {
        Warehouse central = snapshot.inventory().warehouses().stream()
                .filter(Warehouse::isCentral)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The snapshot requires a central warehouse"));
        // Unidades vacías en un almacén: el central o un intermedio donde terminó su último viaje.
        List<VehicleOperationalState> vehicles = snapshot.vehiclesById().values().stream()
                .filter(state -> state.carriedPackages() == 0 && snapshot.inventory().warehouses().stream()
                        .anyMatch(warehouse -> warehouse.location().equals(state.location())))
                .filter(state -> snapshot.isVehiclePlannableAt(state.vehicle().id(), departure(state, snapshot)))
                .sorted(Comparator.comparing((VehicleOperationalState state) -> departure(state, snapshot))
                        .thenComparing(state -> state.vehicle().id()))
                .toList();
        List<Order> ordered = new ArrayList<>(orders);
        ordered.sort(Comparator.comparing(Order::deadline).thenComparing(Order::id));

        OperationalPlan plan = OperationalPlan.empty();
        Map<String, Double> costByVehicle = new HashMap<>();
        Map<String, Integer> reserved = new HashMap<>(); // retiros ya asignados a cada intermedio en este plan
        List<Order> attended = new ArrayList<>();
        List<Order> unattended = new ArrayList<>();
        for (Order order : ordered) {
            Candidate best = null;
            for (int startingVehicle = 0; startingVehicle < vehicles.size(); startingVehicle++) {
                best = better(best, evaluate(appendOrderInDeliveries(plan, vehicles, startingVehicle, central, order,
                        snapshot, reserved), costByVehicle, snapshot, blocks));
            }
            for (VehicleOperationalState vehicle : CONSOLIDAR ? vehicles : List.<VehicleOperationalState>of()) {
                DeliveryRoute route = plan.routeForVehicle(vehicle.vehicle().id()).orElse(null);
                if (route == null) {
                    continue;
                }
                for (DeliveryRoute consolidated : insertIntoLastTrip(route, order, snapshot, reserved)) {
                    best = better(best, evaluate(List.of(consolidated), costByVehicle, snapshot, blocks));
                }
            }
            if (best == null) {
                unattended.add(order);
                continue;
            }
            for (int i = 0; i < best.routes().size(); i++) {
                plan = plan.withRoute(best.routes().get(i));
                costByVehicle.put(best.routes().get(i).vehicle().id(), best.routeCosts().get(i));
            }
            attended.add(order);
            reserved = intermediatePickups(plan);
        }
        return new SeedPlan(plan, attended, unattended);
    }

    private static Instant departure(VehicleOperationalState state, OperationalSnapshot snapshot) {
        return state.availableAt().isAfter(snapshot.planningTime()) ? state.availableAt() : snapshot.planningTime();
    }

    private static Candidate better(Candidate current, Candidate candidate) {
        if (candidate == null) return current;
        if (current == null) return candidate;
        if (candidate.deltaCost() < current.deltaCost() - 1e-9) return candidate;
        if (DESEMPATE_POR_TERMINO && candidate.deltaCost() <= current.deltaCost() + 1e-9
                && candidate.completedAt().isBefore(current.completedAt())) return candidate;
        return current;
    }

    /** Evaluates only the changed routes; null when any of them is infeasible. */
    private Candidate evaluate(List<DeliveryRoute> changed, Map<String, Double> costByVehicle,
                               OperationalSnapshot snapshot, List<RoadBlock> blocks) {
        if (changed == null || changed.isEmpty()) return null;
        double delta = 0;
        Instant completedAt = Instant.MIN;
        List<Double> costs = new ArrayList<>();
        for (DeliveryRoute route : changed) {
            Map<String, Order> routeOrders = new LinkedHashMap<>();
            route.stops().forEach(stop -> {
                if (stop instanceof DeliveryStop delivery) routeOrders.putIfAbsent(delivery.order().id(), delivery.order());
            });
            PlanEvaluation evaluation = evaluator.evaluate(OperationalPlan.empty().withRoute(route), snapshot,
                    routeOrders.values(), blocks);
            boolean feasible = evaluation.violations().stream()
                    .allMatch(violation -> violation.type() == PlanViolationType.PARTIAL_DELIVERY_MISMATCH);
            if (!feasible) return null;
            var schedule = evaluation.schedulesByRouteId().get(route.id());
            costs.add(schedule.totalCost());
            delta += schedule.totalCost() - costByVehicle.getOrDefault(route.vehicle().id(), 0.0);
            if (schedule.completedAt().isAfter(completedAt)) completedAt = schedule.completedAt();
        }
        return new Candidate(changed, costs, delta, completedAt);
    }

    private List<DeliveryRoute> appendOrderInDeliveries(OperationalPlan plan, List<VehicleOperationalState> vehicles,
                                                        int startingVehicle, Warehouse central, Order order,
                                                        OperationalSnapshot snapshot, Map<String, Integer> reserved) {
        Map<String, Integer> pending = new HashMap<>(reserved);
        Map<String, DeliveryRoute> changed = new LinkedHashMap<>();
        int remainingPackages = order.packages();
        int vehicleIndex = startingVehicle;
        // Como antes, se reparte entre vehículos consecutivos; si da la vuelta, un vehículo hace otro viaje.
        while (remainingPackages > 0 && vehicleIndex < startingVehicle + 4 * vehicles.size()) {
            VehicleOperationalState vehicleState = vehicles.get(vehicleIndex % vehicles.size());
            int capacity = snapshot.fleetProfile().parametersFor(vehicleState.vehicle().type()).capacity();
            int deliveredPackages = Math.min(remainingPackages, capacity);
            DeliveryRoute current = changed.containsKey(vehicleState.vehicle().id())
                    ? changed.get(vehicleState.vehicle().id())
                    : plan.routeForVehicle(vehicleState.vehicle().id()).orElse(null);
            DeliveryRoute updated;
            if (current != null) {
                updated = appendAfterReturn(current, central, order, deliveredPackages, snapshot, pending);
            } else if (vehicleState.location().equals(central.location())) {
                updated = DeliveryRoute.startScenarioAtCentral("SA-" + vehicleState.vehicle().id(),
                                vehicleState.vehicle(), central, deliveredPackages, departure(vehicleState, snapshot))
                        .withAppendedStop(new DeliveryStop(order, deliveredPackages))
                        .returningTo(central);
            } else {
                // Está en un intermedio: carga allí (o en el almacén que menos desvía) y luego vuelve al central.
                updated = appendAfterReturn(DeliveryRoute.replanFromCurrentLocation("SA-" + vehicleState.vehicle().id(),
                                vehicleState.vehicle(), vehicleState.location(), 0, departure(vehicleState, snapshot)),
                        central, order, deliveredPackages, snapshot, pending);
            }
            changed.put(vehicleState.vehicle().id(), updated);
            remainingPackages -= deliveredPackages;
            vehicleIndex++;
        }
        return remainingPackages > 0 ? null : List.copyOf(changed.values());
    }

    /**
     * Otro viaje al final de la ruta. Recarga en el almacén que deja el desvío más corto entre la última entrega y el
     * cliente: el central o un intermedio con stock (Semana 13). Un vehículo que terminó cerca del almacén ESTE recarga
     * allí en vez de volver 30 km al central; la ruta siempre termina en el central.
     */
    private DeliveryRoute appendAfterReturn(DeliveryRoute route, Warehouse central, Order order, int deliveredPackages,
                                            OperationalSnapshot snapshot, Map<String, Integer> pending) {
        List<RouteStop> stops = new ArrayList<>(route.stops());
        if (!stops.isEmpty() && stops.getLast() instanceof WarehouseVisit) stops.removeLast();
        Location from = stops.isEmpty() ? route.startLocation() : stops.getLast().location();
        Warehouse reload = central;
        int bestDetour = detour(from, central.location(), order.destination());
        for (Warehouse warehouse : snapshot.inventory().warehouses()) {
            if (warehouse.isCentral()) continue;
            int stock = snapshot.inventory().availableStock(warehouse.id()) - pending.getOrDefault(warehouse.id(), 0);
            int candidate = detour(from, warehouse.location(), order.destination());
            if (stock >= deliveredPackages && candidate < bestDetour) {
                reload = warehouse;
                bestDetour = candidate;
            }
        }
        if (!reload.isCentral()) pending.merge(reload.id(), deliveredPackages, Integer::sum);
        return route.withReplacedStops(stops)
                .withAppendedStop(new WarehouseVisit(reload, deliveredPackages))
                .withAppendedStop(new DeliveryStop(order, deliveredPackages))
                .returningTo(central);
    }

    private static int detour(Location from, Location warehouse, Location customer) {
        return manhattan(from, warehouse) + manhattan(warehouse, customer);
    }

    private static int manhattan(Location a, Location b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
    }

    /** Paquetes que el plan retira de cada almacén intermedio (el central no tiene límite). */
    private static Map<String, Integer> intermediatePickups(OperationalPlan plan) {
        Map<String, Integer> pickups = new HashMap<>();
        for (DeliveryRoute route : plan.routes()) {
            for (RouteStop stop : route.stops()) {
                if (stop instanceof WarehouseVisit visit && !visit.warehouse().isCentral() && visit.pickupPackages() > 0) {
                    pickups.merge(visit.warehouse().id(), visit.pickupPackages(), Integer::sum);
                }
            }
        }
        return pickups;
    }

    /**
     * Variants of {@code route} that carry the whole order in its last trip (from its last central pickup to the final
     * return), one per insertion position. Empty when the order does not fit in that trip.
     */
    private List<DeliveryRoute> insertIntoLastTrip(DeliveryRoute route, Order order, OperationalSnapshot snapshot,
                                                   Map<String, Integer> reserved) {
        List<RouteStop> stops = route.stops();
        if (stops.isEmpty() || !(stops.getLast() instanceof WarehouseVisit)) return List.of();
        int tripStart = -1; // index of the pickup that starts the last trip; -1 = the route's initial load
        for (int i = stops.size() - 2; i >= 0; i--) {
            if (stops.get(i) instanceof WarehouseVisit) {
                tripStart = i;
                break;
            }
        }
        int tripLoad = tripStart < 0 ? route.initialLoad() : ((WarehouseVisit) stops.get(tripStart)).pickupPackages();
        int capacity = snapshot.fleetProfile().parametersFor(route.vehicle().type()).capacity();
        if (tripLoad + order.packages() > capacity) return List.of();
        if (tripStart >= 0 && stops.get(tripStart) instanceof WarehouseVisit pickupAt && !pickupAt.warehouse().isCentral()
                && snapshot.inventory().availableStock(pickupAt.warehouse().id())
                - reserved.getOrDefault(pickupAt.warehouse().id(), 0) < order.packages()) return List.of();
        List<DeliveryRoute> variants = new ArrayList<>();
        for (int position = tripStart + 1; position <= stops.size() - 1; position++) {
            List<RouteStop> updated = new ArrayList<>(stops);
            updated.add(position, new DeliveryStop(order, order.packages()));
            DeliveryRoute variant;
            if (tripStart < 0) {
                if (route.initialWarehouse().isEmpty()) return List.of();
                variant = DeliveryRoute.startScenarioAtCentral(route.id(), route.vehicle(), route.initialWarehouse().get(),
                        tripLoad + order.packages(), route.departureAt()).withReplacedStops(updated);
            } else {
                WarehouseVisit pickup = (WarehouseVisit) stops.get(tripStart);
                updated.set(tripStart, new WarehouseVisit(pickup.warehouse(), tripLoad + order.packages()));
                variant = route.withReplacedStops(updated);
            }
            variants.add(variant);
        }
        return variants;
    }

    record SeedPlan(OperationalPlan plan, List<Order> attended, List<Order> unattended) {
        SeedPlan {
            attended = List.copyOf(attended);
            unattended = List.copyOf(unattended);
        }
    }

    private record Candidate(List<DeliveryRoute> routes, List<Double> routeCosts, double deltaCost,
                             Instant completedAt) {
    }
}
