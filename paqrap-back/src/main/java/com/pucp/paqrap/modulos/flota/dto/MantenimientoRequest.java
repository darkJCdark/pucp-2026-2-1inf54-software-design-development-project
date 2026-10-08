package com.pucp.paqrap.modulos.flota.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record MantenimientoRequest(@NotBlank String vehiculoId, @NotNull LocalDate fecha) {
}
