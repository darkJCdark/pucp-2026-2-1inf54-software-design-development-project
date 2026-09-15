package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.Location;
import pe.edu.pucp.paqrap.planner.domain.Order;

import java.time.Duration;
import java.util.Objects;

/** One complete or partial delivery. Each stop takes one hour, independently of quantity. */
public record DeliveryStop(Order order, int deliveredPackages) implements RouteStop {
    public static final Duration SERVICE_TIME = Duration.ofHours(1);

    public DeliveryStop {
        Objects.requireNonNull(order, "order is required");
        if (deliveredPackages <= 0 || deliveredPackages > order.packages()) {
            throw new IllegalArgumentException("A delivery quantity must be within the order quantity");
        }
    }

    @Override
    public Location location() {
        return order.destination();
    }
}
