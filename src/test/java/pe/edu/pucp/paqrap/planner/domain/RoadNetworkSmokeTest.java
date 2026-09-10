package pe.edu.pucp.paqrap.planner.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkSmokeTest {
    @Test
    void routesAroundBlocksAndPerformsUTurns() {
        Instant now = Instant.parse("2026-09-09T12:00:00Z");
        Duration minute = Duration.ofMinutes(1);
        RoadBlock block = new RoadBlock(now, now.plus(Duration.ofHours(1)),
                List.of(new Location(1, 0), new Location(1, 1)));
        assertTrue(block.blockedNodes().contains(new Location(1, 0)));
        assertTrue(block.blockedSegments().contains(new StreetSegment(new Location(1, 0), new Location(1, 1))));

        RoadNetwork network = new RoadNetwork();
        RoadPath detour = network.shortestPath(new Location(0, 0), new Location(2, 0), now, minute, List.of(block))
                .orElseThrow(() -> new AssertionError("Expected a detour around blocked nodes"));
        assertTrue(detour.distanceKm() > 2);
        assertTrue(detour.legs().stream().noneMatch(leg -> leg.to().equals(new Location(1, 0))));

        TraversalOutcome outcome = network.traverse(new Location(0, 0), new Location(1, 0), now, minute, List.of(block));
        assertTrue(outcome.forcedUTurn());
        assertEquals(new Location(0, 0), outcome.finalLocation());
        assertEquals(2, outcome.legs().size());

        assertThrows(IllegalArgumentException.class, () -> new StreetSegment(new Location(0, 0), new Location(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> new RoadBlock(now, now.plus(minute),
                List.of(new Location(0, 0), new Location(1, 1))));
    }
}
