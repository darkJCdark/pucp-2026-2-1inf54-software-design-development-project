package pe.edu.pucp.paqrap.planner.sa;

import pe.edu.pucp.paqrap.planner.domain.Order;
import pe.edu.pucp.paqrap.planner.route.Route;
import pe.edu.pucp.paqrap.planner.route.RoutePlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Generates the relocation, swap and 2-opt neighborhoods agreed for PaqRap. */
public final class RouteNeighborGenerator implements NeighborGenerator {
    @Override
    public Optional<RoutePlan> generate(RoutePlan current, RandomGenerator random) {
        List<Route> routes = current.routes().stream().toList();
        if (routes.isEmpty()) {
            return Optional.empty();
        }
        return switch (random.nextInt(3)) {
            case 0 -> relocation(current, routes, random);
            case 1 -> swap(current, routes, random);
            default -> twoOpt(current, routes, random);
        };
    }

    private Optional<RoutePlan> relocation(RoutePlan current, List<Route> routes, RandomGenerator random) {
        List<Route> sources = routes.stream().filter(route -> !route.orders().isEmpty()).toList();
        if (sources.isEmpty()) {
            return Optional.empty();
        }
        Route source = sources.get(random.nextInt(sources.size()));
        Route target = routes.get(random.nextInt(routes.size()));
        Order moved = source.orders().get(random.nextInt(source.orders().size()));

        List<Order> sourceOrders = new ArrayList<>(source.orders());
        sourceOrders.remove(moved);
        if (source.vehicle().id().equals(target.vehicle().id())) {
            int insertion = random.nextInt(sourceOrders.size() + 1);
            sourceOrders.add(insertion, moved);
            return replace(current, source.withOrderSequence(sourceOrders));
        }

        List<Order> targetOrders = new ArrayList<>(target.orders());
        targetOrders.add(random.nextInt(targetOrders.size() + 1), moved);
        return replace(current, source.withOrderSequence(sourceOrders), target.withOrderSequence(targetOrders));
    }

    private Optional<RoutePlan> swap(RoutePlan current, List<Route> routes, RandomGenerator random) {
        List<Route> candidates = routes.stream().filter(route -> !route.orders().isEmpty()).toList();
        if (candidates.size() < 2) {
            return Optional.empty();
        }
        Route first = candidates.get(random.nextInt(candidates.size()));
        Route second = candidates.get(random.nextInt(candidates.size()));
        if (first.vehicle().id().equals(second.vehicle().id())) {
            return Optional.empty();
        }
        List<Order> firstOrders = new ArrayList<>(first.orders());
        List<Order> secondOrders = new ArrayList<>(second.orders());
        int firstIndex = random.nextInt(firstOrders.size());
        int secondIndex = random.nextInt(secondOrders.size());
        Order temporary = firstOrders.set(firstIndex, secondOrders.get(secondIndex));
        secondOrders.set(secondIndex, temporary);
        return replace(current, first.withOrderSequence(firstOrders), second.withOrderSequence(secondOrders));
    }

    private Optional<RoutePlan> twoOpt(RoutePlan current, List<Route> routes, RandomGenerator random) {
        List<Route> candidates = routes.stream().filter(route -> route.orders().size() >= 2).toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        Route route = candidates.get(random.nextInt(candidates.size()));
        List<Order> sequence = new ArrayList<>(route.orders());
        int first = random.nextInt(sequence.size() - 1);
        int last = first + 1 + random.nextInt(sequence.size() - first - 1);
        java.util.Collections.reverse(sequence.subList(first, last + 1));
        return replace(current, route.withOrderSequence(sequence));
    }

    private Optional<RoutePlan> replace(RoutePlan source, Route... replacements) {
        RoutePlan neighbor = source.copy();
        for (Route route : replacements) {
            neighbor.addRoute(route);
        }
        neighbor.rebuildOrderIndex();
        return Optional.of(neighbor);
    }
}
