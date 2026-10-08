package com.pucp.paqrap.modulos.incidencias.dto;

import com.pucp.paqrap.modulos.incidencias.entity.BreakdownResolution;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
import com.pucp.paqrap.modulos.incidencias.persistence.BreakdownEventEntity;

import java.time.Instant;

/**
 * Avería registrada con sus consecuencias calculadas por las reglas del curso: hasta cuándo la unidad no puede
 * planificarse y, para averías intermedias y mayores, cuándo es remolcada al almacén central (null en las menores).
 */
public record AveriaResponse(Long id, Long ejecucionId, String vehiculoId, BreakdownType tipo, Instant ocurridaEn,
                             int x, int y, Instant indisponibleHasta, Instant regresaAlCentralEn) {

    public static AveriaResponse de(BreakdownEventEntity entity, BreakdownResolution resolucion) {
        return new AveriaResponse(entity.getBreakdownId(), entity.getExecutionId(), entity.getVehicleId(),
                entity.getBreakdownType(), entity.getOccurredAt(), entity.getLocationX(), entity.getLocationY(),
                resolucion.unavailableUntil(), resolucion.returnsToCentralAt());
    }
}
