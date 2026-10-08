package com.pucp.paqrap.modulos.flota.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Parámetros configurables de un tipo de vehículo (CU-09). Los límites siguen las columnas de la BD. */
public record ActualizarTipoVehiculoRequest(
        @NotNull @Positive @Max(65_535) Integer capacidadPaquetes,
        @NotNull @Positive @Digits(integer = 4, fraction = 2) BigDecimal velocidadKmh,
        @NotNull @PositiveOrZero @Digits(integer = 6, fraction = 2) BigDecimal costoPorKm) {
}
