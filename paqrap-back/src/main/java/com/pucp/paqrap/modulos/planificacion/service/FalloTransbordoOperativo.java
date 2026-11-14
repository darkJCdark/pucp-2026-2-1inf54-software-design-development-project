package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.redvial.entity.Location;

import java.time.Instant;
import java.util.Objects;

/**
 * Evidencia mínima de un encuentro planificado que no llegó a materializarse.
 *
 * <p>Se transporta desde el ejecutor hasta la replanificación para conservar
 * la propiedad física de los paquetes. El donante sigue siendo responsable de
 * la cantidad indicada hasta que se programe una entrega de recuperación o un
 * nuevo transbordo válido.</p>
 */
public record FalloTransbordoOperativo(
        String transferId,
        String orderId,
        int packages,
        String donorVehicleId,
        String receiverVehicleId,
        Location location,
        Instant occurredAt,
        String cause
) {
    public FalloTransbordoOperativo {
        transferId = requireText(transferId, "transferId");
        orderId = requireText(orderId, "orderId");
        donorVehicleId = requireText(donorVehicleId, "donorVehicleId");
        receiverVehicleId = requireText(receiverVehicleId, "receiverVehicleId");
        Objects.requireNonNull(location, "location is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        cause = cause == null || cause.isBlank() ? "causa no especificada" : cause.trim();
        if (packages <= 0) {
            throw new IllegalArgumentException("packages must be positive");
        }
        if (donorVehicleId.equals(receiverVehicleId)) {
            throw new IllegalArgumentException("donor and receiver must be different vehicles");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
