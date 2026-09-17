package com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleParameters;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoIntegralSaTest {

    private static final Instant INICIO = Instant.parse("2026-09-09T12:00:00Z");
    private static final AnnealingConfig CONFIG = new AnnealingConfig(100.0, 1.0, 0.90, 5, 30, 30);

    @Test
    void demuestraPlanificacionYReasignacionPorAveria() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(10, 10));
        Vehicle ta01 = new Vehicle("TA01", VehicleType.CAR, true);
        Vehicle ta02 = new Vehicle("TA02", VehicleType.CAR, true);
        Vehicle tm01 = new Vehicle("TM01", VehicleType.MOTORCYCLE, true);
        Vehicle tb01 = new Vehicle("TB01", VehicleType.BICYCLE, true);
        List<Vehicle> flota = List.of(ta01, ta02, tm01, tb01);
        List<Order> pedidos = List.of(
                new Order("P01", new Location(14, 10), 12, INICIO, INICIO.plusSeconds(10 * 3600)),
                new Order("P02", new Location(10, 13), 8, INICIO, INICIO.plusSeconds(10 * 3600)),
                new Order("P03", new Location(12, 11), 4, INICIO, INICIO.plusSeconds(10 * 3600)));

        OperationalSnapshot antesDeAveria = snapshot(central, flota, Map.of(
                ta02.id(), new VehicleOperationalState(ta02, VehicleStatus.AVAILABLE,
                        central.location(), INICIO.plusSeconds(3 * 3600))), List.of());
        OperationalSimulatedAnnealingPlanner.Ejecucion ejecucionInicial = ejecutar(antesDeAveria, pedidos, List.of(), 20260917L);
        ResultadoPlanificacion inicial = ejecucionInicial.resultado();

        imprimirEncabezado(flota, antesDeAveria.fleetProfile(), pedidos);
        System.out.println("\nPLANIFICACION INICIAL");
        separador();
        imprimirPlan(inicial);
        imprimirDatosSa(ejecucionInicial);
        verificarResultadoFactible(inicial, pedidos);
        assertTrue(inicial.plan().routeForVehicle(ta01.id()).isPresent(),
                "TA01 debe participar inicialmente para que la averia sea demostrable");

        List<String> pedidosAfectados = pedidosDeVehiculo(inicial, ta01.id());
        assertFalse(pedidosAfectados.isEmpty(), "la ruta inicial de TA01 debe contener pedidos");

        BreakdownEvent averia = new BreakdownEvent(ta01.id(), BreakdownType.MINOR, INICIO, central.location());
        OperationalSnapshot despuesDeAveria = snapshot(central, flota, Map.of(), List.of(averia));
        assertFalse(despuesDeAveria.isVehiclePlannable(ta01.id()), "TA01 debe quedar indisponible");

        System.out.println("\n============================================================");
        System.out.println("INCIDENCIA");
        System.out.println("============================================================");
        System.out.printf("Vehiculo averiado: %s%n", averia.vehicleId());
        System.out.printf("Tipo: %s%n", averia.type());
        System.out.printf("Indisponibilidad: %s%n", averia.type().minimumUnavailable());
        System.out.printf("Pedidos afectados: %s%n", pedidosAfectados);

        OperationalSimulatedAnnealingPlanner.Ejecucion ejecucionFinal = ejecutar(despuesDeAveria, pedidos, List.of(), 20260917L);
        ResultadoPlanificacion replanificado = ejecucionFinal.resultado();
        verificarResultadoFactible(replanificado, pedidos);
        assertTrue(replanificado.plan().routeForVehicle(ta01.id()).isEmpty(),
                "TA01 no debe recibir nuevas asignaciones durante la averia");
        for (String pedidoAfectado : pedidosAfectados) {
            Optional<String> nuevoVehiculo = vehiculoQueAtiende(replanificado, pedidoAfectado);
            assertTrue(nuevoVehiculo.isPresent(), "el pedido afectado debe ser atendido si existe capacidad");
            assertFalse(nuevoVehiculo.orElseThrow().equals(ta01.id()), "el pedido debe salir de TA01");
        }

        System.out.println("\n============================================================");
        System.out.println("REPLANIFICACION SA");
        System.out.println("============================================================");
        System.out.println("Plan antes:");
        imprimirPlan(inicial);
        System.out.println("Plan despues:");
        imprimirPlan(replanificado);
        imprimirDatosSa(ejecucionFinal);
        imprimirReasignaciones(pedidosAfectados, inicial, replanificado);

        System.out.println("\nRESULTADO FINAL");
        separador();
        System.out.printf(Locale.ROOT, "Costo inicial: S/ %.2f%n", inicial.costoTotal());
        System.out.printf(Locale.ROOT, "Costo final: S/ %.2f%n", replanificado.costoTotal());
        System.out.printf(Locale.ROOT, "Distancia inicial: %.1f km%n", distanciaTotal(inicial));
        System.out.printf(Locale.ROOT, "Distancia final: %.1f km%n", distanciaTotal(replanificado));
        System.out.printf("Pedidos atendidos: %s%n", pedidosAtendidos(replanificado));
        System.out.printf("Pedidos no atendidos: %s%n", ids(replanificado.noAtendidos()));
        System.out.printf("Plan factible: %s%n", replanificado.esFactible() ? "SI" : "NO");
        System.out.printf("Carga negativa: %s%n", tieneCargaNegativa(replanificado) ? "SI" : "NO");
        System.out.println("============================================================");
        System.out.println("              FIN DEMOSTRACION SA");
        System.out.println("============================================================");
    }

    private OperationalSimulatedAnnealingPlanner.Ejecucion ejecutar(OperationalSnapshot snapshot, List<Order> pedidos,
                                                                      List<com.pucp.paqrap.modulos.redvial.entity.RoadBlock> bloques,
                                                                      long semilla) {
        return planificador().ejecutar(snapshot, pedidos, bloques, CONFIG, random(semilla));
    }

    private OperationalSimulatedAnnealingPlanner planificador() {
        RouteScheduler scheduler = new RouteScheduler(new RoadNetwork());
        return new OperationalSimulatedAnnealingPlanner(new OperationalPlanEvaluator(scheduler));
    }

    private OperationalSnapshot snapshot(Warehouse central, List<Vehicle> flota,
                                         Map<String, VehicleOperationalState> estadosPersonalizados,
                                         List<BreakdownEvent> averias) {
        Map<String, VehicleOperationalState> estados = new LinkedHashMap<>();
        for (Vehicle vehiculo : flota) {
            estados.put(vehiculo.id(), estadosPersonalizados.getOrDefault(vehiculo.id(),
                    new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, central.location(), INICIO)));
        }
        Map<VehicleType, VehicleParameters> parametros = new EnumMap<>(VehicleType.class);
        parametros.put(VehicleType.CAR, new VehicleParameters(24, 40.0, 8.0));
        parametros.put(VehicleType.MOTORCYCLE, new VehicleParameters(8, 25.0, 6.0));
        parametros.put(VehicleType.BICYCLE, new VehicleParameters(4, 12.0, 3.0));
        return new OperationalSnapshot(INICIO, new FleetProfile(parametros), InventorySnapshot.from(List.of(central)), estados,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()), new ShiftSchedule(ShiftSchedule.DEFAULT_ZONE), averias);
    }

    private void verificarResultadoFactible(ResultadoPlanificacion resultado, List<Order> pedidos) {
        assertTrue(resultado.esFactible(), () -> "violaciones: " + resultado.evaluacion().violations());
        assertFalse(resultado.plan().routes().isEmpty(), "el plan debe contener rutas");
        assertTrue(resultado.noAtendidos().isEmpty(), "el escenario debe atender todos los pedidos");
        assertTrue(pedidosAtendidos(resultado).containsAll(ids(pedidos)), "todos los pedidos deben ser atendidos");
        assertFalse(tieneCargaNegativa(resultado), "no puede haber carga negativa");
    }

    private void imprimirEncabezado(List<Vehicle> flota, FleetProfile perfil, List<Order> pedidos) {
        System.out.println("\n============================================================");
        System.out.println("          DEMOSTRACION INTEGRAL SA - PAQRAP");
        System.out.println("============================================================");
        System.out.println("CONFIGURACION SA");
        separador();
        System.out.printf(Locale.ROOT, "Temperatura inicial: %.2f%nTemperatura minima: %.2f%nCooling factor: %.2f%nIteraciones: %d%n",
                CONFIG.initialTemperature(), CONFIG.minimumTemperature(), CONFIG.coolingFactor(), CONFIG.maximumIterations());
        System.out.println("\nFLOTA DISPONIBLE");
        separador();
        for (Vehicle vehiculo : flota) {
            VehicleParameters parametros = perfil.parametersFor(vehiculo.type());
            System.out.printf(Locale.ROOT, "%s | %s | capacidad: %d | velocidad: %.1f km/h%n", vehiculo.id(),
                    vehiculo.type(), parametros.capacity(), parametros.speedKmPerHour());
        }
        System.out.println("\nPEDIDOS");
        separador();
        for (Order pedido : pedidos) {
            System.out.printf("%s | destino %s | cantidad %d | plazo %s%n", pedido.id(), ubicacion(pedido.destination()),
                    pedido.packages(), pedido.deadline());
        }
    }

    private void imprimirPlan(ResultadoPlanificacion resultado) {
        for (DeliveryRoute ruta : rutasOrdenadas(resultado)) {
            ScheduledDeliveryRoute programada = resultado.evaluacion().schedulesByRouteId().get(ruta.id());
            System.out.printf("%s:%n%s%n", ruta.vehicle().id(), textoRuta(ruta));
            System.out.printf(Locale.ROOT, "Distancia: %.1f km | Costo: S/ %.2f%n", programada.totalDistanceKm(), programada.totalCost());
        }
        System.out.printf(Locale.ROOT, "Costo total: S/ %.2f | Distancia total: %.1f km | Plan factible: %s%n",
                resultado.costoTotal(), distanciaTotal(resultado), resultado.esFactible() ? "SI" : "NO");
    }

    private void imprimirDatosSa(OperationalSimulatedAnnealingPlanner.Ejecucion ejecucion) {
        System.out.println("DATOS DEL SA");
        separador();
        System.out.printf(Locale.ROOT, "Costo solucion inicial: S/ %.2f%nMejor costo: S/ %.2f%nVecinos evaluados: %d%nVecinos aceptados: %d%n",
                ejecucion.initialCost(), ejecucion.resultado().costoTotal(), ejecucion.evaluatedNeighbors(), ejecucion.acceptedNeighbors());
    }

    private void imprimirReasignaciones(List<String> afectados, ResultadoPlanificacion antes, ResultadoPlanificacion despues) {
        for (String pedido : afectados) {
            Optional<String> origen = vehiculoQueAtiende(antes, pedido);
            Optional<String> destino = vehiculoQueAtiende(despues, pedido);
            if (origen.isPresent() && destino.isPresent() && !origen.equals(destino)) {
                System.out.printf("%s: %s -> %s%n", pedido, origen.orElseThrow(), destino.orElseThrow());
            }
        }
    }

    private List<DeliveryRoute> rutasOrdenadas(ResultadoPlanificacion resultado) {
        return resultado.plan().routes().stream().sorted(Comparator.comparing(ruta -> ruta.vehicle().id())).toList();
    }

    private List<String> pedidosDeVehiculo(ResultadoPlanificacion resultado, String vehiculoId) {
        return resultado.plan().routeForVehicle(vehiculoId).map(this::pedidosDeRuta).orElse(List.of());
    }

    private List<String> pedidosDeRuta(DeliveryRoute ruta) {
        return ruta.stops().stream().filter(DeliveryStop.class::isInstance).map(DeliveryStop.class::cast)
                .map(entrega -> entrega.order().id()).toList();
    }

    private Set<String> pedidosAtendidos(ResultadoPlanificacion resultado) {
        Set<String> atendidos = new LinkedHashSet<>();
        rutasOrdenadas(resultado).forEach(ruta -> atendidos.addAll(pedidosDeRuta(ruta)));
        return atendidos;
    }

    private Optional<String> vehiculoQueAtiende(ResultadoPlanificacion resultado, String pedidoId) {
        return resultado.plan().routes().stream().filter(ruta -> pedidosDeRuta(ruta).contains(pedidoId))
                .map(ruta -> ruta.vehicle().id()).findFirst();
    }

    private boolean tieneCargaNegativa(ResultadoPlanificacion resultado) {
        return resultado.evaluacion().schedulesByRouteId().values().stream()
                .flatMap(ruta -> ruta.scheduledStops().stream())
                .anyMatch(parada -> parada.loadBefore() < 0 || parada.loadAfter() < 0);
    }

    private double distanciaTotal(ResultadoPlanificacion resultado) {
        return resultado.evaluacion().schedulesByRouteId().values().stream().mapToDouble(ScheduledDeliveryRoute::totalDistanceKm).sum();
    }

    private List<String> ids(List<Order> pedidos) {
        return pedidos.stream().map(Order::id).toList();
    }

    private String textoRuta(DeliveryRoute ruta) {
        StringJoiner texto = new StringJoiner(" -> ");
        texto.add(ruta.initialWarehouse().map(Warehouse::id).orElse("ubicacion actual"));
        for (RouteStop parada : ruta.stops()) {
            if (parada instanceof DeliveryStop entrega) texto.add(entrega.order().id());
            if (parada instanceof WarehouseVisit visita) texto.add(visita.warehouse().id());
        }
        return texto.toString();
    }

    private String ubicacion(Location ubicacion) {
        return "(" + ubicacion.x() + "," + ubicacion.y() + ")";
    }

    private RandomGenerator random(long semilla) {
        return RandomGeneratorFactory.<RandomGenerator>of("L64X128MixRandom").create(semilla);
    }

    private void separador() {
        System.out.println("------------------------------------------------------------");
    }
}
