package com.pucp.paqrap.modulos.escenarios.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Reloj simulado de un escenario: el tiempo simulado avanza {@code factor} veces más rápido que el real
 * (1 = tiempo real) y se congela mientras está pausado. Se crea pausado; {@link #reanudar()} lo pone en marcha.
 * No es seguro entre hilos por sí solo: el motor de escenarios sincroniza su uso.
 */
public final class SimulationClock {

    private final Clock relojReal;
    private final Instant inicioSimulado;
    private final double factor;
    /** Tiempo real acumulado en marcha antes del último reanudar. */
    private Duration realAcumulado = Duration.ZERO;
    /** Momento real del último reanudar; null mientras está pausado. */
    private Instant enMarchaDesde;

    public SimulationClock(Clock relojReal, Instant inicioSimulado, double factor) {
        this.relojReal = Objects.requireNonNull(relojReal, "relojReal is required");
        this.inicioSimulado = Objects.requireNonNull(inicioSimulado, "inicioSimulado is required");
        if (factor <= 0) {
            throw new IllegalArgumentException("El factor de aceleración debe ser positivo");
        }
        this.factor = factor;
    }

    public void reanudar() {
        if (enMarchaDesde == null) {
            enMarchaDesde = relojReal.instant();
        }
    }

    public void pausar() {
        if (enMarchaDesde != null) {
            realAcumulado = realAcumulado.plus(Duration.between(enMarchaDesde, relojReal.instant()));
            enMarchaDesde = null;
        }
    }

    public boolean estaEnMarcha() {
        return enMarchaDesde != null;
    }

    /** Instante simulado actual. */
    public Instant ahora() {
        Duration real = enMarchaDesde == null
                ? realAcumulado
                : realAcumulado.plus(Duration.between(enMarchaDesde, relojReal.instant()));
        return inicioSimulado.plusNanos((long) (real.toNanos() * factor));
    }

    public Instant inicioSimulado() { return inicioSimulado; }
    public double factor() { return factor; }
}
