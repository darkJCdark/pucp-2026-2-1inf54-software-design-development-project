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
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/** Immutable planned-maintenance lookup for a simulation. */
public final class MaintenanceCalendar {
    private final ZoneId zoneId;
    private final List<MaintenanceDay> entries;

    public MaintenanceCalendar(ZoneId zoneId, List<MaintenanceDay> entries) {
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId is required");
        this.entries = List.copyOf(Objects.requireNonNull(entries, "entries are required"));
    }

    public boolean isUnavailable(String vehicleId, Instant instant) {
        return entries.stream().anyMatch(entry -> entry.vehicleId().equals(vehicleId) && entry.contains(instant, zoneId));
    }

    public boolean isUnavailableDuring(String vehicleId, Instant fromInclusive, Instant toExclusive) {
        return entries.stream().anyMatch(entry -> entry.vehicleId().equals(vehicleId)
                && entry.overlaps(fromInclusive, toExclusive, zoneId));
    }
}
