package com.pucp.paqrap.modulos.almacenes.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Fila de {@code inventory_movements} (kardex): un despacho hacia un vehículo o una recarga diaria. */
@Entity
@Table(name = "inventory_movements", uniqueConstraints = @UniqueConstraint(
        name = "uq_inventory_movements_execution_event", columnNames = {"execution_id", "event_key"}))
public class InventoryMovementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movement_id")
    private Long movementId;

    @Column(name = "warehouse_id", nullable = false, length = 32)
    private String warehouseId;

    @Column(name = "execution_id")
    private Long executionId;

    /** Opcional: una carga física puede abastecer más de un pedido. */
    @Column(name = "order_id", length = 64)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "movement_type", nullable = false, length = 20)
    private MovementType movementType;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** Saldo previo y posterior; ambos son nulos para el almacén central ilimitado. */
    @Column(name = "stock_before")
    private Integer stockBefore;

    @Column(name = "stock_after")
    private Integer stockAfter;

    /** Clave estable del evento físico, única dentro de una ejecución cuando está informada. */
    @Column(name = "event_key", length = 160)
    private String eventKey;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected InventoryMovementEntity() {
    }

    private InventoryMovementEntity(String warehouseId, Long executionId, String orderId, MovementType movementType,
                                    int quantity, Integer stockBefore, Integer stockAfter, String eventKey,
                                    Instant occurredAt) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad de un movimiento debe ser positiva");
        }
        this.warehouseId = warehouseId;
        this.executionId = executionId;
        this.orderId = orderId;
        this.movementType = movementType;
        this.quantity = quantity;
        this.stockBefore = stockBefore;
        this.stockAfter = stockAfter;
        this.eventKey = eventKey;
        this.occurredAt = occurredAt;
    }

    public static InventoryMovementEntity despacho(String warehouseId, long executionId, String orderId, int quantity,
                                                   Instant occurredAt) {
        return despacho(warehouseId, executionId, orderId, quantity, null, null, null, occurredAt);
    }

    public static InventoryMovementEntity despacho(String warehouseId, long executionId, String orderId, int quantity,
                                                   Integer stockBefore, Integer stockAfter, String eventKey,
                                                   Instant occurredAt) {
        return new InventoryMovementEntity(warehouseId, executionId, orderId, MovementType.DISPATCH, quantity,
                stockBefore, stockAfter, eventKey, occurredAt);
    }

    public static InventoryMovementEntity recarga(String warehouseId, long executionId, int quantity,
                                                  Instant occurredAt) {
        return recarga(warehouseId, executionId, quantity, null, null, null, occurredAt);
    }

    public static InventoryMovementEntity recarga(String warehouseId, long executionId, int quantity,
                                                  Integer stockBefore, Integer stockAfter, String eventKey,
                                                  Instant occurredAt) {
        return new InventoryMovementEntity(warehouseId, executionId, null, MovementType.RECHARGE, quantity,
                stockBefore, stockAfter, eventKey, occurredAt);
    }

    public Long getMovementId() { return movementId; }
    public String getWarehouseId() { return warehouseId; }
    public Long getExecutionId() { return executionId; }
    public String getOrderId() { return orderId; }
    public MovementType getMovementType() { return movementType; }
    public Integer getQuantity() { return quantity; }
    public Integer getStockBefore() { return stockBefore; }
    public Integer getStockAfter() { return stockAfter; }
    public String getEventKey() { return eventKey; }
    public Instant getOccurredAt() { return occurredAt; }
}
