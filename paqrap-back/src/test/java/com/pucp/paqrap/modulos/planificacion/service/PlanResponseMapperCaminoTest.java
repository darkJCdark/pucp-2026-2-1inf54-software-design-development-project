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
import com.pucp.paqrap.modulos.planificacion.dto.ParadaResponse.Punto;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El plan informa el camino real hacia cada parada, para que el mapa no dibuje al vehículo cruzando un bloqueo.
 * TA01 sale del central (0,0) hacia un cliente en (6,0); la calle (3,0) está bloqueada todo el día.
 */
class PlanResponseMapperCaminoTest {

    private final ZoneId zona = ShiftSchedule.DEFAULT_ZONE;
    private final Instant inicio = ZonedDateTime.of(LocalDate.of(2026, 9, 9), LocalTime.of(5, 0), zona).toInstant();
    private final Location bloqueado = new Location(3, 0);

    private List<ParadaResponse> paradas(List<RoadBlock> bloqueos) {
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
        Order pedido = new Order("C-1", new Location(6, 0), 1, inicio, inicio.plusSeconds(20 * 3600));
        DeliveryRoute ruta = DeliveryRoute.startScenarioAtCentral("R1", vehiculo, central, 1, inicio)
                .withAppendedStop(new DeliveryStop(pedido, 1));
        ScheduledDeliveryRoute programada = new RouteScheduler(new RoadNetwork()).schedule(ruta, snapshot, bloqueos);
        PlanEvaluation evaluacion = new PlanEvaluation(Map.of(ruta.id(), programada), List.of(),
                snapshot.inventory(), programada.totalCost());
        ResultadoPlanificacion resultado = new ResultadoPlanificacion(OperationalPlan.empty().withRoute(ruta),
                evaluacion, List.of());
        return new PlanResponseMapper().aResponse(ModoOperacion.DIA_A_DIA, inicio, resultado, 1L, 0L, 1_500L)
                .rutas().getFirst().paradas();
    }

    @Test
    void sinBloqueosElCaminoVaRectoDelOrigenALaParada() {
        List<Punto> camino = paradas(List.of()).getFirst().camino();

        assertEquals(List.of(new Punto(0, 0), new Punto(1, 0), new Punto(2, 0), new Punto(3, 0), new Punto(4, 0),
                new Punto(5, 0), new Punto(6, 0)), camino);
    }

    @Test
    void conUnBloqueoElCaminoLoRodeaYTerminaEnLaParada() {
        RoadBlock bloqueo = new RoadBlock(inicio.minusSeconds(3600), inicio.plusSeconds(24 * 3600),
                List.of(bloqueado, new Location(3, 1)));

        List<Punto> camino = paradas(List.of(bloqueo)).getFirst().camino();

        assertEquals(new Punto(0, 0), camino.getFirst());
        assertEquals(new Punto(6, 0), camino.getLast());
        assertFalse(camino.contains(new Punto(3, 0)), "pasa por el nodo bloqueado: " + camino);
        assertFalse(camino.contains(new Punto(3, 1)), "pasa por el nodo bloqueado: " + camino);
        for (int i = 1; i < camino.size(); i++) {
            Punto a = camino.get(i - 1);
            Punto b = camino.get(i);
            assertEquals(1, Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()), "nodos no contiguos: " + camino);
        }
        assertTrue(camino.size() - 1 > 6, "el rodeo es más largo que el camino recto");
    }
}
