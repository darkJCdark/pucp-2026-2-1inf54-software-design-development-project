package com.pucp.paqrap.modulos.escenarios.entity;

/** Ciclo de vida de una ejecución; coincide con {@code chk_scenario_execution_status}. */
public enum ScenarioStatus {
    CREATED,
    RUNNING,
    PAUSED,
    /** Detenido por el operario. */
    STOPPED,
    /** Terminó por tiempo (5D). */
    COMPLETED,
    /** El planificador ya no pudo cumplir los plazos (escenario de colapso). */
    COLLAPSED,
    /** Interrumpido por un error o por un reinicio del backend. */
    FAILED;

    public boolean estaActivo() {
        return this == RUNNING || this == PAUSED;
    }

    public boolean estaTerminado() {
        return this == STOPPED || this == COMPLETED || this == COLLAPSED || this == FAILED;
    }
}
