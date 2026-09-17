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
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.entity.RoadLeg;
import com.pucp.paqrap.modulos.redvial.entity.StreetSegment;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoReplanificacionBloqueoGraspTest {

    private static final Instant INICIO = Instant.parse("2026-09-09T12:00:00Z");
    private static final long SEMILLA = 71L;

    @Test
    void demuestraQueUnBloqueoAumentaLaDistanciaYElCostoDeLaReplanificacion() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(0, 0));
        Vehicle auto = new Vehicle("TA01", VehicleType.CAR, true);
        Vehicle moto = new Vehicle("TM01", VehicleType.MOTORCYCLE, true);
        List<Vehicle> flota = List.of(auto, moto);
        List<Order> pedidos = List.of(
                new Order("P01", new Location(0, 3), 12, INICIO, INICIO.plusSeconds(8 * 3600)),
                new Order("P02", new Location(3, 0), 2, INICIO, INICIO.plusSeconds(8 * 3600)));
        OperationalSnapshot snapshot = snapshot(central, flota);

        ResultadoPlanificacion inicial = planificador().planificar(snapshot, pedidos, List.of(), 0.0, 1);
        RoadBlock bloqueo = new RoadBlock(INICIO, INICIO.plusSeconds(2 * 3600),
                List.of(new Location(0, 1), new Location(0, 2)));
        ResultadoPlanificacion replanificado = planificador().planificar(snapshot, pedidos, List.of(bloqueo), 0.0, 1);

        assertTrue(inicial.esFactible(), () -> "violaciones iniciales: " + inicial.evaluacion().violations());
        assertTrue(replanificado.esFactible(), () -> "violaciones finales: " + replanificado.evaluacion().violations());
        assertTrue(inicial.noAtendidos().isEmpty(), "el plan inicial debe atender todos los pedidos");
        assertTrue(replanificado.noAtendidos().isEmpty(), "el plan con bloqueo debe conservar una solucion completa");
        assertTrue(utilizaAlgunoDeLosSegmentos(inicial, bloqueo.blockedSegments()),
                "el bloqueo debe afectar directamente la ruta inicial");
        assertFalse(utilizaAlgunoDeLosSegmentos(replanificado, bloqueo.blockedSegments()),
                "la ruta replanificada no debe atravesar el tramo bloqueado");
        assertFalse(tieneCargaNegativa(inicial), "el plan inicial no puede tener cargas negativas");
        assertFalse(tieneCargaNegativa(replanificado), "el plan replanificado no puede tener cargas negativas");

        double distanciaInicial = distanciaTotal(inicial);
        double distanciaFinal = distanciaTotal(replanificado);
        assertTrue(distanciaFinal > distanciaInicial,
                () -> "la distancia debe crecer: inicial=" + distanciaInicial + ", final=" + distanciaFinal);
        assertTrue(replanificado.costoTotal() > inicial.costoTotal(),
                () -> "el costo debe crecer: inicial=" + inicial.costoTotal() + ", final=" + replanificado.costoTotal());

        imprimirDemostracion(pedidos, bloqueo, inicial, replanificado, distanciaInicial, distanciaFinal);
    }

    private GraspPlanificador planificador() {
        RoadNetwork redVial = new RoadNetwork();
        RouteScheduler scheduler = new RouteScheduler(redVial);
        return new GraspPlanificador(redVial, scheduler, new OperationalPlanEvaluator(scheduler), SEMILLA);
    }

    private OperationalSnapshot snapshot(Warehouse central, List<Vehicle> flota) {
        Map<String, VehicleOperationalState> estados = new LinkedHashMap<>();
        for (Vehicle vehiculo : flota) {
            estados.put(vehiculo.id(), new VehicleOperationalState(
                    vehiculo, VehicleStatus.AVAILABLE, central.location(), INICIO));
        }
        Map<VehicleType, VehicleParameters> parametros = new EnumMap<>(VehicleType.class);
        parametros.put(VehicleType.CAR, new VehicleParameters(24, 40.0, 8.0));
        parametros.put(VehicleType.MOTORCYCLE, new VehicleParameters(8, 25.0, 6.0));
        parametros.put(VehicleType.BICYCLE, new VehicleParameters(4, 12.0, 3.0));
        return new OperationalSnapshot(INICIO, new FleetProfile(parametros), InventorySnapshot.from(List.of(central)),
                estados, new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()),
                new ShiftSchedule(ShiftSchedule.DEFAULT_ZONE), List.of());
    }

    private void imprimirDemostracion(List<Order> pedidos, RoadBlock bloqueo, ResultadoPlanificacion inicial,
                                      ResultadoPlanificacion replanificado, double distanciaInicial, double distanciaFinal) {
        System.out.println("\n============================================================");
        System.out.println("        DEMOSTRACION GRASP - REPLANIFICACION POR BLOQUEO");
        System.out.println("============================================================");
        System.out.println("PLANIFICACION INICIAL");
        separador();
        imprimirPedidos(pedidos);
        imprimirRutas(inicial);
        System.out.printf(Locale.ROOT, "Distancia inicial: %.1f km%n", distanciaInicial);
        System.out.printf(Locale.ROOT, "Costo inicial: S/ %.2f%n", inicial.costoTotal());

        System.out.println("\n============================================================");
        System.out.println("BLOQUEO VIAL DETECTADO");
        System.out.println("============================================================");
        System.out.printf("Tramo bloqueado: %s%n", textoTramos(bloqueo.blockedSegments()));
        System.out.printf("Periodo: %s -> %s%n", bloqueo.startsAt(), bloqueo.endsAt());
        System.out.printf("El tramo formaba parte de la ruta original: %s%n",
                utilizaAlgunoDeLosSegmentos(inicial, bloqueo.blockedSegments()) ? "SI" : "NO");
        System.out.println("Replanificando...");

        System.out.println("\n============================================================");
        System.out.println("NUEVA PLANIFICACION");
        System.out.println("============================================================");
        System.out.println("Ruta anterior:");
        imprimirRutas(inicial);
        System.out.println("Ruta nueva:");
        imprimirRutas(replanificado);
        System.out.printf(Locale.ROOT, "Distancia inicial: %.1f km%n", distanciaInicial);
        System.out.printf(Locale.ROOT, "Distancia final: %.1f km%n", distanciaFinal);
        System.out.printf(Locale.ROOT, "Incremento: +%.1f km%n", distanciaFinal - distanciaInicial);
        System.out.printf(Locale.ROOT, "Costo inicial: S/ %.2f%n", inicial.costoTotal());
        System.out.printf(Locale.ROOT, "Costo final: S/ %.2f%n", replanificado.costoTotal());
        System.out.printf(Locale.ROOT, "Incremento: +S/ %.2f%n", replanificado.costoTotal() - inicial.costoTotal());
        System.out.printf("Pedidos atendidos: %s%n", pedidosAtendidos(replanificado));
        System.out.printf("Pedidos no atendidos: %s%n", ids(replanificado.noAtendidos()));
        System.out.printf("Plan inicial factible: %s%n", inicial.esFactible() ? "SI" : "NO");
        System.out.printf("Plan final factible: %s%n", replanificado.esFactible() ? "SI" : "NO");
        System.out.printf("Tramo bloqueado utilizado despues de replanificar: %s%n",
                utilizaAlgunoDeLosSegmentos(replanificado, bloqueo.blockedSegments()) ? "SI" : "NO");

        System.out.println("\n============================================================");
        System.out.println("COMPARACION");
        System.out.println("============================================================");
        System.out.printf(Locale.ROOT, "DISTANCIA%nInicial: %.1f km%nFinal:   %.1f km%nCambio:  +%.1f km%n",
                distanciaInicial, distanciaFinal, distanciaFinal - distanciaInicial);
        System.out.printf(Locale.ROOT, "COSTO%nInicial: S/ %.2f%nFinal:   S/ %.2f%nCambio:  +S/ %.2f%n",
                inicial.costoTotal(), replanificado.costoTotal(), replanificado.costoTotal() - inicial.costoTotal());
        System.out.println("Resultado:");
        System.out.println("GRASP encontro una nueva ruta factible evitando el bloqueo.");
        System.out.println("============================================================");
        System.out.println("                  FIN DE DEMOSTRACION");
        System.out.println("============================================================");
    }

    private void imprimirPedidos(List<Order> pedidos) {
        System.out.println("PEDIDOS:");
        for (Order pedido : pedidos) {
            System.out.printf("%s | destino: %s | cantidad: %d | plazo: %s%n",
                    pedido.id(), ubicacion(pedido.destination()), pedido.packages(), pedido.deadline());
        }
    }

    private void imprimirRutas(ResultadoPlanificacion resultado) {
        for (DeliveryRoute ruta : rutasOrdenadas(resultado)) {
            ScheduledDeliveryRoute programada = resultado.evaluacion().schedulesByRouteId().get(ruta.id());
            System.out.printf("Vehiculo: %s%n", ruta.vehicle().id());
            System.out.printf("Ruta: %s%n", textoRuta(ruta));
            System.out.printf("Nodos reales: %s%n", nodosRecorridos(programada));
            System.out.printf("Tramos reales: %s%n", textoTramos(tramos(programada)));
        }
    }

    private boolean utilizaAlgunoDeLosSegmentos(ResultadoPlanificacion resultado, Set<StreetSegment> bloqueados) {
        return resultado.evaluacion().schedulesByRouteId().values().stream()
                .flatMap(ruta -> tramos(ruta).stream())
                .anyMatch(bloqueados::contains);
    }

    private Set<StreetSegment> tramos(ScheduledDeliveryRoute ruta) {
        return ruta.scheduledStops().stream()
                .flatMap(parada -> parada.approach().legs().stream())
                .map(tramo -> new StreetSegment(tramo.from(), tramo.to()))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
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

    private List<DeliveryRoute> rutasOrdenadas(ResultadoPlanificacion resultado) {
        return resultado.plan().routes().stream()
                .sorted(Comparator.comparing(ruta -> ruta.vehicle().id()))
                .toList();
    }

    private List<String> pedidosAtendidos(ResultadoPlanificacion resultado) {
        return rutasOrdenadas(resultado).stream()
                .flatMap(ruta -> ruta.stops().stream())
                .filter(DeliveryStop.class::isInstance)
                .map(DeliveryStop.class::cast)
                .map(entrega -> entrega.order().id())
                .toList();
    }

    private List<String> ids(List<Order> pedidos) {
        return pedidos.stream().map(Order::id).toList();
    }

    private String textoRuta(DeliveryRoute ruta) {
        StringJoiner texto = new StringJoiner(" -> ");
        texto.add(ruta.initialWarehouse().map(Warehouse::id).orElse("ubicacion actual"));
        for (RouteStop parada : ruta.stops()) {
            if (parada instanceof DeliveryStop entrega) {
                texto.add(entrega.order().id());
            } else if (parada instanceof WarehouseVisit visita) {
                texto.add(visita.warehouse().id());
            }
        }
        return texto.toString();
    }

    private String nodosRecorridos(ScheduledDeliveryRoute ruta) {
        StringJoiner nodos = new StringJoiner(" -> ");
        nodos.add(ubicacion(ruta.route().startLocation()));
        ruta.scheduledStops().stream()
                .flatMap(parada -> parada.approach().legs().stream())
                .map(RoadLeg::to)
                .map(this::ubicacion)
                .forEach(nodos::add);
        return nodos.toString();
    }

    private String textoTramos(Set<StreetSegment> tramos) {
        return tramos.stream()
                .map(tramo -> ubicacion(tramo.first()) + "->" + ubicacion(tramo.second()))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String ubicacion(Location ubicacion) {
        return "(" + ubicacion.x() + "," + ubicacion.y() + ")";
    }

    private void separador() {
        System.out.println("------------------------------------------------------------");
    }
}
