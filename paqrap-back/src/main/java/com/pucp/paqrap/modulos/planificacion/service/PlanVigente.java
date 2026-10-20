package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;

import java.util.Optional;

/** Último plan generado para una ejecución; lo consume el ejecutor del plan en cada tick. */
public interface PlanVigente {

    Optional<PlanResponse> ultimoPlan(long ejecucionId);
}
