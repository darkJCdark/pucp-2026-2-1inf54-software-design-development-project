package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

class SlaRescueInsertionSupportTest {

    private static final Instant T0 =
            Instant.parse(
                    "2026-01-01T05:00:00Z"
            );

    private final OperationalPlanEvaluator evaluator =
            new OperationalPlanEvaluator(
                    new RouteScheduler(
                            new RoadNetwork()
                    )
            );

    /*
     * Intencionalmente muy pequeño.
     *
     * SLA Rescue debe ignorar este límite cuando
     * el pedido está en emergencia.
     */
    private final InsertionSupport support =
            new InsertionSupport(
                    evaluator,
                    1
            );

    @Test
    void criticoAmpliaLaBusquedaYEncuentraLaMotoCuandoLosOtrosNoLlegan() {

        Fixture fixture =
                fixture();

        /*
         * Distancia = 5 km.
         *
         * Velocidades default:
         *
         * CAR  = 20 km/h -> 15 min
         * MOTO = 40 km/h -> 7.5 min
         * BICI = 14 km/h -> ~21.4 min
         *
         * Deadline = 10 min.
         *
         * Solo la moto puede cumplir.
         */

        Order critico =
                new Order(
                        "CRITICO",

                        new Location(
                                32,
                                14
                        ),

                        3,

                        T0,

                        T0.plus(
                                Duration.ofMinutes(
                                        10
                                )
                        )
                );

        DeliveryDemand demand =
                new DeliveryDemand(
                        critico,
                        critico.packages()
                );

        var resultado =
                support.bestValidatedInsertion(
                        OperationalPlan.empty(),
                        demand,
                        fixture.snapshot(),
                        List.of(
                                critico
                        ),
                        List.of(),
                        Long.MAX_VALUE
                );

        assertTrue(
                resultado.isPresent()
        );

        assertEquals(
                "TM01",
                resultado
                        .orElseThrow()
                        .vehicleId()
        );
    }

    @Test
    void criticoConVariasOpcionesATiempoSigueEligiendoLaMasBarata() {

        Fixture fixture =
                fixture();

        /*
         * Deadline 30 min.
         *
         * Los tres tipos pueden llegar.
         *
         * No necesitamos gastar moto/auto solo por llegar
         * unos minutos antes.
         */

        Order critico =
                new Order(
                        "CRITICO",

                        new Location(
                                32,
                                14
                        ),

                        3,

                        T0,

                        T0.plus(
                                Duration.ofMinutes(
                                        30
                                )
                        )
                );

        DeliveryDemand demand =
                new DeliveryDemand(
                        critico,
                        critico.packages()
                );

        var resultado =
                support.bestValidatedInsertion(
                        OperationalPlan.empty(),
                        demand,
                        fixture.snapshot(),
                        List.of(
                                critico
                        ),
                        List.of(),
                        Long.MAX_VALUE
                );

        assertTrue(
                resultado.isPresent()
        );

        assertEquals(
                "TB01",
                resultado
                        .orElseThrow()
                        .vehicleId(),
                "si varias opciones cumplen el SLA debe conservarse la alternativa económica"
        );
    }

    @Test
    void pedidoHolgadoMantieneLaPoliticaEconomica() {

        Fixture fixture =
                fixture();

        Order normal =
                new Order(
                        "NORMAL",

                        new Location(
                                32,
                                14
                        ),

                        3,

                        T0,

                        T0.plus(
                                Duration.ofHours(
                                        8
                                )
                        )
                );

        DeliveryDemand demand =
                new DeliveryDemand(
                        normal,
                        normal.packages()
                );

        var resultado =
                support.bestValidatedInsertion(
                        OperationalPlan.empty(),
                        demand,
                        fixture.snapshot(),
                        List.of(
                                normal
                        ),
                        List.of(),
                        Long.MAX_VALUE
                );

        assertTrue(
                resultado.isPresent()
        );

        assertEquals(
                "TB01",
                resultado
                        .orElseThrow()
                        .vehicleId()
        );
    }

    private Fixture fixture() {

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",

                        new Location(
                                27,
                                14
                        )
                );

        List<Vehicle> vehicles =
                List.of(
                        new Vehicle(
                                "TA01",
                                VehicleType.CAR,
                                true
                        ),

                        new Vehicle(
                                "TM01",
                                VehicleType.MOTORCYCLE,
                                true
                        ),

                        new Vehicle(
                                "TB01",
                                VehicleType.BICYCLE,
                                true
                        )
                );

        Map<
                String,
                VehicleOperationalState
                > states =
                new LinkedHashMap<>();

        for (
                Vehicle vehicle :
                vehicles
        ) {

            states.put(
                    vehicle.id(),

                    new VehicleOperationalState(
                            vehicle,
                            VehicleStatus.AVAILABLE,
                            central.location(),
                            T0
                    )
            );
        }

        OperationalSnapshot snapshot =
                new OperationalSnapshot(
                        T0,

                        FleetProfile.defaults(),

                        InventorySnapshot.from(
                                List.of(
                                        central
                                )
                        ),

                        states,

                        new MaintenanceCalendar(
                                ShiftSchedule.DEFAULT_ZONE,
                                List.of()
                        ),

                        ShiftSchedule.defaultSchedule(),

                        List.of()
                );

        return new Fixture(
                snapshot
        );
    }

    private record Fixture(
            OperationalSnapshot snapshot
    ) {
    }
}