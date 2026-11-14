package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

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
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ObjetivoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

class OperationalAlnsObjectivePlannerTest {

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

    private final OperationalAlnsObjectivePlanner objectivePlanner =
            new OperationalAlnsObjectivePlanner(
                    evaluator
            );

    private final OperationalAlnsPlanner normalPlanner =
            new OperationalAlnsPlanner(
                    evaluator
            );

    @Test
    void serviceThenCostConservaElComportamientoNormalDeAlns() {

        Fixture fixture =
                fixture();

        List<Order> orders =
                List.of(
                        order(
                                "P1",
                                30,
                                14,
                                3,
                                Duration.ofHours(
                                        8
                                )
                        ),

                        order(
                                "P2",
                                31,
                                14,
                                3,
                                Duration.ofHours(
                                        8
                                )
                        )
                );

        AlnsConfig config =
                config();

        ResultadoPlanificacion esperado =
                normalPlanner.planificar(
                        fixture.snapshot(),
                        orders,
                        List.of(),
                        config,
                        random(
                                42L
                        )
                );

        ResultadoPlanificacion obtenido =
                objectivePlanner.planificar(
                        fixture.snapshot(),
                        orders,
                        List.of(),
                        config,
                        random(
                                42L
                        ),
                        ObjetivoPlanificacion.SERVICE_THEN_COST
                );

        assertEquals(
                esperado
                        .noAtendidos()
                        .size(),

                obtenido
                        .noAtendidos()
                        .size()
        );

        assertEquals(
                esperado.costoTotal(),
                obtenido.costoTotal(),
                0.000001
        );
    }

    @Test
    void serviceMaxNuncaDevuelveMenorNivelDeServicioQueAlnsBase() {

        Fixture fixture =
                fixture();

        List<Order> orders =
                List.of(
                        order(
                                "P1",
                                30,
                                14,
                                3,
                                Duration.ofHours(
                                        8
                                )
                        ),

                        order(
                                "P2",
                                35,
                                14,
                                4,
                                Duration.ofHours(
                                        5
                                )
                        ),

                        order(
                                "P3",
                                40,
                                14,
                                5,
                                Duration.ofHours(
                                        5
                                )
                        ),

                        order(
                                "P4",
                                45,
                                14,
                                3,
                                Duration.ofHours(
                                        4
                                )
                        )
                );

        AlnsConfig config =
                config();

        ResultadoPlanificacion base =
                normalPlanner.planificar(
                        fixture.snapshot(),
                        orders,
                        List.of(),
                        config,
                        random(
                                11L
                        )
                );

        ResultadoPlanificacion serviceMax =
                objectivePlanner.planificar(
                        fixture.snapshot(),
                        orders,
                        List.of(),
                        config,
                        random(
                                11L
                        ),
                        ObjetivoPlanificacion.SERVICE_MAX
                );

        assertTrue(
                serviceMax
                        .noAtendidos()
                        .size()
                        <=
                        base
                                .noAtendidos()
                                .size(),

                "SERVICE_MAX nunca debe dejar más pedidos fuera que ALNS base"
        );
    }

    @Test
    void serviceMaxMantieneFactibilidadOperacional() {

        Fixture fixture =
                fixture();

        List<Order> orders =
                List.of(
                        order(
                                "P1",
                                30,
                                14,
                                3,
                                Duration.ofHours(
                                        8
                                )
                        ),

                        order(
                                "P2",
                                36,
                                14,
                                6,
                                Duration.ofHours(
                                        6
                                )
                        )
                );

        ResultadoPlanificacion resultado =
                objectivePlanner.planificar(
                        fixture.snapshot(),
                        orders,
                        List.of(),
                        config(),
                        random(
                                7L
                        ),
                        ObjetivoPlanificacion.SERVICE_MAX
                );

        assertTrue(
                resultado.esFactible()
        );
    }

    @Test
    void versionConPresupuestoTambienConservaServicio() {

        Fixture fixture =
                fixture();

        List<Order> orders =
                List.of(
                        order(
                                "P1",
                                30,
                                14,
                                3,
                                Duration.ofHours(
                                        8
                                )
                        ),

                        order(
                                "P2",
                                32,
                                14,
                                3,
                                Duration.ofHours(
                                        8
                                )
                        ),

                        order(
                                "P3",
                                34,
                                14,
                                3,
                                Duration.ofHours(
                                        8
                                )
                        )
                );

        ResultadoPlanificacion resultado =
                objectivePlanner.planificar(
                        fixture.snapshot(),
                        orders,
                        List.of(),
                        config(),
                        random(
                                9L
                        ),
                        1_000L,
                        ObjetivoPlanificacion.SERVICE_MAX
                );

        assertTrue(
                resultado.esFactible()
        );

        assertTrue(
                resultado
                        .noAtendidos()
                        .size()
                        <=
                        orders.size()
        );
    }

    /*
     * =====================================================
     * CONFIGURACIÓN
     * =====================================================
     */

    private AlnsConfig config() {

        return new AlnsConfig(
                4,
                4,
                1,
                2,
                50,
                50,
                100.0,
                1.0,
                0.95,
                0.20
        );
    }

    /*
     * =====================================================
     * FIXTURE
     * =====================================================
     */

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
                                "TA02",
                                VehicleType.CAR,
                                true
                        ),

                        new Vehicle(
                                "TM01",
                                VehicleType.MOTORCYCLE,
                                true
                        ),

                        new Vehicle(
                                "TM02",
                                VehicleType.MOTORCYCLE,
                                true
                        ),

                        new Vehicle(
                                "TB01",
                                VehicleType.BICYCLE,
                                true
                        ),

                        new Vehicle(
                                "TB02",
                                VehicleType.BICYCLE,
                                true
                        )
                );

        Map<String, VehicleOperationalState> states =
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

    private Order order(
            String id,
            int x,
            int y,
            int packages,
            Duration deadline
    ) {

        return new Order(
                id,

                new Location(
                        x,
                        y
                ),

                packages,

                T0,

                T0.plus(
                        deadline
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

    private record Fixture(
            OperationalSnapshot snapshot
    ) {
    }
}