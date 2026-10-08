package com.pucp.paqrap.modulos.incidencias.persistence;

import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
import com.pucp.paqrap.modulos.redvial.entity.Location;
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

/** Fila de {@code breakdown_events}. El dominio la consume como {@link BreakdownEvent}. */
@Entity
@Table(name = "breakdown_events")
public class BreakdownEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "breakdown_id")
    private Long breakdownId;

    /** Ejecución de escenario en la que ocurrió; NULL si se registró fuera de una simulación. */
    @Column(name = "execution_id")
    private Long executionId;

    @Column(name = "vehicle_id", nullable = false, length = 8)
    private String vehicleId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "breakdown_type", nullable = false, length = 20)
    private BreakdownType breakdownType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "location_x", nullable = false)
    private Integer locationX;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "location_y", nullable = false)
    private Integer locationY;

    protected BreakdownEventEntity() {
    }

    public BreakdownEventEntity(Long executionId, BreakdownEvent evento) {
        this.executionId = executionId;
        this.vehicleId = evento.vehicleId();
        this.breakdownType = evento.type();
        this.occurredAt = evento.occurredAt();
        this.locationX = evento.location().x();
        this.locationY = evento.location().y();
    }

    public BreakdownEvent toDomain() {
        return new BreakdownEvent(vehicleId, breakdownType, occurredAt, new Location(locationX, locationY));
    }

    public Long getBreakdownId() { return breakdownId; }
    public Long getExecutionId() { return executionId; }
    public String getVehicleId() { return vehicleId; }
    public BreakdownType getBreakdownType() { return breakdownType; }
    public Instant getOccurredAt() { return occurredAt; }
    public Integer getLocationX() { return locationX; }
    public Integer getLocationY() { return locationY; }
}
