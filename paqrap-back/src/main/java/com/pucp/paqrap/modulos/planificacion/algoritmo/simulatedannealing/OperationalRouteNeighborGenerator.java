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
        return switch (random.nextInt(7)) {
            case 0 -> relocation(current, routes, random);
            case 1 -> swap(current, routes, random);
            case 2 -> twoOpt(current, routes, random);
            case 3 -> splitDelivery(current, routes, random);
            case 4 -> openRoute(current, snapshot, routes, random);
            case 5 -> closeEmptyRoute(current, routes, random);
            default -> changeWarehouse(current, snapshot, routes, random);
        };
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
        List<RouteStop> stops = new ArrayList<>(reference.route().stops());
        stops.set(reference.index(), new DeliveryStop(original.order(), firstQuantity));
        stops.add(reference.index() + 1, new DeliveryStop(original.order(), original.deliveredPackages() - firstQuantity));
        return Optional.of(current.withRoute(reference.route().withReplacedStops(stops)));
    }

    private Optional<OperationalPlan> openRoute(OperationalPlan current, OperationalSnapshot snapshot,
                                                List<DeliveryRoute> routes, RandomGenerator random) {
        StopReference source = randomDeliveryStop(routes, random);
        if (source == null) return Optional.empty();
        List<VehicleOperationalState> idleVehicles = snapshot.vehiclesById().values().stream()
                .filter(state -> current.routeForVehicle(state.vehicle().id()).isEmpty())
                .filter(state -> snapshot.isVehiclePlannableAt(state.vehicle().id(), snapshot.planningTime()))
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
                        vehicle.vehicle(), vehicle.location(), vehicle.carriedPackages(), snapshot.planningTime())
                .withAppendedStop(new WarehouseVisit(central, pickup))
                .withAppendedStop(moved)
                .returningTo(central);
        DeliveryRoute sourceAfterRemoval = source.route().withReplacedStops(removeAt(source.route().stops(), source.index()));
        OperationalPlan neighbor = current.withRoute(sourceAfterRemoval).withRoute(newRoute);
        return Optional.of(removeIfNoDeliveries(neighbor, sourceAfterRemoval));
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
