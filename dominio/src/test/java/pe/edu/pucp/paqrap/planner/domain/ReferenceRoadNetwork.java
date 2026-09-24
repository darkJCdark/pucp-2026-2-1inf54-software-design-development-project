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

/** Copia congelada de RoadNetwork ANTES de la optimizacion, usada solo como oraculo en pruebas diferenciales. */
final class ReferenceRoadNetwork {
    public Optional<RoadPath> shortestPath(Location origin, Location destination, Instant departureAt,
                                           Duration travelTimePerStreet, List<RoadBlock> blocks) {
        
        Objects.requireNonNull(origin, "origin is required");
        Objects.requireNonNull(destination, "destination is required");
        Objects.requireNonNull(departureAt, "departureAt is required");
        validateTravelTime(travelTimePerStreet);
        List<RoadBlock> activeBlocks = List.copyOf(Objects.requireNonNull(blocks, "blocks are required"));
        // blockedNodes()/blockedSegments() expanden la poligonal del bloqueo a un
        // Set desde cero en cada llamada (ver RoadBlock). Dijkstra los consulta
        // por cada arista de cada nodo que visita -- sin precalcularlos UNA vez
        // aqui, se reconstruyen decenas de miles de veces por consulta (medido:
        // ~413ms/consulta con 19 bloqueos reales sobre la grilla de 70x50; ver
        // README, seccion de rendimiento). Con la data real del curso esto
        // dominaba por completo el tiempo de planificacion.
        BloqueosResueltos bloqueosResueltos = BloqueosResueltos.de(activeBlocks);
        if (isNodeBlockedDuring(origin, departureAt, departureAt.plusNanos(1), bloqueosResueltos)) {
            return Optional.empty();
        }
        if (origin.equals(destination)) {
            return Optional.of(new RoadPath(origin, destination, departureAt, List.of()));
        }

        Map<Location, Instant> earliestArrival = new HashMap<>();
        Map<Location, RoadLeg> incomingLeg = new HashMap<>();
        PriorityQueue<NodeState> frontier = new PriorityQueue<>(Comparator.comparing(NodeState::arrivalAt)
                .thenComparingInt(n -> n.node().x()).thenComparingInt(n -> n.node().y()));
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
                        travelTimePerStreet, bloqueosResueltos);
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
        BloqueosResueltos bloqueosResueltos = BloqueosResueltos.de(activeBlocks);
        Instant firstArrival = departsAt.plus(travelTimePerStreet);
        RoadLeg outbound = new RoadLeg(from, to, departsAt, firstArrival);
        if (!isTraversalBlockedDuring(from, to, departsAt, firstArrival, bloqueosResueltos)) {
            return new TraversalOutcome(List.of(outbound), to, firstArrival, false);
        }
        Instant returnArrival = firstArrival.plus(travelTimePerStreet);
        RoadLeg returnLeg = new RoadLeg(to, from, firstArrival, returnArrival);
        return new TraversalOutcome(List.of(outbound, returnLeg), from, returnArrival, true);
    }

    private Instant firstLegalDeparture(Location from, Location to, Instant candidateDeparture,
                                        Duration travelTime, BloqueosResueltos bloqueos) {
        Instant departure = candidateDeparture;
        while (true) {
            
            Instant arrival = departure.plus(travelTime);
            Instant latestConflictEnd = null;
            for (RoadBlock block : bloqueos.blocks()) {
                if (blocksTraversal(block, bloqueos, from, to, departure, arrival)) {
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
                                             BloqueosResueltos bloqueos) {
        return bloqueos.blocks().stream().anyMatch(block -> blocksTraversal(block, bloqueos, from, to, departure, arrival));
    }

    private boolean blocksTraversal(RoadBlock block, BloqueosResueltos bloqueos, Location from, Location to,
                                    Instant departure, Instant arrival) {
        if (block.isActiveAt(arrival) && bloqueos.nodosDe(block).contains(to)) return true;
        if (!block.overlaps(departure, arrival)) {
            return false;
        }
        StreetSegment segment = new StreetSegment(from, to);
        return bloqueos.segmentosDe(block).contains(segment) || bloqueos.nodosDe(block).contains(to)
                || bloqueos.nodosDe(block).contains(from);
    }

    private boolean isNodeBlockedDuring(Location node, Instant from, Instant to, BloqueosResueltos bloqueos) {
        return bloqueos.blocks().stream().anyMatch(block -> block.overlaps(from, to) && bloqueos.nodosDe(block).contains(node));
    }

    /** Precalcula, UNA sola vez por llamada a shortestPath()/traverse(), los
     *  nodos y segmentos bloqueados de cada RoadBlock activo -- evita que
     *  Dijkstra los reconstruya en cada arista que examina (RoadBlock.blockedNodes()/
     *  blockedSegments() expanden la poligonal desde cero cada vez que se llaman). */
    private static final class BloqueosResueltos {
        private final List<RoadBlock> blocks;
        private final Map<RoadBlock, java.util.Set<Location>> nodos;
        private final Map<RoadBlock, java.util.Set<StreetSegment>> segmentos;

        private BloqueosResueltos(List<RoadBlock> blocks, Map<RoadBlock, java.util.Set<Location>> nodos,
                                   Map<RoadBlock, java.util.Set<StreetSegment>> segmentos) {
            this.blocks = blocks;
            this.nodos = nodos;
            this.segmentos = segmentos;
        }

        static BloqueosResueltos de(List<RoadBlock> blocks) {
            Map<RoadBlock, java.util.Set<Location>> nodos = new HashMap<>();
            Map<RoadBlock, java.util.Set<StreetSegment>> segmentos = new HashMap<>();
            for (RoadBlock block : blocks) {
                nodos.put(block, block.blockedNodes());
                segmentos.put(block, block.blockedSegments());
            }
            return new BloqueosResueltos(blocks, nodos, segmentos);
        }

        List<RoadBlock> blocks() { return blocks; }
        java.util.Set<Location> nodosDe(RoadBlock block) { return nodos.get(block); }
        java.util.Set<StreetSegment> segmentosDe(RoadBlock block) { return segmentos.get(block); }
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
