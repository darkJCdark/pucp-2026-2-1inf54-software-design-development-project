package com.pucp.paqrap.modulos.incidencias.entity;

import java.time.Instant;
import java.util.Objects;

/** Evento de aplicación: se registró una avería durante una ejecución de escenario. */
public record AveriaRegistrada(long ejecucionId, BreakdownEvent averia) {
    public AveriaRegistrada {
        Objects.requireNonNull(averia, "averia es requerida");
    }

    public String vehiculoId() {
        return averia.vehicleId();
    }

    public Instant ocurridaEn() {
        return averia.occurredAt();
    }
}
