package com.pucp.paqrap.modulos.incidencias.service;

import com.pucp.paqrap.modulos.redvial.entity.Location;

import java.time.Instant;
import java.util.Optional;

/**
 * Lo que el registro de averías necesita saber de una ejecución de escenario en curso. Lo implementa el módulo de
 * escenarios; los métodos devuelven vacío si la ejecución no está activa.
 */
public interface ContextoSimulacion {

    /** Instante simulado actual de la ejecución. */
    Optional<Instant> instanteSimulado(long ejecucionId);

    /** Hasta dónde ya se ejecutó el plan: lo anterior (entregas, posiciones) ya ocurrió y no se rehace. */
    Optional<Instant> ejecutadoHasta(long ejecucionId);

    /** Posición de un vehículo en ruta en {@code instante}, la misma que dibuja el mapa. */
    Optional<Location> posicionEstimada(long ejecucionId, String vehiculoId, Instant instante);
}
