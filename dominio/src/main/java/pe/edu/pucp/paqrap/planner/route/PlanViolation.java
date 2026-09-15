package pe.edu.pucp.paqrap.planner.route;

import java.util.Objects;

public record PlanViolation(PlanViolationType type, String routeId, String detail) {
    public PlanViolation {
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(detail, "detail is required");
    }
}
