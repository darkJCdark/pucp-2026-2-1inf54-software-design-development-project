package pe.edu.pucp.paqrap.planner.replan;

import pe.edu.pucp.paqrap.planner.domain.OperationalSnapshot;
import pe.edu.pucp.paqrap.planner.domain.Order;
import pe.edu.pucp.paqrap.planner.route.OperationProjection;
import pe.edu.pucp.paqrap.planner.route.OperationalPlan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ReplanningResult(OperationalSnapshot nextSnapshot, OperationProjection projection,
                               List<Order> pendingOrders, Optional<OperationalPlan> replannedPlan) {
    public ReplanningResult {
        Objects.requireNonNull(nextSnapshot, "nextSnapshot is required");
        Objects.requireNonNull(projection, "projection is required");
        pendingOrders = List.copyOf(Objects.requireNonNull(pendingOrders, "pendingOrders are required"));
        Objects.requireNonNull(replannedPlan, "replannedPlan is required");
    }
}
