package com.pucp.paqrap.config;

import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Parámetros del motor de escenarios ({@code paqrap.escenarios.*}).
 *
 * @param tick                    cada cuánto tiempo real revisa el motor los escenarios activos
 * @param factorCincoDias         aceleración de 5D (160 = 5 días simulados en 45 minutos reales)
 * @param factorColapso           aceleración del escenario de colapso
 * @param intervaloPlanificacion  cada cuánto tiempo simulado se pide un nuevo plan
 */
@ConfigurationProperties(prefix = "paqrap.escenarios")
public record EscenariosProperties(Duration tick, double factorCincoDias, double factorColapso,
                                   Duration intervaloPlanificacion) {

    public double factorPara(ScenarioType tipo) {
        return switch (tipo) {
            case DAY_TO_DAY -> 1.0;
            case FIVE_DAY -> factorCincoDias;
            case COLLAPSE -> factorColapso;
        };
    }
}
