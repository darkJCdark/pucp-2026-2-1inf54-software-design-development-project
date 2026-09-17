package com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.pedidos.service.CargadorPedidos;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.entity.StreetSegment;
import com.pucp.paqrap.modulos.redvial.service.CargadorBloqueos;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatosProfesorPlanificacionSaTest {
    private static final YearMonth ENERO_2026 = YearMonth.of(2026, 1);
    private static final Instant VENTANA_ENERO = Instant.parse("2026-01-01T15:19:00Z");

    @Test
    void planificaPedidosYBloqueosRealesDeLaVentanaDelPrimeroDeEnero() throws Exception {
        List<Order> pedidos = new CargadorPedidos().cargar(recurso("ventas/ventas.202601.txt"), ENERO_2026,
                ShiftSchedule.DEFAULT_ZONE).stream()
                .filter(pedido -> !pedido.registeredAt().isAfter(VENTANA_ENERO))
                .toList();
        List<RoadBlock> bloqueosVigentes = new CargadorBloqueos().cargar(recurso("bloqueos/bloqueo.2601.txt"), ENERO_2026,
                ShiftSchedule.DEFAULT_ZONE).stream().filter(bloqueo -> bloqueo.isActiveAt(VENTANA_ENERO)).toList();
        Warehouse central = Warehouse.central("CENTRAL", new com.pucp.paqrap.modulos.redvial.entity.Location(27, 14));
        List<Vehicle> flota = List.of(new Vehicle("TA01", VehicleType.CAR, true), new Vehicle("TA02", VehicleType.CAR, true),
                new Vehicle("TM01", VehicleType.MOTORCYCLE, true), new Vehicle("TB01", VehicleType.BICYCLE, true));
        OperationalSnapshot snapshot = snapshot(central, flota, VENTANA_ENERO, List.of());

        ResultadoPlanificacion resultado = new OperationalSimulatedAnnealingPlanner(evaluador()).planificar(snapshot, pedidos,
                bloqueosVigentes, new AnnealingConfig(100.0, 1.0, 0.90, 3, 30, 30), random(20260101L));

        assertNotNull(resultado);
        assertTrue(pedidos.size() > 1);
        assertFalse(bloqueosVigentes.isEmpty());
        assertTrue(resultado.esFactible(), () -> "violaciones: " + resultado.evaluacion().violations());
        assertFalse(tieneCargaNegativa(resultado));
        assertTrue(capacidadesRespetadas(resultado, snapshot));
        assertTrue(bloqueosRespetados(resultado, bloqueosVigentes));
        Set<String> idsPlanificados = resultado.plan().routes().stream().flatMap(ruta -> ruta.stops().stream())
                .filter(DeliveryStop.class::isInstance).map(DeliveryStop.class::cast)
                .map(parada -> parada.order().id()).collect(Collectors.toSet());
        Set<String> idsNoAtendidos = resultado.noAtendidos().stream().map(Order::id).collect(Collectors.toSet());
        Set<String> idsEsperados = pedidos.stream().map(Order::id).collect(Collectors.toSet());
        assertTrue(java.util.Collections.disjoint(idsPlanificados, idsNoAtendidos));
        assertEquals(idsEsperados, union(idsPlanificados, idsNoAtendidos));
        assertTrue(resultado.costoTotal() >= 0.0);
        assertTrue(distancia(resultado) >= 0.0);

        System.out.printf("%nSA DATOS PROFESOR ENERO: pedidos=%d, bloqueos vigentes=%d, atendidos=%d, no atendidos=%d, costo=%.2f, distancia=%.2f km%n",
                pedidos.size(), bloqueosVigentes.size(), idsPlanificados.size(), idsNoAtendidos.size(), resultado.costoTotal(), distancia(resultado));
    }

    private OperationalPlanEvaluator evaluador() {
        return new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()));
    }

    private OperationalSnapshot snapshot(Warehouse central, List<Vehicle> flota, Instant instante,
                                         List<com.pucp.paqrap.modulos.flota.service.MaintenanceDay> mantenimientos) {
        Map<String, VehicleOperationalState> estados = flota.stream().collect(Collectors.toMap(Vehicle::id,
                vehiculo -> new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, central.location(), instante)));
        return new OperationalSnapshot(instante, FleetProfile.defaults(), InventorySnapshot.from(List.of(central)), estados,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, mantenimientos), ShiftSchedule.defaultSchedule(), List.of());
    }

    private boolean tieneCargaNegativa(ResultadoPlanificacion resultado) {
        return resultado.evaluacion().schedulesByRouteId().values().stream().flatMap(ruta -> ruta.scheduledStops().stream())
                .anyMatch(parada -> parada.loadBefore() < 0 || parada.loadAfter() < 0);
    }

    private boolean capacidadesRespetadas(ResultadoPlanificacion resultado, OperationalSnapshot snapshot) {
        return resultado.evaluacion().schedulesByRouteId().values().stream().allMatch(ruta -> {
            int capacidad = snapshot.fleetProfile().parametersFor(ruta.route().vehicle().type()).capacity();
            return ruta.scheduledStops().stream().allMatch(parada -> parada.loadBefore() <= capacidad && parada.loadAfter() <= capacidad);
        });
    }

    private boolean bloqueosRespetados(ResultadoPlanificacion resultado, List<RoadBlock> bloqueos) {
        return resultado.evaluacion().schedulesByRouteId().values().stream().flatMap(ruta -> ruta.scheduledStops().stream())
                .flatMap(parada -> parada.approach().legs().stream()).noneMatch(tramo -> bloqueos.stream().anyMatch(bloqueo ->
                        bloqueo.overlaps(tramo.departsAt(), tramo.arrivesAt())
                                && (bloqueo.blockedSegments().contains(new StreetSegment(tramo.from(), tramo.to()))
                                || bloqueo.blockedNodes().contains(tramo.from()) || bloqueo.blockedNodes().contains(tramo.to()))));
    }

    private double distancia(ResultadoPlanificacion resultado) {
        return resultado.evaluacion().schedulesByRouteId().values().stream().mapToDouble(ruta -> ruta.totalDistanceKm()).sum();
    }

    private Set<String> union(Set<String> primero, Set<String> segundo) {
        Set<String> union = new java.util.HashSet<>(primero);
        union.addAll(segundo);
        return union;
    }

    private RandomGenerator random(long semilla) {
        return RandomGeneratorFactory.<RandomGenerator>of("L64X128MixRandom").create(semilla);
    }

    private Path recurso(String nombre) throws URISyntaxException {
        return Path.of(getClass().getResource("/datos-profesor/" + nombre).toURI());
    }
}
