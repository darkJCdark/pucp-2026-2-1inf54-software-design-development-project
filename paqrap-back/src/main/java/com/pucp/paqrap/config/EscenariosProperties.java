package com.pucp.paqrap.config;

import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

/** Parámetros del motor de escenarios ({@code paqrap.escenarios.*}). */
@ConfigurationProperties(prefix = "paqrap.escenarios")
public record EscenariosProperties(
        Duration tick,
        double factorCincoDias,
        double factorColapso,
        Duration intervaloPlanificacion,
        Duration intervaloRevisionRolling,
        Duration ventanaRiesgoRolling,
        Duration horizonteDecision,
        boolean replanificarFinRuta
) {
    @ConstructorBinding
    public EscenariosProperties {
        tick = positiveOrDefault(tick, Duration.ofSeconds(1));
        intervaloPlanificacion = positiveOrDefault(intervaloPlanificacion, Duration.ofHours(1));
        intervaloRevisionRolling = positiveOrDefault(intervaloRevisionRolling, Duration.ofMinutes(15));
        ventanaRiesgoRolling = positiveOrDefault(ventanaRiesgoRolling, Duration.ofMinutes(60));
        horizonteDecision = positiveOrDefault(horizonteDecision, Duration.ofHours(36));
        if (!(factorCincoDias > 0)) {
            factorCincoDias = 160;
        }
        if (!(factorColapso > 0)) {
            factorColapso = 1440;
        }
    }

    /** Conserva compatibilidad con tests y configuración anteriores. */
    public EscenariosProperties(Duration tick, double factorCincoDias, double factorColapso,
                                 Duration intervaloPlanificacion) {
        this(tick, factorCincoDias, factorColapso, intervaloPlanificacion,
                Duration.ofMinutes(15), Duration.ofMinutes(60), Duration.ofHours(36), true);
    }

    public double factorPara(ScenarioType tipo) {
        return switch (tipo) {
            case DAY_TO_DAY -> 1.0;
            case FIVE_DAY -> factorCincoDias;
            case COLLAPSE -> factorColapso;
        };
    }

    private static Duration positiveOrDefault(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
