package com.pucp.paqrap.modulos.almacenes.persistence;

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

/** Fila de {@code inventory_movements} (kardex): un despacho hacia un pedido o una recarga diaria. */
@Entity
@Table(name = "inventory_movements")
public class InventoryMovementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movement_id")
    private Long movementId;

    @Column(name = "warehouse_id", nullable = false, length = 32)
    private String warehouseId;

    @Column(name = "execution_id")
    private Long executionId;

    /** Obligatorio en DISPATCH y nulo en RECHARGE ({@code chk_inventory_movements_order}). */
    @Column(name = "order_id", length = 64)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "movement_type", nullable = false, length = 20)
    private MovementType movementType;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected InventoryMovementEntity() {
    }

    private InventoryMovementEntity(String warehouseId, Long executionId, String orderId, MovementType movementType,
                                    int quantity, Instant occurredAt) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad de un movimiento debe ser positiva");
        }
        this.warehouseId = warehouseId;
        this.executionId = executionId;
        this.orderId = orderId;
        this.movementType = movementType;
        this.quantity = quantity;
        this.occurredAt = occurredAt;
    }

    public static InventoryMovementEntity despacho(String warehouseId, long executionId, String orderId, int quantity,
                                                   Instant occurredAt) {
        return new InventoryMovementEntity(warehouseId, executionId, orderId, MovementType.DISPATCH, quantity,
                occurredAt);
    }

    public static InventoryMovementEntity recarga(String warehouseId, long executionId, int quantity,
                                                  Instant occurredAt) {
        return new InventoryMovementEntity(warehouseId, executionId, null, MovementType.RECHARGE, quantity,
                occurredAt);
    }

    public Long getMovementId() { return movementId; }
    public String getWarehouseId() { return warehouseId; }
    public Long getExecutionId() { return executionId; }
    public String getOrderId() { return orderId; }
    public MovementType getMovementType() { return movementType; }
    public Integer getQuantity() { return quantity; }
    public Instant getOccurredAt() { return occurredAt; }
}
