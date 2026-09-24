package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.Order;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Keeps both vehicle-to-route and order-to-route indexes consistent. */
public final class RoutePlan {
    private final Map<String, Route> routesByVehicle = new LinkedHashMap<>();
    private final Map<String, String> vehicleByOrder = new LinkedHashMap<>();

    public void addRoute(Route route) {
        Objects.requireNonNull(route, "route is required");
        routesByVehicle.put(route.vehicle().id(), route);
        route.orders().forEach(order -> vehicleByOrder.put(order.id(), route.vehicle().id()));
    }

    public Optional<Route> routeForVehicle(String vehicleId) {
        return Optional.ofNullable(routesByVehicle.get(vehicleId));
    }

    public Optional<Route> routeForOrder(String orderId) {
        return Optional.ofNullable(vehicleByOrder.get(orderId)).map(routesByVehicle::get);
    }

    public Collection<Route> routes() { return routesByVehicle.values(); }
    public boolean containsOrder(String orderId) { return vehicleByOrder.containsKey(orderId); }
    public double totalCost() { return routesByVehicle.values().stream().mapToDouble(Route::cost).sum(); }

    public void rebuildOrderIndex() {
        vehicleByOrder.clear();
        for (Route route : routesByVehicle.values()) {
            for (Order order : route.orders()) {
                if (vehicleByOrder.put(order.id(), route.vehicle().id()) != null) {
                    throw new IllegalStateException("Order is assigned more than once: " + order.id());
                }
            }
        }
    }

    public RoutePlan copy() {
        RoutePlan copy = new RoutePlan();
        routesByVehicle.values().forEach(route -> copy.addRoute(route.copy()));
        return copy;
    }
}
