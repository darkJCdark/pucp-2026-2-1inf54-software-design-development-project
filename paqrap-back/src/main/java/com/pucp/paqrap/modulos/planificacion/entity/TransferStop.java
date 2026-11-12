package com.pucp.paqrap.modulos.planificacion.entity;

import java.time.Duration;
import java.util.Objects;

import com.pucp.paqrap.modulos.redvial.entity.Location;

/**
 * Trasvase: la unidad va hasta donde quedó un vehículo averiado y recibe sus paquetes para entregarlos ella. El
 * transbordo toma {@link #TRANSFER_TIME} (30 min) y suma {@code packages} a la carga de la unidad que rescata.
 *
 * @param fromVehicleId vehículo averiado que entrega su carga
 * @param location      lugar de la avería
 * @param packages      paquetes que pasan de un vehículo al otro
 */
public record TransferStop(String fromVehicleId, Location location, int packages) implements RouteStop {

    public static final Duration TRANSFER_TIME = Duration.ofMinutes(30);

    public TransferStop {
        Objects.requireNonNull(fromVehicleId, "fromVehicleId is required");
        Objects.requireNonNull(location, "location is required");
        if (packages <= 0) {
            throw new IllegalArgumentException("A transfer must move at least one package");
        }
    }
}
