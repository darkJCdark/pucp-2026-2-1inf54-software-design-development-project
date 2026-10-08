package com.pucp.paqrap.modulos.incidencias.entity;

import java.time.Instant;

/** Evento de aplicación: se registró una avería durante una ejecución de escenario. */
public record AveriaRegistrada(long ejecucionId, String vehiculoId, Instant ocurridaEn) {
}
