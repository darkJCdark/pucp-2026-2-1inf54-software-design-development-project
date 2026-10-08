package com.pucp.paqrap.modulos.planificacion.dto;

/**
 * Violación auditada de un plan.
 *
 * @param tipo        nombre de PlanViolationType
 * @param descripcion descripción en español del tipo
 * @param rutaId      ruta afectada (null si la violación es del plan completo)
 * @param vehiculoId  vehículo de la ruta afectada (null si no aplica)
 * @param detalle     detalle original del evaluador
 */
public record ViolacionDetalleResponse(String tipo, String descripcion, String rutaId, String vehiculoId,
                                       String detalle) {
}
