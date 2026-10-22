package com.pucp.paqrap.modulos.planificacion.repository;

import com.pucp.paqrap.modulos.planificacion.persistence.RouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteRepository extends JpaRepository<RouteEntity, Long> {

    List<RouteEntity> findByPlanPlanIdOrderByRouteIdAsc(long planId);
}
