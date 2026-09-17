package com.pucp.paqrap.modulos.planificacion.algoritmo.grasp;

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
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
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
import java.util.EnumMap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoIntegralGraspTest {

    private static final Instant INICIO = Instant.parse("2026-09-09T12:00:00Z");
    private static final long SEMILLA = 20260917L;

    @Test
    void demuestraPlanificacionYReplanificacionAnteAveria() {
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

        Map<String, VehicleOperationalState> estadosIniciales = Map.of(
                ta01.id(), new VehicleOperationalState(ta01, VehicleStatus.AVAILABLE, central.location(), INICIO),
                ta02.id(), new VehicleOperationalState(ta02, VehicleStatus.AVAILABLE, central.location(), INICIO.plusSeconds(3 * 3600)),
                tm01.id(), new VehicleOperationalState(tm01, VehicleStatus.AVAILABLE, central.location(), INICIO),
                tb01.id(), new VehicleOperationalState(tb01, VehicleStatus.AVAILABLE, central.location(), INICIO));
        OperationalSnapshot antesDeAveria = snapshot(central, flota, estadosIniciales, List.of());

        imprimirEncabezado();
        imprimirFlota(flota, antesDeAveria.fleetProfile());
        imprimirPedidos(pedidos);

        ResultadoPlanificacion inicial = planificador().planificar(antesDeAveria, pedidos, List.of(), 0.0, 1);
        System.out.println("\nPLANIFICACION INICIAL GRASP");
        separador();
        imprimirPlan(inicial, antesDeAveria.fleetProfile());
        verificarResultadoFactible(inicial, pedidos);
        assertTrue(inicial.plan().routeForVehicle(ta01.id()).isPresent(),
                "TA01 debe participar en el plan inicial para que la averia tenga efecto demostrable");

        List<String> pedidosAfectados = pedidosDeVehiculo(inicial, ta01.id());
        assertFalse(pedidosAfectados.isEmpty(), "la ruta inicial de TA01 debe contener pedidos");

        BreakdownEvent averia = new BreakdownEvent(ta01.id(), BreakdownType.MINOR, INICIO, central.location());
        OperationalSnapshot despuesDeAveria = snapshot(central, flota, Map.of(), List.of(averia));
        assertFalse(despuesDeAveria.isVehiclePlannable(ta01.id()),
                "TA01 debe quedar indisponible al momento de la averia");

        System.out.println("\n============================================================");
        System.out.println("INCIDENCIA");
        System.out.println("============================================================");
        System.out.printf("Vehiculo averiado: %s%n", averia.vehicleId());
        System.out.printf("Tipo de averia: %s%n", averia.type());
        System.out.printf("Momento de la averia: %s%n", averia.occurredAt());
        System.out.printf("Tiempo minimo de indisponibilidad: %s%n", averia.type().minimumUnavailable());
        System.out.printf("Ubicacion: %s%n", ubicacion(averia.location()));
        System.out.printf("Ruta que tenia %s: %s%n", ta01.id(), textoRuta(inicial.plan().routeForVehicle(ta01.id()).orElseThrow()));
        System.out.printf("Pedidos afectados: %s%n", pedidosAfectados);

        ResultadoPlanificacion finalReplanificado = planificador().planificar(
                despuesDeAveria, pedidos, List.of(), 0.0, 1);
        verificarResultadoFactible(finalReplanificado, pedidos);
        assertTrue(finalReplanificado.plan().routeForVehicle(ta01.id()).isEmpty(),
                "TA01 no debe recibir asignaciones durante su indisponibilidad");
        for (String pedidoAfectado : pedidosAfectados) {
            Optional<String> nuevoVehiculo = vehiculoQueAtiende(finalReplanificado, pedidoAfectado);
            assertTrue(nuevoVehiculo.isPresent(), "el pedido afectado debe ser atendido si existe capacidad");
            assertFalse(nuevoVehiculo.orElseThrow().equals(ta01.id()),
                    "un pedido afectado no puede permanecer asignado a TA01");
        }

        System.out.println("\nREPLANIFICACION GRASP");
        separador();
        System.out.println("Plan antes:");
        imprimirResumen(inicial);
        System.out.println("Plan despues:");
        imprimirPlan(finalReplanificado, despuesDeAveria.fleetProfile());
        imprimirReasignaciones(pedidosAfectados, inicial, finalReplanificado);

        System.out.println("\nRESULTADO FINAL");
        separador();
        System.out.printf(Locale.ROOT, "Costo inicial: S/ %.2f%n", inicial.costoTotal());
        System.out.printf(Locale.ROOT, "Costo final: S/ %.2f%n", finalReplanificado.costoTotal());
        System.out.printf(Locale.ROOT, "Distancia inicial: %.1f km%n", distanciaTotal(inicial));
        System.out.printf(Locale.ROOT, "Distancia final: %.1f km%n", distanciaTotal(finalReplanificado));
        System.out.printf("Vehiculos utilizados: %d%n", finalReplanificado.plan().routes().size());
        System.out.printf("Pedidos atendidos: %s%n", pedidosAtendidos(finalReplanificado));
        System.out.printf("Pedidos no atendidos: %s%n", ids(finalReplanificado.noAtendidos()));
        System.out.printf("Plan factible: %s%n", finalReplanificado.esFactible() ? "SI" : "NO");
        System.out.printf("Carga negativa detectada: %s%n", tieneCargaNegativa(finalReplanificado) ? "SI" : "NO");
        System.out.println("============================================================");
        System.out.println("                 FIN DE DEMOSTRACION");
        System.out.println("============================================================");
    }

    private GraspPlanificador planificador() {
        RoadNetwork redVial = new RoadNetwork();
        RouteScheduler scheduler = new RouteScheduler(redVial);
        return new GraspPlanificador(redVial, scheduler, new OperationalPlanEvaluator(scheduler), SEMILLA);
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
        return new OperationalSnapshot(INICIO, new FleetProfile(parametros), InventorySnapshot.from(List.of(central)),
                estados, new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()),
                new ShiftSchedule(ShiftSchedule.DEFAULT_ZONE), averias);
    }

    private void verificarResultadoFactible(ResultadoPlanificacion resultado, List<Order> pedidos) {
        assertNotNull(resultado);
        assertTrue(resultado.esFactible(), () -> "violaciones: " + resultado.evaluacion().violations());
        assertFalse(resultado.plan().routes().isEmpty(), "el plan debe contener al menos una ruta");
        assertFalse(pedidosAtendidos(resultado).isEmpty(), "el plan debe atender pedidos");
        assertTrue(resultado.noAtendidos().isEmpty(), "el escenario fue elegido para atender todos los pedidos");
        assertTrue(pedidosAtendidos(resultado).containsAll(ids(pedidos)), "todos los pedidos deben estar atendidos");
        assertFalse(tieneCargaNegativa(resultado), "ninguna carga programada puede ser negativa");
    }

    private void imprimirEncabezado() {
        System.out.println("\n============================================================");
        System.out.println("              DEMOSTRACION INTEGRAL GRASP - PAQRAP");
        System.out.println("============================================================");
        System.out.println("FECHA/HORA DE PLANIFICACION");
        separador();
        System.out.println(INICIO);
    }

    private void imprimirFlota(List<Vehicle> flota, FleetProfile perfil) {
        System.out.println("\nFLOTA DISPONIBLE");
        separador();
        for (Vehicle vehiculo : flota) {
            VehicleParameters parametros = perfil.parametersFor(vehiculo.type());
            System.out.printf(Locale.ROOT, "%s | %s | capacidad: %d | velocidad: %.1f km/h%n",
                    vehiculo.id(), vehiculo.type(), parametros.capacity(), parametros.speedKmPerHour());
        }
    }

    private void imprimirPedidos(List<Order> pedidos) {
        System.out.println("\nPEDIDOS");
        separador();
        for (Order pedido : pedidos) {
            System.out.printf("%s | destino: %s | cantidad: %d | plazo: %s%n",
                    pedido.id(), ubicacion(pedido.destination()), pedido.packages(), pedido.deadline());
        }
    }

    private void imprimirPlan(ResultadoPlanificacion resultado, FleetProfile perfil) {
        for (DeliveryRoute ruta : rutasOrdenadas(resultado)) {
            ScheduledDeliveryRoute programada = resultado.evaluacion().schedulesByRouteId().get(ruta.id());
            VehicleParameters parametros = perfil.parametersFor(ruta.vehicle().type());
            System.out.printf("Vehiculo: %s%n", ruta.vehicle().id());
            System.out.printf("Tipo: %s%n", ruta.vehicle().type());
            System.out.printf("Capacidad: %d%n", parametros.capacity());
            System.out.printf("Carga utilizada maxima: %d%n", cargaMaxima(ruta, programada));
            System.out.println("Ruta:");
            System.out.println(textoRuta(ruta));
            System.out.printf(Locale.ROOT, "Distancia total de la ruta: %.1f km%n", programada.totalDistanceKm());
            System.out.printf(Locale.ROOT, "Costo de la ruta: S/ %.2f%n", programada.totalCost());
            System.out.printf("Pedidos atendidos: %s%n%n", pedidosDeRuta(ruta));
        }
        imprimirResumen(resultado);
    }

    private void imprimirResumen(ResultadoPlanificacion resultado) {
        System.out.println("RESUMEN DEL PLAN");
        separador();
        System.out.printf("Vehiculos utilizados: %d%n", resultado.plan().routes().size());
        System.out.printf("Pedidos atendidos: %s%n", pedidosAtendidos(resultado));
        System.out.printf("Pedidos no atendidos: %s%n", ids(resultado.noAtendidos()));
        System.out.printf(Locale.ROOT, "Distancia total: %.1f km%n", distanciaTotal(resultado));
        System.out.printf(Locale.ROOT, "Costo total: S/ %.2f%n", resultado.costoTotal());
        System.out.printf("Plan factible: %s%n", resultado.esFactible() ? "SI" : "NO");
    }

    private void imprimirReasignaciones(List<String> pedidosAfectados, ResultadoPlanificacion inicial,
                                        ResultadoPlanificacion finalReplanificado) {
        for (String pedido : pedidosAfectados) {
            Optional<String> antes = vehiculoQueAtiende(inicial, pedido);
            Optional<String> despues = vehiculoQueAtiende(finalReplanificado, pedido);
            if (antes.isPresent() && despues.isPresent() && !antes.equals(despues)) {
                System.out.printf("%s: %s -> %s%n", pedido, antes.orElseThrow(), despues.orElseThrow());
            }
        }
    }

    private String textoRuta(DeliveryRoute ruta) {
        StringJoiner rutaTexto = new StringJoiner(" -> ");
        rutaTexto.add(ruta.initialWarehouse().map(Warehouse::id).orElse("ubicacion actual"));
        for (RouteStop parada : ruta.stops()) {
            if (parada instanceof DeliveryStop entrega) {
                rutaTexto.add(entrega.order().id());
            } else if (parada instanceof WarehouseVisit visita) {
                rutaTexto.add(visita.warehouse().id() + " (recarga " + visita.pickupPackages() + ")");
            }
        }
        return rutaTexto.toString();
    }

    private int cargaMaxima(DeliveryRoute ruta, ScheduledDeliveryRoute programada) {
        return Math.max(ruta.initialLoad(), programada.scheduledStops().stream()
                .flatMapToInt(parada -> java.util.stream.IntStream.of(parada.loadBefore(), parada.loadAfter()))
                .max().orElse(ruta.initialLoad()));
    }

    private boolean tieneCargaNegativa(ResultadoPlanificacion resultado) {
        return resultado.evaluacion().schedulesByRouteId().values().stream()
                .flatMap(ruta -> ruta.scheduledStops().stream())
                .anyMatch(parada -> parada.loadBefore() < 0 || parada.loadAfter() < 0);
    }

    private double distanciaTotal(ResultadoPlanificacion resultado) {
        return resultado.evaluacion().schedulesByRouteId().values().stream()
                .mapToDouble(ScheduledDeliveryRoute::totalDistanceKm)
                .sum();
    }

    private List<String> pedidosDeVehiculo(ResultadoPlanificacion resultado, String vehiculoId) {
        return resultado.plan().routeForVehicle(vehiculoId)
                .map(this::pedidosDeRuta)
                .orElse(List.of());
    }

    private List<String> pedidosDeRuta(DeliveryRoute ruta) {
        return ruta.stops().stream()
                .filter(DeliveryStop.class::isInstance)
                .map(DeliveryStop.class::cast)
                .map(entrega -> entrega.order().id())
                .toList();
    }

    private Set<String> pedidosAtendidos(ResultadoPlanificacion resultado) {
        Set<String> atendidos = new LinkedHashSet<>();
        for (DeliveryRoute ruta : rutasOrdenadas(resultado)) {
            atendidos.addAll(pedidosDeRuta(ruta));
        }
        return atendidos;
    }

    private List<DeliveryRoute> rutasOrdenadas(ResultadoPlanificacion resultado) {
        return resultado.plan().routes().stream()
                .sorted(Comparator.comparing(ruta -> ruta.vehicle().id()))
                .toList();
    }

    private Optional<String> vehiculoQueAtiende(ResultadoPlanificacion resultado, String pedidoId) {
        return resultado.plan().routes().stream()
                .filter(ruta -> pedidosDeRuta(ruta).contains(pedidoId))
                .map(ruta -> ruta.vehicle().id())
                .findFirst();
    }

    private List<String> ids(List<Order> pedidos) {
        return pedidos.stream().map(Order::id).toList();
    }

    private String ubicacion(Location ubicacion) {
        return "(" + ubicacion.x() + "," + ubicacion.y() + ")";
    }

    private void separador() {
        System.out.println("------------------------------------------------------------");
    }
}
