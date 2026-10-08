package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolationType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescripcionViolacionTest {

    @Test
    void cadaTipoDeViolacionOriginalTieneUnaDescripcionPropia() {
        Set<String> descripciones = new HashSet<>();
        for (PlanViolationType tipo : PlanViolationType.values()) {
            String descripcion = DescripcionViolacion.de(tipo);

            assertFalse(descripcion == null || descripcion.isBlank(), "sin descripción: " + tipo);
            assertTrue(descripciones.add(descripcion), "descripción repetida: " + descripcion);
        }
    }
}
