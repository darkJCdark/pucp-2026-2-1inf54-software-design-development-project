package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.util.Objects;

import com.pucp.paqrap.modulos.pedidos.entity.Order;

/**
 * Cantidad de un pedido que debe insertarse
 * o reinsertarse en una solución.
 *
 * Más adelante será especialmente útil para
 * pedidos fraccionados y operadores destroy/repair.
 */
record DeliveryDemand(
        Order order,
        int packages
) {

    DeliveryDemand {
        Objects.requireNonNull(
                order,
                "order is required"
        );

        if (
                packages <= 0 ||
                packages > order.packages()
        ) {
            throw new IllegalArgumentException(
                    "packages must be within the order quantity"
            );
        }
    }
}