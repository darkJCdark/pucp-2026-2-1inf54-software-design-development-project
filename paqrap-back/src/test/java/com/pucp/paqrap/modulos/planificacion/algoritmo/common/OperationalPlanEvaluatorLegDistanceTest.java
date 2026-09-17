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

/** Hoja "Flota": "Maxima distancia ida: 80 km". Un tramo individual que
 *  supere ese limite debe marcar el plan como infactible. */
class OperationalPlanEvaluatorLegDistanceTest {

    @Test
    void unTramoDeMasDe80KmEsRechazado() {
        Instant horaInicio = Instant.parse("2026-09-09T12:00:00Z");
        Warehouse central = Warehouse.central("CENTRAL", new Location(0, 0));
        Vehicle vehiculo = new Vehicle("TA01", VehicleType.CAR, true);

        Map<VehicleType, VehicleParameters> parametros = new EnumMap<>(VehicleType.class);
        for (VehicleType t : VehicleType.values()) parametros.put(t, new VehicleParameters(24, 40.0, 1.0));
        Map<String, VehicleOperationalState> estados = Map.of(vehiculo.id(),
                new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, central.location(), horaInicio));
        OperationalSnapshot snapshot = new OperationalSnapshot(horaInicio, new FleetProfile(parametros),
                InventorySnapshot.from(List.of(central)), estados,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()),
                new ShiftSchedule(ShiftSchedule.DEFAULT_ZONE), List.of());

        // (0,0) -> (70,50) = 120 km en un solo tramo, supera el limite de 80.
        Order pedidoLejano = new Order("C-1", new Location(70, 50), 1, horaInicio, horaInicio.plusSeconds(48 * 3600));
        DeliveryRoute ruta = DeliveryRoute.startScenarioAtCentral("R1", vehiculo, central, 1, horaInicio)
                .withAppendedStop(new DeliveryStop(pedidoLejano, 1))
                .returningTo(central);

        RoadNetwork roadNetwork = new RoadNetwork();
        RouteScheduler scheduler = new RouteScheduler(roadNetwork);
        OperationalPlanEvaluator evaluator = new OperationalPlanEvaluator(scheduler);

        OperationalPlan plan = OperationalPlan.empty().withRoute(ruta);
        PlanEvaluation evaluacion = evaluator.evaluate(plan, snapshot, List.of(pedidoLejano), List.of());

        assertFalse(evaluacion.isFeasible());
        assertTrue(evaluacion.violations().stream().anyMatch(v -> v.type() == PlanViolationType.LEG_DISTANCE_EXCEEDED),
                () -> "violaciones encontradas: " + evaluacion.violations());
    }
}
