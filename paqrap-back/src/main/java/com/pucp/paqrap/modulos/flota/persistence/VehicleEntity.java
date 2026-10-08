package com.pucp.paqrap.modulos.flota.persistence;

import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Fila de {@code vehicles} (V2): identidad del vehículo y su último estado operativo conocido. */
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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "operational_status", nullable = false, length = 20)
    private VehicleOperationalStatus operationalStatus;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "current_x", nullable = false)
    private Integer currentX;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "current_y", nullable = false)
    private Integer currentY;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "current_load", nullable = false)
    private Integer currentLoad;

    /** Desde cuándo puede volver a planificarse; NULL = ya disponible. */
    @Column(name = "available_at")
    private Instant availableAt;

    protected VehicleEntity() {
    }

    /** Vehículo nuevo en el almacén central, vacío y disponible (mismos valores por defecto que la BD). */
    public VehicleEntity(String vehicleId, VehicleType vehicleType, VehicleOperationalStatus operationalStatus) {
        this.vehicleId = vehicleId;
        this.vehicleType = vehicleType;
        this.operationalStatus = operationalStatus;
        this.currentX = 27;
        this.currentY = 14;
        this.currentLoad = 0;
    }

    public Vehicle toDomain() {
        return new Vehicle(vehicleId, vehicleType, operationalStatus != VehicleOperationalStatus.UNAVAILABLE);
    }

    /** Estado para SA; si {@code available_at} es NULL, el vehículo está libre desde {@code instanteBase}. */
    public VehicleOperationalState toOperationalState(Instant instanteBase) {
        return new VehicleOperationalState(toDomain(), operationalStatus.toDomain(), new Location(currentX, currentY),
                currentLoad, availableAt != null ? availableAt : instanteBase);
    }

    public void setOperationalStatus(VehicleOperationalStatus operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public String getVehicleId() { return vehicleId; }
    public VehicleType getVehicleType() { return vehicleType; }
    public VehicleOperationalStatus getOperationalStatus() { return operationalStatus; }
    public Integer getCurrentX() { return currentX; }
    public Integer getCurrentY() { return currentY; }
    public Integer getCurrentLoad() { return currentLoad; }
    public Instant getAvailableAt() { return availableAt; }
}
