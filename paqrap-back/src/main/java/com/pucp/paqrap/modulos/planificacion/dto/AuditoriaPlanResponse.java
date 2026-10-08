package com.pucp.paqrap.modulos.planificacion.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Resultado de auditar un plan en un instante.
 *
 * @param ejecucionId        ejecución auditada (null si se auditó un plan suelto)
 * @param factible           true si el evaluador no encontró violaciones
 * @param colapso            true si hay pedidos vencidos sin entregar a la hora auditada o pedidos no atendidos
 * @param violacionesPorTipo cantidad de violaciones por tipo, ordenadas por nombre de tipo
 * @param pedidosVencidos    plazo ya cumplido a la hora auditada y paquetes aún sin entregar
 * @param pedidosAtrasados   el plan los entrega después de su plazo
 * @param pedidosNoAtendidos pedidos con paquetes que el plan no cubre
 */
public record AuditoriaPlanResponse(
        Long ejecucionId,
        Instant hora,
        boolean factible,
        boolean colapso,
        int totalViolaciones,
        Map<String, Integer> violacionesPorTipo,
        List<ViolacionDetalleResponse> violaciones,
        List<PedidoAtrasadoResponse> pedidosVencidos,
        List<PedidoAtrasadoResponse> pedidosAtrasados,
        List<String> pedidosNoAtendidos
) {
}
