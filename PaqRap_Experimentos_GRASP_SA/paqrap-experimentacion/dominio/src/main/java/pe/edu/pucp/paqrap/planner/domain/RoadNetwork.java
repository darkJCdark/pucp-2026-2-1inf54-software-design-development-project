package pe.edu.pucp.paqrap.planner.domain;

import pe.edu.pucp.paqrap.planner.search.SearchControl;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Time-aware routing over PaqRap's bidirectional, non-diagonal road grid.
 *
 * <p>Devuelve exactamente el mismo camino que el Dijkstra original por tiempo de llegada
 * (misma llegada, mismos tramos, mismos desempates por (llegada, x, y)); lo verifica
 * {@code RoadNetworkDifferentialTest} contra una copia congelada de la version anterior.
 * Las aceleraciones son de implementacion, no cambian la semantica:
 * <ol>
 *   <li><b>Indice de bloqueos por nodo.</b> Un bloqueo solo afecta un tramo si alguno de sus
 *   extremos es un nodo bloqueado (los extremos de todo segmento bloqueado tambien son nodos
 *   bloqueados), asi que por tramo se revisan solo los bloqueos de esos dos nodos y no todos.</li>
 *   <li><b>Dijkstra sobre arreglos</b> indexados por nodo, sin crear objetos por nodo visitado.</li>
 *   <li><b>Plantillas sin bloqueos.</b> Sin bloqueos, la exploracion de Dijkstra no depende de la
 *   hora de salida ni de la velocidad (solo se escala). Si ningun bloqueo se cruza en tiempo con
 *   la ventana de la consulta y en espacio con los nodos que esa exploracion evaluaria, la
 *   ejecucion real seria identica paso a paso: se devuelve la plantilla desplazada en el tiempo.
 *   Si algun bloqueo pudiera influir, se ejecuta el Dijkstra completo.</li>
 * </ol>
 * Las consultas que si requieren el Dijkstra completo se memorizan por (origen, destino, salida,
 * tiempo por calle) para la misma lista de bloqueos. Las caches son acotadas, seguras entre hilos
 * y solo afectan el tiempo de calculo, nunca el resultado.
 */
public final class RoadNetwork {
    private static final int WIDTH = Location.MAX_X + 1;
    private static final int HEIGHT = Location.MAX_Y + 1;
    private static final int NODES = WIDTH * HEIGHT;
    private static final Location[] LOCATIONS = new Location[NODES];
    private static final int CHECKPOINT_EVERY = 256;
    private static final int TEMPLATE_LIMIT = 40_000;
    private static final int MEMO_LIMIT = 20_000;
    private static final int INDEX_LIMIT = 16;
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    static {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                LOCATIONS[x * HEIGHT + y] = new Location(x, y);
            }
        }
    }

    private final Map<Long, Template> templates = new ConcurrentHashMap<>();
    private final Map<List<RoadBlock>, BlockIndex> indexes = new ConcurrentHashMap<>();
    private volatile LastIndex lastIndex;

    public Optional<RoadPath> shortestPath(Location origin, Location destination, Instant departureAt,
                                           Duration travelTimePerStreet, List<RoadBlock> blocks) {
        SearchControl.pathQuery();
        Objects.requireNonNull(origin, "origin is required");
        Objects.requireNonNull(destination, "destination is required");
        Objects.requireNonNull(departureAt, "departureAt is required");
        validateTravelTime(travelTimePerStreet);
        BlockIndex index = indexFor(Objects.requireNonNull(blocks, "blocks are required"));
        long departure = toNanos(departureAt);
        long travel = travelTimePerStreet.toNanos();
        int from = nodeOf(origin);
        int to = nodeOf(destination);
        if (index.nodeBlockedDuring(from, departure, departure + 1)) {
            return Optional.empty();
        }
        if (from == to) {
            return Optional.of(new RoadPath(origin, destination, departureAt, List.of()));
        }
        Template template = templateFor(from, to);
        if (!index.mayAffect(template, departure, travel)) {
            return Optional.of(template.materialize(origin, destination, departureAt, departure, travel));
        }
        QueryKey key = new QueryKey(from, to, departure, travel);
        Optional<RoadPath> known = index.memo.get(key);
        if (known != null) {
            return known;
        }
        Optional<RoadPath> computed = search(from, to, departure, travel, index, null)
                ? Optional.of(SCRATCH.get().path(origin, destination, departureAt, from, to))
                : Optional.empty();
        if (index.memo.size() >= MEMO_LIMIT) {
            index.memo.clear();
        }
        index.memo.put(key, computed);
        return computed;
    }

    /** Resolves an already-started movement. A new block causes the mandated U-turn. */
    public TraversalOutcome traverse(Location from, Location to, Instant departsAt,
                                     Duration travelTimePerStreet, List<RoadBlock> blocks) {
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(to, "to is required");
        Objects.requireNonNull(departsAt, "departsAt is required");
        validateTravelTime(travelTimePerStreet);
        new StreetSegment(from, to);
        BlockIndex index = indexFor(Objects.requireNonNull(blocks, "blocks are required"));
        Instant firstArrival = departsAt.plus(travelTimePerStreet);
        RoadLeg outbound = new RoadLeg(from, to, departsAt, firstArrival);
        long departure = toNanos(departsAt);
        if (!index.traversalBlocked(nodeOf(from), nodeOf(to), departure, departure + travelTimePerStreet.toNanos())) {
            return new TraversalOutcome(List.of(outbound), to, firstArrival, false);
        }
        Instant returnArrival = firstArrival.plus(travelTimePerStreet);
        RoadLeg returnLeg = new RoadLeg(to, from, firstArrival, returnArrival);
        return new TraversalOutcome(List.of(outbound, returnLeg), from, returnArrival, true);
    }

    // ------------------------------------------------------------------ Dijkstra

    /**
     * Dijkstra por hora de llegada con el mismo orden de extraccion que la version original:
     * (llegada, x, y). El indice x*HEIGHT+y preserva ese orden lexicografico. Si {@code touched}
     * no es nulo, registra el rectangulo de todos los nodos cuyos tramos se evaluaron.
     */
    private static boolean search(int origin, int destination, long departure, long travel,
                                  BlockIndex index, int[] touched) {
        Scratch s = SCRATCH.get();
        s.reset();
        s.relax(origin, departure, -1, 0L);
        s.push(departure, origin);
        int pops = 0;
        while (s.size > 0) {
            if (++pops % CHECKPOINT_EVERY == 0) {
                SearchControl.checkpoint();
            }
            long arrival = s.heapKey[0];
            int node = s.heapNode[0];
            s.pop();
            if (arrival != s.arrival[node]) {
                continue;
            }
            if (node == destination) {
                include(touched, node);
                return true;
            }
            int x = node / HEIGHT;
            int y = node % HEIGHT;
            // Mismo orden de vecinos que la version original: +x, -x, +y, -y.
            if (x + 1 < WIDTH) s.edge(node, node + HEIGHT, arrival, travel, index, touched);
            if (x - 1 >= 0) s.edge(node, node - HEIGHT, arrival, travel, index, touched);
            if (y + 1 < HEIGHT) s.edge(node, node + 1, arrival, travel, index, touched);
            if (y - 1 >= 0) s.edge(node, node - 1, arrival, travel, index, touched);
        }
        return false;
    }

    private static void include(int[] box, int node) {
        if (box == null) return;
        int x = node / HEIGHT;
        int y = node % HEIGHT;
        if (x < box[0]) box[0] = x;
        if (x > box[1]) box[1] = x;
        if (y < box[2]) box[2] = y;
        if (y > box[3]) box[3] = y;
    }

    private Template templateFor(int from, int to) {
        long key = (long) from * NODES + to;
        Template cached = templates.get(key);
        if (cached != null) {
            return cached;
        }
        int[] box = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
        include(box, from);
        if (!search(from, to, 0L, 1L, BlockIndex.EMPTY, box)) {
            throw new IllegalStateException("The unblocked grid must connect every pair of nodes");
        }
        Template template = new Template(SCRATCH.get().nodePath(from, to), box[0], box[1], box[2], box[3]);
        if (templates.size() >= TEMPLATE_LIMIT) {
            templates.clear();
        }
        templates.put(key, template);
        return template;
    }

    // ------------------------------------------------------------------ Indice de bloqueos

    private BlockIndex indexFor(List<RoadBlock> blocks) {
        LastIndex last = lastIndex;
        if (last != null && last.blocks == blocks) {
            return last.index;
        }
        // List.copyOf devuelve la misma instancia solo si ya es inmodificable: solo entonces es
        // seguro reconocerla por identidad en la siguiente consulta. Una lista mutable se copia y
        // se busca por contenido en cada llamada, igual que hacia la version original.
        List<RoadBlock> key = List.copyOf(blocks);
        BlockIndex index;
        if (key.isEmpty()) {
            index = BlockIndex.EMPTY;
        } else {
            index = indexes.get(key);
            if (index == null) {
                if (indexes.size() >= INDEX_LIMIT) {
                    indexes.clear();
                }
                index = new BlockIndex(key);
                indexes.put(key, index);
            }
        }
        if (key == blocks) {
            lastIndex = new LastIndex(blocks, index);
        }
        return index;
    }

    private record LastIndex(List<RoadBlock> blocks, BlockIndex index) {
    }

    private static final class BlockIndex {
        private static final int[] NONE = new int[0];
        static final BlockIndex EMPTY = new BlockIndex(List.of());

        final long[] starts;
        final long[] ends;
        final int[][] nodesOfBlock;
        final int[][] box;
        final int[][] blocksAtNode;
        final Map<QueryKey, Optional<RoadPath>> memo = new ConcurrentHashMap<>();

        BlockIndex(List<RoadBlock> blocks) {
            int n = blocks.size();
            starts = new long[n];
            ends = new long[n];
            nodesOfBlock = new int[n][];
            box = new int[n][];
            List<List<Integer>> byNode = new ArrayList<>(NODES);
            for (int i = 0; i < NODES; i++) byNode.add(null);
            for (int b = 0; b < n; b++) {
                RoadBlock block = blocks.get(b);
                starts[b] = saturatedNanos(block.startsAt());
                ends[b] = saturatedNanos(block.endsAt());
                int[] nodes = block.blockedNodes().stream().mapToInt(RoadNetwork::nodeOf).sorted().toArray();
                nodesOfBlock[b] = nodes;
                int[] bbox = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
                for (int node : nodes) {
                    include(bbox, node);
                    List<Integer> list = byNode.get(node);
                    if (list == null) {
                        list = new ArrayList<>(2);
                        byNode.set(node, list);
                    }
                    list.add(b);
                }
                box[b] = bbox;
            }
            blocksAtNode = new int[NODES][];
            for (int i = 0; i < NODES; i++) {
                List<Integer> list = byNode.get(i);
                blocksAtNode[i] = list == null ? NONE : list.stream().mapToInt(Integer::intValue).toArray();
            }
        }

        /** Mismo criterio que RoadBlock.overlaps: desde &lt; fin y hasta &gt; inicio. */
        private boolean overlaps(int b, long fromInclusive, long toExclusive) {
            return fromInclusive < ends[b] && toExclusive > starts[b];
        }

        boolean nodeBlockedDuring(int node, long from, long to) {
            for (int b : blocksAtNode[node]) {
                if (overlaps(b, from, to)) return true;
            }
            return false;
        }

        boolean traversalBlocked(int from, int to, long departure, long arrival) {
            return nodeBlockedDuring(from, departure, arrival) || nodeBlockedDuring(to, departure, arrival);
        }

        /** Hora de salida legal mas temprana de un tramo: espera al fin del bloqueo mas tardio en conflicto. */
        long firstLegalDeparture(int from, int to, long candidate, long travel) {
            int[] atFrom = blocksAtNode[from];
            int[] atTo = blocksAtNode[to];
            if (atFrom.length == 0 && atTo.length == 0) {
                return candidate;
            }
            long departure = candidate;
            while (true) {
                long arrival = departure + travel;
                long latestConflictEnd = Long.MIN_VALUE;
                boolean conflict = false;
                for (int b : atFrom) {
                    if (overlaps(b, departure, arrival)) { conflict = true; latestConflictEnd = Math.max(latestConflictEnd, ends[b]); }
                }
                for (int b : atTo) {
                    if (overlaps(b, departure, arrival)) { conflict = true; latestConflictEnd = Math.max(latestConflictEnd, ends[b]); }
                }
                if (!conflict) {
                    return departure;
                }
                SearchControl.checkpoint();
                departure = latestConflictEnd;
                Math.addExact(departure, travel);
            }
        }

        /**
         * Conservador: verdadero si algun bloqueo podria alterar la exploracion de la plantilla.
         * Todo tramo que esa exploracion evalua empieza entre la salida y la llegada al destino, y
         * une nodos dentro del rectangulo registrado; un bloqueo que no se cruce en tiempo con
         * [salida, llegada + un tramo] ni tenga nodos dentro del rectangulo no puede influir.
         */
        boolean mayAffect(Template template, long departure, long travel) {
            if (starts.length == 0) return false;
            long windowEnd = departure + (long) (template.nodes.length) * travel;
            for (int b = 0; b < starts.length; b++) {
                if (!overlaps(b, departure, windowEnd)) continue;
                int[] bb = box[b];
                if (bb[1] < template.minX || bb[0] > template.maxX || bb[3] < template.minY || bb[2] > template.maxY) continue;
                for (int node : nodesOfBlock[b]) {
                    int x = node / HEIGHT;
                    int y = node % HEIGHT;
                    if (x >= template.minX && x <= template.maxX && y >= template.minY && y <= template.maxY) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /** Camino sin bloqueos, independiente de la hora y de la velocidad; el tramo i sale en i*tramo. */
    private record Template(int[] nodes, int minX, int maxX, int minY, int maxY) {
        RoadPath materialize(Location origin, Location destination, Instant departureAt, long departure, long travel) {
            List<RoadLeg> legs = new ArrayList<>(nodes.length - 1);
            for (int i = 0; i + 1 < nodes.length; i++) {
                legs.add(new RoadLeg(LOCATIONS[nodes[i]], LOCATIONS[nodes[i + 1]],
                        fromNanos(departure + i * travel), fromNanos(departure + (i + 1) * travel)));
            }
            return new RoadPath(origin, destination, departureAt, legs);
        }
    }

    private record QueryKey(int from, int to, long departure, long travel) {
    }

    /** Memoria de trabajo por hilo: evita reservar arreglos por consulta. */
    private static final class Scratch {
        final long[] arrival = new long[NODES];
        final int[] generationOf = new int[NODES];
        final int[] predecessor = new int[NODES];
        final long[] legDeparture = new long[NODES];
        long[] heapKey = new long[NODES * 4 + 16];
        int[] heapNode = new int[NODES * 4 + 16];
        int size;
        int generation;

        void reset() {
            size = 0;
            if (++generation == Integer.MAX_VALUE) {
                java.util.Arrays.fill(generationOf, 0);
                generation = 1;
            }
        }

        boolean known(int node) {
            return generationOf[node] == generation;
        }

        void relax(int node, long at, int from, long departedAt) {
            generationOf[node] = generation;
            arrival[node] = at;
            predecessor[node] = from;
            legDeparture[node] = departedAt;
        }

        void edge(int node, int next, long arrivalAtNode, long travel, BlockIndex index, int[] touched) {
            include(touched, node);
            include(touched, next);
            long departure = index.firstLegalDeparture(node, next, arrivalAtNode, travel);
            long nextArrival = departure + travel;
            if (!known(next) || nextArrival < arrival[next]) {
                relax(next, nextArrival, node, departure);
                push(nextArrival, next);
            }
        }

        void push(long key, int node) {
            if (size == heapKey.length) {
                heapKey = java.util.Arrays.copyOf(heapKey, size * 2);
                heapNode = java.util.Arrays.copyOf(heapNode, size * 2);
            }
            int i = size++;
            while (i > 0) {
                int parent = (i - 1) >>> 1;
                if (!less(key, node, heapKey[parent], heapNode[parent])) break;
                heapKey[i] = heapKey[parent];
                heapNode[i] = heapNode[parent];
                i = parent;
            }
            heapKey[i] = key;
            heapNode[i] = node;
        }

        void pop() {
            int last = --size;
            if (last == 0) return;
            long key = heapKey[last];
            int node = heapNode[last];
            int i = 0;
            while (true) {
                int child = 2 * i + 1;
                if (child >= last) break;
                if (child + 1 < last && less(heapKey[child + 1], heapNode[child + 1], heapKey[child], heapNode[child])) {
                    child++;
                }
                if (!less(heapKey[child], heapNode[child], key, node)) break;
                heapKey[i] = heapKey[child];
                heapNode[i] = heapNode[child];
                i = child;
            }
            heapKey[i] = key;
            heapNode[i] = node;
        }

        private static boolean less(long keyA, int nodeA, long keyB, int nodeB) {
            return keyA < keyB || (keyA == keyB && nodeA < nodeB);
        }

        int[] nodePath(int origin, int destination) {
            int length = 1;
            for (int cursor = destination; cursor != origin; cursor = predecessor[cursor]) length++;
            int[] nodes = new int[length];
            int i = length - 1;
            for (int cursor = destination; ; cursor = predecessor[cursor]) {
                nodes[i--] = cursor;
                if (cursor == origin) break;
            }
            return nodes;
        }

        RoadPath path(Location originLocation, Location destinationLocation, Instant departureAt, int origin, int destination) {
            int[] nodes = nodePath(origin, destination);
            List<RoadLeg> legs = new ArrayList<>(nodes.length - 1);
            for (int i = 1; i < nodes.length; i++) {
                int node = nodes[i];
                legs.add(new RoadLeg(LOCATIONS[nodes[i - 1]], LOCATIONS[node],
                        fromNanos(legDeparture[node]), fromNanos(arrival[node])));
            }
            return new RoadPath(originLocation, destinationLocation, departureAt, legs);
        }
    }

    // ------------------------------------------------------------------ utilidades

    private static int nodeOf(Location location) {
        return location.x() * HEIGHT + location.y();
    }

    private static long toNanos(Instant instant) {
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
    }

    /** Los extremos de un bloqueo fuera del rango representable se saturan: conserva todas las comparaciones. */
    private static long saturatedNanos(Instant instant) {
        try {
            return toNanos(instant);
        } catch (ArithmeticException outOfRange) {
            return instant.getEpochSecond() < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
    }

    private static Instant fromNanos(long nanos) {
        return Instant.ofEpochSecond(Math.floorDiv(nanos, 1_000_000_000L), Math.floorMod(nanos, 1_000_000_000L));
    }

    private void validateTravelTime(Duration travelTime) {
        Objects.requireNonNull(travelTime, "travelTimePerStreet is required");
        if (travelTime.isZero() || travelTime.isNegative()) {
            throw new IllegalArgumentException("Travel time per street must be positive");
        }
    }
}
