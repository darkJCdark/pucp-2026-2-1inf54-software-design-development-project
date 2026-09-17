package com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.service.CargadorMantenimiento;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.flota.service.MaintenanceDay;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.pedidos.service.CargadorPedidos;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolationType;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.entity.StreetSegment;
import com.pucp.paqrap.modulos.redvial.service.CargadorBloqueos;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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

class DemoIntegradaMantenimientoSaTest {
    private static final YearMonth SEPTIEMBRE_2026 = YearMonth.of(2026, 9);
    private static final Instant INSTANTE_PLANIFICACION = SEPTIEMBRE_2026.atDay(1).atTime(2, 0)
            .atZone(ShiftSchedule.DEFAULT_ZONE).toInstant();
    private static final long SEMILLA = 202609010200L;

    @Test
    void demuestraSaConVentasBloqueosYMantenimientoReales() throws Exception {
        List<Order> pedidos = new CargadorPedidos().cargar(recurso("ventas/ventas.202609.txt"), SEPTIEMBRE_2026,
                        ShiftSchedule.DEFAULT_ZONE).stream()
                .filter(pedido -> !pedido.registeredAt().isAfter(INSTANTE_PLANIFICACION)).toList();
        List<RoadBlock> bloqueos = new CargadorBloqueos().cargar(recurso("bloqueos/bloqueo.2609.txt"), SEPTIEMBRE_2026,
                        ShiftSchedule.DEFAULT_ZONE).stream()
                .filter(bloqueo -> bloqueo.isActiveAt(INSTANTE_PLANIFICACION)).toList();
        List<MaintenanceDay> calendarioReal = new CargadorMantenimiento().cargar(recurso("mant.preventivo.09.10.txt"));
        List<MaintenanceDay> mantenimientosActivos = calendarioReal.stream()
                .filter(mantenimiento -> mantenimiento.contains(INSTANTE_PLANIFICACION, ShiftSchedule.DEFAULT_ZONE)).toList();

        Warehouse central = Warehouse.central("CENTRAL", new Location(27, 14));
        List<Vehicle> flota = List.of(new Vehicle("TA01", VehicleType.CAR, true), new Vehicle("TA02", VehicleType.CAR, true),
                new Vehicle("TM01", VehicleType.MOTORCYCLE, true), new Vehicle("TB01", VehicleType.BICYCLE, true));
        OperationalSnapshot snapshot = snapshot(central, flota, calendarioReal);
        AnnealingConfig configuracion = new AnnealingConfig(100.0, 1.0, 0.90, 3, 30, 30);
        OperationalSimulatedAnnealingPlanner.Ejecucion ejecucion = planificador().ejecutar(snapshot, pedidos, bloqueos,
                configuracion, random(SEMILLA));
        ResultadoPlanificacion resultado = ejecucion.resultado();
        Set<String> vehiculosMantenimiento = mantenimientosActivos.stream().map(MaintenanceDay::vehicleId).collect(Collectors.toSet());

        assertFalse(pedidos.isEmpty());
        assertFalse(mantenimientosActivos.isEmpty());
        assertTrue(bloqueos.size() > 0, "El instante elegido tiene bloqueos reales vigentes");
        assertTrue(vehiculosMantenimiento.stream().allMatch(vehiculo -> !snapshot.isVehiclePlannableAt(vehiculo, INSTANTE_PLANIFICACION)));
        assertNotNull(resultado);
        assertNotNull(resultado.evaluacion());
        assertTrue(resultado.esFactible(), () -> "violaciones: " + resultado.evaluacion().violations());
        assertTrue(resultado.plan().routes().stream().noneMatch(ruta -> vehiculosMantenimiento.contains(ruta.vehicle().id())));
        assertFalse(tieneCargaNegativa(resultado));
        assertEquals(0, violaciones(resultado, PlanViolationType.VEHICLE_CAPACITY));
        assertEquals(0, violaciones(resultado, PlanViolationType.SLA_MISSED));
        assertEquals(0, bloqueosAtravesados(resultado, bloqueos));
        assertPedidosCoherentes(resultado, pedidos);

        imprimirCabecera();
        System.out.println("PERIODO");
        System.out.println("------------------------------------------------------------");
        System.out.printf("Fecha/hora de planificacion: %s%nMes de ventas: %s%nMes de bloqueos: %s%n", INSTANTE_PLANIFICACION,
                SEPTIEMBRE_2026, SEPTIEMBRE_2026);
        System.out.println("Se eligio el 2026-09-01 02:00 (Lima): TA01 tiene mantenimiento real, hay pedidos registrados y tres bloqueos vigentes.");
        System.out.println("\nARCHIVOS UTILIZADOS");
        System.out.println("------------------------------------------------------------");
        System.out.println("Ventas: ventas.202609.txt");
        System.out.println("Bloqueos: bloqueo.2609.txt");
        System.out.println("Mantenimiento: mant.preventivo.09.10.txt");
        System.out.println("\nDATOS DEL ESCENARIO");
        System.out.println("------------------------------------------------------------");
        System.out.printf("Pedidos considerados: %d%nBloqueos vigentes: %d%nVehiculos en mantenimiento: %s%n", pedidos.size(),
                bloqueos.size(), vehiculosMantenimiento);
        System.out.println("\nMANTENIMIENTO ACTIVO");
        System.out.println("------------------------------------------------------------");
        mantenimientosActivos.forEach(mantenimiento -> System.out.printf("Vehiculo: %s%nTipo: %s%nFecha de mantenimiento: %s%nPlanificable: NO%n%n",
                mantenimiento.vehicleId(), tipoDe(mantenimiento.vehicleId()), mantenimiento.date()));
        System.out.println("PEDIDOS");
        System.out.println("------------------------------------------------------------");
        pedidos.forEach(pedido -> System.out.printf("%s | destino: (%d,%d) | cantidad: %d | registro: %s | plazo: %d h | deadline: %s%n",
                pedido.id(), pedido.destination().x(), pedido.destination().y(), pedido.packages(), pedido.registeredAt(),
                java.time.Duration.between(pedido.registeredAt(), pedido.deadline()).toHours(), pedido.deadline()));
        System.out.println("\nBLOQUEOS VIGENTES");
        System.out.println("------------------------------------------------------------");
        bloqueos.forEach(bloqueo -> System.out.printf("Inicio: %s | Fin: %s | Tramos: %s%n", bloqueo.startsAt(), bloqueo.endsAt(),
                bloqueo.blockedSegments()));
        System.out.println("\nCONFIGURACION SA");
        System.out.println("------------------------------------------------------------");
        System.out.printf("Temperatura inicial: %.2f%nTemperatura minima: %.2f%nFactor de enfriamiento: %.2f%n"
                        + "Iteraciones por temperatura: %d%nMaximo de iteraciones: %d%nMaximo sin mejora: %d%nSemilla: %d%n",
                configuracion.initialTemperature(), configuracion.minimumTemperature(), configuracion.coolingFactor(),
                configuracion.iterationsPerTemperature(), configuracion.maximumIterations(),
                configuracion.maximumIterationsWithoutImprovement(), SEMILLA);
        imprimirResultado(resultado, ejecucion, snapshot, bloqueos, vehiculosMantenimiento);
    }

    private OperationalSnapshot snapshot(Warehouse central, List<Vehicle> flota, List<MaintenanceDay> mantenimientos) {
        Map<String, VehicleOperationalState> estados = flota.stream().collect(Collectors.toMap(Vehicle::id,
                vehiculo -> new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, central.location(), INSTANTE_PLANIFICACION)));
        return new OperationalSnapshot(INSTANTE_PLANIFICACION, FleetProfile.defaults(), InventorySnapshot.from(List.of(central)), estados,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, mantenimientos), ShiftSchedule.defaultSchedule(), List.of());
    }

    private OperationalSimulatedAnnealingPlanner planificador() {
        return new OperationalSimulatedAnnealingPlanner(new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork())));
    }

    private void imprimirResultado(ResultadoPlanificacion resultado, OperationalSimulatedAnnealingPlanner.Ejecucion ejecucion,
                                   OperationalSnapshot snapshot, List<RoadBlock> bloqueos, Set<String> vehiculosMantenimiento) {
        Set<String> atendidos = pedidosEntregados(resultado);
        System.out.println("\nRESULTADO");
        System.out.println("------------------------------------------------------------");
        System.out.printf("Plan factible: %s%nPedidos atendidos: %s%nPedidos no atendidos: %s%nCosto total: S/ %.2f%n"
                        + "Distancia total: %.2f km%nVecinos evaluados: %d%nVecinos aceptados: %d%nIteraciones: %d%nTemperatura final: %.4f%n",
                resultado.esFactible() ? "SI" : "NO", atendidos, resultado.noAtendidos().stream().map(Order::id).toList(),
                resultado.costoTotal(), distancia(resultado), ejecucion.evaluatedNeighbors(), ejecucion.acceptedNeighbors(),
                ejecucion.iterations(), ejecucion.finalTemperature());
        System.out.println("\nVEHICULOS UTILIZADOS");
        System.out.println("------------------------------------------------------------");
        resultado.evaluacion().schedulesByRouteId().values().stream().sorted(Comparator.comparing(ruta -> ruta.route().vehicle().id()))
                .forEach(ruta -> imprimirRuta(ruta, snapshot));
        System.out.println("VALIDACION DE MANTENIMIENTO");
        System.out.println("------------------------------------------------------------");
        vehiculosMantenimiento.forEach(vehiculo -> System.out.printf("Vehiculo en mantenimiento: %s | Ruta asignada: NO%n", vehiculo));
        System.out.println("EL VEHICULO EN MANTENIMIENTO NO PARTICIPO EN LA PLANIFICACION");
        System.out.println("\nVALIDACIONES");
        System.out.println("------------------------------------------------------------");
        System.out.printf("Bloqueos vigentes atravesados: %d%nCarga negativa: %s%nViolaciones de capacidad: %d%n"
                        + "Violaciones de plazo: %d%nViolaciones totales: %d%n", bloqueosAtravesados(resultado, bloqueos),
                tieneCargaNegativa(resultado) ? "SI" : "NO", violaciones(resultado, PlanViolationType.VEHICLE_CAPACITY),
                violaciones(resultado, PlanViolationType.SLA_MISSED), resultado.evaluacion().violations().size());
        System.out.println("\nRESUMEN");
        System.out.println("------------------------------------------------------------");
        System.out.printf("Pedidos considerados: %d%nAtendidos: %d%nNo atendidos: %d%nBloqueos vigentes: %d%nVehiculos en mantenimiento: %d%n"
                        + "Vehiculos utilizados: %d%nDistancia total: %.2f km%nCosto total: S/ %.2f%nResultado: %s%n",
                atendidos.size() + resultado.noAtendidos().size(), atendidos.size(), resultado.noAtendidos().size(), bloqueos.size(),
                vehiculosMantenimiento.size(), resultado.plan().routes().size(), distancia(resultado), resultado.costoTotal(),
                resultado.esFactible() ? "PLAN FACTIBLE" : "PLAN NO FACTIBLE");
        System.out.println("============================================================");
        System.out.println("     FIN DEMO INTEGRADA SA");
        System.out.println("============================================================");
    }

    private void imprimirRuta(ScheduledDeliveryRoute ruta, OperationalSnapshot snapshot) {
        Vehicle vehiculo = ruta.route().vehicle();
        var parametros = snapshot.fleetProfile().parametersFor(vehiculo.type());
        List<String> paradas = new ArrayList<>();
        paradas.add("START " + formato(ruta.route().startLocation()));
        List<String> entregados = new ArrayList<>();
        for (RouteStop parada : ruta.route().stops()) {
            if (parada instanceof DeliveryStop entrega) {
                paradas.add("DELIVERY " + entrega.order().id() + " " + formato(entrega.order().destination())
                        + " paquetes=" + entrega.deliveredPackages());
                entregados.add(entrega.order().id());
            } else if (parada instanceof WarehouseVisit visita) {
                paradas.add("WAREHOUSE " + formato(visita.warehouse().location()) + " recarga=" + visita.pickupPackages());
            }
        }
        System.out.printf("Vehiculo: %s%nTipo: %s%nCapacidad: %d%nVelocidad: %.2f km/h%nRuta:%n%s%nPedidos entregados: %s%n"
                        + "Distancia: %.2f km%nCosto: S/ %.2f%n%n", vehiculo.id(), vehiculo.type(), parametros.capacity(),
                parametros.speedKmPerHour(), String.join(System.lineSeparator(), paradas), entregados, ruta.totalDistanceKm(), ruta.totalCost());
    }

    private boolean tieneCargaNegativa(ResultadoPlanificacion resultado) {
        return resultado.evaluacion().schedulesByRouteId().values().stream().flatMap(ruta -> ruta.scheduledStops().stream())
                .anyMatch(parada -> parada.loadBefore() < 0 || parada.loadAfter() < 0);
    }

    private int bloqueosAtravesados(ResultadoPlanificacion resultado, List<RoadBlock> bloqueos) {
        return (int) resultado.evaluacion().schedulesByRouteId().values().stream().flatMap(ruta -> ruta.scheduledStops().stream())
                .flatMap(parada -> parada.approach().legs().stream()).filter(tramo -> bloqueos.stream().anyMatch(bloqueo ->
                        bloqueo.overlaps(tramo.departsAt(), tramo.arrivesAt())
                                && (bloqueo.blockedSegments().contains(new StreetSegment(tramo.from(), tramo.to()))
                                || bloqueo.blockedNodes().contains(tramo.from()) || bloqueo.blockedNodes().contains(tramo.to())))).count();
    }

    private int violaciones(ResultadoPlanificacion resultado, PlanViolationType tipo) {
        return (int) resultado.evaluacion().violations().stream().filter(violacion -> violacion.type() == tipo).count();
    }

    private void assertPedidosCoherentes(ResultadoPlanificacion resultado, List<Order> pedidos) {
        Set<String> atendidos = pedidosEntregados(resultado);
        Set<String> noAtendidos = resultado.noAtendidos().stream().map(Order::id).collect(Collectors.toSet());
        Set<String> esperados = pedidos.stream().map(Order::id).collect(Collectors.toSet());
        assertTrue(java.util.Collections.disjoint(atendidos, noAtendidos));
        Set<String> cubiertos = new HashSet<>(atendidos);
        cubiertos.addAll(noAtendidos);
        assertEquals(esperados, cubiertos);
    }

    private Set<String> pedidosEntregados(ResultadoPlanificacion resultado) {
        return resultado.plan().routes().stream().flatMap(ruta -> ruta.stops().stream()).filter(DeliveryStop.class::isInstance)
                .map(DeliveryStop.class::cast).map(parada -> parada.order().id()).collect(Collectors.toSet());
    }

    private double distancia(ResultadoPlanificacion resultado) {
        return resultado.evaluacion().schedulesByRouteId().values().stream().mapToDouble(ScheduledDeliveryRoute::totalDistanceKm).sum();
    }

    private VehicleType tipoDe(String idVehiculo) {
        return java.util.Arrays.stream(VehicleType.values()).filter(tipo -> idVehiculo.startsWith(tipo.fleetCode())).findFirst().orElseThrow();
    }

    private RandomGenerator random(long semilla) {
        return RandomGeneratorFactory.<RandomGenerator>of("L64X128MixRandom").create(semilla);
    }

    private Path recurso(String nombre) throws URISyntaxException {
        URL url = getClass().getResource("/datos-profesor/" + nombre);
        if (url == null) throw new IllegalStateException("No se encontro el recurso: " + nombre);
        return Path.of(url.toURI());
    }

    private String formato(Location ubicacion) {
        return "(" + ubicacion.x() + "," + ubicacion.y() + ")";
    }

    private void imprimirCabecera() {
        System.out.println("\n============================================================");
        System.out.println("   SA - DEMO INTEGRADA CON MANTENIMIENTO");
        System.out.println("============================================================");
    }
}
