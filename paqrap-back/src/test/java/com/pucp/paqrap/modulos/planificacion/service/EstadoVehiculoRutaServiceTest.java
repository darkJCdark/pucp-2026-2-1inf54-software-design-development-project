package com.pucp.paqrap.modulos.planificacion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
import com.pucp.paqrap.modulos.planificacion.entity.TransferDirection;
import com.pucp.paqrap.modulos.planificacion.entity.TransferStop;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadLeg;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

class EstadoVehiculoRutaServiceTest {

    private static final Instant T0 =
            Instant.parse(
                    "2028-12-01T00:00:00Z"
            );

    @Test
    void snapshotUsaSiguienteNodoSeguroYCargaReal() {

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",
                        new Location(27, 14)
                );

        Vehicle vehicle =
                new Vehicle(
                        "TM01",
                        VehicleType.MOTORCYCLE,
                        true
                );

        Order order =
                new Order(
                        "P1",
                        new Location(35, 14),
                        7,
                        T0,
                        T0.plus(
                                Duration.ofHours(8)
                        )
                );

        OperationalSnapshot snapshot =
                new OperationalSnapshot(
                        T0,
                        FleetProfile.defaults(),
                        InventorySnapshot.from(
                                List.of(
                                        central
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

        DeliveryRoute route =
                DeliveryRoute
                        .startScenarioAtCentral(
                                "R",
                                vehicle,
                                central,
                                7,
                                T0
                        )
                        .withAppendedStop(
                                new DeliveryStop(
                                        order,
                                        7
                                )
                        )
                        .returningTo(
                                central
                        );

        PlanEvaluation evaluation =
                new OperationalPlanEvaluator(
                        new RouteScheduler(
                                new RoadNetwork()
                        )
                )
                        .evaluate(
                                OperationalPlan
                                        .empty()
                                        .withRoute(route),
                                snapshot,
                                List.of(order),
                                List.of()
                        );

        ScheduledDeliveryRoute scheduled =
                evaluation
                        .schedulesByRouteId()
                        .get("R");

        RoadLeg firstLeg =
                scheduled
                        .scheduledStops()
                        .getFirst()
                        .approach()
                        .legs()
                        .getFirst();

        Instant middle =
                firstLeg
                        .departsAt()
                        .plusNanos(
                                Duration
                                        .between(
                                                firstLeg.departsAt(),
                                                firstLeg.arrivesAt()
                                        )
                                        .toNanos()
                                        /
                                        2
                        );

        VehicleOperationalState state =
                new EstadoVehiculoRutaService()
                        .estadoEn(
                                new RutaVigente(
                                        route,
                                        snapshot,
                                        scheduled,
                                        List.of()
                                ),
                                middle
                        );

        assertEquals(
                VehicleStatus.IN_ROUTE,
                state.status()
        );

        assertEquals(
                7,
                state.carriedPackages()
        );

        assertNotEquals(
                central.location(),
                state.location(),
                "el snapshot no debe devolver el vehículo al Central"
        );

        assertEquals(
                firstLeg.to(),
                state.location(),
                "en mitad de una calle debe continuar hasta el siguiente nodo seguro"
        );
    }
    @Test
    void conservaCargaAntesDeIniciarUnTransbordoSincronizado() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(0, 0));
        Vehicle donor = new Vehicle("TA01", VehicleType.CAR, true);
        Order order = new Order(
                "P-T", new Location(8, 0), 3, T0, T0.plus(Duration.ofHours(4)));

        OperationalSnapshot snapshot = new OperationalSnapshot(
                T0,
                FleetProfile.defaults(),
                InventorySnapshot.from(List.of(central)),
                Map.of(donor.id(), new VehicleOperationalState(
                        donor, VehicleStatus.AVAILABLE, central.location(), T0)),
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()),
                ShiftSchedule.defaultSchedule(),
                List.of());

        Instant meeting = T0.plus(Duration.ofMinutes(30));
        DeliveryRoute route = DeliveryRoute.startScenarioAtCentral(
                        "R-T", donor, central, 5, T0)
                .withAppendedStop(new TransferStop(
                        "TR-WAIT", order, 3, "TM01", TransferDirection.DONATE,
                        new Location(1, 0), meeting))
                .returningTo(central);

        ScheduledDeliveryRoute scheduled = new RouteScheduler(new RoadNetwork())
                .schedule(route, snapshot, List.of());
        EstadoVehiculoRutaService service = new EstadoVehiculoRutaService();

        assertEquals(5, service.cargaEn(
                scheduled, meeting.minus(Duration.ofMinutes(5))),
                "la llegada anticipada no debe descontar paquetes antes del encuentro");
        assertEquals(2, service.cargaEn(
                scheduled, meeting.plusSeconds(1)),
                "la carga cambia al comenzar el transbordo");
    }

}
