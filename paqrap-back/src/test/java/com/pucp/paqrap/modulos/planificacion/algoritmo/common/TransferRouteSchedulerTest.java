package com.pucp.paqrap.modulos.planificacion.algoritmo.common;

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
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.entity.TransferDirection;
import com.pucp.paqrap.modulos.planificacion.entity.TransferStop;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferRouteSchedulerTest {

    private static final Instant T0 = Instant.parse("2028-12-01T00:00:00Z");
    private static final Warehouse CENTRAL = Warehouse.central("CENTRAL", new Location(0, 0));
    private static final Vehicle DONANTE = new Vehicle("TA01", VehicleType.CAR, true);
    private static final Vehicle RECEPTOR = new Vehicle("TM01", VehicleType.MOTORCYCLE, true);
    private static final Order PEDIDO = new Order(
            "P-TRANSFER", new Location(10, 0), 5, T0, T0.plus(Duration.ofHours(2)));

    @Test
    void conservaCargaYSincronizaAmbosParticipantes() {
        Instant encuentro = T0.plus(Duration.ofMinutes(10));
        String transferId = "TR-1";

        DeliveryRoute donante = DeliveryRoute.startScenarioAtCentral(
                        "R-D", DONANTE, CENTRAL, 5, T0)
                .withAppendedStop(new TransferStop(
                        transferId, PEDIDO, 5, RECEPTOR.id(), TransferDirection.DONATE,
                        new Location(5, 0), encuentro))
                .returningTo(CENTRAL);

        DeliveryRoute receptor = DeliveryRoute.startScenarioAtCentral(
                        "R-R", RECEPTOR, CENTRAL, 0, T0)
                .withAppendedStop(new TransferStop(
                        transferId, PEDIDO, 5, DONANTE.id(), TransferDirection.RECEIVE,
                        new Location(5, 0), encuentro))
                .withAppendedStop(new DeliveryStop(PEDIDO, 5))
                .returningTo(CENTRAL);

        PlanEvaluation evaluacion = evaluador().evaluate(
                OperationalPlan.empty().withRoute(donante).withRoute(receptor),
                snapshot(), List.of(PEDIDO), List.of());

        assertTrue(evaluacion.isFeasible(), () -> "violaciones: " + evaluacion.violations());

        ScheduledRouteStop salida = evaluacion.schedulesByRouteId().get("R-D").scheduledStops().getFirst();
        ScheduledRouteStop entrada = evaluacion.schedulesByRouteId().get("R-R").scheduledStops().getFirst();

        assertEquals(encuentro, salida.serviceStartedAt());
        assertEquals(encuentro, entrada.serviceStartedAt());
        assertEquals(5, salida.loadBefore());
        assertEquals(0, salida.loadAfter());
        assertEquals(0, entrada.loadBefore());
        assertEquals(5, entrada.loadAfter());
        assertEquals(salida.loadBefore() + entrada.loadBefore(),
                salida.loadAfter() + entrada.loadAfter());
    }

    @Test
    void rechazaUnTransbordoSinContraparte() {
        DeliveryRoute donante = DeliveryRoute.startScenarioAtCentral(
                        "R-D", DONANTE, CENTRAL, 5, T0)
                .withAppendedStop(new TransferStop(
                        "TR-SOLO", PEDIDO, 5, RECEPTOR.id(), TransferDirection.DONATE,
                        new Location(5, 0), T0.plus(Duration.ofMinutes(10))))
                .returningTo(CENTRAL);

        PlanEvaluation evaluacion = evaluador().evaluate(
                OperationalPlan.empty().withRoute(donante), snapshot(), List.of(PEDIDO), List.of());

        assertFalse(evaluacion.isFeasible());
        assertTrue(evaluacion.violations().stream()
                .anyMatch(v -> v.type() == PlanViolationType.INVALID_TRANSFER_PAIR));
    }

    private static OperationalPlanEvaluator evaluador() {
        return new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()));
    }

    private static OperationalSnapshot snapshot() {
        EnumMap<VehicleType, VehicleParameters> parametros = new EnumMap<>(VehicleType.class);
        for (VehicleType tipo : VehicleType.values()) {
            parametros.put(tipo, new VehicleParameters(24, 60.0, 1.0));
        }
        Map<String, VehicleOperationalState> estados = Map.of(
                DONANTE.id(), new VehicleOperationalState(
                        DONANTE, VehicleStatus.AVAILABLE, CENTRAL.location(), T0),
                RECEPTOR.id(), new VehicleOperationalState(
                        RECEPTOR, VehicleStatus.AVAILABLE, CENTRAL.location(), T0));
        return new OperationalSnapshot(
                T0,
                new FleetProfile(parametros),
                InventorySnapshot.from(List.of(CENTRAL)),
                estados,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()),
                ShiftSchedule.defaultSchedule(),
                List.of());
    }
}
