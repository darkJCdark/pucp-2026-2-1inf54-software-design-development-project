package com.pucp.paqrap.modulos.planificacion.service;

import java.util.Locale;

/**
 * Motor heurístico utilizado por el backend.
 */
public enum AlgoritmoPlanificacion {

    SA,
    ALNS;

    public static AlgoritmoPlanificacion desde(
            String valor
    ) {

        if (
                valor == null ||
                valor.isBlank()
        ) {
            return SA;
        }

        try {

            return valueOf(
                    valor
                            .trim()
                            .toUpperCase(
                                    Locale.ROOT
                            )
            );

        } catch (
                IllegalArgumentException excepcion
        ) {

            throw new IllegalArgumentException(
                    "Algoritmo de planificación desconocido: "
                            + valor
                            + ". Use SA o ALNS.",
                    excepcion
            );
        }
    }
}