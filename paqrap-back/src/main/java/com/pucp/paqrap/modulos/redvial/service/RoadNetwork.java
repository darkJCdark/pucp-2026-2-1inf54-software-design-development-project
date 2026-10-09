package com.pucp.paqrap.modulos.redvial.service;
import com.pucp.paqrap.modulos.almacenes.entity.*;
import com.pucp.paqrap.modulos.flota.entity.*;
import com.pucp.paqrap.modulos.flota.service.*;
import com.pucp.paqrap.modulos.incidencias.entity.*;
import com.pucp.paqrap.modulos.incidencias.service.*;
import com.pucp.paqrap.modulos.pedidos.entity.*;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.*;
import com.pucp.paqrap.modulos.planificacion.entity.*;
import com.pucp.paqrap.modulos.redvial.entity.*;
import com.pucp.paqrap.modulos.redvial.service.*;

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
        BlockIndex activeBlocks = BlockIndex.of(Objects.requireNonNull(blocks, "blocks are required"));
        if (activeBlocks.isNodeBlockedDuring(origin, departureAt, departureAt.plusNanos(1))) {
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
        BlockIndex activeBlocks = BlockIndex.of(Objects.requireNonNull(blocks, "blocks are required"));
        Instant firstArrival = departsAt.plus(travelTimePerStreet);
        RoadLeg outbound = new RoadLeg(from, to, departsAt, firstArrival);
        if (!activeBlocks.blocksTraversal(from, to, departsAt, firstArrival)) {
            return new TraversalOutcome(List.of(outbound), to, firstArrival, false);
        }
        Instant returnArrival = firstArrival.plus(travelTimePerStreet);
        RoadLeg returnLeg = new RoadLeg(to, from, firstArrival, returnArrival);
        return new TraversalOutcome(List.of(outbound, returnLeg), from, returnArrival, true);
    }

    private Instant firstLegalDeparture(Location from, Location to, Instant candidateDeparture,
                                        Duration travelTime, BlockIndex blocks) {
        Instant departure = candidateDeparture;
        while (true) {
            Instant latestConflictEnd = blocks.latestConflictEnd(from, to, departure, departure.plus(travelTime));
            if (latestConflictEnd == null) {
                return departure;
            }
            departure = latestConflictEnd;
        }
    }

    /**
     * Blocks indexed by every node they close. A block can only affect the street (from, to) if it closes one of
     * its ends (a blocked segment always has both ends blocked), so each step checks just those few blocks instead
     * of expanding every block's polyline again.
     */
    private record BlockIndex(Map<Location, List<RoadBlock>> blocksByNode) {

        static BlockIndex of(List<RoadBlock> blocks) {
            Map<Location, List<RoadBlock>> byNode = new HashMap<>();
            for (RoadBlock block : blocks) {
                for (Location node : block.blockedNodes()) {
                    byNode.computeIfAbsent(node, ignored -> new ArrayList<>(2)).add(block);
                }
            }
            return new BlockIndex(byNode);
        }

        boolean isNodeBlockedDuring(Location node, Instant from, Instant to) {
            return blocksByNode.getOrDefault(node, List.of()).stream().anyMatch(block -> block.overlaps(from, to));
        }

        boolean blocksTraversal(Location from, Location to, Instant departure, Instant arrival) {
            return latestConflictEnd(from, to, departure, arrival) != null;
        }

        /** End of the latest block closing either end of the street while it is traversed; null if none. */
        Instant latestConflictEnd(Location from, Location to, Instant departure, Instant arrival) {
            Instant latest = latestEnd(blocksByNode.get(from), departure, arrival, null);
            return latestEnd(blocksByNode.get(to), departure, arrival, latest);
        }

        private static Instant latestEnd(List<RoadBlock> candidates, Instant departure, Instant arrival,
                                         Instant latest) {
            if (candidates == null) {
                return latest;
            }
            for (RoadBlock block : candidates) {
                if (block.overlaps(departure, arrival) && (latest == null || block.endsAt().isAfter(latest))) {
                    latest = block.endsAt();
                }
            }
            return latest;
        }
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
