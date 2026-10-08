package com.pucp.paqrap.modulos.incidencias.dto;

import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Avería de una unidad (CU-06). Opcionales: {@code ocurridaEn} (por defecto, ahora), {@code x}/{@code y} (por
 * defecto, la posición actual del vehículo) y {@code ejecucionId} (escenario en el que ocurrió).
 */
public record RegistrarAveriaRequest(
        @NotBlank String vehiculoId,
        @NotNull BreakdownType tipo,
        Instant ocurridaEn,
        @Min(0) @Max(70) Integer x,
        @Min(0) @Max(50) Integer y,
        Long ejecucionId) {
}
