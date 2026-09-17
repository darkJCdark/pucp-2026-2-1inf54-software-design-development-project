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
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolationType;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlCargaGraspTest {

    private static final Instant INICIO = Instant.parse("2026-09-09T12:00:00Z");

    @Test
    void graspRechazaLaSegundaEntregaCuandoLaCargaInicialNoAlcanza() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(0, 0));
        Vehicle vehiculo = new Vehicle("TA01", VehicleType.CAR, true);
        OperationalSnapshot snapshot = snapshot(central, vehiculo);
        List<Order> pedidos = List.of(
                new Order("P01", new Location(1, 0), 6, INICIO, INICIO.plusSeconds(4 * 3600)),
                new Order("P02", new Location(2, 0), 6, INICIO, INICIO.plusSeconds(4 * 3600)));

        ResultadoPlanificacion resultado = planificador(19L).planificar(snapshot, pedidos, List.of(), 0.0, 1);

        System.out.println("\n====================================================");
        System.out.println("ESCENARIO: CONTROL DE CARGA DEL VEHICULO");
        System.out.println("====================================================");
        System.out.println("Vehiculo: TA01 | Capacidad maxima: 24 | Carga inicial: 6");
        System.out.println("Pedido P01: 6 | Pedido P02: 6");
        System.out.println("Inicio: carga = 6");
        System.out.println("Entrega P01: 6 - 6 = 0");
        System.out.println("Intento P02: requiere 6, disponible 0, sin WarehouseVisit intermedio");
        System.out.println("Plan factible: " + resultado.esFactible());
        System.out.println("Pedidos no atendidos: " + resultado.noAtendidos().stream().map(Order::id).toList());
        System.out.println("====================================================");

        assertTrue(resultado.esFactible(), () -> "violaciones: " + resultado.evaluacion().violations());
        assertEquals(List.of("P02"), resultado.noAtendidos().stream().map(Order::id).toList());
        assertTrue(resultado.evaluacion().schedulesByRouteId().values().stream()
                .flatMap(ruta -> ruta.scheduledStops().stream())
                .allMatch(parada -> parada.loadAfter() >= 0));
    }

    @Test
    void cargaExactaTerminaEnCeroYEsFactible() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(0, 0));
        Vehicle vehiculo = new Vehicle("TA01", VehicleType.CAR, true);
        OperationalSnapshot snapshot = snapshot(central, vehiculo);
        Order p01 = new Order("P01", new Location(1, 0), 4, INICIO, INICIO.plusSeconds(4 * 3600));
        Order p02 = new Order("P02", new Location(2, 0), 6, INICIO, INICIO.plusSeconds(4 * 3600));
        DeliveryRoute ruta = DeliveryRoute.startScenarioAtCentral("R1", vehiculo, central, 10, INICIO)
                .withAppendedStop(new DeliveryStop(p01, 4))
                .withAppendedStop(new DeliveryStop(p02, 6))
                .returningTo(central);

        var evaluacion = evaluador().evaluate(OperationalPlan.empty().withRoute(ruta), snapshot, List.of(p01, p02), List.of());

        assertTrue(evaluacion.isFeasible(), () -> "violaciones: " + evaluacion.violations());
        assertEquals(0, evaluacion.schedulesByRouteId().get("R1").scheduledStops().get(1).loadAfter());
    }

    @Test
    void evaluadorRechazaCargaInsuficienteYUnaRecargaExplicitaLaHaceValida() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(0, 0));
        Vehicle vehiculo = new Vehicle("TA01", VehicleType.CAR, true);
        OperationalSnapshot snapshot = snapshot(central, vehiculo);
        Order p01 = new Order("P01", new Location(1, 0), 6, INICIO, INICIO.plusSeconds(4 * 3600));
        Order p02 = new Order("P02", new Location(2, 0), 6, INICIO, INICIO.plusSeconds(4 * 3600));
        DeliveryRoute sinRecarga = DeliveryRoute.startScenarioAtCentral("SIN-RECARGA", vehiculo, central, 10, INICIO)
                .withAppendedStop(new DeliveryStop(p01, 6))
                .withAppendedStop(new DeliveryStop(p02, 6))
                .returningTo(central);
        DeliveryRoute conRecarga = DeliveryRoute.startScenarioAtCentral("CON-RECARGA", vehiculo, central, 10, INICIO)
                .withAppendedStop(new DeliveryStop(p01, 6))
                .withAppendedStop(new WarehouseVisit(central, 6))
                .withAppendedStop(new DeliveryStop(p02, 6))
                .returningTo(central);

        var invalida = evaluador().evaluate(OperationalPlan.empty().withRoute(sinRecarga), snapshot, List.of(p01, p02), List.of());
        var valida = evaluador().evaluate(OperationalPlan.empty().withRoute(conRecarga), snapshot, List.of(p01, p02), List.of());

        assertFalse(invalida.isFeasible());
        assertTrue(invalida.violations().stream().anyMatch(v -> v.type() == PlanViolationType.NEGATIVE_LOAD));
        assertTrue(valida.isFeasible(), () -> "violaciones: " + valida.violations());
        assertEquals(4, valida.schedulesByRouteId().get("CON-RECARGA").scheduledStops().get(2).loadAfter());
    }

    private GraspPlanificador planificador(long semilla) {
        RoadNetwork redVial = new RoadNetwork();
        RouteScheduler scheduler = new RouteScheduler(redVial);
        return new GraspPlanificador(redVial, scheduler, new OperationalPlanEvaluator(scheduler), semilla);
    }

    private OperationalPlanEvaluator evaluador() {
        return new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()));
    }

    private OperationalSnapshot snapshot(Warehouse central, Vehicle vehiculo) {
        Map<VehicleType, VehicleParameters> parametros = new EnumMap<>(VehicleType.class);
        parametros.put(VehicleType.CAR, new VehicleParameters(24, 40.0, 8.0));
        parametros.put(VehicleType.MOTORCYCLE, new VehicleParameters(8, 25.0, 6.0));
        parametros.put(VehicleType.BICYCLE, new VehicleParameters(4, 12.0, 3.0));
        return new OperationalSnapshot(INICIO, new FleetProfile(parametros), InventorySnapshot.from(List.of(central)),
                Map.of(vehiculo.id(), new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE,
                        central.location(), INICIO)),
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()),
                new ShiftSchedule(ShiftSchedule.DEFAULT_ZONE), List.of());
    }
}
