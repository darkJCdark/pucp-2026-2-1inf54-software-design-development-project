package com.pucp.paqrap.modulos.pedidos.dto;

import com.pucp.paqrap.modulos.pedidos.entity.OrderStatus;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderStatusHistoryEntity;

import java.time.Instant;
import java.util.List;

/** Pedido con su trazabilidad (CU-04): cada cambio de estado con la unidad y el almacén implicados. */
public record PedidoDetalleResponse(PedidoResponse pedido, List<CambioEstado> historial) {

    public record CambioEstado(OrderStatus estado, Instant ocurridoEn, String vehiculoId, String almacenId) {

        public static CambioEstado de(OrderStatusHistoryEntity entity) {
            return new CambioEstado(entity.getStatus(), entity.getChangedAt(), entity.getVehicleId(),
                    entity.getWarehouseId());
        }
    }
}
