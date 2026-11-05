package com.pucp.paqrap.modulos.redvial.service;

import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.entity.RoadLeg;
import com.pucp.paqrap.modulos.redvial.entity.RoadPath;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Un cliente o almacén sobre un nodo bloqueado: no se entra ni se sale de él mientras el bloqueo está vigente; el
 * vehículo espera a que termine en lugar de quedar sin camino.
 */
class RoadNetworkNodoBloqueadoTest {

    private final RoadNetwork roadNetwork = new RoadNetwork();
    private final Location almacen = new Location(10, 10);
    private final Location cliente = new Location(10, 15);
    private final Duration porTramo = Duration.ofMinutes(5);
    private final Instant salida = Instant.parse("2026-09-10T12:00:00Z");
    private final Instant finBloqueo = salida.plus(Duration.ofHours(3));
    private final RoadBlock bloqueoDelCliente = new RoadBlock(salida.plus(Duration.ofMinutes(10)), finBloqueo,
            List.of(new Location(10, 15), new Location(12, 15)));

    @Test
    void aUnClienteBloqueadoSeLlegaRecienCuandoTerminaElBloqueo() {
        RoadPath ida = roadNetwork.shortestPath(almacen, cliente, salida, porTramo, List.of(bloqueoDelCliente))
                .orElseThrow();

        assertFalse(ida.arrivesAt().isBefore(finBloqueo), "llegó a las " + ida.arrivesAt() + " con el nodo bloqueado");
        assertTrue(ida.legs().stream().noneMatch(tramo -> entraAlBloqueoMientrasRige(tramo)));
    }

    @Test
    void siBloqueanElNodoDondeEstaElVehiculoEsperaAQueTermineYLuegoSale() {
        // Llegó al cliente antes del bloqueo y lo atiende durante una hora: el nodo se bloquea mientras está ahí.
        Instant finDeLaAtencion = salida.plus(Duration.ofHours(1));

        RoadPath vuelta = roadNetwork.shortestPath(cliente, almacen, finDeLaAtencion, porTramo,
                List.of(bloqueoDelCliente)).orElseThrow(() -> new AssertionError("quedó sin camino"));

        assertFalse(vuelta.legs().getFirst().departsAt().isBefore(finBloqueo), "salió con el nodo bloqueado");
        assertEquals(5, vuelta.distanceKm(), "sale por el camino directo al terminar el bloqueo");
    }

    @Test
    void sinBloqueoVigenteEnElNodoSaleDeInmediato() {
        Instant despuesDelBloqueo = finBloqueo.plus(Duration.ofMinutes(1));

        RoadPath vuelta = roadNetwork.shortestPath(cliente, almacen, despuesDelBloqueo, porTramo,
                List.of(bloqueoDelCliente)).orElseThrow();

        assertEquals(despuesDelBloqueo, vuelta.legs().getFirst().departsAt());
    }

    private boolean entraAlBloqueoMientrasRige(RoadLeg tramo) {
        return bloqueoDelCliente.blockedNodes().contains(tramo.to())
                && bloqueoDelCliente.overlaps(tramo.departsAt(), tramo.arrivesAt());
    }
}
