package com.pucp.paqrap.modulos.redvial.service;

import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.entity.RoadLeg;
import com.pucp.paqrap.modulos.redvial.entity.RoadPath;
import com.pucp.paqrap.modulos.redvial.entity.StreetSegment;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Indicación del profesor: si un pedido queda dentro de un bloqueo, se llega por la calle perpendicular, se entrega en
 * el nodo y se regresa por la misma calle; no se atraviesa el bloqueo ni se recorre. Bloqueo de (10,15) a (12,15),
 * horizontal, vigente de 12:10 a 15:00; 5 min por calle.
 */
class RoadNetworkNodoBloqueadoTest {

    private final RoadNetwork roadNetwork = new RoadNetwork();
    private final Location almacen = new Location(10, 10);
    private final Duration porTramo = Duration.ofMinutes(5);
    private final Instant salida = Instant.parse("2026-09-10T12:00:00Z");
    private final Instant finBloqueo = salida.plus(Duration.ofHours(3));
    private final RoadBlock bloqueo = new RoadBlock(salida.plus(Duration.ofMinutes(10)), finBloqueo,
            List.of(new Location(10, 15), new Location(12, 15)));

    @Test
    void aUnClienteSobreElBloqueoSeLlegaPorLaPerpendicularSinEsperar() {
        Location cliente = new Location(11, 15); // en medio del bloqueo

        RoadPath ida = roadNetwork.shortestPath(almacen, cliente, salida, porTramo, List.of(bloqueo)).orElseThrow();

        assertEquals(6, ida.distanceKm(), "camino directo, sin esperar al fin del bloqueo");
        assertEquals(salida.plus(Duration.ofMinutes(30)), ida.arrivesAt());
        RoadLeg ultima = ida.legs().getLast();
        assertEquals(new Location(11, 14), ultima.from(), "entra por la calle perpendicular");
        assertTrue(ida.legs().stream().noneMatch(this::recorreElBloqueoMientrasRige));
    }

    @Test
    void noSeAtraviesaElBloqueo() {
        Location origen = new Location(11, 10);
        Location destino = new Location(11, 20); // al otro lado: el camino recto cruza (11,15)

        RoadPath camino = roadNetwork.shortestPath(origen, destino, salida, porTramo, List.of(bloqueo)).orElseThrow();

        assertTrue(camino.legs().stream().noneMatch(this::tocaUnNodoDelBloqueoMientrasRige),
                "no puede pasar por un nodo bloqueado que no es su destino");
        assertTrue(camino.distanceKm() > 10, "rodea el bloqueo");
        assertTrue(camino.arrivesAt().isBefore(finBloqueo), "rodearlo es más rápido que esperar");
    }

    @Test
    void trasEntregarSaleDelBloqueoPorLaMismaCalleQueEntro() {
        Location cliente = new Location(11, 15);
        Instant finDeLaAtencion = salida.plus(Duration.ofMinutes(90));

        RoadPath vuelta = roadNetwork.shortestPath(cliente, new Location(11, 20), finDeLaAtencion, porTramo,
                List.of(bloqueo), new Location(11, 14)).orElseThrow();

        assertEquals(new Location(11, 14), vuelta.legs().getFirst().to(), "regresa por donde entró");
        assertEquals(finDeLaAtencion, vuelta.legs().getFirst().departsAt(), "sale sin esperar");
        assertTrue(vuelta.legs().stream().skip(1).noneMatch(this::tocaUnNodoDelBloqueoMientrasRige));
    }

    @Test
    void sinSaberPorDondeEntroSalePorCualquierPerpendicularPeroNoPorElBloqueo() {
        Location cliente = new Location(11, 15);
        Instant ahora = salida.plus(Duration.ofMinutes(30));

        RoadPath vuelta = roadNetwork.shortestPath(cliente, new Location(11, 20), ahora, porTramo, List.of(bloqueo))
                .orElseThrow();

        assertEquals(new Location(11, 16), vuelta.legs().getFirst().to());
        assertEquals(5, vuelta.distanceKm());
        assertTrue(vuelta.legs().stream().noneMatch(this::recorreElBloqueoMientrasRige));
    }

    @Test
    void elBloqueoNoSeRecorreAunqueElDestinoEsteSobreEl() {
        Location desde = new Location(10, 15); // extremo del bloqueo, ya atendido
        Location cliente = new Location(12, 15); // otro extremo

        RoadPath camino = roadNetwork.shortestPath(desde, cliente, salida.plus(Duration.ofMinutes(20)), porTramo,
                List.of(bloqueo), new Location(10, 14)).orElseThrow();

        assertTrue(camino.legs().stream().noneMatch(this::recorreElBloqueoMientrasRige),
                "no va por las calles del bloqueo");
        assertTrue(camino.distanceKm() > 2);
    }

    @Test
    void sinBloqueoVigenteEnElNodoSaleDeInmediato() {
        Location cliente = new Location(10, 15);
        Instant despuesDelBloqueo = finBloqueo.plus(Duration.ofMinutes(1));

        RoadPath vuelta = roadNetwork.shortestPath(cliente, almacen, despuesDelBloqueo, porTramo,
                List.of(bloqueo)).orElseThrow();

        assertEquals(despuesDelBloqueo, vuelta.legs().getFirst().departsAt());
        assertEquals(5, vuelta.distanceKm());
    }

    private boolean recorreElBloqueoMientrasRige(RoadLeg tramo) {
        return bloqueo.blockedSegments().contains(new StreetSegment(tramo.from(), tramo.to()))
                && bloqueo.overlaps(tramo.departsAt(), tramo.arrivesAt());
    }

    private boolean tocaUnNodoDelBloqueoMientrasRige(RoadLeg tramo) {
        return (bloqueo.blockedNodes().contains(tramo.from()) || bloqueo.blockedNodes().contains(tramo.to()))
                && bloqueo.overlaps(tramo.departsAt(), tramo.arrivesAt());
    }
}
