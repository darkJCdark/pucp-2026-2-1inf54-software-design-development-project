package com.pucp.paqrap.modulos.planificacion.dto;

import java.time.Instant;

/**
 * Pedido vencido o que llegará tarde según el plan.
 *
 * @param llegadaPlanificada última llegada planificada a su destino (null si el plan no lo atiende)
 * @param minutosAtraso      minutos entre el plazo y la hora auditada (vencidos) o la llegada planificada (atrasados)
 * @param paquetesPendientes paquetes aún no entregados a la hora auditada
 */
public record PedidoAtrasadoResponse(String pedidoId, Instant plazo, Instant llegadaPlanificada, long minutosAtraso,
                                     int paquetesPendientes) {
}
