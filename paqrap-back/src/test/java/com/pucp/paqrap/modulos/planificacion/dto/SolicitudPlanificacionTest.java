package com.pucp.paqrap.modulos.planificacion.dto;

import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolicitudPlanificacionTest {

    private static final Instant HORA = Instant.parse("2026-09-09T12:00:00Z");

    private static Order pedido(String id) {
        return new Order(id, new Location(31, 14), 4, HORA, HORA.plusSeconds(3600));
    }

    @Test
    void modoNuloEsRechazado() {
        assertThrows(NullPointerException.class,
                () -> new SolicitudPlanificacion(null, HORA, List.of(), List.of(), null));
    }

    @Test
    void horaNulaEsRechazada() {
        assertThrows(NullPointerException.class,
                () -> new SolicitudPlanificacion(ModoOperacion.DIA_A_DIA, null, List.of(), List.of(), null));
    }

    @Test
    void listasNulasSeConvierteEnListasVacias() {
        SolicitudPlanificacion solicitud =
                new SolicitudPlanificacion(ModoOperacion.DIA_A_DIA, HORA, null, null, null);

        assertTrue(solicitud.pedidosPendientes().isEmpty());
        assertTrue(solicitud.bloqueosActivos().isEmpty());
    }

    @Test
    void conservaModoHoraYContenidoDeLasListas() {
        RoadBlock bloqueo = new RoadBlock(HORA, HORA.plusSeconds(3600),
                List.of(new Location(28, 14), new Location(31, 14)));
        SolicitudPlanificacion solicitud = new SolicitudPlanificacion(
                ModoOperacion.COLAPSO, HORA, List.of(pedido("P01"), pedido("P02")), List.of(bloqueo), 99L);

        assertEquals(ModoOperacion.COLAPSO, solicitud.modo());
        assertEquals(HORA, solicitud.horaPlanificacion());
        assertEquals(List.of(pedido("P01"), pedido("P02")), solicitud.pedidosPendientes());
        assertEquals(List.of(bloqueo), solicitud.bloqueosActivos());
        assertEquals(Long.valueOf(99L), solicitud.semilla());
    }

    @Test
    void semillaEsOpcional() {
        SolicitudPlanificacion sinSemilla =
                new SolicitudPlanificacion(ModoOperacion.SIMULACION_5D, HORA, List.of(), List.of(), null);

        assertNull(sinSemilla.semilla());
    }

    @Test
    void hacerCopiaDefensivaDeLasListas() {
        List<Order> original = new ArrayList<>(List.of(pedido("P01")));
        SolicitudPlanificacion solicitud =
                new SolicitudPlanificacion(ModoOperacion.DIA_A_DIA, HORA, original, List.of(), null);

        original.add(pedido("P02"));

        assertEquals(1, solicitud.pedidosPendientes().size());
        assertThrows(UnsupportedOperationException.class, () -> solicitud.pedidosPendientes().add(pedido("P03")));
        assertThrows(UnsupportedOperationException.class, () -> solicitud.bloqueosActivos().clear());
    }

    @Test
    void enumDeModosTieneLosTresModosDefinidos() {
        assertEquals(List.of(ModoOperacion.DIA_A_DIA, ModoOperacion.SIMULACION_5D, ModoOperacion.COLAPSO),
                List.of(ModoOperacion.values()));
    }
}
