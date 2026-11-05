package com.pucp.paqrap.modulos.planificacion.dto;

import java.time.Instant;
import java.util.Objects;

/** Una calle real de RoadNetwork. Los huecos entre tramos representan esperas, no movimiento.
 * {@code ordenParada} identifica la parada a la que conduce este tramo. */
public record TramoRutaResponse(
        int ordenParada,
        int desdeX,
        int desdeY,
        int hastaX,
        int hastaY,
        Instant salida,
        Instant llegada
) {
    public TramoRutaResponse {
        Objects.requireNonNull(salida, "salida is required");
        Objects.requireNonNull(llegada, "llegada is required");
        if (ordenParada < 1 || Math.abs(hastaX - desdeX) + Math.abs(hastaY - desdeY) != 1) {
            throw new IllegalArgumentException("Un tramo debe identificar una parada y recorrer una calle adyacente");
        }
        if (!llegada.isAfter(salida)) {
            throw new IllegalArgumentException("Un tramo debe tener tiempo de viaje positivo");
        }
    }
}
