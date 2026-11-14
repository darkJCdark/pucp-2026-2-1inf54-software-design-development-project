package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.config.RescatePreventivoProperties;
import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleParameters;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RescatePreventivoPedidosServiceTest {

    private static final Instant T0 = Instant.parse("2028-12-01T12:00:00Z");
    private static final Instant AHORA = T0.plus(Duration.ofMinutes(10));
    private static final Warehouse CENTRAL = Warehouse.central("CENTRAL", new Location(0, 0));
    private static final Warehouse EAST = Warehouse.intermediate("EAST", new Location(70, 0), 1_000);
    private static final Vehicle DONANTE = new Vehicle("TB01", VehicleType.BICYCLE, true);
    private static final Vehicle RECEPTOR = new Vehicle("TM01", VehicleType.MOTORCYCLE, true);
    private static final Order PEDIDO = new Order(
            "P-RIESGO", new Location(60, 0), 3, T0, T0.plus(Duration.ofHours(2).plusMinutes(15)));

    @Test
    void rescataCargaYaAbordoConEncuentroSinPerderPaquetes() {
        Escenario escenario = escenario();
        RescatePreventivoPedidosService servicio = new RescatePreventivoPedidosService(
                escenario.evaluador(), escenario.scheduler(), RescatePreventivoProperties.defaults());

        RescatePreventivoPedidosService.Resultado resultado = servicio.rescatar(
                AHORA,
                escenario.rutas(),
                CENTRAL,
                escenario.snapshot().inventory(),
                List.of(),
                new HashMap<>(),
                new HashMap<>(),
                System.nanoTime() + Duration.ofSeconds(5).toNanos());

        assertEquals(1, resultado.rescates());
        assertEquals(1, resultado.transbordos());
        assertEquals(java.util.Set.of(PEDIDO.id()), resultado.pedidosRescatados());

        RutaVigente donante = ruta(resultado.rutas(), DONANTE.id());
        RutaVigente receptor = ruta(resultado.rutas(), RECEPTOR.id());

        TransferStop salida = donante.ruta().stops().stream()
                .filter(TransferStop.class::isInstance)
                .map(TransferStop.class::cast)
                .findFirst().orElseThrow();
        TransferStop entrada = receptor.ruta().stops().stream()
                .filter(TransferStop.class::isInstance)
                .map(TransferStop.class::cast)
                .findFirst().orElseThrow();

        assertEquals(TransferDirection.DONATE, salida.direction());
        assertEquals(TransferDirection.RECEIVE, entrada.direction());
        assertEquals(salida.transferId(), entrada.transferId());
        assertEquals(salida.meetingAt(), entrada.meetingAt());
        assertEquals(3, salida.packages());
        assertEquals(3, entrada.packages());
        assertFalse(donante.ruta().stops().stream()
                .anyMatch(stop -> stop instanceof DeliveryStop delivery
                        && delivery.order().id().equals(PEDIDO.id())));
        assertTrue(receptor.ruta().stops().stream()
                .anyMatch(stop -> stop instanceof DeliveryStop delivery
                        && delivery.order().id().equals(PEDIDO.id())));

        int cargaAntes = salida.packages();
        int cargaDespues = 0;
        int receptorAntes = 0;
        int receptorDespues = entrada.packages();
        assertEquals(cargaAntes + receptorAntes, cargaDespues + receptorDespues);
    }


    @Test
    void reasignaPedidoTodaviaNoCargadoSinProgramarTransbordo() {
        Warehouse intermedio = Warehouse.intermediate("NEAR", new Location(20, 0), 1_000);
        Order pedido = new Order(
                "P-SIN-CARGA", new Location(60, 0), 3, T0,
                T0.plus(Duration.ofHours(2).plusMinutes(15)));
        OperationalSnapshot snapshot = snapshot(List.of(CENTRAL, intermedio));
        RouteScheduler scheduler = new RouteScheduler(new RoadNetwork());
        OperationalPlanEvaluator evaluador = new OperationalPlanEvaluator(scheduler);

        DeliveryRoute rutaDonante = DeliveryRoute.startScenarioAtCentral(
                        "R-DONANTE-SIN-CARGA", DONANTE, CENTRAL, 0, T0)
                .withAppendedStop(new com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit(intermedio, 3))
                .withAppendedStop(new DeliveryStop(pedido, 3))
                .returningTo(CENTRAL);
        DeliveryRoute rutaReceptor = DeliveryRoute.startScenarioAtCentral(
                        "R-RECEPTOR-SIN-CARGA", RECEPTOR, CENTRAL, 0, T0)
                .returningTo(intermedio);

        List<RutaVigente> rutas = List.of(
                new RutaVigente(rutaDonante, snapshot, scheduler.schedule(rutaDonante, snapshot, List.of()), List.of()),
                new RutaVigente(rutaReceptor, snapshot, scheduler.schedule(rutaReceptor, snapshot, List.of()), List.of()));

        RescatePreventivoPedidosService servicio = new RescatePreventivoPedidosService(
                evaluador, scheduler, RescatePreventivoProperties.defaults());
        RescatePreventivoPedidosService.Resultado resultado = servicio.rescatar(
                AHORA, rutas, CENTRAL, snapshot.inventory(), List.of(),
                new HashMap<>(), new HashMap<>(),
                System.nanoTime() + Duration.ofSeconds(5).toNanos());

        assertEquals(1, resultado.rescates());
        assertEquals(0, resultado.transbordos());
        assertTrue(resultado.rutas().stream()
                .flatMap(ruta -> ruta.ruta().stops().stream())
                .noneMatch(TransferStop.class::isInstance));
        assertFalse(ruta(resultado.rutas(), DONANTE.id()).ruta().stops().stream()
                .anyMatch(stop -> stop instanceof DeliveryStop delivery
                        && delivery.order().id().equals(pedido.id())));
        assertTrue(ruta(resultado.rutas(), RECEPTOR.id()).ruta().stops().stream()
                .anyMatch(stop -> stop instanceof DeliveryStop delivery
                        && delivery.order().id().equals(pedido.id())));
    }

    @Test
    void noMueveCargaFisicaCuandoLosTransbordosEstanDesactivados() {
        Escenario escenario = escenario();
        RescatePreventivoProperties sinTransbordos = new RescatePreventivoProperties(
                true, false,
                Duration.ofMinutes(60), Duration.ofMinutes(15),
                Duration.ofMinutes(15), Duration.ofMinutes(30),
                2, 1, 0.10, Duration.ofMillis(25), Duration.ofMillis(150));
        RescatePreventivoPedidosService servicio = new RescatePreventivoPedidosService(
                escenario.evaluador(), escenario.scheduler(), sinTransbordos);

        RescatePreventivoPedidosService.Resultado resultado = servicio.rescatar(
                AHORA, escenario.rutas(), CENTRAL, escenario.snapshot().inventory(), List.of(),
                new HashMap<>(), new HashMap<>(),
                System.nanoTime() + Duration.ofSeconds(5).toNanos());

        assertEquals(0, resultado.rescates());
        assertEquals(0, resultado.transbordos());
        assertTrue(resultado.rutas().stream()
                .flatMap(ruta -> ruta.ruta().stops().stream())
                .noneMatch(TransferStop.class::isInstance));
    }

    private static Escenario escenario() {
        RouteScheduler scheduler = new RouteScheduler(new RoadNetwork());
        OperationalPlanEvaluator evaluador = new OperationalPlanEvaluator(scheduler);
        OperationalSnapshot snapshot = snapshot();

        DeliveryRoute rutaDonante = DeliveryRoute.startScenarioAtCentral(
                        "R-DONANTE", DONANTE, CENTRAL, 3, T0)
                .withAppendedStop(new DeliveryStop(PEDIDO, 3))
                .returningTo(CENTRAL);

        DeliveryRoute rutaReceptor = DeliveryRoute.startScenarioAtCentral(
                        "R-RECEPTOR", RECEPTOR, CENTRAL, 0, T0)
                .returningTo(EAST);

        ScheduledDeliveryRoute programadaDonante = scheduler.schedule(rutaDonante, snapshot, List.of());
        ScheduledDeliveryRoute programadaReceptor = scheduler.schedule(rutaReceptor, snapshot, List.of());

        List<RutaVigente> rutas = List.of(
                new RutaVigente(rutaDonante, snapshot, programadaDonante, List.of()),
                new RutaVigente(rutaReceptor, snapshot, programadaReceptor, List.of()));
        return new Escenario(scheduler, evaluador, snapshot, rutas);
    }

    private static OperationalSnapshot snapshot() {
        return snapshot(List.of(CENTRAL, EAST));
    }

    private static OperationalSnapshot snapshot(List<Warehouse> almacenes) {
        EnumMap<VehicleType, VehicleParameters> parametros = new EnumMap<>(VehicleType.class);
        parametros.put(VehicleType.CAR, new VehicleParameters(24, 20.0, 8.0));
        parametros.put(VehicleType.MOTORCYCLE, new VehicleParameters(8, 40.0, 6.0));
        parametros.put(VehicleType.BICYCLE, new VehicleParameters(4, 14.0, 3.0));
        Map<String, VehicleOperationalState> estados = Map.of(
                DONANTE.id(), new VehicleOperationalState(
                        DONANTE, VehicleStatus.AVAILABLE, CENTRAL.location(), T0),
                RECEPTOR.id(), new VehicleOperationalState(
                        RECEPTOR, VehicleStatus.AVAILABLE, CENTRAL.location(), T0));
        return new OperationalSnapshot(
                T0,
                new FleetProfile(parametros),
                InventorySnapshot.from(almacenes),
                estados,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()),
                ShiftSchedule.defaultSchedule(),
                List.of());
    }

    private static RutaVigente ruta(List<RutaVigente> rutas, String vehiculoId) {
        return rutas.stream().filter(ruta -> ruta.vehiculoId().equals(vehiculoId))
                .findFirst().orElseThrow();
    }

    private record Escenario(
            RouteScheduler scheduler,
            OperationalPlanEvaluator evaluador,
            OperationalSnapshot snapshot,
            List<RutaVigente> rutas
    ) {
    }
}
