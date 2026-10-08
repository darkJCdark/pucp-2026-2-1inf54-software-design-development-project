package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;

import java.util.List;
import java.util.Objects;

/**
 * Ruta del plan vigente junto con el snapshot con el que se creó (necesario para volver a programarla
 * ante un bloqueo) y su programación. {@code programada} es null si el evaluador no pudo programarla.
 */
record RutaVigente(DeliveryRoute ruta, OperationalSnapshot snapshotOrigen, ScheduledDeliveryRoute programada,
                   List<PlanViolation> violaciones) {
    RutaVigente {
        Objects.requireNonNull(ruta, "ruta es requerida");
        Objects.requireNonNull(snapshotOrigen, "snapshotOrigen es requerido");
        violaciones = violaciones == null ? List.of() : List.copyOf(violaciones);
    }

    String vehiculoId() {
        return ruta.vehicle().id();
    }
}
