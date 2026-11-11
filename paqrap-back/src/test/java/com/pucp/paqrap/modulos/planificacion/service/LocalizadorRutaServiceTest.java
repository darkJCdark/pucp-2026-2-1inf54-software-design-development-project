package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadLeg;
import com.pucp.paqrap.modulos.redvial.entity.RoadPath;

class LocalizadorRutaServiceTest {

    private static final Instant T0 =
            Instant.parse(
                    "2026-01-01T05:00:00Z"
            );

    private final LocalizadorRutaService localizador =
            new LocalizadorRutaService();

    @Test
    void enTransitoSoloPermiteDesvioDesdeElSiguienteNodo() {

        ScheduledDeliveryRoute programada =
                rutaConRefrigerio();

        /*
         * 05:01:30
         *
         * Está entre:
         *
         * (28,14) -> (29,14)
         */
        Instant ahora =
                T0.plusSeconds(
                        90
                );

        PosicionRutaEnInstante posicion =
                localizador.localizar(
                        programada,
                        ahora
                );

        assertEquals(
                EstadoRutaEnInstante.EN_TRANSITO,
                posicion.estado()
        );

        assertEquals(
                new Location(
                        28,
                        14
                ),
                posicion.ultimoNodoSeguro()
        );

        assertEquals(
                new Location(
                        29,
                        14
                ),
                posicion.proximoNodoSeguro()
        );

        /*
         * Ese RoadLeg termina a 05:02.
         */
        assertEquals(
                T0.plusSeconds(
                        120
                ),
                posicion.desviableDesde()
        );

        assertTrue(
                posicion
                        .tramoActual()
                        .isPresent()
        );

        assertFalse(
                posicion
                        .puedeDesviarseAhora(
                                ahora
                        )
        );

        assertEquals(
                0,
                posicion.indiceParadaComprometida()
        );

        assertEquals(
                1,
                posicion.primeraParadaPlanificada()
        );

        assertEquals(
                List.of(
                        EstadoCompromisoParada
                                .COMPROMETIDA,

                        EstadoCompromisoParada
                                .PLANIFICADA
                ),

                posicion.estadosParadas()
        );
    }

    @Test
    void duranteServicioPermaneceEnElCliente() {

        ScheduledDeliveryRoute programada =
                rutaConRefrigerio();

        /*
         * P1 llega 05:04:30.
         *
         * A las 05:05 está realizando servicio.
         */
        Instant ahora =
                T0.plusSeconds(
                        5 * 60
                );

        PosicionRutaEnInstante posicion =
                localizador.localizar(
                        programada,
                        ahora
                );

        assertEquals(
                EstadoRutaEnInstante.EN_SERVICIO,
                posicion.estado()
        );

        assertEquals(
                new Location(
                        30,
                        14
                ),
                posicion.ultimoNodoSeguro()
        );

        assertEquals(
                new Location(
                        30,
                        14
                ),
                posicion.proximoNodoSeguro()
        );

        /*
         * Servicio:
         *
         * 05:04:30 -> 06:04:30
         */
        assertEquals(
                T0.plusSeconds(
                        64 * 60 +
                                30
                ),
                posicion.desviableDesde()
        );

        assertEquals(
                0,
                posicion.indiceParadaComprometida()
        );
    }

    @Test
    void duranteRefrigerioDebeTerminarloAntesDeDesviarse() {

        ScheduledDeliveryRoute programada =
                rutaConRefrigerio();

        /*
         * Refrigerio:
         *
         * 06:04:30 -> 07:04:30
         */
        Instant ahora =
                T0.plusSeconds(
                        70 * 60
                );

        PosicionRutaEnInstante posicion =
                localizador.localizar(
                        programada,
                        ahora
                );

        assertEquals(
                EstadoRutaEnInstante.EN_REFRIGERIO,
                posicion.estado()
        );

        assertEquals(
                new Location(
                        30,
                        14
                ),
                posicion.proximoNodoSeguro()
        );

        assertEquals(
                T0.plusSeconds(
                        124 * 60 +
                                30
                ),
                posicion.desviableDesde()
        );

        assertFalse(
                posicion
                        .puedeDesviarseAhora(
                                ahora
                        )
        );
    }

    @Test
    void despuesDeLaPrimeraParadaLaSegundaQuedaComprometida() {

        ScheduledDeliveryRoute programada =
                rutaConRefrigerio();

        /*
         * 07:05.
         *
         * P1 ya terminó.
         *
         * El vehículo ya inició el viaje hacia P2.
         */
        Instant ahora =
                T0.plusSeconds(
                        125 * 60
                );

        PosicionRutaEnInstante posicion =
                localizador.localizar(
                        programada,
                        ahora
                );

        assertEquals(
                EstadoRutaEnInstante.EN_TRANSITO,
                posicion.estado()
        );

        assertEquals(
                List.of(
                        EstadoCompromisoParada
                                .EJECUTADA,

                        EstadoCompromisoParada
                                .COMPROMETIDA
                ),

                posicion.estadosParadas()
        );

        assertEquals(
                1,
                posicion.indiceParadaComprometida()
        );

        assertEquals(
                2,
                posicion.primeraParadaPlanificada()
        );
    }

    @Test
    void alFinalTodaLaRutaEstaEjecutada() {

        ScheduledDeliveryRoute programada =
                rutaConRefrigerio();

        Instant ahora =
                programada.completedAt();

        PosicionRutaEnInstante posicion =
                localizador.localizar(
                        programada,
                        ahora
                );

        assertEquals(
                EstadoRutaEnInstante.TERMINADA,
                posicion.estado()
        );

        assertEquals(
                List.of(
                        EstadoCompromisoParada
                                .EJECUTADA,

                        EstadoCompromisoParada
                                .EJECUTADA
                ),

                posicion.estadosParadas()
        );

        assertEquals(
                -1,
                posicion.indiceParadaComprometida()
        );

        assertEquals(
                2,
                posicion.primeraParadaPlanificada()
        );
    }

    /*
     * =====================================================
     * FIXTURE
     * =====================================================
     */

    private ScheduledDeliveryRoute rutaConRefrigerio() {

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",
                        new Location(
                                27,
                                14
                        )
                );

        Vehicle vehiculo =
                new Vehicle(
                        "TA01",
                        VehicleType.CAR,
                        true
                );

        Order p1 =
                new Order(
                        "P1",
                        new Location(
                                30,
                                14
                        ),
                        5,
                        T0,
                        T0.plusSeconds(
                                12 * 3600
                        )
                );

        Order p2 =
                new Order(
                        "P2",
                        new Location(
                                32,
                                14
                        ),
                        2,
                        T0,
                        T0.plusSeconds(
                                12 * 3600
                        )
                );

        DeliveryStop s1 =
                new DeliveryStop(
                        p1,
                        5
                );

        DeliveryStop s2 =
                new DeliveryStop(
                        p2,
                        2
                );

        DeliveryRoute ruta =
                DeliveryRoute
                        .startScenarioAtCentral(
                                "ALNS-TA01",
                                vehiculo,
                                central,
                                7,
                                T0
                        )
                        .withAppendedStop(
                                s1
                        )
                        .withAppendedStop(
                                s2
                        );

        /*
         * =================================================
         * CAMINO A P1
         * =================================================
         */

        RoadPath a1 =
                new RoadPath(
                        new Location(
                                27,
                                14
                        ),

                        new Location(
                                30,
                                14
                        ),

                        T0,

                        List.of(
                                new RoadLeg(
                                        new Location(
                                                27,
                                                14
                                        ),
                                        new Location(
                                                28,
                                                14
                                        ),
                                        T0,
                                        T0.plusSeconds(
                                                60
                                        )
                                ),

                                new RoadLeg(
                                        new Location(
                                                28,
                                                14
                                        ),
                                        new Location(
                                                29,
                                                14
                                        ),
                                        T0.plusSeconds(
                                                60
                                        ),
                                        T0.plusSeconds(
                                                120
                                        )
                                ),

                                new RoadLeg(
                                        new Location(
                                                29,
                                                14
                                        ),
                                        new Location(
                                                30,
                                                14
                                        ),
                                        T0.plusSeconds(
                                                120
                                        ),
                                        T0.plusSeconds(
                                                270
                                        )
                                )
                        )
                );

        Instant llegada1 =
                T0.plusSeconds(
                        270
                );

        Instant finServicio1 =
                llegada1.plusSeconds(
                        3600
                );

        Instant finRefrigerio1 =
                finServicio1.plusSeconds(
                        3600
                );

        ScheduledRouteStop p1Programada =
                new ScheduledRouteStop(
                        s1,
                        a1,
                        llegada1,
                        finRefrigerio1,
                        7,
                        2
                );

        /*
         * =================================================
         * CAMINO A P2
         * =================================================
         */

        RoadPath a2 =
                new RoadPath(
                        new Location(
                                30,
                                14
                        ),

                        new Location(
                                32,
                                14
                        ),

                        finRefrigerio1,

                        List.of(
                                new RoadLeg(
                                        new Location(
                                                30,
                                                14
                                        ),
                                        new Location(
                                                31,
                                                14
                                        ),
                                        finRefrigerio1,
                                        finRefrigerio1
                                                .plusSeconds(
                                                        60
                                                )
                                ),

                                new RoadLeg(
                                        new Location(
                                                31,
                                                14
                                        ),
                                        new Location(
                                                32,
                                                14
                                        ),
                                        finRefrigerio1
                                                .plusSeconds(
                                                        60
                                                ),
                                        finRefrigerio1
                                                .plusSeconds(
                                                        120
                                                )
                                )
                        )
                );

        Instant llegada2 =
                finRefrigerio1
                        .plusSeconds(
                                120
                        );

        Instant fin2 =
                llegada2.plusSeconds(
                        3600
                );

        ScheduledRouteStop p2Programada =
                new ScheduledRouteStop(
                        s2,
                        a2,
                        llegada2,
                        fin2,
                        2,
                        0
                );

        return new ScheduledDeliveryRoute(
                ruta,

                List.of(
                        p1Programada,
                        p2Programada
                ),

                fin2,

                5.0,

                40.0
        );
    }
}