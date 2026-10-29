package com.pucp.paqrap.modulos.escenarios.dto;

import com.pucp.paqrap.modulos.escenarios.entity.ScenarioStatus;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;

import java.math.BigDecimal;
import java.time.Instant;

/** Resultado final consolidado y persistido de una ejecución de escenario. */
public record ResultadoEscenarioResponse(long executionId, ScenarioType tipo, ScenarioStatus estado,
                                         Instant inicioSimulado, Instant finSimulado,
                                         Instant inicioReal, Instant finReal, Instant colapsoEn,
                                         int totalOrders, int deliveredOrders, int onTimeOrders, int undeliveredOrders,
                                         BigDecimal totalDistanceKm, BigDecimal totalCost, long computationTimeMs,
                                         int breakdownCount, int roadBlockCount, int replanningCount) {
}
