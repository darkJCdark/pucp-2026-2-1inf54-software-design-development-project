package com.pucp.paqrap.modulos.flota.persistence;

import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Fila de {@code vehicles}. El dominio la consume como {@link Vehicle}. */
@Entity
@Table(name = "vehicles")
public class VehicleEntity {

    @Id
    @Column(name = "vehicle_id", length = 8)
    private String vehicleId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "available", nullable = false)
    private boolean available;

    protected VehicleEntity() {
    }

    public VehicleEntity(String vehicleId, VehicleType vehicleType, boolean available) {
        this.vehicleId = vehicleId;
        this.vehicleType = vehicleType;
        this.available = available;
    }

    public Vehicle toDomain() {
        return new Vehicle(vehicleId, vehicleType, available);
    }

    public void setAvailable(boolean available) { this.available = available; }

    public String getVehicleId() { return vehicleId; }
    public VehicleType getVehicleType() { return vehicleType; }
    public boolean isAvailable() { return available; }
}
