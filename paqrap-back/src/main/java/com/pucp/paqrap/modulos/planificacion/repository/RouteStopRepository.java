package com.pucp.paqrap.modulos.planificacion.repository;

import com.pucp.paqrap.modulos.planificacion.persistence.RouteStopEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteStopRepository extends JpaRepository<RouteStopEntity, Long> {

    List<RouteStopEntity> findByRouteRouteIdOrderBySequenceNoAsc(long routeId);
}
