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
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cubre un hueco reportado en una revision anterior: el evaluador/scheduler
 * del algoritmo 2 (SA) no implementaba el refrigerio (hora de alimentacion
 * por turno), aunque el enunciado lo exige y GraspPlanificador si lo tenia
 * en su version previa (antes de compartir este modulo). Ahora vive en
 * RouteScheduler, compartido por ambos algoritmos.
 */
class RouteSchedulerRefrigerioTest {

    private final ZoneId zona = ShiftSchedule.DEFAULT_ZONE;

    private OperationalSnapshot snapshotConVelocidad(double velocidadKmH, Instant horaInicio, Warehouse central, Vehicle vehiculo) {
        Map<VehicleType, VehicleParameters> parametros = new EnumMap<>(VehicleType.class);
        for (VehicleType t : VehicleType.values()) {
            parametros.put(t, new VehicleParameters(24, velocidadKmH, 1.0));
        }
        Map<String, VehicleOperationalState> estados = Map.of(vehiculo.id(),
                new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, central.location(), horaInicio));
        return new OperationalSnapshot(horaInicio, new FleetProfile(parametros),
                InventorySnapshot.from(List.of(central)), estados,
                new MaintenanceCalendar(zona, List.of()), new ShiftSchedule(zona), List.of());
    }

    @Test
    void elRefrigerioSeAgregaUnaVezDentroDeLaVentanaDelTurnoYNoSeRepite() {
        Instant horaInicio = ZonedDateTime.of(LocalDate.of(2026, 9, 9), LocalTime.of(7, 0), zona).toInstant();
        Warehouse central = Warehouse.central("CENTRAL", new Location(0, 0));
        Vehicle vehiculo = new Vehicle("TA01", VehicleType.CAR, true);
        OperationalSnapshot snapshot = snapshotConVelocidad(10.0, horaInicio, central, vehiculo);

        // Primera entrega a 15 km (10 km/h -> 90 min): llegada sin
        // refrigerio 08:30, cae dentro de la ventana del turno 07:00-15:00
        // (ventana 08:00-14:00) -> debe sumar 1h una vez: llegada real 09:30.
        Order pedido1 = new Order("C-1", new Location(15, 0), 1, horaInicio, horaInicio.plusSeconds(20 * 3600));
        // Segunda entrega, 5 km mas adelante, mismo turno: NO debe sumar
        // otra hora (el refrigerio de este turno ya se tomo).
        Order pedido2 = new Order("C-2", new Location(20, 0), 1, horaInicio, horaInicio.plusSeconds(20 * 3600));

        DeliveryRoute ruta = DeliveryRoute.startScenarioAtCentral("R1", vehiculo, central, 2, horaInicio)
                .withAppendedStop(new DeliveryStop(pedido1, 1))
                .withAppendedStop(new DeliveryStop(pedido2, 1));

        RoadNetwork roadNetwork = new RoadNetwork();
        ScheduledDeliveryRoute programada = new RouteScheduler(roadNetwork).schedule(ruta, snapshot, List.of());

        Instant llegada1SinRefrigerio = horaInicio.plusSeconds(90 * 60);
        assertEquals(llegada1SinRefrigerio.plusSeconds(3600), programada.scheduledStops().get(0).arrivedAt(),
                "la primera parada debe incluir 1h de refrigerio");

        // completedAt de la parada 1 (llegada + 1h de servicio) + 5 km mas (30 min)
        Instant salidaHaciaParada2 = programada.scheduledStops().get(0).completedAt();
        Instant llegada2Esperada = salidaHaciaParada2.plusSeconds(30 * 60);
        assertEquals(llegada2Esperada, programada.scheduledStops().get(1).arrivedAt(),
                "la segunda parada NO debe sumar otro refrigerio en el mismo turno");
    }
}
