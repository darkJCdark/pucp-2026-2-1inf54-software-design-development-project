package com.pucp.paqrap.modulos.escenarios.persistence;

import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioStatus;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Set;

/**
 * Fila de {@code scenario_executions}. Las horas {@code started/finished} son reales; las {@code simulation_*} son
 * del reloj simulado. Las transiciones de estado válidas se controlan aquí.
 */
@Entity
@Table(name = "scenario_executions")
public class ScenarioExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "execution_id")
    private Long executionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "scenario_type", nullable = false, length = 20)
    private ScenarioType scenarioType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private ScenarioStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "simulation_started_at", nullable = false)
    private Instant simulationStartedAt;

    @Column(name = "simulation_finished_at")
    private Instant simulationFinishedAt;

    @Column(name = "collapse_at")
    private Instant collapseAt;

    protected ScenarioExecutionEntity() {
    }

    public ScenarioExecutionEntity(ScenarioType scenarioType, Instant simulationStartedAt) {
        this.scenarioType = scenarioType;
        this.simulationStartedAt = simulationStartedAt;
        this.status = ScenarioStatus.CREATED;
    }

    public void iniciar(Instant ahoraReal) {
        transicion(Set.of(ScenarioStatus.CREATED), ScenarioStatus.RUNNING, "iniciar");
        this.startedAt = ahoraReal;
    }

    public void pausar() {
        transicion(Set.of(ScenarioStatus.RUNNING), ScenarioStatus.PAUSED, "pausar");
    }

    public void reanudar() {
        transicion(Set.of(ScenarioStatus.PAUSED), ScenarioStatus.RUNNING, "reanudar");
    }

    /** Cierra la ejecución con un estado final (STOPPED, COMPLETED, COLLAPSED o FAILED). */
    public void terminar(ScenarioStatus estadoFinal, Instant ahoraReal, Instant ahoraSimulado) {
        if (!estadoFinal.estaTerminado()) {
            throw new IllegalArgumentException(estadoFinal + " no es un estado final");
        }
        Set<ScenarioStatus> origen = estadoFinal == ScenarioStatus.FAILED
                ? Set.of(ScenarioStatus.CREATED, ScenarioStatus.RUNNING, ScenarioStatus.PAUSED)
                : Set.of(ScenarioStatus.RUNNING, ScenarioStatus.PAUSED);
        transicion(origen, estadoFinal, "terminar como " + estadoFinal);
        this.finishedAt = ahoraReal;
        this.simulationFinishedAt = ahoraSimulado;
        if (estadoFinal == ScenarioStatus.COLLAPSED) {
            this.collapseAt = ahoraSimulado;
        }
    }

    private void transicion(Set<ScenarioStatus> origenesValidos, ScenarioStatus destino, String accion) {
        if (!origenesValidos.contains(status)) {
            throw new ReglaNegocioException("No se puede " + accion + " la ejecución " + executionId
                    + " porque está en estado " + status);
        }
        this.status = destino;
    }

    public Long getExecutionId() { return executionId; }
    public ScenarioType getScenarioType() { return scenarioType; }
    public ScenarioStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getSimulationStartedAt() { return simulationStartedAt; }
    public Instant getSimulationFinishedAt() { return simulationFinishedAt; }
    public Instant getCollapseAt() { return collapseAt; }
}
