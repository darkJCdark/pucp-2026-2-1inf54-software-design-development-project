package com.pucp.paqrap.modulos.flota.entity;
import com.pucp.paqrap.modulos.almacenes.entity.*;
import com.pucp.paqrap.modulos.flota.entity.*;
import com.pucp.paqrap.modulos.flota.service.*;
import com.pucp.paqrap.modulos.incidencias.entity.*;
import com.pucp.paqrap.modulos.incidencias.service.*;
import com.pucp.paqrap.modulos.pedidos.entity.*;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.*;
import com.pucp.paqrap.modulos.planificacion.entity.*;
import com.pucp.paqrap.modulos.redvial.entity.*;
import com.pucp.paqrap.modulos.redvial.service.*;

import java.time.Instant;
import java.util.Objects;

/** Position and operational readiness of one vehicle at a planning instant. */
public record VehicleOperationalState(Vehicle vehicle, VehicleStatus status,
                                      Location location, int carriedPackages, Instant availableAt) {
    public VehicleOperationalState {
        Objects.requireNonNull(vehicle, "vehicle is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(location, "location is required");
        Objects.requireNonNull(availableAt, "availableAt is required");
        if (carriedPackages < 0 || carriedPackages > vehicle.type().capacity()) {
            throw new IllegalArgumentException("Current vehicle load must fit its capacity");
        }
    }

    public VehicleOperationalState(Vehicle vehicle, VehicleStatus status, Location location, Instant availableAt) {
        this(vehicle, status, location, 0, availableAt);
    }

    /** Vehicles in route remain replannable from their current location. */
    public boolean isPlannableAt(Instant instant) {
        return vehicle.available() && status != VehicleStatus.OUT_OF_SERVICE && !instant.isBefore(availableAt);
    }
}
