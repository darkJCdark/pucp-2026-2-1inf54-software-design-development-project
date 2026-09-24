package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.Order;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Enforces hard operational constraints before a plan can enter SA's acceptance test. */
public final class PlanEvaluator {
    public boolean isFeasible(RoutePlan plan, Collection<Order> requiredOrders) {
        Set<String> expectedIds = requiredOrders.stream().map(Order::id).collect(java.util.stream.Collectors.toSet());
        Set<String> assignedIds = new HashSet<>();

        for (Route route : plan.routes()) {
            if (!route.vehicle().available() || route.load() > route.vehicle().type().capacity()) {
                return false;
            }
            if (!route.origin().hasStockFor(route.load())) {
                return false;
            }
            for (Order order : route.orders()) {
                if (!assignedIds.add(order.id()) || route.arrivalAt(order.id()).isAfter(order.deadline())) {
                    return false;
                }
            }
        }
        return assignedIds.equals(expectedIds);
    }

    public double cost(RoutePlan plan) {
        return plan.totalCost();
    }
}
