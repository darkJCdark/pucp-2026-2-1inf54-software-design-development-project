package com.pucp.paqrap.modulos.incidencias.entity;
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
import java.util.Objects;

/** An event; its effective availability window is calculated in phase 2 from shifts and current time. */
public record BreakdownEvent(String vehicleId, BreakdownType type, Instant occurredAt, Location location) {
    public BreakdownEvent {
        Objects.requireNonNull(vehicleId, "vehicleId is required");
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(location, "location is required");
    }
}
