package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.service.InicializadorFlota;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

class PresupuestoTiempoAlnsTest {

    private static final Instant HORA =
            Instant.parse(
                    "2026-09-09T12:00:00Z"
            );

    /*
     * Configuración deliberadamente larga.
     *
     * Sin presupuesto temporal tendría trabajo
     * para muchas iteraciones.
     */
    private static final AlnsConfig LARGA =
            new AlnsConfig(
                    4,
                    6,
                    1,
                    6,
                    1_000_000,
                    1_000_000,
                    1_000.0,
                    0.0001,
                    0.99999,
                    0.20
            );

    private final OperationalAlnsPlanner planificador =
            new OperationalAlnsPlanner(
                    new OperationalPlanEvaluator(
                            new RouteScheduler(
                                    new RoadNetwork()
                            )
                    )
            );

    @Test
    void presupuestoCortoDetieneLaBusqueda() {

        long inicio =
                System.nanoTime();

        OperationalAlnsPlanner.Ejecucion ejecucion =
                planificador.ejecutar(
                        snapshot(),
                        pedidos(),
                        List.of(),
                        LARGA,
                        new Random(
                                42L
                        ),
                        250L
                );

        long transcurridoMs =
                (
                        System.nanoTime() -
                        inicio
                ) /
                1_000_000L;

        assertTrue(
                ejecucion.iterations() <
                        LARGA.maximumIterations(),
                () ->
                        "iteraciones: " +
                                ejecucion.iterations()
        );

        /*
         * Dejamos margen grande por JVM, GC y CI.
         *
         * Lo importante es evitar que una ejecución
         * configurada con millones de iteraciones
         * se quede trabajando indefinidamente.
         */
        assertTrue(
                transcurridoMs <
                        3_000,
                () ->
                        "duración: " +
                                transcurridoMs +
                                " ms"
        );
    }

    @Test
    void resultadoSigueSiendoFactibleConPresupuestoCorto() {

        OperationalAlnsPlanner.Ejecucion ejecucion =
                planificador.ejecutar(
                        snapshot(),
                        pedidos(),
                        List.of(),
                        LARGA,
                        new Random(
                                42L
                        ),
                        200L
                );

        assertTrue(
                ejecucion
                        .resultado()
                        .esFactible(),
                () ->
                        "violaciones: " +
                                ejecucion
                                        .resultado()
                                        .evaluacion()
                                        .violations()
        );

        /*
         * El mejor resultado de ALNS comienza
         * siendo el seed.
         */
        assertTrue(
                ejecucion
                        .resultado()
                        .costoTotal()
                        <=
                        ejecucion.initialCost()
                                +
                                1e-9
        );
    }

    @Test
    void rechazaPresupuestosFueraDeRango() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        planificador.planificar(
                                snapshot(),
                                pedidos(),
                                List.of(),
                                LARGA,
                                new Random(
                                        1L
                                ),
                                0L
                        )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        planificador.planificar(
                                snapshot(),
                                pedidos(),
                                List.of(),
                                LARGA,
                                new Random(
                                        1L
                                ),
                                -1L
                        )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        planificador.planificar(
                                snapshot(),
                                pedidos(),
                                List.of(),
                                LARGA,
                                new Random(
                                        1L
                                ),
                                OperationalAlnsPlanner
                                        .PRESUPUESTO_MAXIMO_MS
                                        +
                                        1
                        )
        );
    }

    /*
     * =====================================================
     * FIXTURES
     * =====================================================
     */

    private static OperationalSnapshot snapshot() {

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",
                        new Location(
                                27,
                                14
                        )
                );

        List<Warehouse> almacenes =
                List.of(
                        central,

                        Warehouse.intermediate(
                                "NOROESTE",
                                new Location(
                                        12,
                                        38
                                ),
                                1_000
                        ),

                        Warehouse.intermediate(
                                "ESTE",
                                new Location(
                                        57,
                                        27
                                ),
                                1_000
                        )
                );

        Map<
                String,
                VehicleOperationalState
                > estados =
                new LinkedHashMap<>();

        for (
                Vehicle vehiculo :
                InicializadorFlota
                        .crearFlotaInicial()
        ) {

            estados.put(
                    vehiculo.id(),

                    new VehicleOperationalState(
                            vehiculo,
                            VehicleStatus.AVAILABLE,
                            central.location(),
                            HORA
                    )
            );
        }

        return new OperationalSnapshot(
                HORA,
                FleetProfile.defaults(),
                InventorySnapshot.from(
                        almacenes
                ),
                estados,
                new MaintenanceCalendar(
                        ShiftSchedule.DEFAULT_ZONE,
                        List.of()
                ),
                ShiftSchedule.defaultSchedule(),
                List.of()
        );
    }

    private static List<Order> pedidos() {

        Instant plazo =
                HORA.plusSeconds(
                        10 * 3600
                );

        return List.of(
                new Order(
                        "P01",
                        new Location(
                                31,
                                14
                        ),
                        12,
                        HORA,
                        plazo
                ),

                new Order(
                        "P02",
                        new Location(
                                27,
                                18
                        ),
                        8,
                        HORA,
                        plazo
                ),

                new Order(
                        "P03",
                        new Location(
                                29,
                                16
                        ),
                        4,
                        HORA,
                        plazo
                ),

                new Order(
                        "P04",
                        new Location(
                                35,
                                20
                        ),
                        6,
                        HORA,
                        plazo
                ),

                new Order(
                        "P05",
                        new Location(
                                22,
                                10
                        ),
                        10,
                        HORA,
                        plazo
                ),

                new Order(
                        "P06",
                        new Location(
                                30,
                                9
                        ),
                        2,
                        HORA,
                        plazo
                )
        );
    }
}