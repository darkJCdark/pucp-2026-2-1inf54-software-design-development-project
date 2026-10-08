package com.pucp.paqrap.modulos.escenarios.repository;

import com.pucp.paqrap.modulos.escenarios.entity.ScenarioStatus;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ScenarioExecutionRepository extends JpaRepository<ScenarioExecutionEntity, Long> {

    List<ScenarioExecutionEntity> findByStatusIn(Collection<ScenarioStatus> estados);

    boolean existsByStatusIn(Collection<ScenarioStatus> estados);
}
