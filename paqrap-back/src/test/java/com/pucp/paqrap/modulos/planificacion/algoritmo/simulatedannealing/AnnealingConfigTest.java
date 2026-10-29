package com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AnnealingConfigTest {

    @Test
    void rechazaParametrosTermicosNoFinitos() {
        assertThrows(IllegalArgumentException.class,
                () -> new AnnealingConfig(Double.NaN, 1.0, 0.95, 50, 100, 100));
        assertThrows(IllegalArgumentException.class,
                () -> new AnnealingConfig(100.0, Double.POSITIVE_INFINITY, 0.95, 50, 100, 100));
        assertThrows(IllegalArgumentException.class,
                () -> new AnnealingConfig(100.0, 1.0, Double.NEGATIVE_INFINITY, 50, 100, 100));
    }
}