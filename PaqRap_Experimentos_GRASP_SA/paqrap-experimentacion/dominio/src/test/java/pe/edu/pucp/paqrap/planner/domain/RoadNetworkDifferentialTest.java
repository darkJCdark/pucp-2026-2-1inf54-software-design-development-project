package pe.edu.pucp.paqrap.planner.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * La RoadNetwork optimizada debe devolver EXACTAMENTE lo mismo que la version anterior
 * (ReferenceRoadNetwork): mismo resultado vacio/no vacio, mismos tramos con las mismas horas.
 * Se reutiliza una sola instancia para ejercitar tambien sus caches entre consultas.
 */
class RoadNetworkDifferentialTest {
    private static final Instant BASE = Instant.parse("2026-09-09T12:00:00Z");
    private static final Duration[] SPEEDS = {
            Duration.ofMillis(Math.round(3_600_000.0 / 40)),
            Duration.ofMillis(Math.round(3_600_000.0 / 25)),
            Duration.ofMillis(Math.round(3_600_000.0 / 12)),
            Duration.ofMinutes(1)};

    @Test
    void optimizedNetworkMatchesReferenceLegByLeg() {
        Random random = new Random(20260923L);
        RoadNetwork optimized = new RoadNetwork();
        ReferenceRoadNetwork reference = new ReferenceRoadNetwork();
        int compared = 0;
        int nonEmptyBlockSets = 0;
        for (int set = 0; set < 60; set++) {
            List<RoadBlock> blocks = randomBlocks(random, set % 6 == 0 ? 0 : 1 + random.nextInt(24));
            if (!blocks.isEmpty()) nonEmptyBlockSets++;
            // Mitad inmodificables (camino rapido por identidad), mitad mutables (busqueda por contenido).
            List<RoadBlock> passed = set % 2 == 0 ? List.copyOf(blocks) : new ArrayList<>(blocks);
            for (int q = 0; q < 30; q++) {
                Location origin = randomLocation(random);
                Location destination = q % 7 == 0 ? origin : randomLocation(random);
                Instant departure = BASE.plusSeconds(random.nextInt(12 * 3600)).plusNanos(random.nextInt(1_000_000_000));
                Duration travel = SPEEDS[random.nextInt(SPEEDS.length)];
                assertSame(reference.shortestPath(origin, destination, departure, travel, blocks),
                        optimized.shortestPath(origin, destination, departure, travel, passed),
                        "set " + set + " query " + q);
                // Repetida: debe salir de cache con el mismo resultado.
                assertSame(reference.shortestPath(origin, destination, departure, travel, blocks),
                        optimized.shortestPath(origin, destination, departure, travel, passed),
                        "cached set " + set + " query " + q);
                Location neighbour = neighbourOf(origin, random);
                assertEquals(reference.traverse(origin, neighbour, departure, travel, blocks),
                        optimized.traverse(origin, neighbour, departure, travel, passed), "traverse " + set + "/" + q);
                compared++;
            }
        }
        assertEquals(1800, compared);
        assertEquals(true, nonEmptyBlockSets > 40);
    }

    @Test
    void blockedSegmentBetweenBlockedNodesIsEquivalentToNodeCheck() {
        // Fundamento del indice por nodo: todo segmento bloqueado une dos nodos bloqueados.
        Random random = new Random(7L);
        for (int i = 0; i < 500; i++) {
            RoadBlock block = randomBlocks(random, 1).getFirst();
            for (StreetSegment segment : block.blockedSegments()) {
                assertEquals(true, block.blockedNodes().contains(segment.first())
                        && block.blockedNodes().contains(segment.second()));
            }
        }
    }

    private static void assertSame(Optional<RoadPath> expected, Optional<RoadPath> actual, String where) {
        assertEquals(expected.isPresent(), actual.isPresent(), where);
        if (expected.isPresent()) {
            assertEquals(expected.get().legs(), actual.get().legs(), where);
            assertEquals(expected.get().arrivesAt(), actual.get().arrivesAt(), where);
            assertEquals(expected.get().origin(), actual.get().origin(), where);
            assertEquals(expected.get().destination(), actual.get().destination(), where);
        }
    }

    private static List<RoadBlock> randomBlocks(Random random, int count) {
        List<RoadBlock> blocks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<Location> nodes = new ArrayList<>();
            Location cursor = randomLocation(random);
            nodes.add(cursor);
            boolean horizontal = random.nextBoolean();
            int pieces = 1 + random.nextInt(4);
            for (int p = 0; p < pieces; p++) {
                Location next = cursor;
                for (int attempt = 0; attempt < 20 && next.equals(cursor); attempt++) {
                    int length = 1 + random.nextInt(15);
                    int sign = random.nextBoolean() ? 1 : -1;
                    int x = horizontal ? clamp(cursor.x() + sign * length, Location.MAX_X) : cursor.x();
                    int y = horizontal ? cursor.y() : clamp(cursor.y() + sign * length, Location.MAX_Y);
                    next = new Location(x, y);
                }
                if (next.equals(cursor)) break;
                nodes.add(next);
                cursor = next;
                horizontal = !horizontal;
            }
            if (nodes.size() < 2) {
                Location first = nodes.getFirst();
                nodes.add(first.x() < Location.MAX_X ? new Location(first.x() + 1, first.y()) : new Location(first.x() - 1, first.y()));
            }
            Instant start = BASE.minusSeconds(2 * 3600).plusSeconds(random.nextInt(12 * 3600));
            Instant end = start.plusSeconds(600 + random.nextInt(4 * 3600));
            blocks.add(new RoadBlock(start, end, nodes));
        }
        return blocks;
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }

    private static Location randomLocation(Random random) {
        return new Location(random.nextInt(Location.MAX_X + 1), random.nextInt(Location.MAX_Y + 1));
    }

    private static Location neighbourOf(Location origin, Random random) {
        List<Location> options = new ArrayList<>();
        if (origin.x() < Location.MAX_X) options.add(new Location(origin.x() + 1, origin.y()));
        if (origin.x() > 0) options.add(new Location(origin.x() - 1, origin.y()));
        if (origin.y() < Location.MAX_Y) options.add(new Location(origin.x(), origin.y() + 1));
        if (origin.y() > 0) options.add(new Location(origin.x(), origin.y() - 1));
        return options.get(random.nextInt(options.size()));
    }
}
