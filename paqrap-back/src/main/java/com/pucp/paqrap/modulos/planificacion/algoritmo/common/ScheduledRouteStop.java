package com.pucp.paqrap.modulos.planificacion.algoritmo.common;
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

import com.pucp.paqrap.modulos.redvial.entity.RoadPath;

import java.time.Instant;
import java.util.Objects;

/** Timing and vehicle load before and after executing one planned stop. */
public record ScheduledRouteStop(RouteStop stop, RoadPath approach, Instant arrivedAt, Instant completedAt,
                                 int loadBefore, int loadAfter) {
    public ScheduledRouteStop {
        Objects.requireNonNull(stop, "stop is required");
        Objects.requireNonNull(approach, "approach is required");
        Objects.requireNonNull(arrivedAt, "arrivedAt is required");
        Objects.requireNonNull(completedAt, "completedAt is required");
        if (completedAt.isBefore(arrivedAt)) {
            throw new IllegalArgumentException("Stop cannot complete before arrival");
        }
    }
}
