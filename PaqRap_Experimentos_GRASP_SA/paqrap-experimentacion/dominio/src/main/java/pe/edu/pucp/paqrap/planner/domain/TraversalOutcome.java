package pe.edu.pucp.paqrap.planner.domain;

import java.time.Instant;
import java.util.List;

/** Result of a real-time traversal attempt, including a forced U-turn when a block is encountered. */
public record TraversalOutcome(List<RoadLeg> legs, Location finalLocation, Instant completedAt,
                               boolean forcedUTurn) {
    public TraversalOutcome {
        legs = List.copyOf(legs);
    }
}
