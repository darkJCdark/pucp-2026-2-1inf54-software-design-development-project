package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.entity.TransferDirection;
import com.pucp.paqrap.modulos.planificacion.entity.TransferStop;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecuperacionTransbordoFallidoServiceTest {

    private static final Instant T0 = Instant.parse("2028-12-01T12:00:00Z");
    private static final Warehouse CENTRAL = Warehouse.central("CENTRAL", new Location(0, 0));
    private static final Vehicle DONOR = new Vehicle("TA01", VehicleType.CAR, true);
    private static final Vehicle RECEIVER = new Vehicle("TM01", VehicleType.MOTORCYCLE, true);
    private static final Order ORDER = new Order(
            "P-FALLBACK", new Location(8, 0), 5, T0, T0.plus(Duration.ofHours(4)));

    @Test
    void conservaLaPropiedadFisicaYReconstruyeLaEntregaDelDonante() {
        Escenario escenario = escenario(5);
        RecuperacionTransbordoFallidoService service =
                new RecuperacionTransbordoFallidoService(escenario.scheduler());

        RecuperacionTransbordoFallidoService.Resultado result = service.recuperar(
                escenario.failure().occurredAt(),
                List.of(new RecuperacionTransbordoFallidoService.Pendiente(
                        escenario.failure(), escenario.donorRoute(), escenario.receiverRoute())),
                List.of(CENTRAL),
                escenario.actualStates(),
                escenario.actualSnapshot(),
                List.of());

        assertTrue(result.pendientes().isEmpty());
        assertTrue(result.cantidadesProtegidas().isEmpty());
        assertEquals(java.util.Set.of("TR-F"), result.transferenciasResueltas());

        RutaVigente donorRecovered = result.rutasRecuperadas().stream()
                .filter(route -> route.vehiculoId().equals(DONOR.id()))
                .findFirst().orElseThrow();

        assertTrue(donorRecovered.ruta().stops().stream()
                .anyMatch(stop -> stop instanceof DeliveryStop delivery
                        && delivery.order().id().equals(ORDER.id())
                        && delivery.deliveredPackages() == 5));
        assertFalse(donorRecovered.ruta().stops().stream()
                .anyMatch(TransferStop.class::isInstance));

        int deliveriesInReceiver = result.rutasRecuperadas().stream()
                .filter(route -> route.vehiculoId().equals(RECEIVER.id()))
                .flatMap(route -> route.ruta().stops().stream())
                .filter(stop -> stop instanceof DeliveryStop delivery
                        && delivery.order().id().equals(ORDER.id()))
                .mapToInt(stop -> ((DeliveryStop) stop).deliveredPackages())
                .sum();
        assertEquals(0, deliveriesInReceiver);
    }

    @Test
    void protegeLaCantidadSiLaCargaRealDelDonanteNoEsSuficiente() {
        Escenario escenario = escenario(2);
        RecuperacionTransbordoFallidoService service =
                new RecuperacionTransbordoFallidoService(escenario.scheduler());

        RecuperacionTransbordoFallidoService.Resultado result = service.recuperar(
                escenario.failure().occurredAt(),
                List.of(new RecuperacionTransbordoFallidoService.Pendiente(
                        escenario.failure(), escenario.donorRoute(), escenario.receiverRoute())),
                List.of(CENTRAL),
                escenario.actualStates(),
                escenario.actualSnapshot(),
                List.of());

        assertTrue(result.rutasRecuperadas().isEmpty());
        assertEquals(1, result.pendientes().size());
        assertEquals(Integer.valueOf(5), result.cantidadesProtegidas().get(ORDER.id()));
    }

    @Test
    void protegeLosCompromisosFuturosDeAmbosVehiculosSiLaRecuperacionQuedaPendiente() {
        Order donorTail = new Order(
                "P-DONOR-TAIL", new Location(9, 0), 2,
                T0, T0.plus(Duration.ofHours(5)));
        Order receiverTail = new Order(
                "P-RECEIVER-TAIL", new Location(10, 0), 2,
                T0, T0.plus(Duration.ofHours(5)));
        RouteScheduler scheduler = new RouteScheduler(new RoadNetwork());
        Instant meeting = T0.plus(Duration.ofMinutes(10));
        Location node = new Location(2, 0);

        OperationalSnapshot initial = snapshot(
                T0,
                Map.of(
                        DONOR.id(), new VehicleOperationalState(
                                DONOR, VehicleStatus.AVAILABLE, CENTRAL.location(), 7, T0),
                        RECEIVER.id(), new VehicleOperationalState(
                                RECEIVER, VehicleStatus.AVAILABLE, CENTRAL.location(), 2, T0)));

        DeliveryRoute donor = DeliveryRoute.startScenarioAtCentral(
                        "R-D-PENDING", DONOR, CENTRAL, 7, T0)
                .withAppendedStop(new TransferStop(
                        "TR-PENDING", ORDER, 5, RECEIVER.id(),
                        TransferDirection.DONATE, node, meeting))
                .withAppendedStop(new DeliveryStop(donorTail, 2))
                .returningTo(CENTRAL);
        DeliveryRoute receiver = DeliveryRoute.startScenarioAtCentral(
                        "R-R-PENDING", RECEIVER, CENTRAL, 2, T0)
                .withAppendedStop(new TransferStop(
                        "TR-PENDING", ORDER, 5, DONOR.id(),
                        TransferDirection.RECEIVE, node, meeting))
                .withAppendedStop(new DeliveryStop(ORDER, 5))
                .withAppendedStop(new DeliveryStop(receiverTail, 2))
                .returningTo(CENTRAL);

        RutaVigente donorRoute = new RutaVigente(
                donor, initial, scheduler.schedule(donor, initial, List.of()), List.of());
        RutaVigente receiverRoute = new RutaVigente(
                receiver, initial, scheduler.schedule(receiver, initial, List.of()), List.of());
        Map<String, VehicleOperationalState> actualStates = Map.of(
                DONOR.id(), new VehicleOperationalState(
                        DONOR, VehicleStatus.AVAILABLE, node, 2, meeting),
                RECEIVER.id(), new VehicleOperationalState(
                        RECEIVER, VehicleStatus.AVAILABLE, node, 2, meeting));
        OperationalSnapshot actual = snapshot(meeting, actualStates);
        FalloTransbordoOperativo failure = new FalloTransbordoOperativo(
                "TR-PENDING", ORDER.id(), 5,
                DONOR.id(), RECEIVER.id(), node, meeting,
                "carga donante inconsistente");

        RecuperacionTransbordoFallidoService.Resultado result =
                new RecuperacionTransbordoFallidoService(scheduler).recuperar(
                        meeting,
                        List.of(new RecuperacionTransbordoFallidoService.Pendiente(
                                failure, donorRoute, receiverRoute)),
                        List.of(CENTRAL),
                        actualStates,
                        actual,
                        List.of());

        assertEquals(1, result.pendientes().size());
        assertEquals(Integer.valueOf(5), result.cantidadesProtegidas().get(ORDER.id()));
        assertEquals(Integer.valueOf(2), result.cantidadesProtegidas().get(donorTail.id()));
        assertEquals(Integer.valueOf(2), result.cantidadesProtegidas().get(receiverTail.id()));
    }

    private static Escenario escenario(int donorActualLoad) {
        RouteScheduler scheduler = new RouteScheduler(new RoadNetwork());
        Instant meeting = T0.plus(Duration.ofMinutes(10));
        Location node = new Location(2, 0);

        OperationalSnapshot initial = snapshot(
                T0,
                Map.of(
                        DONOR.id(), new VehicleOperationalState(
                                DONOR, VehicleStatus.AVAILABLE, CENTRAL.location(), 5, T0),
                        RECEIVER.id(), new VehicleOperationalState(
                                RECEIVER, VehicleStatus.AVAILABLE, CENTRAL.location(), 0, T0)));

        DeliveryRoute donor = DeliveryRoute.startScenarioAtCentral(
                        "R-D", DONOR, CENTRAL, 5, T0)
                .withAppendedStop(new TransferStop(
                        "TR-F", ORDER, 5, RECEIVER.id(), TransferDirection.DONATE, node, meeting))
                .returningTo(CENTRAL);
        DeliveryRoute receiver = DeliveryRoute.startScenarioAtCentral(
                        "R-R", RECEIVER, CENTRAL, 0, T0)
                .withAppendedStop(new TransferStop(
                        "TR-F", ORDER, 5, DONOR.id(), TransferDirection.RECEIVE, node, meeting))
                .withAppendedStop(new DeliveryStop(ORDER, 5))
                .returningTo(CENTRAL);

        ScheduledDeliveryRoute scheduledDonor = scheduler.schedule(donor, initial, List.of());
        ScheduledDeliveryRoute scheduledReceiver = scheduler.schedule(receiver, initial, List.of());
        RutaVigente donorRoute = new RutaVigente(donor, initial, scheduledDonor, List.of());
        RutaVigente receiverRoute = new RutaVigente(receiver, initial, scheduledReceiver, List.of());

        Map<String, VehicleOperationalState> actualStates = Map.of(
                DONOR.id(), new VehicleOperationalState(
                        DONOR, VehicleStatus.AVAILABLE, node, donorActualLoad, meeting),
                RECEIVER.id(), new VehicleOperationalState(
                        RECEIVER, VehicleStatus.AVAILABLE, node, 0, meeting));
        OperationalSnapshot actual = snapshot(meeting, actualStates);
        FalloTransbordoOperativo failure = new FalloTransbordoOperativo(
                "TR-F", ORDER.id(), 5, DONOR.id(), RECEIVER.id(), node, meeting,
                "prueba de recuperación");
        return new Escenario(scheduler, donorRoute, receiverRoute, actualStates, actual, failure);
    }

    private static OperationalSnapshot snapshot(
            Instant time,
            Map<String, VehicleOperationalState> states
    ) {
        return new OperationalSnapshot(
                time,
                FleetProfile.defaults(),
                InventorySnapshot.from(List.of(CENTRAL)),
                states,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()),
                ShiftSchedule.defaultSchedule(),
                List.of());
    }

    private record Escenario(
            RouteScheduler scheduler,
            RutaVigente donorRoute,
            RutaVigente receiverRoute,
            Map<String, VehicleOperationalState> actualStates,
            OperationalSnapshot actualSnapshot,
            FalloTransbordoOperativo failure
    ) {
    }
}
