package com.pucp.paqrap.modulos.flota.dto;

import com.pucp.paqrap.modulos.flota.persistence.MaintenanceDayEntity;

import java.time.LocalDate;

public record MantenimientoResponse(String vehiculoId, LocalDate fecha) {

    public static MantenimientoResponse de(MaintenanceDayEntity entity) {
        return new MantenimientoResponse(entity.getVehicleId(), entity.getMaintenanceDate());
    }
}
