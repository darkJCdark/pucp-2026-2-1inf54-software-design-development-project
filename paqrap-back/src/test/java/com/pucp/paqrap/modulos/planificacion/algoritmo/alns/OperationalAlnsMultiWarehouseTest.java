package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

class OperationalAlnsMultiWarehouseTest {

    private static final Instant T0 =
            Instant.parse(
                    "2028-12-01T00:00:00Z"
            );

    @Test
    void usaAlmacenEsteYRegistraLosNuevosOperadores() {

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",
                        new Location(27, 14)
                );

        Warehouse east =
                Warehouse.intermediate(
                        "EAST",
                        new Location(57, 27),
                        1_000
                );

        Warehouse northwest =
                Warehouse.intermediate(
                        "NORTHWEST",
                        new Location(12, 38),
                        1_000
                );

        Vehicle vehicle =
                new Vehicle(
                        "TA01",
                        VehicleType.CAR,
                        true
                );

        OperationalSnapshot snapshot =
                new OperationalSnapshot(
                        T0,
                        FleetProfile.defaults(),
                        InventorySnapshot.from(
                                List.of(
                                        central,
                                        east,
                                        northwest
                                )
                        ),
                        Map.of(
                                vehicle.id(),
                                new VehicleOperationalState(
                                        vehicle,
                                        VehicleStatus.AVAILABLE,
                                        central.location(),
                                        T0
                                )
                        ),
                        new MaintenanceCalendar(
                                ShiftSchedule.DEFAULT_ZONE,
                                List.of()
                        ),
                        ShiftSchedule.defaultSchedule(),
                        List.of()
                );

        List<Order> orders =
                List.of(
                        new Order(
                                "P1",
                                new Location(60, 27),
                                5,
                                T0,
                                T0.plus(
                                        Duration.ofHours(10)
                                )
                        ),
                        new Order(
                                "P2",
                                new Location(58, 30),
                                5,
                                T0,
                                T0.plus(
                                        Duration.ofHours(12)
                                )
                        )
                );

        OperationalAlnsPlanner planner =
                new OperationalAlnsPlanner(
                        new OperationalPlanEvaluator(
                                new RouteScheduler(
                                        new RoadNetwork()
                                )
                        )
                );

        AlnsConfig config =
                new AlnsConfig(
                        4,
                        4,
                        1,
                        2,
                        20,
                        20,
                        50.0,
                        0.5,
                        0.95,
                        0.20
                );

        OperationalAlnsPlanner.Ejecucion execution =
                planner.ejecutar(
                        snapshot,
                        orders,
                        List.of(),
                        config,
                        new Random(7L),
                        -1L
                );

        assertTrue(
                execution
                        .resultado()
                        .esFactible(),
                () ->
                        "violaciones: "
                                +
                                execution
                                        .resultado()
                                        .evaluacion()
                                        .violations()
        );

        assertTrue(
                execution.destroyWeights()
                        .containsKey(
                                "related-route-removal"
                        )
        );

        assertTrue(
                execution.repairWeights()
                        .containsKey(
                                "regret-3-insertion"
                        )
        );

        assertTrue(
                execution
                        .resultado()
                        .plan()
                        .routes()
                        .stream()
                        .anyMatch(
                                route ->
                                        !route.stops().isEmpty()
                                                &&
                                                route.stops().getLast()
                                                        instanceof WarehouseVisit visit
                                                &&
                                                "EAST".equals(
                                                        visit.warehouse().id()
                                                )
                        ),
                "al menos una ruta debe terminar posicionada en EAST"
        );

        assertTrue(
                execution
                        .resultado()
                        .plan()
                        .routes()
                        .stream()
                        .flatMap(
                                route ->
                                        route.stops().stream()
                        )
                        .anyMatch(
                                stop ->
                                        stop instanceof WarehouseVisit visit
                                                &&
                                                visit.pickupPackages() > 0
                                                &&
                                                "EAST".equals(
                                                        visit.warehouse().id()
                                                )
                        ),
                "la segunda carga debe poder realizarse en EAST"
        );
    }
}
