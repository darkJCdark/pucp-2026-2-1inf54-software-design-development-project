package com.pucp.paqrap.modulos.planificacion.entity;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.AnnealingConfig;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.OperationalSimulatedAnnealingPlanner;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Semana 13, paso 7b: el orden de iteración de plan, snapshot e inventario es por id y no depende de cómo se
 * armó el mapa. Antes usaban {@code Map.copyOf}, cuyo orden cambia en cada JVM, y con la misma semilla el SA daba
 * costos distintos entre ejecuciones.
 */
class OrdenDeterministaTest {

    private static final Instant HORA = Instant.parse("2026-09-09T12:00:00Z");
    private static final List<String> IDS = List.of("TA01", "TA02", "TA03", "TA04", "TA05", "TA06");
    private static final Warehouse CENTRAL = Warehouse.central("CENTRAL", new Location(27, 14));
    private static final AnnealingConfig CONFIG_PRUEBA = new AnnealingConfig(100.0, 1.0, 0.90, 5, 30, 30);

    // ---------------------------------------------------------------- OperationalPlan

    @Test
    void plan_iteraLasRutasPorIdDeVehiculoSinImportarElOrdenDeInsercion() {
        for (List<String> orden : permutaciones(List.of("TA03", "TA01", "TA02"))) {
            OperationalPlan plan = OperationalPlan.empty();
            for (String id : orden) {
                plan = plan.withRoute(ruta(id));
            }
            assertEquals(List.of("TA01", "TA02", "TA03"),
                    plan.routes().stream().map(r -> r.vehicle().id()).toList());
        }
    }

    @Test
    void plan_conMuchasRutas_mantieneElOrdenPorIdTrasAgregarYQuitar() {
        OperationalPlan plan = OperationalPlan.empty();
        List<String> invertidos = new ArrayList<>(IDS);
        Collections.reverse(invertidos);
        for (String id : invertidos) {
            plan = plan.withRoute(ruta(id));
        }
        plan = plan.withoutRoute("TA03");

        assertEquals(List.of("TA01", "TA02", "TA04", "TA05", "TA06"),
                plan.routes().stream().map(r -> r.vehicle().id()).toList());
    }

    @Test
    void plan_esInmutable() {
        OperationalPlan plan = OperationalPlan.empty().withRoute(ruta("TA01"));

        assertThrows(UnsupportedOperationException.class, () -> plan.routes().clear());
        assertEquals(1, plan.routes().size());
    }

    // ---------------------------------------------------------------- OperationalSnapshot

    @Test
    void snapshot_iteraLosVehiculosPorIdSinImportarElOrdenDelMapaOriginal() {
        for (List<String> orden : permutaciones(List.of("TA02", "TA03", "TA01"))) {
            OperationalSnapshot snapshot = snapshot(orden);

            assertEquals(List.of("TA01", "TA02", "TA03"), List.copyOf(snapshot.vehiclesById().keySet()));
            assertEquals(List.of("TA01", "TA02", "TA03"),
                    snapshot.vehiclesById().values().stream().map(e -> e.vehicle().id()).toList());
        }
    }

    @Test
    void snapshot_esInmutableYRechazaValoresNulos() {
        OperationalSnapshot snapshot = snapshot(List.of("TA01"));
        Map<String, VehicleOperationalState> conNulo = new HashMap<>();
        conNulo.put("TA01", null);

        assertThrows(UnsupportedOperationException.class, () -> snapshot.vehiclesById().clear());
        assertThrows(NullPointerException.class, () -> snapshot(conNulo));
    }

    @Test
    void snapshot_noSeVeAfectadoPorCambiosPosterioresEnElMapaOriginal() {
        Map<String, VehicleOperationalState> original = estados(List.of("TA01", "TA02"));
        OperationalSnapshot snapshot = snapshot(original);

        original.remove("TA01");

        assertEquals(List.of("TA01", "TA02"), List.copyOf(snapshot.vehiclesById().keySet()));
    }

    // ---------------------------------------------------------------- InventorySnapshot

    @Test
    void inventario_iteraLosAlmacenesPorId() {
        Warehouse este = Warehouse.intermediate("ESTE", new Location(57, 27), 1_000);
        Warehouse noroeste = Warehouse.intermediate("NOROESTE", new Location(12, 38), 1_000);

        for (List<Warehouse> orden : permutaciones(List.of(este, CENTRAL, noroeste))) {
            InventorySnapshot inventario = InventorySnapshot.from(orden);

            assertEquals(List.of("CENTRAL", "ESTE", "NOROESTE"),
                    inventario.warehouses().stream().map(Warehouse::id).toList());
        }
    }

    @Test
    void inventario_mantieneOrdenYStockTrasRetirarYRecargar() {
        Warehouse este = Warehouse.intermediate("ESTE", new Location(57, 27), 1_000);
        Warehouse noroeste = Warehouse.intermediate("NOROESTE", new Location(12, 38), 1_000);
        InventorySnapshot inventario = InventorySnapshot.from(List.of(noroeste, CENTRAL, este));

        InventorySnapshot retirado = inventario.withdraw("ESTE", 10);
        InventorySnapshot recargado = retirado.reloadIntermediateWarehouses();

        assertEquals(List.of("CENTRAL", "ESTE", "NOROESTE"),
                retirado.warehouses().stream().map(Warehouse::id).toList());
        assertEquals(este.initialStock() - 10, retirado.availableStock("ESTE"));
        assertEquals(noroeste.initialStock(), retirado.availableStock("NOROESTE"));
        assertEquals(este.capacity(), recargado.availableStock("ESTE"));
        assertThrows(UnsupportedOperationException.class, () -> retirado.warehouses().clear());
    }

    // ---------------------------------------------------------------- efecto en el SA

    @Test
    void sa_conLaMismaSemillaDaElMismoResultadoSinImportarElOrdenDeConstruccion() {
        List<String> ascendente = new ArrayList<>(IDS);
        List<String> descendente = new ArrayList<>(IDS);
        Collections.reverse(descendente);

        ResultadoPlanificacion a = planificar(snapshot(ascendente), 21);
        ResultadoPlanificacion b = planificar(snapshot(descendente), 21);

        assertEquals(a.costoTotal(), b.costoTotal());
        assertEquals(a.esFactible(), b.esFactible());
        assertEquals(describir(a), describir(b));
    }

    // ---------------------------------------------------------------- auxiliares

    private static ResultadoPlanificacion planificar(OperationalSnapshot snapshot, long semilla) {
        Instant plazo = HORA.plusSeconds(10 * 3600);
        List<Order> pedidos = List.of(
                new Order("P01", new Location(31, 14), 12, HORA, plazo),
                new Order("P02", new Location(27, 18), 8, HORA, plazo),
                new Order("P03", new Location(29, 16), 4, HORA, plazo),
                new Order("P04", new Location(20, 10), 6, HORA, plazo));
        return new OperationalSimulatedAnnealingPlanner(new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork())))
                .planificar(snapshot, pedidos, List.of(), CONFIG_PRUEBA, new Random(semilla));
    }

    private static List<String> describir(ResultadoPlanificacion resultado) {
        return resultado.plan().routes().stream()
                .map(r -> r.id() + "|" + r.vehicle().id() + "|" + r.departureAt() + "|" + r.stops())
                .toList();
    }

    private static DeliveryRoute ruta(String vehiculoId) {
        return DeliveryRoute.startScenarioAtCentral("R-" + vehiculoId, vehiculo(vehiculoId), CENTRAL, 1, HORA);
    }

    private static Vehicle vehiculo(String id) {
        return new Vehicle(id, VehicleType.CAR, true);
    }

    private static Map<String, VehicleOperationalState> estados(List<String> ids) {
        Map<String, VehicleOperationalState> estados = new LinkedHashMap<>();
        for (String id : ids) {
            estados.put(id, new VehicleOperationalState(vehiculo(id), VehicleStatus.AVAILABLE, CENTRAL.location(), HORA));
        }
        return estados;
    }

    private static OperationalSnapshot snapshot(List<String> idsEnOrden) {
        return snapshot(estados(idsEnOrden));
    }

    private static OperationalSnapshot snapshot(Map<String, VehicleOperationalState> estados) {
        return new OperationalSnapshot(HORA, FleetProfile.defaults(), InventorySnapshot.from(List.of(CENTRAL)), estados,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()), ShiftSchedule.defaultSchedule(), List.of());
    }

    private static <T> List<List<T>> permutaciones(List<T> elementos) {
        if (elementos.size() <= 1) {
            return List.of(List.copyOf(elementos));
        }
        List<List<T>> resultado = new ArrayList<>();
        for (int i = 0; i < elementos.size(); i++) {
            List<T> resto = new ArrayList<>(elementos);
            T elegido = resto.remove(i);
            for (List<T> cola : permutaciones(resto)) {
                List<T> permutacion = new ArrayList<>();
                permutacion.add(elegido);
                permutacion.addAll(cola);
                resultado.add(permutacion);
            }
        }
        return resultado;
    }
}
