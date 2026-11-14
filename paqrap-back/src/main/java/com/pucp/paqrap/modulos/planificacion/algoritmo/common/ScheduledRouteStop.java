package com.pucp.paqrap.modulos.planificacion.algoritmo.common;

import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.redvial.entity.RoadPath;

import java.time.Instant;
import java.util.Objects;

/**
 * Programación temporal de una parada y carga del vehículo antes/después.
 *
 * <p>{@code serviceStartedAt} puede ser posterior a {@code arrivedAt} cuando
 * un vehículo espera a su contraparte para un transbordo sincronizado.</p>
 */
public record ScheduledRouteStop(
        RouteStop stop,
        RoadPath approach,
        Instant arrivedAt,
        Instant serviceStartedAt,
        Instant completedAt,
        int loadBefore,
        int loadAfter
) {
    public ScheduledRouteStop {
        Objects.requireNonNull(stop, "stop is required");
        Objects.requireNonNull(approach, "approach is required");
        Objects.requireNonNull(arrivedAt, "arrivedAt is required");
        Objects.requireNonNull(serviceStartedAt, "serviceStartedAt is required");
        Objects.requireNonNull(completedAt, "completedAt is required");
        if (serviceStartedAt.isBefore(arrivedAt)) {
            throw new IllegalArgumentException("Service cannot start before arrival");
        }
        if (completedAt.isBefore(serviceStartedAt)) {
            throw new IllegalArgumentException("Stop cannot complete before service starts");
        }
    }

    /** Conserva compatibilidad con el modelo anterior, sin espera previa. */
    public ScheduledRouteStop(
            RouteStop stop,
            RoadPath approach,
            Instant arrivedAt,
            Instant completedAt,
            int loadBefore,
            int loadAfter
    ) {
        this(stop, approach, arrivedAt, arrivedAt, completedAt, loadBefore, loadAfter);
    }
}
