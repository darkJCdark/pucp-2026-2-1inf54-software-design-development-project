package com.pucp.paqrap.modulos.planificacion.algoritmo.common;

import java.util.Locale;

/**
 * Define qué prioriza el planificador al comparar soluciones.
 *
 * SERVICE_MAX:
 *
 *     Maximiza exclusivamente el nivel de servicio.
 *
 *     Una solución con menos pedidos no atendidos siempre
 *     domina a otra, aunque sea mucho más cara.
 *
 *     Si ambas dejan exactamente la misma cantidad de
 *     pedidos no atendidos, son equivalentes para el
 *     objetivo.
 *
 *
 * SERVICE_THEN_COST:
 *
 *     Primero maximiza el nivel de servicio.
 *
 *     Solamente cuando dos soluciones tienen exactamente
 *     el mismo número de pedidos no atendidos se utiliza
 *     el costo como criterio de desempate.
 */
public enum ObjetivoPlanificacion {

    SERVICE_MAX,

    SERVICE_THEN_COST;

    public static ObjetivoPlanificacion parse(
            String value
    ) {

        if (
                value == null
                ||
                value.isBlank()
        ) {

            return SERVICE_THEN_COST;
        }

        String normalized =
                value
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        return switch (
                normalized
        ) {

            case "SERVICE_MAX",
                 "MAX_SERVICE",
                 "SERVICIO_MAX",
                 "MAX_SERVICIO" ->

                    SERVICE_MAX;

            case "SERVICE_THEN_COST",
                 "SERVICE_COST",
                 "SERVICIO_LUEGO_COSTO",
                 "SERVICIO_COSTO",
                 "COST" ->

                    SERVICE_THEN_COST;

            default ->

                    throw new IllegalArgumentException(
                            "Objetivo de planificación desconocido: "
                                    +
                                    value
                    );
        };
    }
}