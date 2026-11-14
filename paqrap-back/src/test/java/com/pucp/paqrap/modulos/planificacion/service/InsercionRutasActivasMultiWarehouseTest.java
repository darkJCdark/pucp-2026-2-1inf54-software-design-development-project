package com.pucp.paqrap.modulos.planificacion.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

class InsercionRutasActivasMultiWarehouseTest {

    private static final Instant T0 =
            Instant.parse(
                    "2028-12-01T00:00:00Z"
            );

    @Test
    void recargaPedidoNuevoEnEsteCuandoLaRutaYaEstaEnEsaZona() {

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

        Order existing =
                new Order(
                        "P0",
                        new Location(55, 27),
                        1,
                        T0,
                        T0.plus(
                                Duration.ofHours(8)
                        )
                );

        Order incoming =
                new Order(
                        "P1",
                        new Location(60, 27),
                        5,
                        T0,
                        T0.plus(
                                Duration.ofHours(10)
                        )
                );

        InventorySnapshot inventory =
                InventorySnapshot.from(
                        List.of(
                                central,
                                east,
                                northwest
                        )
                );

        OperationalSnapshot snapshot =
                new OperationalSnapshot(
                        T0,
                        FleetProfile.defaults(),
                        inventory,
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

        DeliveryRoute route =
                DeliveryRoute
                        .startScenarioAtCentral(
                                "R",
                                vehicle,
                                central,
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
                                central
                        );

        OperationalPlanEvaluator evaluator =
                new OperationalPlanEvaluator(
                        new RouteScheduler(
                                new RoadNetwork()
                        )
                );

        PlanEvaluation evaluation =
                evaluator.evaluate(
                        OperationalPlan
                                .empty()
                                .withRoute(route),
                        snapshot,
                        List.of(existing),
                        List.of()
                );

        ScheduledDeliveryRoute scheduled =
                evaluation
                        .schedulesByRouteId()
                        .get("R");

        Instant now =
                scheduled
                        .scheduledStops()
                        .getFirst()
                        .approach()
                        .legs()
                        .getFirst()
                        .arrivesAt()
                        .minusSeconds(5);

        InsercionRutasActivasAlns.Resultado result =
                new InsercionRutasActivasAlns(
                        evaluator
                )
                        .insertar(
                                now,
                                List.of(
                                        new RutaVigente(
                                                route,
                                                snapshot,
                                                scheduled,
                                                List.of()
                                        )
                                ),
                                List.of(incoming),
                                central,
                                inventory,
                                List.of(),
                                Long.MAX_VALUE
                        );

        assertTrue(
                result.pendientes().isEmpty()
        );

        assertTrue(
                result
                        .rutas()
                        .getFirst()
                        .ruta()
                        .stops()
                        .stream()
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
                "la ruta activa debe poder recargar en EAST"
        );
    }
}
