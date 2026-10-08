package com.pucp.paqrap.modulos.pedidos.dto;

import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.entity.OrderStatus;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;

import java.time.Instant;

public record PedidoResponse(String id, String clienteId, int x, int y, int paquetes, DeliveryType tipoEntrega,
                             int horasPrometidas, Instant registradoEn, Instant vence, OrderStatus estado,
                             Instant entregadoEn) {

    public static PedidoResponse de(OrderEntity entity) {
        return new PedidoResponse(entity.getOrderId(), entity.getClientId(), entity.getDestinationX(),
                entity.getDestinationY(), entity.getPackages(), entity.getDeliveryType(), entity.getPromisedHours(),
                entity.getRegisteredAt(), entity.getDeadline(), entity.getStatus(), entity.getDeliveredAt());
    }
}
