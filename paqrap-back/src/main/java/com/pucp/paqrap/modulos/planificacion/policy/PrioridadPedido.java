package com.pucp.paqrap.modulos.planificacion.policy;

import java.time.Duration;
import java.util.Objects;

/**
 * Resultado de evaluar la urgencia operacional
 * de un pedido en un instante determinado.
 *
 * slack =
 *
 * deadline
 * - ahora
 * - tiempo mínimo estimado para llegar y atender.
 */
public record PrioridadPedido(
        Nivel nivel,
        Duration slack,
        Duration espera,
        Duration tiempoMinimoEstimado
) {

    public PrioridadPedido {

        Objects.requireNonNull(
                nivel,
                "nivel es requerido"
        );

        Objects.requireNonNull(
                slack,
                "slack es requerido"
        );

        Objects.requireNonNull(
                espera,
                "espera es requerida"
        );

        Objects.requireNonNull(
                tiempoMinimoEstimado,
                "tiempoMinimoEstimado es requerido"
        );

        if (
                espera.isNegative()
        ) {
            throw new IllegalArgumentException(
                    "espera no puede ser negativa"
            );
        }

        if (
                tiempoMinimoEstimado.isNegative()
        ) {
            throw new IllegalArgumentException(
                    "tiempoMinimoEstimado no puede ser negativo"
            );
        }
    }

    public boolean critica() {
        return nivel ==
                Nivel.CRITICA;
    }

    public boolean urgenteOCritica() {

        return nivel ==
                Nivel.CRITICA
                ||
                nivel ==
                        Nivel.URGENTE;
    }

    public enum Nivel {

        /**
         * Incluso usando una estimación optimista,
         * el pedido está en riesgo inmediato.
         */
        CRITICA,

        /**
         * Menos de una hora de holgura.
         */
        URGENTE,

        /**
         * Entre una y tres horas de holgura.
         */
        ALTA,

        /**
         * Holgura superior a tres horas.
         */
        NORMAL
    }
}