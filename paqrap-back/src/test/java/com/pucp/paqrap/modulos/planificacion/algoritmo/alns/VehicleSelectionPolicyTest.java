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
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;

class VehicleSelectionPolicyTest {

    private static final Instant T0 =
            Instant.parse(
                    "2026-01-01T05:00:00Z"
            );

    private final VehicleSelectionPolicy policy =
            new VehicleSelectionPolicy();

    @Test
    void pedidoPequenoYHolgadoPrefiereBicicleta() {

        Fixture fixture =
                fixture();

        Order order =
                order(
                        "P3",
                        3,
                        new Location(
                                30,
                                14
                        ),
                        Duration.ofHours(
                                8
                        )
                );

        List<VehicleOperationalState> ranked =
                policy.rank(
                        OperationalPlan.empty(),
                        order,
                        fixture.snapshot()
                );

        assertEquals(
                VehicleType.BICYCLE,
                ranked
                        .getFirst()
                        .vehicle()
                        .type()
        );
    }

    @Test
    void pedidoMedianoPrefiereMotoPorqueEvitaDosViajesDeBicicleta() {

        Fixture fixture =
                fixture();

        Order order =
                order(
                        "P7",
                        7,
                        new Location(
                                30,
                                14
                        ),
                        Duration.ofHours(
                                8
                        )
                );

        List<VehicleOperationalState> ranked =
                policy.rank(
                        OperationalPlan.empty(),
                        order,
                        fixture.snapshot()
                );

        assertEquals(
                VehicleType.MOTORCYCLE,
                ranked
                        .getFirst()
                        .vehicle()
                        .type()
        );
    }

    @Test
    void pedidoGrandePrefiereAutoPorCostoTotalDeLosViajesNecesarios() {

        Fixture fixture =
                fixture();

        Order order =
                order(
                        "P15",
                        15,
                        new Location(
                                30,
                                14
                        ),
                        Duration.ofHours(
                                8
                        )
                );

        List<VehicleOperationalState> ranked =
                policy.rank(
                        OperationalPlan.empty(),
                        order,
                        fixture.snapshot()
                );

        assertEquals(
                VehicleType.CAR,
                ranked
                        .getFirst()
                        .vehicle()
                        .type()
        );
    }

    @Test
    void deadlineAjustadoDescartaLaBicicletaAunqueSeaMasBarata() {

        Fixture fixture =
                fixture();

        /*
         * Central = (27,14)
         * cliente = (37,14)
         *
         * 10 km.
         *
         * Bici a 14 km/h:
         * ≈ 42.9 min -> ya no cumple 35 min.
         *
         * Moto a 40 km/h:
         * 15 min -> sí cumple.
         */
        Order order =
                order(
                        "URGENTE",
                        3,
                        new Location(
                                37,
                                14
                        ),
                        Duration.ofMinutes(
                                35
                        )
                );

        List<VehicleOperationalState> ranked =
                policy.rank(
                        OperationalPlan.empty(),
                        order,
                        fixture.snapshot()
                );

        assertEquals(
                VehicleType.MOTORCYCLE,
                ranked
                        .getFirst()
                        .vehicle()
                        .type()
        );
    }

    @Test
    void aCostoIgualPrefiereReutilizarRutaExistente() {

        Fixture fixture =
                fixture();

        Vehicle tb01 =
                fixture.vehicle(
                        "TB01"
                );

        Order existing =
                order(
                        "EXISTENTE",
                        1,
                        new Location(
                                28,
                                14
                        ),
                        Duration.ofHours(
                                8
                        )
                );

        DeliveryRoute openRoute =
                DeliveryRoute
                        .startScenarioAtCentral(
                                "ALNS-TB01",
                                tb01,
                                fixture.central(),
                                1,
                                T0
                        )

                        .withAppendedStop(
                                new DeliveryStop(
                                        existing,
                                        1
                                )
                        )

                        .returningTo(
                                fixture.central()
                        );

        OperationalPlan plan =
                OperationalPlan
                        .empty()
                        .withRoute(
                                openRoute
                        );

        Order next =
                order(
                        "NUEVO",
                        3,
                        new Location(
                                30,
                                14
                        ),
                        Duration.ofHours(
                                8
                        )
                );

        List<VehicleOperationalState> ranked =
                policy.rank(
                        plan,
                        next,
                        fixture.snapshot()
                );

        /*
         * TB01 y TB02 tienen mismo costo por km.
         *
         * Mientras TB01 siga siendo temporalmente
         * factible, reutilizamos la ruta abierta.
         */
        assertEquals(
                "TB01",
                ranked
                        .getFirst()
                        .vehicle()
                        .id()
        );
    }

    @Test
    void candidatosInicialesConservanDiversidadDeTipos() {

        Fixture fixture =
                fixture();

        Order order =
                order(
                        "P3",
                        3,
                        new Location(
                                30,
                                14
                        ),
                        Duration.ofHours(
                                8
                        )
                );

        List<VehicleOperationalState> ranked =
                policy.rank(
                        OperationalPlan.empty(),
                        order,
                        fixture.snapshot()
                );

        List<VehicleType> firstThree =
                ranked
                        .stream()
                        .limit(
                                3
                        )

                        .map(
                                state ->
                                        state
                                                .vehicle()
                                                .type()
                        )

                        .toList();

        assertEquals(
                3,
                firstThree
                        .stream()
                        .distinct()
                        .count()
        );

        assertTrue(
                firstThree.contains(
                        VehicleType.CAR
                )
        );

        assertTrue(
                firstThree.contains(
                        VehicleType.MOTORCYCLE
                )
        );

        assertTrue(
                firstThree.contains(
                        VehicleType.BICYCLE
                )
        );
    }

    private Order order(
            String id,
            int packages,
            Location destination,
            Duration deadlineAfter
    ) {

        return new Order(
                id,
                destination,
                packages,
                T0,
                T0.plus(
                        deadlineAfter
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
                central,
                vehicles,
                snapshot
        );
    }

    private record Fixture(
            Warehouse central,
            List<Vehicle> vehicles,
            OperationalSnapshot snapshot
    ) {

        Vehicle vehicle(
                String id
        ) {

            return vehicles
                    .stream()

                    .filter(
                            vehicle ->
                                    vehicle
                                            .id()
                                            .equals(
                                                    id
                                            )
                    )

                    .findFirst()

                    .orElseThrow();
        }
    }
}