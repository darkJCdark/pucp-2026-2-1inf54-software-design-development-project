package com.pucp.paqrap.modulos.pedidos.persistence;

import com.pucp.paqrap.modulos.pedidos.entity.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Fila de {@code order_status_history}: un cambio de estado de un pedido, con la unidad y el almacén implicados. */
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "status_history_id")
    private Long statusHistoryId;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "vehicle_id", length = 8)
    private String vehicleId;

    @Column(name = "warehouse_id", length = 32)
    private String warehouseId;

    protected OrderStatusHistoryEntity() {
    }

    public OrderStatusHistoryEntity(String orderId, OrderStatus status, Instant changedAt, String vehicleId,
                                    String warehouseId) {
        this.orderId = orderId;
        this.status = status;
        this.changedAt = changedAt;
        this.vehicleId = vehicleId;
        this.warehouseId = warehouseId;
    }

    public static OrderStatusHistoryEntity registro(OrderEntity pedido) {
        return new OrderStatusHistoryEntity(pedido.getOrderId(), OrderStatus.REGISTERED, pedido.getRegisteredAt(),
                null, null);
    }

    public Long getStatusHistoryId() { return statusHistoryId; }
    public String getOrderId() { return orderId; }
    public OrderStatus getStatus() { return status; }
    public Instant getChangedAt() { return changedAt; }
    public String getVehicleId() { return vehicleId; }
    public String getWarehouseId() { return warehouseId; }
}
