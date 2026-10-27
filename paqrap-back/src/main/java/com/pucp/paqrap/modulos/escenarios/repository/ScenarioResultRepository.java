package com.pucp.paqrap.modulos.escenarios.repository;

import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioResultEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScenarioResultRepository extends JpaRepository<ScenarioResultEntity, Long> {

    @EntityGraph(attributePaths = "execution")
    Optional<ScenarioResultEntity> findWithExecutionByExecutionId(Long executionId);
}
