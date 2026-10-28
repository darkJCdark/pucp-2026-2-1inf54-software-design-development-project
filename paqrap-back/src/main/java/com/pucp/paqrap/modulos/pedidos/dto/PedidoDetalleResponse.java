package com.pucp.paqrap.modulos.pedidos.dto;

import com.pucp.paqrap.modulos.pedidos.entity.OrderStatus;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderStatusHistoryEntity;

import java.time.Instant;
import java.util.List;

/**
 * Pedido con su trazabilidad (CU-04): cada cambio de estado con la unidad y el almacén implicados.
 * {@code unidadAsignadaId} es la última unidad registrada en el historial (nula si aún no se asignó).
 * {@code incumplido} es verdadero si se entregó después del plazo o, sin entregar, si el instante consultado ya lo
 * superó; {@code minutosRestantes} (negativo si venció) es nulo si ya se entregó o no se indicó el instante.
 */
public record PedidoDetalleResponse(PedidoResponse pedido, List<CambioEstado> historial, String unidadAsignadaId,
                                    boolean incumplido, Long minutosRestantes) {

    public record CambioEstado(OrderStatus estado, Instant ocurridoEn, String vehiculoId, String almacenId) {

        public static CambioEstado de(OrderStatusHistoryEntity entity) {
            return new CambioEstado(entity.getStatus(), entity.getChangedAt(), entity.getVehicleId(),
                    entity.getWarehouseId());
        }
    }
}
