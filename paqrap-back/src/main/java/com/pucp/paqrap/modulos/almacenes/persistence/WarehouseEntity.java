package com.pucp.paqrap.modulos.almacenes.persistence;

import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Fila de {@code warehouses}. El dominio la consume como {@link Warehouse}. */
@Entity
@Table(name = "warehouses")
public class WarehouseEntity {

    @Id
    @Column(name = "warehouse_id", length = 32)
    private String warehouseId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "warehouse_kind", nullable = false, length = 16)
    private WarehouseKind warehouseKind;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "x", nullable = false)
    private Integer x;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "y", nullable = false)
    private Integer y;

    /** NULL para el almacén central, que tiene stock ilimitado. */
    @Column(name = "initial_stock")
    private Integer initialStock;

    protected WarehouseEntity() {
    }

    public WarehouseEntity(String warehouseId, WarehouseKind warehouseKind, int x, int y, Integer initialStock) {
        this.warehouseId = warehouseId;
        this.warehouseKind = warehouseKind;
        this.x = x;
        this.y = y;
        this.initialStock = initialStock;
    }

    public Warehouse toDomain() {
        Location location = new Location(x, y);
        return warehouseKind == WarehouseKind.CENTRAL
                ? Warehouse.central(warehouseId, location)
                : Warehouse.intermediate(warehouseId, location, initialStock);
    }

    public String getWarehouseId() { return warehouseId; }
    public WarehouseKind getWarehouseKind() { return warehouseKind; }
    public Integer getX() { return x; }
    public Integer getY() { return y; }
    public Integer getInitialStock() { return initialStock; }
}
