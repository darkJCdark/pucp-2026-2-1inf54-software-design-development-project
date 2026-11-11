package com.pucp.paqrap.modulos.planificacion.algoritmo.common;

import java.time.Instant;

/**
 * Estado heredado al reprogramar únicamente el futuro
 * de una ruta.
 *
 * Actualmente lo único que RouteScheduler necesita
 * recordar del pasado es si el conductor ya tomó
 * refrigerio en un turno determinado.
 */
public record ContextoProgramacionRuta(
        Instant turnoConRefrigerioTomado
) {

    public static ContextoProgramacionRuta vacio() {

        return new ContextoProgramacionRuta(
                null
        );
    }

    public boolean refrigerioTomadoEn(
            Instant inicioTurno
    ) {

        return turnoConRefrigerioTomado != null
                &&
                turnoConRefrigerioTomado.equals(
                        inicioTurno
                );
    }
}