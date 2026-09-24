package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.Order;

import java.util.List;
import java.util.Objects;

/** Result of deterministic seed construction, including orders not admitted atomically. */
public record SeedPlan(OperationalPlan plan, List<Order> attended, List<Order> unattended) {
    public SeedPlan {
        Objects.requireNonNull(plan, "plan is required");
        attended = List.copyOf(Objects.requireNonNull(attended, "attended orders are required"));
        unattended = List.copyOf(Objects.requireNonNull(unattended, "unattended orders are required"));
    }
}
