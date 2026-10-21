package com.pucp.paqrap.modulos.pedidos.entity;

import java.time.Instant;

/** Evento de aplicación: se registró un pedido manualmente (Día a Día); dispara una replanificación (CU-07). */
public record PedidoRegistrado(String pedidoId, Instant registradoEn) {
}
