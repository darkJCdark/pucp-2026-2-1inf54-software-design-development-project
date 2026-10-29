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

/** Consequences calculated from a breakdown according to the course rules. */
public record BreakdownResolution(Instant unavailableUntil, Instant returnsToCentralAt) {

    /**
     * First instant at which the vehicle can be considered by the planner again.
     * Minor breakdowns have no return trip, while intermediate and major ones must
     * satisfy both the unavailability window and the return-to-central time.
     */
    public Instant availableFrom() {
        return returnsToCentralAt != null && returnsToCentralAt.isAfter(unavailableUntil)
                ? returnsToCentralAt
                : unavailableUntil;
    }
}
