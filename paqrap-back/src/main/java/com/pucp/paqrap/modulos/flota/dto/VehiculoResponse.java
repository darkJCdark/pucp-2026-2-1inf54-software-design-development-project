package com.pucp.paqrap.modulos.flota.dto;

import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;

public record VehiculoResponse(String id, VehicleType tipo, boolean disponible) {

    public static VehiculoResponse de(VehicleEntity entity) {
        return new VehiculoResponse(entity.getVehicleId(), entity.getVehicleType(), entity.isAvailable());
    }
}
