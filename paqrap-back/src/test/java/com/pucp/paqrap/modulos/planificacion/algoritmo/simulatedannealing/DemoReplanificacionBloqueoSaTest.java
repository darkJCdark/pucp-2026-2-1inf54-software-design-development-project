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
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
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
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoReplanificacionBloqueoSaTest {

    private static final Instant INICIO = Instant.parse("2026-09-09T12:00:00Z");
    private static final AnnealingConfig CONFIG = new AnnealingConfig(100.0, 1.0, 0.90, 5, 30, 30);

    @Test
    void demuestraQueUnBloqueoAumentaLaDistanciaYElCostoDeLaReplanificacion() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(0, 0));
        List<Vehicle> flota = List.of(new Vehicle("TA01", VehicleType.CAR, true),
                new Vehicle("TM01", VehicleType.MOTORCYCLE, true));
        List<Order> pedidos = List.of(
                new Order("P01", new Location(0, 3), 12, INICIO, INICIO.plusSeconds(8 * 3600)),
                new Order("P02", new Location(3, 0), 2, INICIO, INICIO.plusSeconds(8 * 3600)));
        OperationalSnapshot snapshot = snapshot(central, flota);

        OperationalSimulatedAnnealingPlanner.Ejecucion ejecucionInicial = ejecutar(snapshot, pedidos, List.of(), 71L);
        ResultadoPlanificacion inicial = ejecucionInicial.resultado();
        RoadBlock bloqueo = new RoadBlock(INICIO, INICIO.plusSeconds(2 * 3600),
                List.of(new Location(0, 1), new Location(0, 2)));
        OperationalSimulatedAnnealingPlanner.Ejecucion ejecucionFinal = ejecutar(snapshot, pedidos, List.of(bloqueo), 71L);
        ResultadoPlanificacion replanificado = ejecucionFinal.resultado();

        assertTrue(inicial.esFactible(), () -> "violaciones iniciales: " + inicial.evaluacion().violations());
        assertTrue(replanificado.esFactible(), () -> "violaciones finales: " + replanificado.evaluacion().violations());
        assertTrue(inicial.noAtendidos().isEmpty());
        assertTrue(replanificado.noAtendidos().isEmpty());
        assertTrue(pedidosAtendidos(inicial).containsAll(ids(pedidos)));
        assertTrue(pedidosAtendidos(replanificado).containsAll(ids(pedidos)));
        assertTrue(utilizaElBloqueoDuranteSuVigencia(inicial, bloqueo),
                "el bloqueo debe afectar la ruta inicial");
        assertFalse(utilizaElBloqueoDuranteSuVigencia(replanificado, bloqueo),
                "la nueva ruta no debe usar el tramo mientras el bloqueo esta activo");
        assertFalse(tieneCargaNegativa(inicial));
        assertFalse(tieneCargaNegativa(replanificado));

        double distanciaInicial = distanciaTotal(inicial);
        double distanciaFinal = distanciaTotal(replanificado);
        assertTrue(distanciaFinal > distanciaInicial);
        assertTrue(replanificado.costoTotal() > inicial.costoTotal());

        imprimirDemostracion(pedidos, bloqueo, ejecucionInicial, ejecucionFinal, distanciaInicial, distanciaFinal);
    }

    private OperationalSimulatedAnnealingPlanner.Ejecucion ejecutar(OperationalSnapshot snapshot, List<Order> pedidos,
                                                                      List<RoadBlock> bloques, long semilla) {
        RouteScheduler scheduler = new RouteScheduler(new RoadNetwork());
        return new OperationalSimulatedAnnealingPlanner(new OperationalPlanEvaluator(scheduler)).ejecutar(
                snapshot, pedidos, bloques, CONFIG, random(semilla));
    }

    private OperationalSnapshot snapshot(Warehouse central, List<Vehicle> flota) {
        Map<String, VehicleOperationalState> estados = new LinkedHashMap<>();
        for (Vehicle vehiculo : flota) {
            estados.put(vehiculo.id(), new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, central.location(), INICIO));
        }
        Map<VehicleType, VehicleParameters> parametros = new EnumMap<>(VehicleType.class);
        parametros.put(VehicleType.CAR, new VehicleParameters(24, 40.0, 8.0));
        parametros.put(VehicleType.MOTORCYCLE, new VehicleParameters(8, 25.0, 6.0));
        parametros.put(VehicleType.BICYCLE, new VehicleParameters(4, 12.0, 3.0));
        return new OperationalSnapshot(INICIO, new FleetProfile(parametros), InventorySnapshot.from(List.of(central)), estados,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()), new ShiftSchedule(ShiftSchedule.DEFAULT_ZONE), List.of());
    }

    private void imprimirDemostracion(List<Order> pedidos, RoadBlock bloqueo,
                                      OperationalSimulatedAnnealingPlanner.Ejecucion inicial,
                                      OperationalSimulatedAnnealingPlanner.Ejecucion finalReplanificado,
                                      double distanciaInicial, double distanciaFinal) {
        ResultadoPlanificacion antes = inicial.resultado();
        ResultadoPlanificacion despues = finalReplanificado.resultado();
        System.out.println("\n============================================================");
        System.out.println("      DEMOSTRACION SA - REPLANIFICACION POR BLOQUEO");
        System.out.println("============================================================");
        System.out.println("PLANIFICACION INICIAL");
        separador();
        imprimirPedidos(pedidos);
        imprimirRutas(antes);
        System.out.printf(Locale.ROOT, "Distancia inicial: %.1f km%nCosto inicial: S/ %.2f%n", distanciaInicial, antes.costoTotal());
        imprimirDatosSa(inicial);

        System.out.println("\n============================================================");
        System.out.println("BLOQUEO VIAL");
        System.out.println("============================================================");
        System.out.printf("Tramo bloqueado: %s%n", textoTramos(bloqueo.blockedSegments()));
        System.out.printf("El tramo estaba en la ruta inicial durante el bloqueo: %s%n",
                utilizaElBloqueoDuranteSuVigencia(antes, bloqueo) ? "SI" : "NO");
        System.out.println("Replanificando con Simulated Annealing...");

        System.out.println("\n============================================================");
        System.out.println("PLANIFICACION POSTERIOR");
        System.out.println("============================================================");
        System.out.println("Ruta anterior:");
        imprimirRutas(antes);
        System.out.println("Ruta nueva:");
        imprimirRutas(despues);
        System.out.printf(Locale.ROOT, "Distancia inicial: %.1f km%nDistancia final: %.1f km%nIncremento: +%.1f km%n",
                distanciaInicial, distanciaFinal, distanciaFinal - distanciaInicial);
        System.out.printf(Locale.ROOT, "Costo inicial: S/ %.2f%nCosto final: S/ %.2f%nIncremento: +S/ %.2f%n",
                antes.costoTotal(), despues.costoTotal(), despues.costoTotal() - antes.costoTotal());
        System.out.printf("Plan inicial factible: %s%nPlan final factible: %s%n", antes.esFactible() ? "SI" : "NO",
                despues.esFactible() ? "SI" : "NO");
        System.out.printf("Tramo bloqueado usado durante su vigencia despues: %s%nCarga negativa: %s%n",
                utilizaElBloqueoDuranteSuVigencia(despues, bloqueo) ? "SI" : "NO",
                tieneCargaNegativa(despues) ? "SI" : "NO");
        imprimirDatosSa(finalReplanificado);

        System.out.println("\n============================================================");
        System.out.println("COMPARACION");
        System.out.println("============================================================");
        System.out.printf(Locale.ROOT, "DISTANCIA%nInicial: %.1f km%nFinal: %.1f km%nCambio: +%.1f km%n",
                distanciaInicial, distanciaFinal, distanciaFinal - distanciaInicial);
        System.out.printf(Locale.ROOT, "COSTO%nInicial: S/ %.2f%nFinal: S/ %.2f%nCambio: +S/ %.2f%n",
                antes.costoTotal(), despues.costoTotal(), despues.costoTotal() - antes.costoTotal());
        System.out.println("SA encontro una nueva solucion factible evitando el bloqueo.");
        System.out.println("============================================================");
        System.out.println("             FIN DEMOSTRACION");
        System.out.println("============================================================");
    }

    private void imprimirPedidos(List<Order> pedidos) {
        for (Order pedido : pedidos) {
            System.out.printf("%s | destino: %s | cantidad: %d | plazo: %s%n", pedido.id(), ubicacion(pedido.destination()),
                    pedido.packages(), pedido.deadline());
        }
    }

    private void imprimirRutas(ResultadoPlanificacion resultado) {
        for (DeliveryRoute ruta : resultado.plan().routes().stream().sorted(Comparator.comparing(ruta -> ruta.vehicle().id())).toList()) {
            ScheduledDeliveryRoute programada = resultado.evaluacion().schedulesByRouteId().get(ruta.id());
            System.out.printf("Ruta %s: %s%n", ruta.vehicle().id(), nodosRecorridos(programada));
            System.out.printf("Tramos: %s%n", textoTramos(tramos(programada)));
        }
    }

    private void imprimirDatosSa(OperationalSimulatedAnnealingPlanner.Ejecucion ejecucion) {
        System.out.printf(Locale.ROOT, "Datos SA: inicial S/ %.2f | mejor S/ %.2f | vecinos evaluados: %d | aceptados: %d%n",
                ejecucion.initialCost(), ejecucion.resultado().costoTotal(), ejecucion.evaluatedNeighbors(), ejecucion.acceptedNeighbors());
    }

    private boolean utilizaElBloqueoDuranteSuVigencia(ResultadoPlanificacion resultado, RoadBlock bloqueo) {
        return resultado.evaluacion().schedulesByRouteId().values().stream()
                .flatMap(ruta -> ruta.scheduledStops().stream())
                .flatMap(parada -> parada.approach().legs().stream())
                .anyMatch(tramo -> bloqueo.blockedSegments().contains(new StreetSegment(tramo.from(), tramo.to()))
                        && bloqueo.overlaps(tramo.departsAt(), tramo.arrivesAt()));
    }

    private Set<StreetSegment> tramos(ScheduledDeliveryRoute ruta) {
        return ruta.scheduledStops().stream().flatMap(parada -> parada.approach().legs().stream())
                .map(tramo -> new StreetSegment(tramo.from(), tramo.to()))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private boolean tieneCargaNegativa(ResultadoPlanificacion resultado) {
        return resultado.evaluacion().schedulesByRouteId().values().stream().flatMap(ruta -> ruta.scheduledStops().stream())
                .anyMatch(parada -> parada.loadBefore() < 0 || parada.loadAfter() < 0);
    }

    private double distanciaTotal(ResultadoPlanificacion resultado) {
        return resultado.evaluacion().schedulesByRouteId().values().stream().mapToDouble(ScheduledDeliveryRoute::totalDistanceKm).sum();
    }

    private List<String> pedidosAtendidos(ResultadoPlanificacion resultado) {
        return resultado.plan().routes().stream().flatMap(ruta -> ruta.stops().stream()).filter(DeliveryStop.class::isInstance)
                .map(DeliveryStop.class::cast).map(entrega -> entrega.order().id()).toList();
    }

    private List<String> ids(List<Order> pedidos) {
        return pedidos.stream().map(Order::id).toList();
    }

    private String nodosRecorridos(ScheduledDeliveryRoute ruta) {
        StringJoiner nodos = new StringJoiner(" -> ");
        nodos.add(ubicacion(ruta.route().startLocation()));
        ruta.scheduledStops().stream().flatMap(parada -> parada.approach().legs().stream()).map(RoadLeg::to)
                .map(this::ubicacion).forEach(nodos::add);
        return nodos.toString();
    }

    private String textoTramos(Set<StreetSegment> tramos) {
        return tramos.stream().map(tramo -> ubicacion(tramo.first()) + "->" + ubicacion(tramo.second()))
                .collect(java.util.stream.Collectors.joining(", "));
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
