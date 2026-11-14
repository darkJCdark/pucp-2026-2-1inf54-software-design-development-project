package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

class ServiceFirstSeedRescueTest {

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

    private final ServiceFirstSeedRescue rescue =
            new ServiceFirstSeedRescue(
                    evaluator
            );

    @Test
    void rescataPedidoFactibleAunqueElSeedLoHayaDejadoFuera() {

        Fixture fixture =
                fixture();

        Order pedido =
                new Order(
                        "P1",

                        new Location(
                                30,
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

        ServiceFirstSeedRescue.Resultado resultado =
                rescue.rescatar(
                        OperationalPlan.empty(),
                        List.of(),
                        List.of(
                                pedido
                        ),
                        fixture.snapshot(),
                        List.of(),
                        Long.MAX_VALUE
                );

        assertTrue(
                resultado
                        .noAtendidos()
                        .isEmpty()
        );

        assertEquals(
                List.of(
                        "P1"
                ),
                resultado.pedidosRescatados()
        );

        assertEquals(
                1,
                resultado.cantidadRescatados()
        );

        assertTrue(
                contienePedido(
                        resultado.plan(),
                        "P1"
                )
        );

        assertTrue(
                resultado
                        .evaluacion()
                        .isFeasible()
        );
    }

    @Test
    void noSacrificaPedidoYaAtendidoParaRescatarOtro() {

        Fixture fixture =
                fixture();

        Order atendido =
                new Order(
                        "YA_ATENDIDO",

                        new Location(
                                28,
                                14
                        ),

                        2,

                        T0,

                        T0.plus(
                                Duration.ofHours(
                                        8
                                )
                        )
                );

        DeliveryDemand demandaAtendida =
                new DeliveryDemand(
                        atendido,
                        atendido.packages()
                );

        InsertionSupport insertion =
                new InsertionSupport(
                        evaluator,
                        Integer.MAX_VALUE
                );

        OperationalPlan seed =
                insertion
                        .bestValidatedInsertion(
                                OperationalPlan.empty(),
                                demandaAtendida,
                                fixture.snapshot(),
                                List.of(
                                        atendido
                                ),
                                List.of(),
                                Long.MAX_VALUE
                        )
                        .orElseThrow()
                        .plan();

        Order nuevo =
                new Order(
                        "NUEVO",

                        new Location(
                                31,
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

        ServiceFirstSeedRescue.Resultado resultado =
                rescue.rescatar(
                        seed,
                        List.of(
                                atendido
                        ),
                        List.of(
                                nuevo
                        ),
                        fixture.snapshot(),
                        List.of(),
                        Long.MAX_VALUE
                );

        assertTrue(
                contienePedido(
                        resultado.plan(),
                        "YA_ATENDIDO"
                )
        );

        assertTrue(
                contienePedido(
                        resultado.plan(),
                        "NUEVO"
                )
        );

        assertTrue(
                resultado
                        .noAtendidos()
                        .isEmpty()
        );
    }

    @Test
    void mantieneComoNoAtendidoUnPedidoFisicamenteImposible() {

        Fixture fixture =
                fixture();

        /*
         * 40 km aproximadamente desde el Central,
         * deadline de 1 minuto.
         *
         * Ningún vehículo puede llegar físicamente.
         */
        Order imposible =
                new Order(
                        "IMPOSIBLE",

                        new Location(
                                67,
                                14
                        ),

                        1,

                        T0,

                        T0.plus(
                                Duration.ofMinutes(
                                        1
                                )
                        )
                );

        ServiceFirstSeedRescue.Resultado resultado =
                rescue.rescatar(
                        OperationalPlan.empty(),
                        List.of(),
                        List.of(
                                imposible
                        ),
                        fixture.snapshot(),
                        List.of(),
                        Long.MAX_VALUE
                );

        assertEquals(
                List.of(
                        imposible
                ),
                resultado.noAtendidos()
        );

        assertTrue(
                resultado
                        .pedidosRescatados()
                        .isEmpty()
        );

        assertFalse(
                contienePedido(
                        resultado.plan(),
                        "IMPOSIBLE"
                )
        );
    }

    @Test
    void priorizaPedidoConMenorSlackEntreLosNoAtendidos() {

        Fixture fixture =
                fixture();

        Order flexible =
                new Order(
                        "FLEXIBLE",

                        new Location(
                                30,
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

        Order urgente =
                new Order(
                        "URGENTE",

                        new Location(
                                29,
                                14
                        ),

                        3,

                        T0,

                        T0.plus(
                                Duration.ofHours(
                                        1
                                )
                        )
                );

        ServiceFirstSeedRescue.Resultado resultado =
                rescue.rescatar(
                        OperationalPlan.empty(),
                        List.of(),

                        /*
                         * Orden deliberadamente contrario.
                         */
                        List.of(
                                flexible,
                                urgente
                        ),

                        fixture.snapshot(),
                        List.of(),
                        Long.MAX_VALUE
                );

        assertFalse(
                resultado
                        .pedidosRescatados()
                        .isEmpty()
        );

        assertEquals(
                "URGENTE",
                resultado
                        .pedidosRescatados()
                        .getFirst(),
                "el rescate debe procesar primero el pedido con mayor riesgo SLA"
        );
    }

    private boolean contienePedido(
            OperationalPlan plan,
            String id
    ) {

        return plan
                .routes()
                .stream()

                .flatMap(
                        route ->
                                route
                                        .stops()
                                        .stream()
                )

                .filter(
                        DeliveryStop
                                .class::
                                isInstance
                )

                .map(
                        DeliveryStop
                                .class::
                                cast
                )

                .anyMatch(
                        stop ->
                                stop
                                        .order()
                                        .id()
                                        .equals(
                                                id
                                        )
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

    private record Fixture(
            OperationalSnapshot snapshot
    ) {
    }
}