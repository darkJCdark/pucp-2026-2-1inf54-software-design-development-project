package com.pucp.paqrap.modulos.planificacion.algoritmo.common;
import com.pucp.paqrap.modulos.almacenes.entity.*;
import com.pucp.paqrap.modulos.flota.entity.*;
import com.pucp.paqrap.modulos.flota.service.*;
import com.pucp.paqrap.modulos.incidencias.entity.*;
import com.pucp.paqrap.modulos.incidencias.service.*;
import com.pucp.paqrap.modulos.pedidos.entity.*;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.*;
import com.pucp.paqrap.modulos.planificacion.entity.*;
import com.pucp.paqrap.modulos.redvial.entity.*;
import com.pucp.paqrap.modulos.redvial.service.*;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** El limite por tramo es configurable: el evaluador por defecto no limita (maxLegKm = 0, regla final del caso)
 *  y con un limite positivo, como el historico de 80 km, un tramo que lo supere vuelve infactible el plan. */
class OperationalPlanEvaluatorLegDistanceTest {

    private static final Instant HORA_INICIO = Instant.parse("2026-09-09T12:00:00Z");
    private static final Warehouse CENTRAL = Warehouse.central("CENTRAL", new Location(0, 0));
    private static final Vehicle VEHICULO = new Vehicle("TA01", VehicleType.CAR, true);
    // (0,0) -> (70,50) = 120 km en un solo tramo.
    private static final Order PEDIDO_LEJANO = new Order("C-1", new Location(70, 50), 1, HORA_INICIO,
            HORA_INICIO.plusSeconds(48 * 3600));

    @Test
    void porDefectoUnTramoDeMasDe80KmEsAceptado() {
        PlanEvaluation evaluacion = evaluar(new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork())));

        assertTrue(evaluacion.violations().stream().noneMatch(v -> v.type() == PlanViolationType.LEG_DISTANCE_EXCEEDED),
                () -> "violaciones encontradas: " + evaluacion.violations());
        assertTrue(evaluacion.isFeasible(), () -> "violaciones encontradas: " + evaluacion.violations());
    }

    @Test
    void conElLimiteHistoricoUnTramoDeMasDe80KmEsRechazado() {
        PlanEvaluation evaluacion = evaluar(new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()),
                OperationalPlanEvaluator.HISTORICAL_MAX_LEG_DISTANCE_KM));

        assertFalse(evaluacion.isFeasible());
        assertTrue(evaluacion.violations().stream().anyMatch(v -> v.type() == PlanViolationType.LEG_DISTANCE_EXCEEDED),
                () -> "violaciones encontradas: " + evaluacion.violations());
    }

    private static PlanEvaluation evaluar(OperationalPlanEvaluator evaluator) {
        Map<VehicleType, VehicleParameters> parametros = new EnumMap<>(VehicleType.class);
        for (VehicleType t : VehicleType.values()) parametros.put(t, new VehicleParameters(24, 40.0, 1.0));
        Map<String, VehicleOperationalState> estados = Map.of(VEHICULO.id(),
                new VehicleOperationalState(VEHICULO, VehicleStatus.AVAILABLE, CENTRAL.location(), HORA_INICIO));
        OperationalSnapshot snapshot = new OperationalSnapshot(HORA_INICIO, new FleetProfile(parametros),
                InventorySnapshot.from(List.of(CENTRAL)), estados,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()),
                new ShiftSchedule(ShiftSchedule.DEFAULT_ZONE), List.of());

        DeliveryRoute ruta = DeliveryRoute.startScenarioAtCentral("R1", VEHICULO, CENTRAL, 1, HORA_INICIO)
                .withAppendedStop(new DeliveryStop(PEDIDO_LEJANO, 1))
                .returningTo(CENTRAL);
        return evaluator.evaluate(OperationalPlan.empty().withRoute(ruta), snapshot, List.of(PEDIDO_LEJANO), List.of());
    }
}
