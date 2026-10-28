package com.pucp.paqrap.modulos.planificacion.service;

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
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.dto.ModoOperacion;
import com.pucp.paqrap.modulos.planificacion.dto.ParadaResponse;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * O3: el plan informa en qué parada el conductor toma su refrigerio. TA01 sale 07:00 (Lima) a 10 km/h; la primera
 * entrega a 15 km la alcanza 08:30, dentro de la ventana del turno (08:00-14:00): descansa hasta las 09:30. La
 * segunda entrega, en el mismo turno, ya no tiene refrigerio.
 */
class PlanResponseMapperRefrigerioTest {

    private final ZoneId zona = ShiftSchedule.DEFAULT_ZONE;

    @Test
    void marcaElInicioDelRefrigerioSoloEnLaParadaDondeSeToma() {
        Instant inicio = ZonedDateTime.of(LocalDate.of(2026, 9, 9), LocalTime.of(7, 0), zona).toInstant();
        Warehouse central = Warehouse.central("CENTRAL", new Location(0, 0));
        Vehicle vehiculo = new Vehicle("TA01", VehicleType.CAR, true);
        Map<VehicleType, VehicleParameters> parametros = new EnumMap<>(VehicleType.class);
        for (VehicleType tipo : VehicleType.values()) {
            parametros.put(tipo, new VehicleParameters(24, 10.0, 1.0));
        }
        OperationalSnapshot snapshot = new OperationalSnapshot(inicio, new FleetProfile(parametros),
                InventorySnapshot.from(List.of(central)),
                Map.of(vehiculo.id(), new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, central.location(),
                        inicio)),
                new MaintenanceCalendar(zona, List.of()), new ShiftSchedule(zona), List.of());
        Order pedido1 = new Order("C-1", new Location(15, 0), 1, inicio, inicio.plusSeconds(20 * 3600));
        Order pedido2 = new Order("C-2", new Location(20, 0), 1, inicio, inicio.plusSeconds(20 * 3600));
        DeliveryRoute ruta = DeliveryRoute.startScenarioAtCentral("R1", vehiculo, central, 2, inicio)
                .withAppendedStop(new DeliveryStop(pedido1, 1))
                .withAppendedStop(new DeliveryStop(pedido2, 1));
        ScheduledDeliveryRoute programada = new RouteScheduler(new RoadNetwork()).schedule(ruta, snapshot, List.of());
        PlanEvaluation evaluacion = new PlanEvaluation(Map.of(ruta.id(), programada), List.of(),
                snapshot.inventory(), programada.totalCost());
        ResultadoPlanificacion resultado = new ResultadoPlanificacion(OperationalPlan.empty().withRoute(ruta),
                evaluacion, List.of());

        List<ParadaResponse> paradas = new PlanResponseMapper()
                .aResponse(ModoOperacion.DIA_A_DIA, inicio, resultado, 1L, 0L, 1_500L)
                .rutas().getFirst().paradas();

        assertEquals(inicio.plusSeconds(90 * 60), paradas.get(0).inicioRefrigerio(), "llega al cliente a las 08:30");
        assertEquals(inicio.plusSeconds(150 * 60), paradas.get(0).llegada(), "y lo atiende tras el refrigerio, 09:30");
        assertNull(paradas.get(1).inicioRefrigerio(), "un solo refrigerio por turno");
    }
}
