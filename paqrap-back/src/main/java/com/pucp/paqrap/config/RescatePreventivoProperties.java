package com.pucp.paqrap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Parámetros configurables del rescate preventivo y los transbordos. */
@ConfigurationProperties(prefix = "paqrap.planificacion.rescate-preventivo")
public record RescatePreventivoProperties(
        boolean enabled,
        boolean transbordosEnabled,
        Duration ventanaRiesgo,
        Duration mejoraMinimaEta,
        Duration esperaMaximaEncuentro,
        Duration cooldownReasignacion,
        int maxReasignacionesPorPedido,
        int maxRescatesPorCiclo,
        double fraccionPresupuesto,
        Duration presupuestoMinimo,
        Duration presupuestoMaximo
) {
    public RescatePreventivoProperties {
        ventanaRiesgo = positiveOrDefault(ventanaRiesgo, Duration.ofMinutes(60));
        mejoraMinimaEta = positiveOrDefault(mejoraMinimaEta, Duration.ofMinutes(15));
        esperaMaximaEncuentro = positiveOrDefault(esperaMaximaEncuentro, Duration.ofMinutes(15));
        cooldownReasignacion = positiveOrDefault(cooldownReasignacion, Duration.ofMinutes(30));
        maxReasignacionesPorPedido = positiveOrDefault(maxReasignacionesPorPedido, 2);
        maxRescatesPorCiclo = positiveOrDefault(maxRescatesPorCiclo, 1);
        fraccionPresupuesto = fraccionPresupuesto > 0.0 && fraccionPresupuesto <= 1.0
                ? fraccionPresupuesto : 0.10;
        presupuestoMinimo = positiveOrDefault(presupuestoMinimo, Duration.ofMillis(25));
        presupuestoMaximo = positiveOrDefault(presupuestoMaximo, Duration.ofMillis(150));
        if (presupuestoMaximo.compareTo(presupuestoMinimo) < 0) {
            presupuestoMaximo = presupuestoMinimo;
        }
    }

    public static RescatePreventivoProperties defaults() {
        return new RescatePreventivoProperties(
                true, true,
                Duration.ofMinutes(60), Duration.ofMinutes(15),
                Duration.ofMinutes(15), Duration.ofMinutes(30),
                2, 1, 0.10,
                Duration.ofMillis(25), Duration.ofMillis(150));
    }

    public long presupuestoPara(long presupuestoTotalMs) {
        long proporcional = Math.max(1L, Math.round(presupuestoTotalMs * fraccionPresupuesto));
        long minimo = Math.max(1L, presupuestoMinimo.toMillis());
        long maximo = Math.max(minimo, presupuestoMaximo.toMillis());
        return Math.min(maximo, Math.max(minimo, proporcional));
    }

    private static Duration positiveOrDefault(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private static int positiveOrDefault(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
