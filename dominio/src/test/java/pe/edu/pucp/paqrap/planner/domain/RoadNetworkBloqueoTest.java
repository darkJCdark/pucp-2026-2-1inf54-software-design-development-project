package pe.edu.pucp.paqrap.planner.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Un tramo bloqueado debe desviar la ruta solo mientras el transito real
 *  cae dentro de la ventana del bloqueo -- no antes ni despues. */
class RoadNetworkBloqueoTest {

    private final RoadNetwork roadNetwork = new RoadNetwork();
    private final Location origen = new Location(10, 10);
    private final Location destino = new Location(10, 15);
    private final Duration porTramo = Duration.ofMillis(Math.round(3_600_000.0 / 12.0)); // 12 km/h
    private final Instant horaSalida = Instant.parse("2026-09-10T12:00:00Z");

    @Test
    void sinBloqueosUsaElCaminoDirecto() {
        RoadPath camino = roadNetwork.shortestPath(origen, destino, horaSalida, porTramo, List.of()).orElseThrow();
        assertEquals(5, camino.distanceKm());
    }

    @Test
    void bloqueoVigenteEnElInstanteDeTransitoObligaUnDesvio() {
        RoadBlock bloqueo = new RoadBlock(horaSalida.plusSeconds(8 * 60), horaSalida.plusSeconds(16 * 60),
                List.of(new Location(10, 12), new Location(10, 13)));

        Optional<RoadPath> camino = roadNetwork.shortestPath(origen, destino, horaSalida, porTramo, List.of(bloqueo));

        assertTrue(camino.isPresent());
        assertEquals(7, camino.get().distanceKm(), "con el tramo bloqueado en el instante de transito, debe desviarse (+2 km)");
    }

    @Test
    void elMismoBloqueoFueraDeSuVentanaNoAfectaLaRuta() {
        RoadBlock bloqueo = new RoadBlock(horaSalida.minusSeconds(3 * 3600), horaSalida.minusSeconds(3600),
                List.of(new Location(10, 12), new Location(10, 13)));

        RoadPath camino = roadNetwork.shortestPath(origen, destino, horaSalida, porTramo, List.of(bloqueo)).orElseThrow();

        assertEquals(5, camino.distanceKm());
    }
}
