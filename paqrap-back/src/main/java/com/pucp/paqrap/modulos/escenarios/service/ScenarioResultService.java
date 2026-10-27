package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.modulos.escenarios.dto.ResultadoEscenarioResponse;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioResultEntity;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioResultRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Persiste y consulta las métricas finales conocidas de una ejecución. */
@Service
public class ScenarioResultService {

    private final ScenarioResultRepository resultRepository;
    private final ScenarioExecutionRepository executionRepository;
    private final EntityManager entityManager;

    public ScenarioResultService(ScenarioResultRepository resultRepository,
                                 ScenarioExecutionRepository executionRepository, EntityManager entityManager) {
        this.resultRepository = resultRepository;
        this.executionRepository = executionRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public ScenarioResultEntity persistir(long executionId, int totalOrders,
                                          EjecutorPlanService.Metricas ejecucion,
                                          long computationTimeMs, int replanningCount,
                                          int breakdownCount, int roadBlockCount) {
        if (resultRepository.existsById(executionId)) {
            throw new IllegalStateException("Ya existe un resultado para la ejecución " + executionId);
        }
        if (totalOrders < 0 || ejecucion.pedidosEntregados() < 0 || ejecucion.entregadosATiempo() < 0
                || ejecucion.pedidosEntregados() > totalOrders
                || ejecucion.entregadosATiempo() > ejecucion.pedidosEntregados()) {
            throw new IllegalStateException("Métricas de pedidos inválidas para la ejecución " + executionId);
        }
        int undeliveredOrders = totalOrders - ejecucion.pedidosEntregados();
        if (ejecucion.distanciaKm() < 0 || ejecucion.costo() < 0 || computationTimeMs < 0
                || replanningCount < 0 || breakdownCount < 0 || roadBlockCount < 0) {
            throw new IllegalArgumentException("Las métricas finales no pueden ser negativas");
        }
        return resultRepository.save(new ScenarioResultEntity(
                entityManager.getReference(ScenarioExecutionEntity.class, executionId), totalOrders,
                ejecucion.pedidosEntregados(), ejecucion.entregadosATiempo(), undeliveredOrders,
                BigDecimal.valueOf(ejecucion.distanciaKm()), BigDecimal.valueOf(ejecucion.costo()),
                computationTimeMs, breakdownCount,
                roadBlockCount, replanningCount));
    }

    /** Consulta el resultado persistido con la ejecución que lo contextualiza; nunca recompone métricas operativas. */
    @Transactional(readOnly = true)
    public ResultadoEscenarioResponse consultarResultado(long executionId) {
        return resultRepository.findWithExecutionByExecutionId(executionId)
                .map(ScenarioResultService::respuesta)
                .orElseGet(() -> {
                    if (executionRepository.findById(executionId).isEmpty()) {
                        throw new RecursoNoEncontradoException("Ejecución", executionId);
                    }
                    throw new ReglaNegocioException("El resultado consolidado de la ejecución " + executionId
                            + " aún no está disponible");
                });
    }

    private static ResultadoEscenarioResponse respuesta(ScenarioResultEntity resultado) {
        ScenarioExecutionEntity ejecucion = resultado.getExecution();
        return new ResultadoEscenarioResponse(ejecucion.getExecutionId(), ejecucion.getScenarioType(),
                ejecucion.getStatus(), ejecucion.getSimulationStartedAt(), ejecucion.getSimulationFinishedAt(),
                ejecucion.getStartedAt(), ejecucion.getFinishedAt(), ejecucion.getCollapseAt(),
                resultado.getTotalOrders(), resultado.getDeliveredOrders(), resultado.getOnTimeOrders(),
                resultado.getUndeliveredOrders(), resultado.getTotalDistanceKm(), resultado.getTotalCost(),
                resultado.getComputationTimeMs(), resultado.getBreakdownCount(), resultado.getRoadBlockCount(),
                resultado.getReplanningCount());
    }
}
