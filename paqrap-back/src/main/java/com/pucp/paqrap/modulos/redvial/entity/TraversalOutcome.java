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
import java.util.List;

/** Result of a real-time traversal attempt, including a forced U-turn when a block is encountered. */
public record TraversalOutcome(List<RoadLeg> legs, Location finalLocation, Instant completedAt,
                               boolean forcedUTurn) {
    public TraversalOutcome {
        legs = List.copyOf(legs);
    }
}
