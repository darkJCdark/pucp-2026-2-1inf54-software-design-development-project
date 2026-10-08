package com.pucp.paqrap.modulos.planificacion.dto;

/** Violación detectada por el evaluador del plan. {@code tipo} es el nombre de PlanViolationType. */
public record ViolacionResponse(String tipo, String rutaId, String detalle) {
}
