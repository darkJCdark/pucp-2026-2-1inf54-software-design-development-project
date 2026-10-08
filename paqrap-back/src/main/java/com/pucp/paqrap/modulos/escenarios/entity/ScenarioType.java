package com.pucp.paqrap.modulos.escenarios.entity;

import java.time.Duration;
import java.util.Optional;

/** Escenarios de operación; coinciden con {@code chk_scenario_execution_type}. */
public enum ScenarioType {
    /** Operación en tiempo real, sin fin: corre hasta que el operario la detiene. */
    DAY_TO_DAY(null),
    /** Cinco días consecutivos de operación, acelerados. */
    FIVE_DAY(Duration.ofDays(5)),
    /** Corre acelerado hasta que el planificador ya no puede cumplir los plazos. */
    COLLAPSE(null);

    private final Duration duracionSimulada;

    ScenarioType(Duration duracionSimulada) {
        this.duracionSimulada = duracionSimulada;
    }

    /** Duración simulada fija del escenario; vacía si no termina por tiempo. */
    public Optional<Duration> duracionSimulada() {
        return Optional.ofNullable(duracionSimulada);
    }
}
