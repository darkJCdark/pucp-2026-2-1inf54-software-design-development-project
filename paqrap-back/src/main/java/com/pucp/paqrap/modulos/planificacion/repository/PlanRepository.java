package com.pucp.paqrap.modulos.planificacion.repository;

import com.pucp.paqrap.modulos.planificacion.persistence.PlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanRepository extends JpaRepository<PlanEntity, Long> {

    List<PlanEntity> findByExecutionExecutionIdOrderByPlanIdAsc(long executionId);
}
