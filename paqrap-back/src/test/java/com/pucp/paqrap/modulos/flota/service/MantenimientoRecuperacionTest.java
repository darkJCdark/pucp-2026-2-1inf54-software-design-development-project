package com.pucp.paqrap.modulos.flota.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-35: recuperación tras mantenimiento. La unidad no está disponible durante todo el día programado
 * (00:00 a 23:59, hora de Lima) y vuelve a estar disponible al día siguiente.
 */
class MantenimientoRecuperacionTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate DIA = LocalDate.of(2026, 1, 15);

    private final MaintenanceCalendar calendario =
            new MaintenanceCalendar(LIMA, List.of(new MaintenanceDay("TM01", DIA)));

    private static Instant lima(int dia, int hora, int minuto) {
        return LocalDate.of(2026, 1, dia).atTime(hora, minuto).atZone(LIMA).toInstant();
    }

    @Test
    void laUnidadNoEstaDisponibleTodoElDiaProgramado() {
        assertTrue(calendario.isUnavailable("TM01", lima(15, 0, 0)));
        assertTrue(calendario.isUnavailable("TM01", lima(15, 12, 0)));
        assertTrue(calendario.isUnavailable("TM01", lima(15, 23, 59)));
    }

    @Test
    void alDiaSiguienteLaUnidadVuelveAEstarDisponible() {
        assertFalse(calendario.isUnavailable("TM01", lima(16, 0, 0)));
        assertFalse(calendario.isUnavailable("TM01", lima(16, 8, 0)));
    }

    @Test
    void elDiaAnteriorNoSeAfecta() {
        assertFalse(calendario.isUnavailable("TM01", lima(14, 23, 59)));
    }

    @Test
    void unTramoQueEmpiezaAlTerminarElMantenimientoYaPuedePlanificarse() {
        assertFalse(calendario.isUnavailableDuring("TM01", lima(16, 0, 0), lima(16, 6, 0)));
    }

    @Test
    void unTramoQueTerminaAlEmpezarElMantenimientoNoSeSolapa() {
        assertFalse(calendario.isUnavailableDuring("TM01", lima(14, 18, 0), lima(15, 0, 0)));
    }

    @Test
    void unTramoQueCruzaElInicioDelMantenimientoSiSeSolapa() {
        assertTrue(calendario.isUnavailableDuring("TM01", lima(14, 22, 0), lima(15, 2, 0)));
    }

    @Test
    void otraUnidadNoSeAfecta() {
        assertFalse(calendario.isUnavailable("TA01", lima(15, 12, 0)));
    }
}
