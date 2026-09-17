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

import java.time.Duration;

/** Course-defined vehicle breakdown classes. */
public enum BreakdownType {
    MINOR(Duration.ofHours(2), Duration.ZERO),
    INTERMEDIATE(Duration.ofHours(4), Duration.ofHours(4)),
    MAJOR(Duration.ofDays(2), Duration.ofHours(4));

    private final Duration minimumUnavailable;
    private final Duration maximumRoadsideDuration;

    BreakdownType(Duration minimumUnavailable, Duration maximumRoadsideDuration) {
        this.minimumUnavailable = minimumUnavailable;
        this.maximumRoadsideDuration = maximumRoadsideDuration;
    }

    public Duration minimumUnavailable() { return minimumUnavailable; }
    public Duration maximumRoadsideDuration() { return maximumRoadsideDuration; }
}
