package com.pucp.paqrap.modulos.flota.dto;

import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import jakarta.validation.constraints.NotNull;

/** Solo AVAILABLE o UNAVAILABLE: IN_ROUTE lo asigna la simulación. */
public record CambiarEstadoRequest(@NotNull VehicleOperationalStatus estado) {
}
