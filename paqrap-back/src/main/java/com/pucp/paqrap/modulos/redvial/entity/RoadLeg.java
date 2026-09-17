package com.pucp.paqrap.modulos.redvial.entity;
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

/** An executed or planned one-kilometre move, including any wait before departure. */
public record RoadLeg(Location from, Location to, Instant departsAt, Instant arrivesAt) {
    public RoadLeg {
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(to, "to is required");
        Objects.requireNonNull(departsAt, "departsAt is required");
        Objects.requireNonNull(arrivesAt, "arrivesAt is required");
        new StreetSegment(from, to);
        if (!arrivesAt.isAfter(departsAt)) {
            throw new IllegalArgumentException("A road leg must have positive travel time");
        }
    }
}
