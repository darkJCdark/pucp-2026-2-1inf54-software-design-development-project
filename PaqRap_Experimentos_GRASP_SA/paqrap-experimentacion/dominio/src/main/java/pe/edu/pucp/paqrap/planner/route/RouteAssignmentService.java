package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.Order;
import pe.edu.pucp.paqrap.planner.domain.Vehicle;
import pe.edu.pucp.paqrap.planner.domain.Warehouse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Builds a feasible seed by assigning earliest-deadline orders to the least-cost feasible insertion. */
public final class RouteAssignmentService {
    private final PlanEvaluator evaluator;

    public RouteAssignmentService(PlanEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public Optional<RoutePlan> createInitialPlan(Collection<Order> orders, Collection<Vehicle> vehicles,
                                                  Warehouse central, Instant departureAt) {
        List<Order> pending = new ArrayList<>(orders);
        pending.sort(Comparator.comparing(Order::deadline));
        RoutePlan plan = new RoutePlan();

        for (Order order : pending) {
            Insertion best = findBestInsertion(plan, order, vehicles, central, departureAt);
            if (best == null) {
                return Optional.empty();
            }
            best.applyTo(plan);
            plan.rebuildOrderIndex();
        }
        return evaluator.isFeasible(plan, orders) ? Optional.of(plan) : Optional.empty();
    }

    private Insertion findBestInsertion(RoutePlan plan, Order order, Collection<Vehicle> vehicles,
                                        Warehouse central, Instant departureAt) {
        Insertion best = null;
        for (Route route : plan.routes()) {
            best = bestOfRoute(best, route, order, false);
        }
        for (Vehicle vehicle : vehicles) {
            if (vehicle.available() && plan.routeForVehicle(vehicle.id()).isEmpty()) {
                best = bestOfRoute(best, new Route(vehicle, central, departureAt), order, true);
            }
        }
        return best;
    }

    private Insertion bestOfRoute(Insertion currentBest, Route base, Order order, boolean newRoute) {
        if (!base.canCarry(order) || !base.origin().hasStockFor(base.load() + order.packages())) {
            return currentBest;
        }
        for (int position = 0; position <= base.orders().size(); position++) {
            Route candidate = base.copy();
            candidate.insert(position, order);
            if (candidate.arrivalAt(order.id()).isAfter(order.deadline())) {
                continue;
            }
            boolean allOnTime = candidate.orders().stream()
                    .allMatch(existing -> !candidate.arrivalAt(existing.id()).isAfter(existing.deadline()));
            if (allOnTime && (currentBest == null || candidate.cost() < currentBest.candidate().cost())) {
                currentBest = new Insertion(base.vehicle().id(), candidate, newRoute);
            }
        }
        return currentBest;
    }

    private record Insertion(String vehicleId, Route candidate, boolean newRoute) {
        void applyTo(RoutePlan plan) { plan.addRoute(candidate); }
    }
}
