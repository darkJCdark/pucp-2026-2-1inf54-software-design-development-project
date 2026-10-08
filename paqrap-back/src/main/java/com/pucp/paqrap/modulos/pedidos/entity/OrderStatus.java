package com.pucp.paqrap.modulos.pedidos.entity;

/** Ciclo de vida de un pedido (CU-04); coincide con {@code chk_orders_status}. */
public enum OrderStatus {
    REGISTERED,
    ASSIGNED,
    IN_TRANSIT,
    DELIVERED
}
