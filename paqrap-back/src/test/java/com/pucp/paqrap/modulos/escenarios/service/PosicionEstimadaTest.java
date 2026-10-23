package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.modulos.planificacion.dto.ParadaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.RutaResponse;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * La posición estimada debe coincidir con la que dibuja el front (calcularPosicionVehiculoAnimada). Ruta de prueba:
 * sale de (27,14) a las 10:00; P1 en (30,14) llega 10:30 y termina 11:30; P2 en (30,18) llega 12:00 y termina 13:00.
 */
class PosicionEstimadaTest {

    private static Instant hora(String hhmm) {
        return Instant.parse("2026-01-01T" + hhmm + ":00Z");
    }

    private static RutaResponse ruta(String vehiculoId) {
        return new RutaResponse("SA-" + vehiculoId, vehiculoId, "CAR", hora("10:00"), hora("13:00"), 7.0, 56.0, List.of(
                new ParadaResponse(1, ParadaResponse.ENTREGA, "P1", null, 30, 14, 5, hora("10:30"), hora("11:30"), 7, 2),
                new ParadaResponse(2, ParadaResponse.ENTREGA, "P2", null, 30, 18, 2, hora("12:00"), hora("13:00"), 2, 0)));
    }

    /** Mismo cálculo que horizontalPrimeroParaVehiculo del front, en aritmética de 32 bits. */
    private static boolean horizontalPrimeroComoElFront(String id) {
        int hash = 0;
        for (int i = 0; i < id.length(); i++) {
            hash = hash * 31 + id.charAt(i);
        }
        return Math.abs(hash) % 2 == 0;
    }

    @Test
    void decideElPrimerEjeIgualQueElFront() {
        for (String id : List.of("TA01", "TA02", "TA10", "TM01", "TM07", "TM15", "TB01", "TB12")) {
            assertEquals(horizontalPrimeroComoElFront(id), EjecutorPlanService.horizontalPrimero(id), id);
        }
    }

    @Test
    void recorreElCaminoEnLSegunElEjeYRedondeaAlNodo() {
        Location origen = new Location(27, 14);
        Location destino = new Location(30, 18);

        // 7 km en total; a la mitad lleva 3,5 km.
        assertEquals(new Location(30, 15), EjecutorPlanService.interpolarOrtogonal(origen, destino, 0.5, true));
        assertEquals(new Location(27, 18), EjecutorPlanService.interpolarOrtogonal(origen, destino, 0.5, false));
        assertEquals(origen, EjecutorPlanService.interpolarOrtogonal(origen, destino, 0, true));
        assertEquals(destino, EjecutorPlanService.interpolarOrtogonal(origen, destino, 1, false));
    }

    @Test
    void aMitadDelPrimerTramoAvanzaDesdeSuPosicionConfirmada() {
        // 10:15 = mitad de 10:00-10:30: 1,5 de 3 km hacia P1 (solo eje X).
        assertEquals(new Location(29, 14), EjecutorPlanService.posicionEnRuta(ruta("TA01"), 27, 14, hora("10:15")));
    }

    @Test
    void mientrasHaceElServicioSeQuedaEnLaParada() {
        assertEquals(new Location(30, 14), EjecutorPlanService.posicionEnRuta(ruta("TA01"), 27, 14, hora("11:00")));
    }

    @Test
    void entreParadasSaleDelFinDelServicioAnterior() {
        // 11:45 = mitad de 11:30 (fin en P1) - 12:00 (llegada a P2): 2 de 4 km hacia arriba.
        assertEquals(new Location(30, 16), EjecutorPlanService.posicionEnRuta(ruta("TA01"), 30, 14, hora("11:45")));
    }

    @Test
    void despuesDeLaUltimaParadaUsaLaPosicionConfirmada() {
        assertEquals(new Location(30, 18), EjecutorPlanService.posicionEnRuta(ruta("TA01"), 30, 18, hora("13:30")));
    }
}
