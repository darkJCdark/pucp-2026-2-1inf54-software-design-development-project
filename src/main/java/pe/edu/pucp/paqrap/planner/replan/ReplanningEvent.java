package pe.edu.pucp.paqrap.planner.replan;

import java.time.Instant;

/** An event that starts a new planning iteration. */
public sealed interface ReplanningEvent permits SpeedChangeEvent, VehicleBreakdownEvent, RoadBlockChangeEvent {
    Instant occurredAt();
}
