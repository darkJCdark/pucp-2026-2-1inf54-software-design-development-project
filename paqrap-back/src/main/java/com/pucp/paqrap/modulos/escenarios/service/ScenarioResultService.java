package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioResultEntity;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioResultRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Persiste una sola vez las métricas finales conocidas de una ejecución. */
@Service
public class ScenarioResultService {

    private final ScenarioResultRepository resultRepository;
    private final EntityManager entityManager;

    public ScenarioResultService(ScenarioResultRepository resultRepository, EntityManager entityManager) {
        this.resultRepository = resultRepository;
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
}
