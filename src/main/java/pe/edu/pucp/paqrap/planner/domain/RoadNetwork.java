package pe.edu.pucp.paqrap.planner.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;

/** Time-aware routing over PaqRap's bidirectional, non-diagonal road grid. */
public final class RoadNetwork {
    public Optional<RoadPath> shortestPath(Location origin, Location destination, Instant departureAt,
                                           Duration travelTimePerStreet, List<RoadBlock> blocks) {
        Objects.requireNonNull(origin, "origin is required");
        Objects.requireNonNull(destination, "destination is required");
        Objects.requireNonNull(departureAt, "departureAt is required");
        validateTravelTime(travelTimePerStreet);
        List<RoadBlock> activeBlocks = List.copyOf(Objects.requireNonNull(blocks, "blocks are required"));
        if (isNodeBlockedDuring(origin, departureAt, departureAt.plusNanos(1), activeBlocks)) {
            return Optional.empty();
        }
        if (origin.equals(destination)) {
            return Optional.of(new RoadPath(origin, destination, departureAt, List.of()));
        }

        Map<Location, Instant> earliestArrival = new HashMap<>();
        Map<Location, RoadLeg> incomingLeg = new HashMap<>();
        PriorityQueue<NodeState> frontier = new PriorityQueue<>(Comparator.comparing(NodeState::arrivalAt));
        earliestArrival.put(origin, departureAt);
        frontier.add(new NodeState(origin, departureAt));

        while (!frontier.isEmpty()) {
            NodeState state = frontier.remove();
            if (!state.arrivalAt().equals(earliestArrival.get(state.node()))) {
                continue;
            }
            if (state.node().equals(destination)) {
                return Optional.of(reconstructPath(origin, destination, departureAt, incomingLeg));
            }
            for (Location next : neighboursOf(state.node())) {
                Instant legDeparture = firstLegalDeparture(state.node(), next, state.arrivalAt(),
                        travelTimePerStreet, activeBlocks);
                Instant nextArrival = legDeparture.plus(travelTimePerStreet);
                Instant knownArrival = earliestArrival.get(next);
                if (knownArrival == null || nextArrival.isBefore(knownArrival)) {
                    earliestArrival.put(next, nextArrival);
                    incomingLeg.put(next, new RoadLeg(state.node(), next, legDeparture, nextArrival));
                    frontier.add(new NodeState(next, nextArrival));
                }
            }
        }
        return Optional.empty();
    }

    /** Resolves an already-started movement. A new block causes the mandated U-turn. */
    public TraversalOutcome traverse(Location from, Location to, Instant departsAt,
                                     Duration travelTimePerStreet, List<RoadBlock> blocks) {
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(to, "to is required");
        Objects.requireNonNull(departsAt, "departsAt is required");
        validateTravelTime(travelTimePerStreet);
        new StreetSegment(from, to);
        List<RoadBlock> activeBlocks = List.copyOf(Objects.requireNonNull(blocks, "blocks are required"));
        Instant firstArrival = departsAt.plus(travelTimePerStreet);
        RoadLeg outbound = new RoadLeg(from, to, departsAt, firstArrival);
        if (!isTraversalBlockedDuring(from, to, departsAt, firstArrival, activeBlocks)) {
            return new TraversalOutcome(List.of(outbound), to, firstArrival, false);
        }
        Instant returnArrival = firstArrival.plus(travelTimePerStreet);
        RoadLeg returnLeg = new RoadLeg(to, from, firstArrival, returnArrival);
        return new TraversalOutcome(List.of(outbound, returnLeg), from, returnArrival, true);
    }

    private Instant firstLegalDeparture(Location from, Location to, Instant candidateDeparture,
                                        Duration travelTime, List<RoadBlock> blocks) {
        Instant departure = candidateDeparture;
        while (true) {
            Instant arrival = departure.plus(travelTime);
            Instant latestConflictEnd = null;
            for (RoadBlock block : blocks) {
                if (blocksTraversal(block, from, to, departure, arrival)) {
                    if (latestConflictEnd == null || block.endsAt().isAfter(latestConflictEnd)) {
                        latestConflictEnd = block.endsAt();
                    }
                }
            }
            if (latestConflictEnd == null) {
                return departure;
            }
            departure = latestConflictEnd;
        }
    }

    private boolean isTraversalBlockedDuring(Location from, Location to, Instant departure, Instant arrival,
                                             List<RoadBlock> blocks) {
        return blocks.stream().anyMatch(block -> blocksTraversal(block, from, to, departure, arrival));
    }

    private boolean blocksTraversal(RoadBlock block, Location from, Location to, Instant departure, Instant arrival) {
        if (!block.overlaps(departure, arrival)) {
            return false;
        }
        StreetSegment segment = new StreetSegment(from, to);
        return block.blockedSegments().contains(segment) || block.blockedNodes().contains(to)
                || block.blockedNodes().contains(from);
    }

    private boolean isNodeBlockedDuring(Location node, Instant from, Instant to, List<RoadBlock> blocks) {
        return blocks.stream().anyMatch(block -> block.overlaps(from, to) && block.blockedNodes().contains(node));
    }

    private List<Location> neighboursOf(Location node) {
        List<Location> neighbours = new ArrayList<>(4);
        addIfInside(neighbours, node.x() + 1, node.y());
        addIfInside(neighbours, node.x() - 1, node.y());
        addIfInside(neighbours, node.x(), node.y() + 1);
        addIfInside(neighbours, node.x(), node.y() - 1);
        return neighbours;
    }

    private void addIfInside(List<Location> nodes, int x, int y) {
        if (x >= 0 && x <= Location.MAX_X && y >= 0 && y <= Location.MAX_Y) {
            nodes.add(new Location(x, y));
        }
    }

    private RoadPath reconstructPath(Location origin, Location destination, Instant departureAt,
                                     Map<Location, RoadLeg> incomingLeg) {
        List<RoadLeg> reverse = new ArrayList<>();
        Location cursor = destination;
        while (!cursor.equals(origin)) {
            RoadLeg leg = incomingLeg.get(cursor);
            if (leg == null) {
                throw new IllegalStateException("Path reconstruction failed");
            }
            reverse.add(leg);
            cursor = leg.from();
        }
        List<RoadLeg> ordered = new ArrayList<>(reverse.size());
        for (int index = reverse.size() - 1; index >= 0; index--) {
            ordered.add(reverse.get(index));
        }
        return new RoadPath(origin, destination, departureAt, ordered);
    }

    private void validateTravelTime(Duration travelTime) {
        Objects.requireNonNull(travelTime, "travelTimePerStreet is required");
        if (travelTime.isZero() || travelTime.isNegative()) {
            throw new IllegalArgumentException("Travel time per street must be positive");
        }
    }

    private record NodeState(Location node, Instant arrivalAt) {
    }
}
