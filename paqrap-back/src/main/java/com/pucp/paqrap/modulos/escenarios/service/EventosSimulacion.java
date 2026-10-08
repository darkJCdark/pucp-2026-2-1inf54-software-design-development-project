package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.modulos.almacenes.service.InventarioService;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.repository.RoadBlockRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Eventos que ocurren dentro de la simulación al avanzar el reloj de (desde, hasta]: la recarga diaria de los
 * almacenes intermedios (23:59:59 en Lima, igual que el dominio de SA) y el inicio de bloqueos viales.
 * Los pedidos no necesitan un evento: el planificador los toma con {@code OrderRepository.pendientesEn}.
 */
@Component
public class EventosSimulacion {

    private static final ZoneId ZONA = ShiftSchedule.DEFAULT_ZONE;
    private static final LocalTime HORA_RECARGA = LocalTime.of(23, 59, 59);

    private final InventarioService inventarioService;
    private final RoadBlockRepository bloqueoRepository;

    public EventosSimulacion(InventarioService inventarioService, RoadBlockRepository bloqueoRepository) {
        this.inventarioService = inventarioService;
        this.bloqueoRepository = bloqueoRepository;
    }

    /** Lo que pasó en el intervalo; {@code bloqueosIniciados > 0} obliga a replanificar. */
    public record Resumen(int recargas, long bloqueosIniciados) {
    }

    public Resumen procesar(long ejecucionId, Instant desde, Instant hasta) {
        int recargas = 0;
        for (LocalDate dia = desde.atZone(ZONA).toLocalDate(); ; dia = dia.plusDays(1)) {
            Instant recarga = dia.atTime(HORA_RECARGA).atZone(ZONA).toInstant();
            if (recarga.isAfter(hasta)) {
                break;
            }
            if (recarga.isAfter(desde)) {
                inventarioService.recargarIntermedios(ejecucionId, recarga);
                recargas++;
            }
        }
        long bloqueos = bloqueoRepository.countByStartsAtGreaterThanAndStartsAtLessThanEqual(desde, hasta);
        return new Resumen(recargas, bloqueos);
    }
}
