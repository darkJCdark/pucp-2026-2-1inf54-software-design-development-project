package com.pucp.paqrap.modulos.planificacion.persistence;

import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Snapshot inmutable de un ciclo de planificaci?n de una ejecuci?n. */
@Entity
@Table(name = "plans")
public class PlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Long planId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private ScenarioExecutionEntity execution;

    @Column(name = "plan_type", nullable = false, length = 20)
    private String planType;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "feasible", nullable = false)
    private boolean feasible;

    @Column(name = "total_distance_km", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDistanceKm;

    @Column(name = "total_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCost;

    protected PlanEntity() {
    }

    public PlanEntity(ScenarioExecutionEntity execution, String planType, boolean feasible,
                      BigDecimal totalDistanceKm, BigDecimal totalCost) {
        this.execution = execution;
        this.planType = planType;
        this.feasible = feasible;
        this.totalDistanceKm = totalDistanceKm;
        this.totalCost = totalCost;
    }

    public Long getPlanId() { return planId; }
    public ScenarioExecutionEntity getExecution() { return execution; }
    public String getPlanType() { return planType; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isFeasible() { return feasible; }
    public BigDecimal getTotalDistanceKm() { return totalDistanceKm; }
    public BigDecimal getTotalCost() { return totalCost; }
}
