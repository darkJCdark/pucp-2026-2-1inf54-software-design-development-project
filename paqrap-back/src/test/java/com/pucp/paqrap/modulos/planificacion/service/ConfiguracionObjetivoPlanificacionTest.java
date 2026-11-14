package com.pucp.paqrap.modulos.planificacion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ObjetivoPlanificacion;

/**
 * Comprueba que Spring inyecta correctamente:
 *
 * - ALNS como algoritmo;
 * - SERVICE_MAX como objetivo.
 *
 * Se utiliza el perfil "test" para que el contexto use
 * la configuración de pruebas del proyecto y no intente
 * conectarse a la BD normal de desarrollo.
 */
@SpringBootTest(
        properties = {
                "paqrap.planificacion.algoritmo=ALNS",
                "paqrap.planificacion.objetivo=SERVICE_MAX",

                "paqrap.planificacion.alns.maximum-iterations=10",
                "paqrap.planificacion.alns.maximum-iterations-without-improvement=10"
        }
)
@ActiveProfiles("test")
class ConfiguracionObjetivoPlanificacionTest {

    @Autowired
    private PlanificadorSaService planificador;

    @Test
    void springConfiguraAlns() {

        assertEquals(
                "ALNS",
                planificador.algoritmoConfigurado()
        );

        assertTrue(
                planificador.usaAlns()
        );
    }

    @Test
    void springConfiguraServiceMax() {

        assertEquals(
                ObjetivoPlanificacion.SERVICE_MAX,
                planificador.objetivoConfigurado()
        );
    }
}