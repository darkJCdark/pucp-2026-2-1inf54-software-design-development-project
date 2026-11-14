package com.pucp.paqrap.modulos.planificacion.algoritmo.common;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.redvial.entity.Location;

class CriterioObjetivoPlanificacionTest {

    private static final Instant T0 =
            Instant.parse(
                    "2026-01-01T05:00:00Z"
            );

    @Test
    void serviceMaxPrefiereAtenderMasAunqueCuesteMuchoMas() {

        CriterioObjetivoPlanificacion criterio =
                new CriterioObjetivoPlanificacion(
                        ObjetivoPlanificacion.SERVICE_MAX
                );

        ResultadoPlanificacion baratoPeroPeorServicio =
                resultado(
                        100.0,
                        pedido(
                                "P1"
                        )
                );

        ResultadoPlanificacion caroPeroMejorServicio =
                resultado(
                        10_000.0
                );

        assertTrue(
                criterio.esMejor(
                        caroPeroMejorServicio,
                        baratoPeroPeorServicio
                )
        );

        assertFalse(
                criterio.esMejor(
                        baratoPeroPeorServicio,
                        caroPeroMejorServicio
                )
        );
    }

    @Test
    void serviceMaxIgnoraCostoCuandoElServicioEsIgual() {

        CriterioObjetivoPlanificacion criterio =
                new CriterioObjetivoPlanificacion(
                        ObjetivoPlanificacion.SERVICE_MAX
                );

        ResultadoPlanificacion barato =
                resultado(
                        100.0
                );

        ResultadoPlanificacion caro =
                resultado(
                        50_000.0
                );

        assertTrue(
                criterio.esEquivalente(
                        barato,
                        caro
                )
        );

        assertEquals(
                0,
                criterio.comparar(
                        barato,
                        caro
                )
        );
    }

    @Test
    void serviceThenCostTambienPriorizaPrimeroElServicio() {

        CriterioObjetivoPlanificacion criterio =
                new CriterioObjetivoPlanificacion(
                        ObjetivoPlanificacion
                                .SERVICE_THEN_COST
                );

        ResultadoPlanificacion baratoPeroConPendiente =
                resultado(
                        10.0,
                        pedido(
                                "P1"
                        )
                );

        ResultadoPlanificacion caroSinPendientes =
                resultado(
                        100_000.0
                );

        assertTrue(
                criterio.esMejor(
                        caroSinPendientes,
                        baratoPeroConPendiente
                )
        );
    }

    @Test
    void serviceThenCostUsaCostoCuandoServicioEmpata() {

        CriterioObjetivoPlanificacion criterio =
                new CriterioObjetivoPlanificacion(
                        ObjetivoPlanificacion
                                .SERVICE_THEN_COST
                );

        ResultadoPlanificacion barato =
                resultado(
                        100.0
                );

        ResultadoPlanificacion caro =
                resultado(
                        200.0
                );

        assertTrue(
                criterio.esMejor(
                        barato,
                        caro
                )
        );

        assertFalse(
                criterio.esMejor(
                        caro,
                        barato
                )
        );
    }

    @Test
    void nuncaAceptaPerderServicioAunqueLaTemperaturaSeaEnorme() {

        CriterioObjetivoPlanificacion criterio =
                new CriterioObjetivoPlanificacion(
                        ObjetivoPlanificacion
                                .SERVICE_THEN_COST
                );

        ResultadoPlanificacion actual =
                resultado(
                        10_000.0
                );

        ResultadoPlanificacion candidatoBaratisimoPeroPierdePedido =
                resultado(
                        1.0,
                        pedido(
                                "P1"
                        )
                );

        assertFalse(
                criterio.aceptar(
                        candidatoBaratisimoPeroPierdePedido,
                        actual,
                        1_000_000_000.0,
                        random(
                                7L
                        )
                )
        );
    }

    @Test
    void serviceMaxAceptaExplorarOtraSolucionConMismoServicio() {

        CriterioObjetivoPlanificacion criterio =
                new CriterioObjetivoPlanificacion(
                        ObjetivoPlanificacion.SERVICE_MAX
                );

        ResultadoPlanificacion actual =
                resultado(
                        100.0
                );

        ResultadoPlanificacion candidatoMuchoMasCaro =
                resultado(
                        100_000.0
                );

        assertTrue(
                criterio.aceptar(
                        candidatoMuchoMasCaro,
                        actual,
                        1.0,
                        random(
                                3L
                        )
                )
        );
    }

    @Test
    void serviceThenCostAceptaSiempreMenorCostoConMismoServicio() {

        CriterioObjetivoPlanificacion criterio =
                new CriterioObjetivoPlanificacion(
                        ObjetivoPlanificacion
                                .SERVICE_THEN_COST
                );

        ResultadoPlanificacion actual =
                resultado(
                        500.0
                );

        ResultadoPlanificacion candidato =
                resultado(
                        400.0
                );

        assertTrue(
                criterio.aceptar(
                        candidato,
                        actual,
                        1.0,
                        random(
                                11L
                        )
                )
        );
    }

    @Test
    void parseAceptaLosDosModosPrincipales() {

        assertEquals(
                ObjetivoPlanificacion.SERVICE_MAX,

                ObjetivoPlanificacion.parse(
                        "SERVICE_MAX"
                )
        );

        assertEquals(
                ObjetivoPlanificacion.SERVICE_THEN_COST,

                ObjetivoPlanificacion.parse(
                        "SERVICE_THEN_COST"
                )
        );

        assertEquals(
                ObjetivoPlanificacion.SERVICE_MAX,

                ObjetivoPlanificacion.parse(
                        "servicio_max"
                )
        );
    }

    @Test
    void parseUsaServiceThenCostComoDefault() {

        assertEquals(
                ObjetivoPlanificacion.SERVICE_THEN_COST,

                ObjetivoPlanificacion.parse(
                        null
                )
        );

        assertEquals(
                ObjetivoPlanificacion.SERVICE_THEN_COST,

                ObjetivoPlanificacion.parse(
                        ""
                )
        );
    }

    @Test
    void parseRechazaObjetivoDesconocido() {

        assertThrows(
                IllegalArgumentException.class,

                () ->
                        ObjetivoPlanificacion.parse(
                                "NO_EXISTE"
                        )
        );
    }

    /*
     * =====================================================
     * FIXTURES
     * =====================================================
     */

    private ResultadoPlanificacion resultado(
            double costo,
            Order... noAtendidos
    ) {

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",

                        new Location(
                                0,
                                0
                        )
                );

        InventorySnapshot inventory =
                InventorySnapshot.from(
                        List.of(
                                central
                        )
                );

        PlanEvaluation evaluation =
                new PlanEvaluation(
                        Map.of(),
                        List.of(),
                        inventory,
                        costo
                );

        return new ResultadoPlanificacion(
                OperationalPlan.empty(),
                evaluation,
                List.of(
                        noAtendidos
                )
        );
    }

    private Order pedido(
            String id
    ) {

        return new Order(
                id,

                new Location(
                        1,
                        1
                ),

                1,

                T0,

                T0.plus(
                        Duration.ofHours(
                                8
                        )
                )
        );
    }

    private RandomGenerator random(
            long seed
    ) {

        return RandomGeneratorFactory
                .<RandomGenerator>of(
                        "L64X128MixRandom"
                )
                .create(
                        seed
                );
    }
}