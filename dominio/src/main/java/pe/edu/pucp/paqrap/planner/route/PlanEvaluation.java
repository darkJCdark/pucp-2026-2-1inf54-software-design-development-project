package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.InventorySnapshot;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Evaluation result; complete feasibility and structural validity of partial plans are separate. */
public final class PlanEvaluation {
    private final Map<String, ScheduledDeliveryRoute> schedulesByRouteId;
    private final List<PlanViolation> violations;
    private final InventorySnapshot remainingInventory;
    private final double totalCost;
    private final Map<String, Integer> missingPackages;

    public PlanEvaluation(Map<String, ScheduledDeliveryRoute> schedulesByRouteId, List<PlanViolation> violations,
                          InventorySnapshot remainingInventory, double totalCost) {
        this(schedulesByRouteId, violations, remainingInventory, totalCost, Map.of());
    }

    public PlanEvaluation(Map<String, ScheduledDeliveryRoute> schedulesByRouteId, List<PlanViolation> violations,
                          InventorySnapshot remainingInventory, double totalCost, Map<String,Integer> missingPackages) {
        this.missingPackages = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(missingPackages));
        this.schedulesByRouteId = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(Objects.requireNonNull(schedulesByRouteId, "schedules are required")));
        this.violations = List.copyOf(Objects.requireNonNull(violations, "violations are required"));
        this.remainingInventory = Objects.requireNonNull(remainingInventory, "remainingInventory is required");
        this.totalCost = totalCost;
    }

    /** Feasible against ALL required orders, including exact demand coverage. */
    public boolean isFeasible() { return violations.isEmpty(); }
    /** A partial plan may be operationally valid but is never a complete solution. */
    public boolean isRouteFeasible() {
        return violations.stream().allMatch(v -> v.type() == PlanViolationType.PARTIAL_DELIVERY_MISMATCH);
    }
    public Map<String,Integer> missingPackages() { return missingPackages; }
    public int missingOrders() { return missingPackages.size(); }
    public long missingUnits() { return missingPackages.values().stream().mapToLong(Integer::longValue).sum(); }
    public double totalDistanceKm() { return schedulesByRouteId.values().stream().mapToDouble(ScheduledDeliveryRoute::totalDistanceKm).sum(); }
    public Map<String, ScheduledDeliveryRoute> schedulesByRouteId() { return schedulesByRouteId; }
    public List<PlanViolation> violations() { return violations; }
    public InventorySnapshot remainingInventory() { return remainingInventory; }
    public double totalCost() { return totalCost; }
}
