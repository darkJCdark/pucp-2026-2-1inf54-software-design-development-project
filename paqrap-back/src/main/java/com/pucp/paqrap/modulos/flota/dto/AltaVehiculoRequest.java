package com.pucp.paqrap.modulos.flota.dto;

import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import jakarta.validation.constraints.NotNull;

/** Alta de una unidad: solo el tipo; el id lo genera el sistema (TA03 -> TA04) y nace en el central, disponible. */
public record AltaVehiculoRequest(@NotNull VehicleType tipo) {
}
