package com.pucp.paqrap.modulos.flota.service;
import com.pucp.paqrap.modulos.almacenes.entity.*;
import com.pucp.paqrap.modulos.flota.entity.*;
import com.pucp.paqrap.modulos.flota.service.*;
import com.pucp.paqrap.modulos.incidencias.entity.*;
import com.pucp.paqrap.modulos.incidencias.service.*;
import com.pucp.paqrap.modulos.pedidos.entity.*;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.*;
import com.pucp.paqrap.modulos.planificacion.entity.*;
import com.pucp.paqrap.modulos.redvial.entity.*;
import com.pucp.paqrap.modulos.redvial.service.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/** A vehicle is unavailable for the complete local simulation day scheduled for preventive maintenance. */
public record MaintenanceDay(String vehicleId, LocalDate date) {
    public MaintenanceDay {
        Objects.requireNonNull(vehicleId, "vehicleId is required");
        Objects.requireNonNull(date, "date is required");
    }

    public boolean contains(Instant instant, ZoneId zoneId) {
        Objects.requireNonNull(instant, "instant is required");
        Objects.requireNonNull(zoneId, "zoneId is required");
        return date.equals(instant.atZone(zoneId).toLocalDate());
    }

    public boolean overlaps(Instant fromInclusive, Instant toExclusive, ZoneId zoneId) {
        Objects.requireNonNull(fromInclusive, "fromInclusive is required");
        Objects.requireNonNull(toExclusive, "toExclusive is required");
        Objects.requireNonNull(zoneId, "zoneId is required");
        if (!toExclusive.isAfter(fromInclusive)) {
            throw new IllegalArgumentException("An interval must have positive duration");
        }
        Instant maintenanceStart = date.atStartOfDay(zoneId).toInstant();
        Instant maintenanceEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant();
        return fromInclusive.isBefore(maintenanceEnd) && toExclusive.isAfter(maintenanceStart);
    }
}
