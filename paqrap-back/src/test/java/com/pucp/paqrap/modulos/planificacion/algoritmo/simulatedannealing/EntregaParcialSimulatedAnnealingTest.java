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
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntregaParcialSimulatedAnnealingTest {

    private static final Instant INICIO = Instant.parse("2026-09-09T12:00:00Z");

    @Test
    void mantieneLaEntregaCompletaCuandoElPedidoEsMenorQueLaCapacidad() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(0, 0));
        Order pedido = pedido("P01", 12, INICIO.plusSeconds(8 * 3600));

        ResultadoPlanificacion resultado = planificar(snapshot(central, List.of(auto())), pedido);

        assertTrue(resultado.esFactible());
        assertTrue(resultado.noAtendidos().isEmpty());
        assertEquals(List.of(12), entregasDe(resultado, pedido));
    }

    @Test
    void completaUnPedidoIgualALaCapacidadDelVehiculo() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(0, 0));
        Order pedido = pedido("P01", 24, INICIO.plusSeconds(8 * 3600));

        ResultadoPlanificacion resultado = planificar(snapshot(central, List.of(auto())), pedido);

        assertTrue(resultado.esFactible());
        assertTrue(resultado.noAtendidos().isEmpty());
        assertEquals(List.of(24), entregasDe(resultado, pedido));
    }

    @Test
    void atiendeUnPedidoMayorQueLaCapacidadMedianteEntregasParciales() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(0, 0));
        Order pedido = pedido("P30", 30, INICIO.plusSeconds(8 * 3600));

        ResultadoPlanificacion resultado = planificar(snapshot(central, List.of(auto(), moto())), pedido);

        List<Integer> entregas = entregasDe(resultado, pedido);
        System.out.println("\nENTREGA PARCIAL SA");
        resultado.plan().routes().forEach(route -> route.stops().stream()
                .filter(DeliveryStop.class::isInstance)
                .map(DeliveryStop.class::cast)
                .filter(stop -> stop.order().id().equals(pedido.id()))
                .forEach(stop -> System.out.printf("Vehiculo %s: %d paquetes%n", route.vehicle().id(), stop.deliveredPackages())));
        System.out.printf("Total entregado: %d/%d%n", entregas.stream().mapToInt(Integer::intValue).sum(), pedido.packages());
        System.out.printf("Plan factible: %s | Carga negativa: %s | Pedido completado: %s%n",
                resultado.esFactible(), tieneCargaNegativa(resultado), resultado.noAtendidos().isEmpty());

        assertTrue(resultado.esFactible(), () -> "violaciones: " + resultado.evaluacion().violations());
        assertTrue(resultado.noAtendidos().isEmpty());
        assertEquals(30, entregas.stream().mapToInt(Integer::intValue).sum());
        assertTrue(entregas.size() > 1);
        assertFalse(tieneCargaNegativa(resultado));
    }

    @Test
    void respetaLasCapacidadesRealesSinFijarUnaParticionEspecifica() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(0, 0));
        Order pedido = pedido("P30", 30, INICIO.plusSeconds(8 * 3600));
        OperationalSnapshot snapshot = snapshot(central, List.of(auto(), moto()));

        ResultadoPlanificacion resultado = planificar(snapshot, pedido);

        assertTrue(resultado.esFactible());
        assertEquals(30, entregasDe(resultado, pedido).stream().mapToInt(Integer::intValue).sum());
        resultado.plan().routes().forEach(route -> {
            int capacidad = snapshot.fleetProfile().parametersFor(route.vehicle().type()).capacity();
            route.stops().stream()
                    .filter(DeliveryStop.class::isInstance)
                    .map(DeliveryStop.class::cast)
                    .filter(stop -> stop.order().id().equals(pedido.id()))
                    .forEach(stop -> assertTrue(stop.deliveredPackages() > 0 && stop.deliveredPackages() <= capacidad));
        });
    }

    @Test
    void conservaElPedidoNoAtendidoSiNoAlcanzaElTiempoParaCompletarSusPartes() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(0, 0));
        Order pedido = pedido("P30", 30, INICIO.plusSeconds(3600));

        ResultadoPlanificacion resultado = planificar(snapshot(central, List.of(auto())), pedido);

        assertEquals(List.of(pedido), resultado.noAtendidos());
        assertTrue(resultado.plan().routes().isEmpty());
        assertTrue(entregasDe(resultado, pedido).isEmpty());
        assertFalse(tieneCargaNegativa(resultado));
    }

    private ResultadoPlanificacion planificar(OperationalSnapshot snapshot, Order pedido) {
        return new OperationalSimulatedAnnealingPlanner(evaluador()).planificar(snapshot, List.of(pedido), List.of(),
                new AnnealingConfig(100.0, 1.0, 0.90, 1, 1, 1), random(13L));
    }

    private Order pedido(String id, int paquetes, Instant deadline) {
        return new Order(id, new Location(0, 2), paquetes, INICIO, deadline);
    }

    private Vehicle auto() {
        return new Vehicle("TA01", VehicleType.CAR, true);
    }

    private Vehicle moto() {
        return new Vehicle("TM01", VehicleType.MOTORCYCLE, true);
    }

    private OperationalSnapshot snapshot(Warehouse central, List<Vehicle> flota) {
        Map<String, VehicleOperationalState> states = flota.stream().collect(java.util.stream.Collectors.toMap(
                Vehicle::id, vehicle -> new VehicleOperationalState(vehicle, VehicleStatus.AVAILABLE, central.location(), INICIO)));
        Map<VehicleType, VehicleParameters> parameters = new EnumMap<>(VehicleType.class);
        parameters.put(VehicleType.CAR, new VehicleParameters(24, 40.0, 8.0));
        parameters.put(VehicleType.MOTORCYCLE, new VehicleParameters(8, 25.0, 6.0));
        parameters.put(VehicleType.BICYCLE, new VehicleParameters(4, 12.0, 3.0));
        return new OperationalSnapshot(INICIO, new FleetProfile(parameters), InventorySnapshot.from(List.of(central)), states,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()), new ShiftSchedule(ShiftSchedule.DEFAULT_ZONE), List.of());
    }

    private OperationalPlanEvaluator evaluador() {
        return new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()));
    }

    private List<Integer> entregasDe(ResultadoPlanificacion resultado, Order pedido) {
        return resultado.plan().routes().stream()
                .flatMap(route -> route.stops().stream())
                .filter(DeliveryStop.class::isInstance)
                .map(DeliveryStop.class::cast)
                .filter(stop -> stop.order().id().equals(pedido.id()))
                .map(DeliveryStop::deliveredPackages)
                .toList();
    }

    private boolean tieneCargaNegativa(ResultadoPlanificacion resultado) {
        return resultado.evaluacion().schedulesByRouteId().values().stream()
                .flatMap(route -> route.scheduledStops().stream())
                .anyMatch(stop -> stop.loadBefore() < 0 || stop.loadAfter() < 0);
    }

    private RandomGenerator random(long seed) {
        return RandomGeneratorFactory.<RandomGenerator>of("L64X128MixRandom").create(seed);
    }
}
