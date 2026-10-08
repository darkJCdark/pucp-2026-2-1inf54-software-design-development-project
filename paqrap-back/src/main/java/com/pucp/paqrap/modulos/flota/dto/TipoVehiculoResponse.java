package com.pucp.paqrap.modulos.flota.dto;

import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleTypeParametersEntity;

import java.math.BigDecimal;
import java.time.Instant;

public record TipoVehiculoResponse(VehicleType tipo, String codigoFlota, int capacidadPaquetes,
                                   BigDecimal velocidadKmh, BigDecimal costoPorKm, Instant actualizadoEn) {

    public static TipoVehiculoResponse de(VehicleTypeParametersEntity entity) {
        return new TipoVehiculoResponse(entity.getVehicleType(), entity.getVehicleType().fleetCode(),
                entity.getCapacityPackages(), entity.getSpeedKmh(), entity.getCostPerKm(), entity.getUpdatedAt());
    }
}
