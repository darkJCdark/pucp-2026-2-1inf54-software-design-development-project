package com.pucp.paqrap.modulos.escenarios.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulationClockTest {

    private static final Instant INICIO_SIMULADO = Instant.parse("2026-01-01T05:00:00Z");

    /** Reloj real que solo avanza cuando el test lo pide. */
    static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-10-08T12:00:00Z");

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public Instant instant() { return ahora; }

        @Override
        public ZoneId getZone() { return ZoneOffset.UTC; }

        @Override
        public Clock withZone(ZoneId zone) { return this; }
    }

    @Test
    void empiezaPausadoEnElInicioSimulado() {
        RelojManual real = new RelojManual();
        SimulationClock reloj = new SimulationClock(real, INICIO_SIMULADO, 160);

        real.avanzar(Duration.ofMinutes(10));

        assertFalse(reloj.estaEnMarcha());
        assertEquals(INICIO_SIMULADO, reloj.ahora());
    }

    @Test
    void avanzaSegunElFactorYSeCongelaAlPausar() {
        RelojManual real = new RelojManual();
        SimulationClock reloj = new SimulationClock(real, INICIO_SIMULADO, 160);
        reloj.reanudar();

        // 45 min reales x 160 = 5 días simulados
        real.avanzar(Duration.ofMinutes(45));
        assertEquals(INICIO_SIMULADO.plus(Duration.ofDays(5)), reloj.ahora());

        reloj.pausar();
        real.avanzar(Duration.ofHours(3));
        assertEquals(INICIO_SIMULADO.plus(Duration.ofDays(5)), reloj.ahora());

        reloj.reanudar();
        real.avanzar(Duration.ofSeconds(90));
        assertEquals(INICIO_SIMULADO.plus(Duration.ofDays(5)).plus(Duration.ofMinutes(240)), reloj.ahora());
    }

    @Test
    void conFactorUnoEsTiempoReal() {
        RelojManual real = new RelojManual();
        SimulationClock reloj = new SimulationClock(real, INICIO_SIMULADO, 1);
        reloj.reanudar();

        real.avanzar(Duration.ofMinutes(7));

        assertEquals(INICIO_SIMULADO.plus(Duration.ofMinutes(7)), reloj.ahora());
    }

    @Test
    void rechazaUnFactorNoPositivo() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationClock(new RelojManual(), INICIO_SIMULADO, 0));
    }
}
