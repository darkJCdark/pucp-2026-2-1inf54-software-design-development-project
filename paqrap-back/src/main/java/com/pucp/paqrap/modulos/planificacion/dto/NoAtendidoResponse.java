package com.pucp.paqrap.modulos.planificacion.dto;

import java.time.Instant;

/** Pedido que el planificador no logró atender. */
public record NoAtendidoResponse(String pedidoId, int paquetes, Instant plazo) {
}
