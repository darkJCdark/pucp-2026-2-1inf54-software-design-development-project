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
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.entity.StreetSegment;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscenariosGraspTest {

    private static final Instant INICIO = Instant.parse("2026-09-09T12:00:00Z");

    @Test
    void planificacionNormalGeneraRutasFactiblesYEntregaTodosLosPedidos() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(27, 14));
        List<Vehicle> flota = List.of(
                new Vehicle("TA01", VehicleType.CAR, true),
                new Vehicle("TM01", VehicleType.MOTORCYCLE, true));
        List<Order> pedidos = List.of(
                new Order("P01", new Location(28, 14), 24, INICIO, INICIO.plusSeconds(4 * 3600)),
                new Order("P02", new Location(29, 14), 2, INICIO, INICIO.plusSeconds(4 * 3600)));

        ResultadoPlanificacion resultado = planificador(11L).planificar(
                snapshot(central, flota, Map.of(), List.of()), pedidos, List.of(), 0.0, 10);

        imprimir("PLANIFICACION NORMAL", pedidos, flota, resultado);
        assertTrue(resultado.esFactible(), () -> "violaciones: " + resultado.evaluacion().violations());
        assertEquals(0, resultado.noAtendidos().size());
        assertFalse(resultado.plan().routes().isEmpty());
        assertEquals(pedidos.size(), entregas(resultado));
        assertTrue(resultado.plan().routes().stream().allMatch(ruta -> ruta.stops().stream()
                .filter(DeliveryStop.class::isInstance)
                .map(DeliveryStop.class::cast)
                .mapToInt(DeliveryStop::deliveredPackages)
                .sum() <= resultado.plan().routeForVehicle(ruta.vehicle().id()).orElseThrow().vehicle().type().capacity()));
        assertTrue(resultado.evaluacion().schedulesByRouteId().values().stream()
                .flatMap(ruta -> ruta.scheduledStops().stream())
                .filter(parada -> parada.stop() instanceof DeliveryStop)
                .allMatch(parada -> !parada.arrivedAt().isAfter(((DeliveryStop) parada.stop()).order().deadline())));
    }

    @Test
    void bloqueoVialObligaUnaNuevaPlanificacionQueEvitaElTramoBloqueado() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(0, 0));
        List<Vehicle> flota = List.of(new Vehicle("TA01", VehicleType.CAR, true));
        List<Order> pedidos = List.of(new Order("P01", new Location(0, 3), 2, INICIO, INICIO.plusSeconds(8 * 3600)));
        OperationalSnapshot snapshot = snapshot(central, flota, Map.of(), List.of());

        ResultadoPlanificacion antes = planificador(7L).planificar(snapshot, pedidos, List.of(), 0.0, 1);
        RoadBlock bloqueo = new RoadBlock(INICIO, INICIO.plusSeconds(2 * 3600),
                List.of(new Location(0, 1), new Location(0, 2)));
        ResultadoPlanificacion despues = planificador(7L).planificar(snapshot, pedidos, List.of(bloqueo), 0.0, 1);

        System.out.println("\n====================================================");
        System.out.println("ESCENARIO: BLOQUEO VIAL");
        System.out.println("====================================================");
        System.out.println("Bloqueo detectado: (0,1) -> (0,2)");
        imprimirResultado("Ruta antes del bloqueo", antes);
        imprimirResultado("Ruta despues del bloqueo", despues);
        System.out.printf("Costo antes: %.2f | Costo despues: %.2f%n", antes.costoTotal(), despues.costoTotal());
        System.out.println("====================================================");

        assertTrue(antes.esFactible());
        assertTrue(despues.esFactible(), () -> "violaciones: " + despues.evaluacion().violations());
        assertEquals(0, despues.noAtendidos().size());
        assertFalse(utilizaSegmentos(despues, bloqueo.blockedSegments()));
        assertTrue(despues.costoTotal() >= antes.costoTotal());
    }

    @Test
    void averiaMenorEvitaAsignarElVehiculoAfectadoYPermiteReasignar() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(10, 10));
        Vehicle ta01 = new Vehicle("TA01", VehicleType.CAR, true);
        Vehicle ta02 = new Vehicle("TA02", VehicleType.CAR, true);
        List<Vehicle> flota = List.of(ta01, ta02);
        List<Order> pedidos = List.of(new Order("P01", new Location(14, 10), 12, INICIO, INICIO.plusSeconds(8 * 3600)));

        Map<String, VehicleOperationalState> estadoInicial = Map.of(
                ta01.id(), new VehicleOperationalState(ta01, VehicleStatus.AVAILABLE, central.location(), INICIO),
                ta02.id(), new VehicleOperationalState(ta02, VehicleStatus.AVAILABLE, central.location(), INICIO.plusSeconds(3 * 3600)));
        ResultadoPlanificacion antes = planificador(3L).planificar(
                snapshot(central, flota, estadoInicial, List.of()), pedidos, List.of(), 0.0, 1);

        BreakdownEvent averia = new BreakdownEvent(ta01.id(), BreakdownType.MINOR, INICIO, central.location());
        ResultadoPlanificacion despues = planificador(3L).planificar(
                snapshot(central, flota, Map.of(), List.of(averia)), pedidos, List.of(), 0.0, 1);

        System.out.println("\n====================================================");
        System.out.println("ESCENARIO: AVERIA DE VEHICULO");
        System.out.println("====================================================");
        System.out.println("Vehiculo afectado: TA01 | Tipo: MINOR | Ubicacion: (10,10)");
        imprimirResultado("Plan antes de la averia", antes);
        imprimirResultado("Plan despues de la averia", despues);
        System.out.println("Reasignacion esperada: P01 de TA01 a TA02");
        System.out.println("====================================================");

        assertTrue(antes.esFactible());
        assertTrue(antes.plan().routeForVehicle(ta01.id()).isPresent());
        assertTrue(despues.esFactible(), () -> "violaciones: " + despues.evaluacion().violations());
        assertTrue(despues.plan().routeForVehicle(ta01.id()).isEmpty());
        assertTrue(despues.plan().routeForVehicle(ta02.id()).isPresent());
        assertEquals(0, despues.noAtendidos().size());
    }

    private GraspPlanificador planificador(long semilla) {
        RoadNetwork redVial = new RoadNetwork();
        RouteScheduler scheduler = new RouteScheduler(redVial);
        return new GraspPlanificador(redVial, scheduler, new OperationalPlanEvaluator(scheduler), semilla);
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

    private int entregas(ResultadoPlanificacion resultado) {
        return (int) resultado.plan().routes().stream()
                .flatMap(ruta -> ruta.stops().stream())
                .filter(DeliveryStop.class::isInstance)
                .count();
    }

    private boolean utilizaSegmentos(ResultadoPlanificacion resultado, Set<StreetSegment> bloqueados) {
        return resultado.evaluacion().schedulesByRouteId().values().stream()
                .flatMap(ruta -> ruta.scheduledStops().stream())
                .flatMap(parada -> parada.approach().legs().stream())
                .map(tramo -> new StreetSegment(tramo.from(), tramo.to()))
                .anyMatch(bloqueados::contains);
    }

    private void imprimir(String escenario, List<Order> pedidos, List<Vehicle> flota, ResultadoPlanificacion resultado) {
        System.out.println("\n====================================================");
        System.out.println("ESCENARIO: " + escenario);
        System.out.println("====================================================");
        System.out.println("Pedidos recibidos: " + pedidos.size());
        System.out.println("Vehiculos disponibles: " + flota.size());
        imprimirResultado("PLAN GENERADO", resultado);
        System.out.println("====================================================");
    }

    private void imprimirResultado(String titulo, ResultadoPlanificacion resultado) {
        System.out.println(titulo);
        for (DeliveryRoute ruta : resultado.plan().routes()) {
            ScheduledDeliveryRoute programada = resultado.evaluacion().schedulesByRouteId().get(ruta.id());
            System.out.print("  " + ruta.vehicle().id() + ": ");
            for (RouteStop parada : ruta.stops()) {
                if (parada instanceof DeliveryStop entrega) {
                    System.out.print("Pedido " + entrega.order().id() + " -> ");
                }
            }
            System.out.printf("distancia=%.1f km, costo=%.2f%n", programada.totalDistanceKm(), programada.totalCost());
        }
        System.out.printf("Factible: %s | Costo total: %.2f | Pedidos atendidos: %d | No atendidos: %d%n",
                resultado.esFactible(), resultado.costoTotal(), entregas(resultado), resultado.noAtendidos().size());
    }
}
