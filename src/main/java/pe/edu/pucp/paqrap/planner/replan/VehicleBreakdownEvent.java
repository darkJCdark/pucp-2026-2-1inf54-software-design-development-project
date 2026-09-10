package pe.edu.pucp.paqrap.planner.replan;

import pe.edu.pucp.paqrap.planner.domain.BreakdownEvent;

import java.time.Instant;
import java.util.Objects;

public record VehicleBreakdownEvent(BreakdownEvent breakdown) implements ReplanningEvent {
    public VehicleBreakdownEvent {
        Objects.requireNonNull(breakdown, "breakdown is required");
    }

    @Override
    public Instant occurredAt() {
        return breakdown.occurredAt();
    }
}
