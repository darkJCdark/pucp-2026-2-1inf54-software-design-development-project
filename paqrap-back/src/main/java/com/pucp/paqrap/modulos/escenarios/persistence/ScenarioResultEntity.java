package com.pucp.paqrap.modulos.escenarios.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Resultado final inmutable de una ejecución de escenario. */
@Entity
@Table(name = "scenario_results")
public class ScenarioResultEntity {

    @Id
    @Column(name = "execution_id")
    private Long executionId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "execution_id", nullable = false)
    private ScenarioExecutionEntity execution;

    @Column(name = "total_orders", nullable = false)
    private int totalOrders;

    @Column(name = "delivered_orders", nullable = false)
    private int deliveredOrders;

    @Column(name = "on_time_orders", nullable = false)
    private int onTimeOrders;

    @Column(name = "undelivered_orders", nullable = false)
    private int undeliveredOrders;

    @Column(name = "total_distance_km", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDistanceKm;

    @Column(name = "total_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "computation_time_ms", nullable = false)
    private long computationTimeMs;

    @Column(name = "breakdown_count", nullable = false)
    private int breakdownCount;

    @Column(name = "road_block_count", nullable = false)
    private int roadBlockCount;

    @Column(name = "replanning_count", nullable = false)
    private int replanningCount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected ScenarioResultEntity() {
    }

    public ScenarioResultEntity(ScenarioExecutionEntity execution, int totalOrders, int deliveredOrders,
                                int onTimeOrders, int undeliveredOrders, BigDecimal totalDistanceKm, BigDecimal totalCost,
                                long computationTimeMs, int breakdownCount, int roadBlockCount, int replanningCount) {
        this.execution = execution;
        this.totalOrders = totalOrders;
        this.deliveredOrders = deliveredOrders;
        this.onTimeOrders = onTimeOrders;
        this.undeliveredOrders = undeliveredOrders;
        this.totalDistanceKm = totalDistanceKm;
        this.totalCost = totalCost;
        this.computationTimeMs = computationTimeMs;
        this.breakdownCount = breakdownCount;
        this.roadBlockCount = roadBlockCount;
        this.replanningCount = replanningCount;
    }

    public Long getExecutionId() { return executionId; }
    public ScenarioExecutionEntity getExecution() { return execution; }
    public int getTotalOrders() { return totalOrders; }
    public int getDeliveredOrders() { return deliveredOrders; }
    public int getOnTimeOrders() { return onTimeOrders; }
    public int getUndeliveredOrders() { return undeliveredOrders; }
    public BigDecimal getTotalDistanceKm() { return totalDistanceKm; }
    public BigDecimal getTotalCost() { return totalCost; }
    public long getComputationTimeMs() { return computationTimeMs; }
    public int getBreakdownCount() { return breakdownCount; }
    public int getRoadBlockCount() { return roadBlockCount; }
    public int getReplanningCount() { return replanningCount; }
    public Instant getCreatedAt() { return createdAt; }
}
