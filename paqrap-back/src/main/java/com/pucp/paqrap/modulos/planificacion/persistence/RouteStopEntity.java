package com.pucp.paqrap.modulos.planificacion.persistence;

import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Parada proyectada de una ruta persistida. */
@Entity
@Table(name = "route_stops", uniqueConstraints = @UniqueConstraint(name = "uq_route_stops_sequence",
        columnNames = {"route_id", "sequence_no"}))
public class RouteStopEntity {

    public static final String DELIVERY = "DELIVERY";
    public static final String WAREHOUSE = "WAREHOUSE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "route_stop_id")
    private Long routeStopId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private RouteEntity route;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "stop_type", nullable = false, length = 20)
    private String stopType;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private OrderEntity order;

    @ManyToOne
    @JoinColumn(name = "warehouse_id")
    private WarehouseEntity warehouse;

    @Column(name = "delivered_packages")
    private Integer deliveredPackages;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "x", nullable = false)
    private int x;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "y", nullable = false)
    private int y;

    @Column(name = "planned_arrival_at")
    private Instant plannedArrivalAt;

    protected RouteStopEntity() {
    }

    public RouteStopEntity(RouteEntity route, int sequenceNo, String stopType, OrderEntity order,
                           WarehouseEntity warehouse, Integer deliveredPackages, int x, int y,
                           Instant plannedArrivalAt) {
        this.route = route;
        this.sequenceNo = sequenceNo;
        this.stopType = stopType;
        this.order = order;
        this.warehouse = warehouse;
        this.deliveredPackages = deliveredPackages;
        this.x = x;
        this.y = y;
        this.plannedArrivalAt = plannedArrivalAt;
    }

    public Long getRouteStopId() { return routeStopId; }
    public RouteEntity getRoute() { return route; }
    public int getSequenceNo() { return sequenceNo; }
    public String getStopType() { return stopType; }
    public OrderEntity getOrder() { return order; }
    public WarehouseEntity getWarehouse() { return warehouse; }
    public Integer getDeliveredPackages() { return deliveredPackages; }
    public int getX() { return x; }
    public int getY() { return y; }
    public Instant getPlannedArrivalAt() { return plannedArrivalAt; }
}
