package com.pucp.paqrap.modulos.flota.dto;

import jakarta.validation.constraints.NotNull;

public record CambiarDisponibilidadRequest(@NotNull Boolean disponible) {
}
