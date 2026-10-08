package com.pucp.paqrap.modulos.escenarios.dto;

import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Configura una ejecución (CU-09). {@code inicioSimulado} es obligatorio en FIVE_DAY y COLLAPSE (desde qué fecha de
 * los datos históricos se simula) y no se envía en DAY_TO_DAY, que siempre parte de la hora actual.
 */
public record CrearEscenarioRequest(@NotNull ScenarioType tipo, Instant inicioSimulado) {
}
