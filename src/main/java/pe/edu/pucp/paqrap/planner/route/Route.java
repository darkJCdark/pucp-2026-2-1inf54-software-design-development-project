package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.Location;
import pe.edu.pucp.paqrap.planner.domain.Order;
import pe.edu.pucp.paqrap.planner.domain.Vehicle;
import pe.edu.pucp.paqrap.planner.domain.Warehouse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A vehicle trip whose first departure starts at the central warehouse. */
public final class Route {
    public static final Duration DELIVERY_SERVICE_TIME = Duration.ofHours(1);

    private final Vehicle vehicle;
    private final Warehouse origin;
    private final Instant departureAt;
    private final List<Order> orders;

    public Route(Vehicle vehicle, Warehouse origin, Instant departureAt) {
        this(vehicle, origin, departureAt, List.of());
    }

    private Route(Vehicle vehicle, Warehouse origin, Instant departureAt, List<Order> orders) {
        this.vehicle = Objects.requireNonNull(vehicle, "vehicle is required");
        this.origin = Objects.requireNonNull(origin, "origin is required");
        this.departureAt = Objects.requireNonNull(departureAt, "departureAt is required");
        this.orders = new ArrayList<>(orders);
    }

    public Vehicle vehicle() { return vehicle; }
    public Warehouse origin() { return origin; }
    public Instant departureAt() { return departureAt; }
    public List<Order> orders() { return List.copyOf(orders); }
    public int load() { return orders.stream().mapToInt(Order::packages).sum(); }
    public boolean canCarry(Order order) { return load() + order.packages() <= vehicle.type().capacity(); }

    public void insert(int position, Order order) {
        if (position < 0 || position > orders.size()) {
            throw new IndexOutOfBoundsException("Invalid insertion position");
        }
        orders.add(position, Objects.requireNonNull(order, "order is required"));
    }

    public boolean removeById(String orderId) {
        return orders.removeIf(order -> order.id().equals(orderId));
    }

    /** Arrival is the SLA-relevant instant; service time consumes vehicle time but not the SLA. */
    public Instant arrivalAt(String orderId) {
        Instant clock = departureAt;
        Location current = origin.location();
        for (Order order : orders) {
            long travelSeconds = Math.round(current.manhattanDistanceTo(order.destination())
                    / vehicle.type().speedKmPerHour() * 3_600);
            clock = clock.plusSeconds(travelSeconds);
            if (order.id().equals(orderId)) {
                return clock;
            }
            clock = clock.plus(DELIVERY_SERVICE_TIME);
            current = order.destination();
        }
        throw new IllegalArgumentException("Order is not assigned to this route: " + orderId);
    }

    public double distanceKm() {
        Location current = origin.location();
        double distance = 0;
        for (Order order : orders) {
            distance += current.manhattanDistanceTo(order.destination());
            current = order.destination();
        }
        return distance;
    }

    public double cost() { return distanceKm() * vehicle.type().costPerKm(); }
    public Route copy() { return new Route(vehicle, origin, departureAt, orders); }

    public Route withOrderSequence(List<Order> sequence) {
        return new Route(vehicle, origin, departureAt, sequence);
    }
}
