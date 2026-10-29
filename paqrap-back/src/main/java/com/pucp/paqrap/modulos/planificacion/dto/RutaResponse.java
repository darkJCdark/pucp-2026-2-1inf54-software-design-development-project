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
        List<ParadaResponse> paradas,
        String almacenInicialId,
        Long planPersistedId,
        Long routePersistedId
) {
    /** Constructor legado para clientes que todavía no reciben el almacén de origen. */
    public RutaResponse(String id, String vehiculoId, String tipoVehiculo, Instant salida, Instant llegadaFinal,
                        double distanciaKm, double costo, List<ParadaResponse> paradas) {
        this(id, vehiculoId, tipoVehiculo, salida, llegadaFinal, distanciaKm, costo, paradas, null, null, null);
    }

    /** Constructor compatible con la extensión R4 previa. */
    public RutaResponse(String id, String vehiculoId, String tipoVehiculo, Instant salida, Instant llegadaFinal,
                        double distanciaKm, double costo, List<ParadaResponse> paradas, String almacenInicialId) {
        this(id, vehiculoId, tipoVehiculo, salida, llegadaFinal, distanciaKm, costo, paradas, almacenInicialId,
                null, null);
    }
}
