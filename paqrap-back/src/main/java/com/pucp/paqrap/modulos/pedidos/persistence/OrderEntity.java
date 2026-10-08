package com.pucp.paqrap.modulos.pedidos.persistence;

import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.pedidos.entity.OrderStatus;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Fila de {@code orders} (V2). El dominio la consume como {@link Order}. El deadline nunca se recibe: se calcula
 * como {@code registered_at + promised_hours}, igual que exige {@code chk_orders_deadline_consistency}.
 */
@Entity
@Table(name = "orders")
public class OrderEntity implements Persistable<String> {

    @Id
    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "client_id", nullable = false, length = 32)
    private String clientId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "destination_x", nullable = false)
    private Integer destinationX;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "destination_y", nullable = false)
    private Integer destinationY;

    @Column(name = "packages", nullable = false)
    private Integer packages;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "deadline", nullable = false)
    private Instant deadline;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "delivery_type", nullable = false, length = 20)
    private DeliveryType deliveryType;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "promised_hours", nullable = false)
    private Integer promisedHours;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    /** Con id asignado, Spring Data haría un SELECT por cada save() para saber si es nuevo; así se evita. */
    @Transient
    private boolean nuevo = true;

    protected OrderEntity() {
    }

    /** Pedido recién registrado. La hora se trunca a microsegundos, la precisión de TIMESTAMP(6). */
    public OrderEntity(String orderId, String clientId, Location destino, int packages, Instant registeredAt,
                       DeliveryType deliveryType, int promisedHours) {
        if (!deliveryType.admite(promisedHours)) {
            throw new IllegalArgumentException("Un pedido " + deliveryType + " no admite un plazo de "
                    + promisedHours + " h");
        }
        this.orderId = orderId;
        this.clientId = clientId;
        this.destinationX = destino.x();
        this.destinationY = destino.y();
        this.packages = packages;
        this.registeredAt = registeredAt.truncatedTo(ChronoUnit.MICROS);
        this.deadline = this.registeredAt.plus(Duration.ofHours(promisedHours));
        this.status = OrderStatus.REGISTERED;
        this.deliveryType = deliveryType;
        this.promisedHours = promisedHours;
        toDomain(); // aplica las invariantes del dominio (paquetes > 0, id no vacío)
    }

    public Order toDomain() {
        return new Order(orderId, new Location(destinationX, destinationY), packages, registeredAt, deadline);
    }

    @Override
    public String getId() { return orderId; }

    @Override
    public boolean isNew() { return nuevo; }

    @PostLoad
    @PostPersist
    void marcarPersistido() { this.nuevo = false; }

    public String getOrderId() { return orderId; }
    public String getClientId() { return clientId; }
    public Integer getDestinationX() { return destinationX; }
    public Integer getDestinationY() { return destinationY; }
    public Integer getPackages() { return packages; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getDeadline() { return deadline; }
    public OrderStatus getStatus() { return status; }
    public DeliveryType getDeliveryType() { return deliveryType; }
    public Integer getPromisedHours() { return promisedHours; }
    public Instant getDeliveredAt() { return deliveredAt; }
}
