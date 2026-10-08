package com.pucp.paqrap.modulos.escenarios.dto;

import com.pucp.paqrap.modulos.escenarios.entity.ScenarioStatus;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;

import java.time.Instant;

/**
 * Ejecución de un escenario. Las horas {@code *Simulado} son del reloj de la simulación; {@code instanteSimulado}
 * solo tiene valor mientras la ejecución está activa. Las horas {@code *Real} son del sistema.
 */
public record EscenarioResponse(Long id, ScenarioType tipo, ScenarioStatus estado, double factorAceleracion,
                                Instant inicioSimulado, Instant instanteSimulado, Instant finSimulado,
                                Instant colapsoEn, Instant inicioReal, Instant finReal) {

    public static EscenarioResponse de(ScenarioExecutionEntity entity, double factor, Instant instanteSimulado) {
        return new EscenarioResponse(entity.getExecutionId(), entity.getScenarioType(), entity.getStatus(), factor,
                entity.getSimulationStartedAt(), instanteSimulado, entity.getSimulationFinishedAt(),
                entity.getCollapseAt(), entity.getStartedAt(), entity.getFinishedAt());
    }
}
