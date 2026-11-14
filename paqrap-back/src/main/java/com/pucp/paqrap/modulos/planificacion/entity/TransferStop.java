package com.pucp.paqrap.modulos.planificacion.entity;

import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.redvial.entity.Location;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Parada sincronizada donde dos vehículos transfieren físicamente paquetes
 * de un pedido. La transferencia solo se materializa cuando ambas unidades
 * llegan al mismo nodo y el ejecutor procesa el encuentro exactamente una vez.
 */
public record TransferStop(
        String transferId,
        Order order,
        int packages,
        String counterpartyVehicleId,
        TransferDirection direction,
        Location location,
        Instant meetingAt
) implements RouteStop {

    /** Tiempo operacional de comprobación y manipulación de la carga. */
    public static final Duration SERVICE_TIME = Duration.ofMinutes(5);

    public TransferStop {
        transferId = requireText(transferId, "transferId");
        Objects.requireNonNull(order, "order is required");
        counterpartyVehicleId = requireText(counterpartyVehicleId, "counterpartyVehicleId");
        Objects.requireNonNull(direction, "direction is required");
        Objects.requireNonNull(location, "location is required");
        Objects.requireNonNull(meetingAt, "meetingAt is required");
        if (packages <= 0 || packages > order.packages()) {
            throw new IllegalArgumentException("Transfer packages must be within the order quantity");
        }
    }

    public boolean donates() {
        return direction == TransferDirection.DONATE;
    }

    public boolean receives() {
        return direction == TransferDirection.RECEIVE;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
