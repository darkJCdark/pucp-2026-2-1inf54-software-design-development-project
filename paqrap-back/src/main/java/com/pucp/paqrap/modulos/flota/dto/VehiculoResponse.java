package com.pucp.paqrap.modulos.flota.dto;

import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;

import java.time.Instant;

/** Vehículo con su último estado operativo conocido; {@code disponibleDesde} nulo = ya disponible. */
public record VehiculoResponse(String id, VehicleType tipo, VehicleOperationalStatus estado, int x, int y,
                               int cargaActual, Instant disponibleDesde) {

    public static VehiculoResponse de(VehicleEntity entity) {
        return new VehiculoResponse(entity.getVehicleId(), entity.getVehicleType(), entity.getOperationalStatus(),
                entity.getCurrentX(), entity.getCurrentY(), entity.getCurrentLoad(), entity.getAvailableAt());
    }
}
