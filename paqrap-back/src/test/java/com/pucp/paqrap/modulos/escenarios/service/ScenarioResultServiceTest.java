package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioResultEntity;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioResultRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class ScenarioResultServiceTest {

    @Autowired private ScenarioResultService service;
    @Autowired private ScenarioResultRepository resultRepository;
    @Autowired private ScenarioExecutionRepository executionRepository;

    @AfterEach
    void limpiar() {
        resultRepository.deleteAll();
        executionRepository.deleteAll();
    }

    @Test
    void persisteMetricasEjecutadasYCalculaPedidosNoEntregados() {
        ScenarioExecutionEntity execution = executionRepository.save(
                new ScenarioExecutionEntity(ScenarioType.FIVE_DAY, Instant.parse("2026-01-01T05:00:00Z")));

        ScenarioResultEntity result = service.persistir(execution.getExecutionId(), 7,
                new EjecutorPlanService.Metricas(3, 2, 9, 12.5, 48.75),
                321, 4, 2, 3);

        assertEquals(execution.getExecutionId(), result.getExecutionId());
        assertEquals(7, result.getTotalOrders());
        assertEquals(3, result.getDeliveredOrders());
        assertEquals(2, result.getOnTimeOrders());
        assertEquals(4, result.getUndeliveredOrders());
        assertEquals(BigDecimal.valueOf(12.5), result.getTotalDistanceKm());
        assertEquals(BigDecimal.valueOf(48.75), result.getTotalCost());
        assertEquals(321, result.getComputationTimeMs());
        assertEquals(4, result.getReplanningCount());
        assertEquals(2, result.getBreakdownCount());
        assertEquals(3, result.getRoadBlockCount());
    }

    @Test
    void rechazaUnSegundoResultadoDeLaMismaEjecucion() {
        ScenarioExecutionEntity execution = executionRepository.save(
                new ScenarioExecutionEntity(ScenarioType.FIVE_DAY, Instant.parse("2026-01-01T05:00:00Z")));
        EjecutorPlanService.Metricas metricas = new EjecutorPlanService.Metricas(0, 0, 0, 0, 0);

        service.persistir(execution.getExecutionId(), 0, metricas, 0, 0, 0, 0);

        assertThrows(IllegalStateException.class,
                () -> service.persistir(execution.getExecutionId(), 0, metricas, 0, 0, 0, 0));
    }
}
