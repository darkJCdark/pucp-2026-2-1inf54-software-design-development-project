package com.pucp.paqrap.modulos.incidencias.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Only MINOR is configurable for B07. */
@Entity
@Table(name = "breakdown_type_settings")
public class BreakdownTypeSettingsEntity {
    @Id
    @Column(name = "breakdown_type", length = 20)
    private String type;
    @Column(name = "repair_minutes", nullable = false)
    private int repairMinutes;

    protected BreakdownTypeSettingsEntity() { }
    public BreakdownTypeSettingsEntity(int repairMinutes) {
        if (repairMinutes <= 0) throw new IllegalArgumentException("La duración debe ser positiva");
        this.type = "MINOR";
        this.repairMinutes = repairMinutes;
    }
    public int getRepairMinutes() { return repairMinutes; }
}