package com.pucp.paqrap.modulos.flota.persistence;

import com.pucp.paqrap.modulos.flota.service.MaintenanceDay;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** Fila de {@code maintenance_days}. El dominio la consume como {@link MaintenanceDay}. */
@Entity
@Table(name = "maintenance_days")
@IdClass(MaintenanceDayEntity.Id.class)
public class MaintenanceDayEntity {

    @jakarta.persistence.Id
    @Column(name = "vehicle_id", length = 8)
    private String vehicleId;

    @jakarta.persistence.Id
    @Column(name = "maintenance_date")
    private LocalDate maintenanceDate;

    protected MaintenanceDayEntity() {
    }

    public MaintenanceDayEntity(String vehicleId, LocalDate maintenanceDate) {
        this.vehicleId = vehicleId;
        this.maintenanceDate = maintenanceDate;
    }

    public MaintenanceDay toDomain() {
        return new MaintenanceDay(vehicleId, maintenanceDate);
    }

    public String getVehicleId() { return vehicleId; }
    public LocalDate getMaintenanceDate() { return maintenanceDate; }

    /** Clave compuesta (vehicle_id, maintenance_date). */
    public static class Id implements Serializable {
        private String vehicleId;
        private LocalDate maintenanceDate;

        protected Id() {
        }

        public Id(String vehicleId, LocalDate maintenanceDate) {
            this.vehicleId = vehicleId;
            this.maintenanceDate = maintenanceDate;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Id id
                    && Objects.equals(vehicleId, id.vehicleId)
                    && Objects.equals(maintenanceDate, id.maintenanceDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(vehicleId, maintenanceDate);
        }
    }
}
