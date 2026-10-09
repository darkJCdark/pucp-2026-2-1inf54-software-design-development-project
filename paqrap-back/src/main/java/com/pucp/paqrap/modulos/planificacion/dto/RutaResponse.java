package com.pucp.paqrap.modulos.planificacion.dto;

import java.time.Instant;
import java.util.List;

/** Ruta de un vehículo dentro del plan. {@code llegadaFinal} es null si la ruta no pudo programarse. */
public record RutaResponse(
        String id,
        String vehiculoId,
        String tipoVehiculo,
        Instant salida,
        Instant llegadaFinal,
        double distanciaKm,
        double costo,
        List<ParadaResponse> paradas
) {
}
