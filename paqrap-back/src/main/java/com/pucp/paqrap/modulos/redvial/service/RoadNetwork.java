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

/**
 * Time-aware routing over PaqRap's bidirectional, non-diagonal road grid.
 *
 * <p>Regla de bloqueos (indicación del profesor): las calles de un bloqueo vigente no se recorren y un nodo bloqueado
 * no se atraviesa. Si el destino está sobre el bloqueo, se llega por una calle perpendicular (que no es del bloqueo),
 * se atiende en ese nodo y se sale por la misma calle por la que se entró ({@code arrivedFrom}).
 */
public final class RoadNetwork {
    public Optional<RoadPath> shortestPath(Location origin, Location destination, Instant departureAt,
                                           Duration travelTimePerStreet, List<RoadBlock> blocks) {
        return shortestPath(origin, destination, departureAt, travelTimePerStreet, blocks, null);
    }

    /**
     * @param arrivedFrom nodo desde el que el vehículo llegó a {@code origin}; si {@code origin} está bloqueado solo
     *                    puede salir por esa calle (vuelve por donde entró). Null si se desconoce: sale por cualquier
     *                    calle que no sea del bloqueo.
     */
    public Optional<RoadPath> shortestPath(Location origin, Location destination, Instant departureAt,
                                           Duration travelTimePerStreet, List<RoadBlock> blocks,
                                           Location arrivedFrom) {
        Objects.requireNonNull(origin, "origin is required");
        Objects.requireNonNull(destination, "destination is required");
        Objects.requireNonNull(departureAt, "departureAt is required");
        validateTravelTime(travelTimePerStreet);
        BlockIndex activeBlocks = BlockIndex.of(Objects.requireNonNull(blocks, "blocks are required"));
        // Un vehículo que ya está en un nodo cuando lo bloquean (p. ej. atendiendo a un cliente) no queda sin camino:
        // espera en él hasta que termine el bloqueo, porque firstLegalDeparture también revisa el nodo de salida.
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
                boolean mayEnter = next.equals(destination);
                boolean mayLeave = state.node().equals(origin) && (arrivedFrom == null || next.equals(arrivedFrom));
                Instant legDeparture = firstLegalDeparture(state.node(), next, state.arrivalAt(),
                        travelTimePerStreet, activeBlocks, mayEnter, mayLeave);
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
                                        Duration travelTime, BlockIndex blocks, boolean mayEnter, boolean mayLeave) {
        Instant departure = candidateDeparture;
        while (true) {
            Instant latestConflictEnd = blocks.routingConflictEnd(from, to, departure, departure.plus(travelTime),
                    mayEnter, mayLeave);
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

        boolean blocksTraversal(Location from, Location to, Instant departure, Instant arrival) {
            return latestConflictEnd(from, to, departure, arrival) != null;
        }

        /**
         * Fin del último bloqueo que impide recorrer la calle (from, to) en ese intervalo; null si nada lo impide. La
         * calle está cerrada si es un tramo del bloqueo; además no se entra a un nodo bloqueado salvo que sea el
         * destino ({@code mayEnter}) ni se sale de uno salvo desde el origen por la calle de llegada ({@code mayLeave}).
         */
        Instant routingConflictEnd(Location from, Location to, Instant departure, Instant arrival, boolean mayEnter,
                                   boolean mayLeave) {
            StreetSegment street = new StreetSegment(from, to);
            Instant latest = null;
            List<RoadBlock> atFrom = blocksByNode.get(from);
            if (atFrom != null) {
                for (RoadBlock block : atFrom) {
                    if (block.overlaps(departure, arrival)
                            && (!mayLeave || block.blockedSegments().contains(street))
                            && (latest == null || block.endsAt().isAfter(latest))) {
                        latest = block.endsAt();
                    }
                }
            }
            List<RoadBlock> atTo = blocksByNode.get(to);
            if (atTo != null) {
                for (RoadBlock block : atTo) {
                    if (block.overlaps(departure, arrival)
                            && (!mayEnter || block.blockedSegments().contains(street))
                            && (latest == null || block.endsAt().isAfter(latest))) {
                        latest = block.endsAt();
                    }
                }
            }
            return latest;
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
