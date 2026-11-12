package com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing;

import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Operational SA neighborhoods. This class proposes only; OperationalPlanEvaluator
 * remains the sole authority for capacity, load, inventory, deadlines and incidents.
 */
public final class OperationalRouteNeighborGenerator implements OperationalNeighborGenerator {
    @Override
    public Optional<OperationalPlan> generate(OperationalPlan current, OperationalSnapshot snapshot,
                                              RandomGenerator random) {
        List<DeliveryRoute> routes = current.routes().stream().toList();
        if (routes.isEmpty()) return Optional.empty();
        Optional<OperationalPlan> neighbor = switch (random.nextInt(7)) {
            case 0 -> relocation(current, routes, random);
            case 1 -> swap(current, routes, random);
            case 2 -> twoOpt(current, routes, random);
            case 3 -> splitDelivery(current, routes, random);
            case 4 -> openRoute(current, snapshot, routes, random);
            case 5 -> closeEmptyRoute(current, routes, random);
            default -> changeWarehouse(current, snapshot, routes, random);
        };
        return neighbor.map(OperationalRouteNeighborGenerator::mergeConsecutiveParts);
    }

    /**
     * Dos paradas seguidas del mismo pedido en el mismo vehículo son una sola entrega: partirla solo suma otra hora de
     * acondicionamiento y deja la segunda parte al límite del plazo (Semana 13: con un bloqueo esa parte llegaba tarde).
     */
    static OperationalPlan mergeConsecutiveParts(OperationalPlan plan) {
        OperationalPlan result = plan;
        for (DeliveryRoute route : plan.routes()) {
            List<RouteStop> merged = new ArrayList<>();
            boolean changed = false;
            for (RouteStop stop : route.stops()) {
                if (stop instanceof DeliveryStop delivery && !merged.isEmpty()
                        && merged.getLast() instanceof DeliveryStop previous
                        && previous.order().id().equals(delivery.order().id())) {
                    merged.set(merged.size() - 1, new DeliveryStop(previous.order(),
                            previous.deliveredPackages() + delivery.deliveredPackages()));
                    changed = true;
                } else {
                    merged.add(stop);
                }
            }
            DeliveryRoute normalized = rebalanceLoads(changed ? route.withReplacedStops(merged) : route);
            if (changed || normalized != route) result = result.withRoute(normalized);
        }
        return result;
    }

    /**
     * Ajusta lo que el vehículo retira en cada viaje a lo que entrega en ese viaje. Los vecindarios mueven paradas
     * entre rutas; sin este ajuste la ruta que recibe una entrega no la lleva cargada (carga negativa) y casi todos
     * los vecinos eran infactibles. La carga inicial solo cambia si la ruta sale de un almacén; si parte desde donde
     * está el vehículo, su carga es la que lleva.
     */
    static DeliveryRoute rebalanceLoads(DeliveryRoute route) {
        List<RouteStop> stops = route.stops();
        if (stops.stream().anyMatch(stop -> !(stop instanceof DeliveryStop) && !(stop instanceof WarehouseVisit))) {
            return route; // una ruta con trasvase la arma la replanificación, no el SA
        }
        int firstSegment = segmentDemand(stops, 0);
        int load = route.initialWarehouse().isPresent() ? firstSegment : route.initialLoad();
        boolean changed = load != route.initialLoad();
        List<RouteStop> updated = new ArrayList<>(stops.size());
        for (int i = 0; i < stops.size(); i++) {
            RouteStop stop = stops.get(i);
            if (stop instanceof DeliveryStop delivery) {
                load -= delivery.deliveredPackages();
                updated.add(stop);
            } else if (stop instanceof WarehouseVisit visit) {
                int pickup = Math.max(0, segmentDemand(stops, i + 1) - Math.max(0, load));
                if (pickup != visit.pickupPackages()) {
                    changed = true;
                    updated.add(new WarehouseVisit(visit.warehouse(), pickup));
                } else {
                    updated.add(stop);
                }
                load += pickup;
            } else {
                updated.add(stop);
            }
        }
        if (!changed) return route;
        int initialLoad = route.initialWarehouse().isPresent() ? firstSegment : route.initialLoad();
        if (initialLoad > route.vehicle().type().capacity()) return route;
        DeliveryRoute base = route.initialWarehouse()
                .map(warehouse -> DeliveryRoute.startScenarioAtCentral(route.id(), route.vehicle(), warehouse,
                        initialLoad, route.departureAt()))
                .orElse(route);
        return base.withReplacedStops(updated);
    }

    private static int segmentDemand(List<RouteStop> stops, int from) {
        int demand = 0;
        for (int i = from; i < stops.size() && !(stops.get(i) instanceof WarehouseVisit); i++) {
            if (stops.get(i) instanceof DeliveryStop delivery) demand += delivery.deliveredPackages();
        }
        return demand;
    }

    private Optional<OperationalPlan> relocation(OperationalPlan current, List<DeliveryRoute> routes,
                                                 RandomGenerator random) {
        StopReference source = randomDeliveryStop(routes, random);
        if (source == null) return Optional.empty();
        DeliveryRoute target = routes.get(random.nextInt(routes.size()));
        DeliveryStop moved = (DeliveryStop) source.route().stops().get(source.index());
        if (source.route().vehicle().id().equals(target.vehicle().id())) {
            List<RouteStop> reordered = new ArrayList<>(source.route().stops());
            reordered.remove(source.index());
            reordered.add(insertionIndex(reordered), moved);
            return Optional.of(current.withRoute(source.route().withReplacedStops(reordered)));
        }
        DeliveryRoute sourceAfterRemoval = source.route().withReplacedStops(removeAt(source.route().stops(), source.index()));
        DeliveryRoute targetAfterInsertion = target.withInsertedStop(insertionIndex(target.stops()), moved);
        OperationalPlan neighbor = current.withRoute(sourceAfterRemoval).withRoute(targetAfterInsertion);
        return Optional.of(removeIfNoDeliveries(neighbor, sourceAfterRemoval));
    }

    private Optional<OperationalPlan> swap(OperationalPlan current, List<DeliveryRoute> routes, RandomGenerator random) {
        StopReference first = randomDeliveryStop(routes, random);
        StopReference second = randomDeliveryStop(routes, random);
        if (first == null || second == null) return Optional.empty();
        if (first.route().vehicle().id().equals(second.route().vehicle().id())) {
            if (first.index() == second.index()) return Optional.empty();
            List<RouteStop> stops = new ArrayList<>(first.route().stops());
            RouteStop temporary = stops.set(first.index(), stops.get(second.index()));
            stops.set(second.index(), temporary);
            return Optional.of(current.withRoute(first.route().withReplacedStops(stops)));
        }
        List<RouteStop> firstStops = new ArrayList<>(first.route().stops());
        List<RouteStop> secondStops = new ArrayList<>(second.route().stops());
        RouteStop temporary = firstStops.set(first.index(), secondStops.get(second.index()));
        secondStops.set(second.index(), temporary);
        return Optional.of(current.withRoute(first.route().withReplacedStops(firstStops))
                .withRoute(second.route().withReplacedStops(secondStops)));
    }

    private Optional<OperationalPlan> twoOpt(OperationalPlan current, List<DeliveryRoute> routes, RandomGenerator random) {
        List<DeliveryRoute> eligible = routes.stream().filter(route -> deliveryIndexes(route).size() >= 2).toList();
        if (eligible.isEmpty()) return Optional.empty();
        DeliveryRoute route = eligible.get(random.nextInt(eligible.size()));
        List<Integer> indexes = deliveryIndexes(route);
        int first = random.nextInt(indexes.size() - 1);
        int last = first + 1 + random.nextInt(indexes.size() - first - 1);
        List<RouteStop> stops = new ArrayList<>(route.stops());
        while (first < last) {
            int left = indexes.get(first++);
            int right = indexes.get(last--);
            RouteStop temporary = stops.set(left, stops.get(right));
            stops.set(right, temporary);
        }
        return Optional.of(current.withRoute(route.withReplacedStops(stops)));
    }

    private Optional<OperationalPlan> splitDelivery(OperationalPlan current, List<DeliveryRoute> routes,
                                                    RandomGenerator random) {
        List<StopReference> candidates = allDeliveryStops(routes).stream()
                .filter(reference -> ((DeliveryStop) reference.route().stops().get(reference.index())).deliveredPackages() > 1)
                .toList();
        if (candidates.isEmpty()) return Optional.empty();
        StopReference reference = candidates.get(random.nextInt(candidates.size()));
        DeliveryStop original = (DeliveryStop) reference.route().stops().get(reference.index());
        int firstQuantity = random.nextInt(1, original.deliveredPackages());
        // La otra parte la lleva otro vehículo: en el mismo vehículo partir no adelanta nada (respuesta 14).
        List<DeliveryRoute> others = routes.stream()
                .filter(route -> !route.vehicle().id().equals(reference.route().vehicle().id()))
                .toList();
        if (others.isEmpty()) return Optional.empty();
        DeliveryRoute target = others.get(random.nextInt(others.size()));
        List<RouteStop> stops = new ArrayList<>(reference.route().stops());
        stops.set(reference.index(), new DeliveryStop(original.order(), firstQuantity));
        DeliveryRoute targetAfterInsertion = target.withInsertedStop(insertionIndex(target.stops()),
                new DeliveryStop(original.order(), original.deliveredPackages() - firstQuantity));
        return Optional.of(current.withRoute(reference.route().withReplacedStops(stops)).withRoute(targetAfterInsertion));
    }

    private Optional<OperationalPlan> openRoute(OperationalPlan current, OperationalSnapshot snapshot,
                                                List<DeliveryRoute> routes, RandomGenerator random) {
        StopReference source = randomDeliveryStop(routes, random);
        if (source == null) return Optional.empty();
        // Despacho diferido: también un vehículo que sigue en ruta, desde la hora en que queda disponible.
        List<VehicleOperationalState> idleVehicles = snapshot.vehiclesById().values().stream()
                .filter(state -> current.routeForVehicle(state.vehicle().id()).isEmpty())
                .filter(state -> snapshot.isVehiclePlannableAt(state.vehicle().id(), departure(state, snapshot)))
                .toList();
        if (idleVehicles.isEmpty()) return Optional.empty();
        VehicleOperationalState vehicle = idleVehicles.get(random.nextInt(idleVehicles.size()));
        DeliveryStop moved = (DeliveryStop) source.route().stops().get(source.index());
        int capacity = snapshot.fleetProfile().parametersFor(vehicle.vehicle().type()).capacity();
        int pickup = Math.min(capacity - vehicle.carriedPackages(), moved.deliveredPackages());
        if (pickup < moved.deliveredPackages()) return Optional.empty();
        Warehouse central = snapshot.inventory().warehouses().stream().filter(Warehouse::isCentral).findFirst().orElse(null);
        if (central == null) return Optional.empty();
        DeliveryRoute newRoute = DeliveryRoute.replanFromCurrentLocation("SA-" + vehicle.vehicle().id(),
                        vehicle.vehicle(), vehicle.location(), vehicle.carriedPackages(), departure(vehicle, snapshot))
                .withAppendedStop(new WarehouseVisit(central, pickup))
                .withAppendedStop(moved)
                .returningTo(central);
        DeliveryRoute sourceAfterRemoval = source.route().withReplacedStops(removeAt(source.route().stops(), source.index()));
        OperationalPlan neighbor = current.withRoute(sourceAfterRemoval).withRoute(newRoute);
        return Optional.of(removeIfNoDeliveries(neighbor, sourceAfterRemoval));
    }

    private static java.time.Instant departure(VehicleOperationalState state, OperationalSnapshot snapshot) {
        return state.availableAt().isAfter(snapshot.planningTime()) ? state.availableAt() : snapshot.planningTime();
    }

    private Optional<OperationalPlan> closeEmptyRoute(OperationalPlan current, List<DeliveryRoute> routes,
                                                       RandomGenerator random) {
        List<DeliveryRoute> emptyRoutes = routes.stream().filter(route -> deliveryIndexes(route).isEmpty()).toList();
        if (emptyRoutes.isEmpty()) return Optional.empty();
        return Optional.of(current.withoutRoute(emptyRoutes.get(random.nextInt(emptyRoutes.size())).vehicle().id()));
    }

    private Optional<OperationalPlan> changeWarehouse(OperationalPlan current, OperationalSnapshot snapshot,
                                                       List<DeliveryRoute> routes, RandomGenerator random) {
        List<VisitReference> visits = allWarehouseVisits(routes);
        List<Warehouse> warehouses = snapshot.inventory().warehouses().stream().toList();
        if (visits.isEmpty() || warehouses.size() < 2) return Optional.empty();
        VisitReference reference = visits.get(random.nextInt(visits.size()));
        Warehouse currentWarehouse = ((WarehouseVisit) reference.route().stops().get(reference.index())).warehouse();
        List<Warehouse> alternatives = warehouses.stream().filter(warehouse -> !warehouse.id().equals(currentWarehouse.id())).toList();
        if (alternatives.isEmpty()) return Optional.empty();
        Warehouse replacement = alternatives.get(random.nextInt(alternatives.size()));
        WarehouseVisit oldVisit = (WarehouseVisit) reference.route().stops().get(reference.index());
        List<RouteStop> stops = new ArrayList<>(reference.route().stops());
        stops.set(reference.index(), new WarehouseVisit(replacement, oldVisit.pickupPackages()));
        return Optional.of(current.withRoute(reference.route().withReplacedStops(stops)));
    }

    private OperationalPlan removeIfNoDeliveries(OperationalPlan plan, DeliveryRoute route) {
        return deliveryIndexes(route).isEmpty() ? plan.withoutRoute(route.vehicle().id()) : plan;
    }

    private StopReference randomDeliveryStop(List<DeliveryRoute> routes, RandomGenerator random) {
        List<StopReference> references = allDeliveryStops(routes);
        return references.isEmpty() ? null : references.get(random.nextInt(references.size()));
    }

    private List<StopReference> allDeliveryStops(List<DeliveryRoute> routes) {
        List<StopReference> references = new ArrayList<>();
        for (DeliveryRoute route : routes) for (int index : deliveryIndexes(route)) references.add(new StopReference(route, index));
        return references;
    }

    private List<VisitReference> allWarehouseVisits(List<DeliveryRoute> routes) {
        List<VisitReference> references = new ArrayList<>();
        for (DeliveryRoute route : routes) {
            for (int index = 0; index < route.stops().size(); index++) {
                if (route.stops().get(index) instanceof WarehouseVisit) references.add(new VisitReference(route, index));
            }
        }
        return references;
    }

    private List<Integer> deliveryIndexes(DeliveryRoute route) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < route.stops().size(); index++) {
            if (route.stops().get(index) instanceof DeliveryStop) indexes.add(index);
        }
        return indexes;
    }

    private List<RouteStop> removeAt(List<RouteStop> stops, int index) {
        List<RouteStop> updated = new ArrayList<>(stops);
        updated.remove(index);
        return updated;
    }

    private int insertionIndex(List<RouteStop> stops) {
        return !stops.isEmpty() && stops.getLast() instanceof WarehouseVisit ? stops.size() - 1 : stops.size();
    }

    private record StopReference(DeliveryRoute route, int index) { }
    private record VisitReference(DeliveryRoute route, int index) { }
}
