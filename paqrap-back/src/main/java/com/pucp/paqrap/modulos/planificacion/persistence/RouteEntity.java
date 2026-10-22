package com.pucp.paqrap.modulos.planificacion.persistence;

import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
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

import java.math.BigDecimal;
import java.time.Instant;

/** Ruta proyectada dentro de un snapshot de plan. */
@Entity
@Table(name = "routes", uniqueConstraints = @UniqueConstraint(name = "uq_routes_plan_operational",
        columnNames = {"plan_id", "operational_route_id"}))
public class RouteEntity {

    public static final String PLANNED = "PLANNED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "route_id")
    private Long routeId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private PlanEntity plan;

    @Column(name = "operational_route_id", nullable = false, length = 64)
    private String operationalRouteId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private VehicleEntity vehicle;

    @ManyToOne
    @JoinColumn(name = "start_warehouse_id")
    private WarehouseEntity startWarehouse;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "origin_x", nullable = false)
    private int originX;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "origin_y", nullable = false)
    private int originY;

    @Column(name = "initial_load", nullable = false)
    private int initialLoad;

    @Column(name = "departure_at", nullable = false)
    private Instant departureAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "total_distance_km", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDistanceKm;

    @Column(name = "total_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCost;

    protected RouteEntity() {
    }

    public RouteEntity(PlanEntity plan, String operationalRouteId, VehicleEntity vehicle, WarehouseEntity startWarehouse,
                       int originX, int originY, int initialLoad, Instant departureAt,
                       BigDecimal totalDistanceKm, BigDecimal totalCost) {
        this.plan = plan;
        this.operationalRouteId = operationalRouteId;
        this.vehicle = vehicle;
        this.startWarehouse = startWarehouse;
        this.originX = originX;
        this.originY = originY;
        this.initialLoad = initialLoad;
        this.departureAt = departureAt;
        this.status = PLANNED;
        this.completedAt = null;
        this.totalDistanceKm = totalDistanceKm;
        this.totalCost = totalCost;
    }

    public Long getRouteId() { return routeId; }
    public PlanEntity getPlan() { return plan; }
    public String getOperationalRouteId() { return operationalRouteId; }
    public VehicleEntity getVehicle() { return vehicle; }
    public WarehouseEntity getStartWarehouse() { return startWarehouse; }
    public int getOriginX() { return originX; }
    public int getOriginY() { return originY; }
    public int getInitialLoad() { return initialLoad; }
    public Instant getDepartureAt() { return departureAt; }
    public String getStatus() { return status; }
    public Instant getCompletedAt() { return completedAt; }
    public BigDecimal getTotalDistanceKm() { return totalDistanceKm; }
    public BigDecimal getTotalCost() { return totalCost; }
}
