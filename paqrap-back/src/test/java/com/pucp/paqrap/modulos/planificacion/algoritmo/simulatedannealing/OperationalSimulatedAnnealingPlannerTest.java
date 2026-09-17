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
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalSimulatedAnnealingPlannerTest {

    private static final Instant INICIO = Instant.parse("2026-09-09T12:00:00Z");

    @Test
    void generaUnPlanFactibleYResultadoComun() {
        Escenario escenario = escenarioNormal();
        ResultadoPlanificacion resultado = planificador().planificar(escenario.snapshot(), escenario.orders(), List.of(),
                configuracion(30), random(7L));

        assertInstanceOf(ResultadoPlanificacion.class, resultado);
        assertTrue(resultado.esFactible(), () -> "violaciones: " + resultado.evaluacion().violations());
        assertTrue(resultado.noAtendidos().isEmpty());
        assertFalse(resultado.plan().routes().isEmpty());
    }

    @Test
    void enfriaLaTemperaturaSegunElFactorConfigurado() {
        Escenario escenario = escenarioNormal();
        OperationalSimulatedAnnealingPlanner.Ejecucion ejecucion = planificador().ejecutar(
                escenario.snapshot(), escenario.orders(), List.of(),
                new AnnealingConfig(100.0, 1.0, 0.90, 1, 1, 10), random(8L));

        assertEquals(90.0, ejecucion.finalTemperature());
        assertEquals(1, ejecucion.iterations());
    }

    @Test
    void aceptaUnVecinoMejor() {
        assertTrue(OperationalSimulatedAnnealingPlanner.accept(-1.0, 10.0, random(1L)));
    }

    @Test
    void puedeAceptarUnVecinoPeorConMetropolis() {
        assertTrue(OperationalSimulatedAnnealingPlanner.accept(Double.MIN_VALUE, 1.0, random(2L)));
    }

    @Test
    void descartaVecinoConCargaNegativaYContinuaLaEjecucion() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(0, 0));
        Vehicle auto = new Vehicle("TA01", VehicleType.CAR, true);
        Order pedido = new Order("P01", new Location(0, 2), 6, INICIO, INICIO.plusSeconds(8 * 3600));
        OperationalSnapshot snapshot = snapshot(central, List.of(auto));
        OperationalNeighborGenerator vecinoInvalido = (actual, estado, random) -> {
            DeliveryRoute base = actual.routeForVehicle(auto.id()).orElseThrow();
            DeliveryRoute negativo = base.withReplacedStops(List.of(
                    new DeliveryStop(pedido, 6), new DeliveryStop(pedido, 6), new WarehouseVisit(central, 0)));
            return Optional.of(actual.withRoute(negativo));
        };
        OperationalSimulatedAnnealingPlanner planner = new OperationalSimulatedAnnealingPlanner(evaluador(), vecinoInvalido);

        OperationalSimulatedAnnealingPlanner.Ejecucion ejecucion = planner.ejecutar(snapshot, List.of(pedido), List.of(),
                new AnnealingConfig(100.0, 1.0, 0.90, 1, 1, 10), random(3L));

        assertEquals(1, ejecucion.evaluatedNeighbors());
        assertEquals(0, ejecucion.acceptedNeighbors());
        assertTrue(ejecucion.resultado().esFactible());
        assertFalse(tieneCargaNegativa(ejecucion.resultado()));
    }

    @Test
    void mismaSemillaProduceElMismoResultado() {
        Escenario escenario = escenarioNormal();
        ResultadoPlanificacion primero = planificador().planificar(escenario.snapshot(), escenario.orders(), List.of(),
                configuracion(30), random(77L));
        ResultadoPlanificacion segundo = planificador().planificar(escenario.snapshot(), escenario.orders(), List.of(),
                configuracion(30), random(77L));

        assertEquals(primero.costoTotal(), segundo.costoTotal());
        assertEquals(firma(primero), firma(segundo));
        assertEquals(primero.noAtendidos(), segundo.noAtendidos());
    }

    @Test
    void ejecucionOperacionalMuestraMetricasReales() {
        Escenario escenario = escenarioNormal();
        AnnealingConfig config = configuracion(30);
        OperationalSimulatedAnnealingPlanner.Ejecucion ejecucion = planificador().ejecutar(
                escenario.snapshot(), escenario.orders(), List.of(), config, random(19L));

        System.out.println("\n====================================================");
        System.out.println("SIMULATED ANNEALING");
        System.out.println("====================================================");
        System.out.printf("Temperatura inicial: %.2f%n", config.initialTemperature());
        System.out.printf("Temperatura minima: %.2f%n", config.minimumTemperature());
        System.out.printf("Cooling factor: %.2f%n%n", config.coolingFactor());
        System.out.printf("Costo solucion inicial: S/ %.2f%n", ejecucion.initialCost());
        System.out.printf("Vecinos evaluados: %d%n", ejecucion.evaluatedNeighbors());
        System.out.printf("Vecinos aceptados: %d%n%n", ejecucion.acceptedNeighbors());
        System.out.printf("Mejor costo: S/ %.2f%n", ejecucion.resultado().costoTotal());
        System.out.printf("Plan factible: %s%n", ejecucion.resultado().esFactible() ? "SI" : "NO");
        System.out.printf("Pedidos no atendidos: %s%n", ejecucion.resultado().noAtendidos().stream().map(Order::id).toList());
        System.out.println("====================================================");

        assertTrue(ejecucion.resultado().esFactible());
        assertTrue(ejecucion.resultado().noAtendidos().isEmpty());
    }

    private OperationalSimulatedAnnealingPlanner planificador() {
        return new OperationalSimulatedAnnealingPlanner(evaluador());
    }

    private OperationalPlanEvaluator evaluador() {
        RoadNetwork redVial = new RoadNetwork();
        return new OperationalPlanEvaluator(new RouteScheduler(redVial));
    }

    private Escenario escenarioNormal() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(0, 0));
        Vehicle auto = new Vehicle("TA01", VehicleType.CAR, true);
        Vehicle moto = new Vehicle("TM01", VehicleType.MOTORCYCLE, true);
        return new Escenario(snapshot(central, List.of(auto, moto)), List.of(
                new Order("P01", new Location(0, 3), 12, INICIO, INICIO.plusSeconds(8 * 3600)),
                new Order("P02", new Location(3, 0), 2, INICIO, INICIO.plusSeconds(8 * 3600))));
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

    private AnnealingConfig configuracion(int maximumIterations) {
        return new AnnealingConfig(100.0, 1.0, 0.90, 5, maximumIterations, maximumIterations);
    }

    private RandomGenerator random(long seed) {
        return RandomGeneratorFactory.<RandomGenerator>of("L64X128MixRandom").create(seed);
    }

    private boolean tieneCargaNegativa(ResultadoPlanificacion resultado) {
        return resultado.evaluacion().schedulesByRouteId().values().stream()
                .flatMap(route -> route.scheduledStops().stream())
                .anyMatch(stop -> stop.loadBefore() < 0 || stop.loadAfter() < 0);
    }

    private List<String> firma(ResultadoPlanificacion resultado) {
        return resultado.plan().routes().stream()
                .sorted(java.util.Comparator.comparing(route -> route.vehicle().id()))
                .map(route -> route.vehicle().id() + route.stops())
                .toList();
    }

    private record Escenario(OperationalSnapshot snapshot, List<Order> orders) { }
}
