package com.pucp.paqrap.modulos.flota.persistence;

import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;

/** Valores permitidos por {@code chk_vehicles_operational_status}; el dominio llama OUT_OF_SERVICE a UNAVAILABLE. */
public enum VehicleOperationalStatus {
    AVAILABLE(VehicleStatus.AVAILABLE),
    IN_ROUTE(VehicleStatus.IN_ROUTE),
    UNAVAILABLE(VehicleStatus.OUT_OF_SERVICE);

    private final VehicleStatus domainStatus;

    VehicleOperationalStatus(VehicleStatus domainStatus) {
        this.domainStatus = domainStatus;
    }

    public VehicleStatus toDomain() {
        return domainStatus;
    }
}
