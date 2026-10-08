package com.pucp.paqrap.modulos.flota.persistence;

import com.pucp.paqrap.modulos.flota.entity.VehicleParameters;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/** Fila de {@code vehicle_type_parameters}. El dominio la consume como {@link VehicleParameters}. */
@Entity
@Table(name = "vehicle_type_parameters")
public class VehicleTypeParametersEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "vehicle_type", length = 20)
    private VehicleType vehicleType;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "capacity_packages", nullable = false)
    private Integer capacityPackages;

    @Column(name = "speed_kmh", nullable = false, precision = 6, scale = 2)
    private BigDecimal speedKmh;

    @Column(name = "cost_per_km", nullable = false, precision = 8, scale = 2)
    private BigDecimal costPerKm;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VehicleTypeParametersEntity() {
    }

    public VehicleTypeParametersEntity(VehicleType vehicleType, int capacityPackages, BigDecimal speedKmh,
                                       BigDecimal costPerKm) {
        this.vehicleType = vehicleType;
        this.capacityPackages = capacityPackages;
        this.speedKmh = speedKmh;
        this.costPerKm = costPerKm;
        this.updatedAt = Instant.now();
    }

    public VehicleParameters toDomain() {
        return new VehicleParameters(capacityPackages, speedKmh.doubleValue(), costPerKm.doubleValue());
    }

    public void actualizar(int capacityPackages, BigDecimal speedKmh, BigDecimal costPerKm) {
        this.capacityPackages = capacityPackages;
        this.speedKmh = speedKmh;
        this.costPerKm = costPerKm;
        this.updatedAt = Instant.now();
    }

    public VehicleType getVehicleType() { return vehicleType; }
    public Integer getCapacityPackages() { return capacityPackages; }
    public BigDecimal getSpeedKmh() { return speedKmh; }
    public BigDecimal getCostPerKm() { return costPerKm; }
    public Instant getUpdatedAt() { return updatedAt; }
}
