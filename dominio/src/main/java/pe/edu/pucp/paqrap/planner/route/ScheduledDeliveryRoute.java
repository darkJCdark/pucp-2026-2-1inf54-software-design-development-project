package pe.edu.pucp.paqrap.planner.route;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Fully timed form of a delivery route, ready for feasibility and cost evaluation. */
public final class ScheduledDeliveryRoute {
    private final DeliveryRoute route;
    private final List<ScheduledRouteStop> scheduledStops;
    private final Instant completedAt;
    private final List<ScheduledMealBreak> mealBreaks;
    private final double totalDistanceKm;
    private final double totalCost;

    public ScheduledDeliveryRoute(DeliveryRoute route, List<ScheduledRouteStop> scheduledStops,
                                  Instant completedAt, double totalDistanceKm, double totalCost) {
        this(route, scheduledStops, completedAt, totalDistanceKm, totalCost, List.of());
    }

    public ScheduledDeliveryRoute(DeliveryRoute route, List<ScheduledRouteStop> scheduledStops,
                                  Instant completedAt, double totalDistanceKm, double totalCost,
                                  List<ScheduledMealBreak> mealBreaks) {
        this.mealBreaks = List.copyOf(mealBreaks);
        this.route = Objects.requireNonNull(route, "route is required");
        this.scheduledStops = List.copyOf(Objects.requireNonNull(scheduledStops, "scheduledStops are required"));
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt is required");
        if (totalDistanceKm < 0 || totalCost < 0) {
            throw new IllegalArgumentException("Distance and cost cannot be negative");
        }
        this.totalDistanceKm = totalDistanceKm;
        this.totalCost = totalCost;
    }

    public DeliveryRoute route() { return route; }
    public List<ScheduledRouteStop> scheduledStops() { return scheduledStops; }
    public List<ScheduledMealBreak> mealBreaks() { return mealBreaks; }
    /** Physical return can precede completion of the mandatory final-shift meal. */
    public Instant returnedAt() { return scheduledStops.isEmpty() ? route.departureAt() : scheduledStops.getLast().completedAt(); }
    public Instant completedAt() { return completedAt; }
    public double totalDistanceKm() { return totalDistanceKm; }
    public double totalCost() { return totalCost; }
    public Duration elapsedTime() { return Duration.between(route.departureAt(), completedAt); }
}
