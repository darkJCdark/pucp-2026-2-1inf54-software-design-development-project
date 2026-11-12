package com.pucp.paqrap.modulos.planificacion.algoritmo.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

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
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

/**
 * Semana 13: el refrigerio se toma después de atender una parada, pero puede esperar hasta el fin de su ventana. Si
 * tomarlo enseguida haría llegar tarde a la entrega siguiente, el conductor la atiende primero y come después.
 */
class RouteSchedulerRefrigerioPostergadoTest {

    private final ZoneId zona = ShiftSchedule.DEFAULT_ZONE;
    // Turno 07:00-15:00 (Lima): ventana de refrigerio 08:00-14:00.
    private final Instant inicio = ZonedDateTime.of(LocalDate.of(2026, 9, 9), LocalTime.of(7, 0), zona).toInstant();
    private final Warehouse central = Warehouse.central("CENTRAL", new Location(0, 0));
    private final Vehicle auto = new Vehicle("TA01", VehicleType.CAR, true);

    private OperationalSnapshot snapshot() {
        Map<VehicleType, VehicleParameters> parametros = new EnumMap<>(VehicleType.class);
        for (VehicleType tipo : VehicleType.values()) {
            parametros.put(tipo, new VehicleParameters(24, 10.0, 1.0)); // 10 km/h: 6 min por calle
        }
        return new OperationalSnapshot(inicio, new FleetProfile(parametros), InventorySnapshot.from(List.of(central)),
                Map.of(auto.id(), new VehicleOperationalState(auto, VehicleStatus.AVAILABLE, central.location(), inicio)),
                new MaintenanceCalendar(zona, List.of()), new ShiftSchedule(zona), List.of());
    }

    @Test
    void elRefrigerioEsperaSiTomarloHaceLlegarTardeALaSiguienteEntrega() {
        // C-1 a 10 km: llega 08:00, servicio hasta 09:00 (ventana ya abierta). C-2 está 10 km más allá con plazo
        // 10:30: sin refrigerio llega 10:00; con refrigerio llegaría 11:00, tarde.
        Order c1 = new Order("C-1", new Location(10, 0), 1, inicio, inicio.plusSeconds(20 * 3600));
        Order c2 = new Order("C-2", new Location(20, 0), 1, inicio, inicio.plus(Duration.ofMinutes(210)));
        DeliveryRoute ruta = DeliveryRoute.startScenarioAtCentral("R1", auto, central, 2, inicio)
                .withAppendedStop(new DeliveryStop(c1, 1))
                .withAppendedStop(new DeliveryStop(c2, 1))
                .returningTo(central);

        ScheduledDeliveryRoute programada = new RouteScheduler(new RoadNetwork()).schedule(ruta, snapshot(), List.of());

        var primera = programada.scheduledStops().get(0);
        var segunda = programada.scheduledStops().get(1);
        assertEquals(primera.arrivedAt().plus(DeliveryStop.SERVICE_TIME), primera.completedAt(),
                "en C-1 no toma refrigerio: haría llegar tarde a C-2");
        assertEquals(inicio.plus(Duration.ofMinutes(180)), segunda.arrivedAt());
        assertFalse(segunda.arrivedAt().isAfter(c2.deadline()));
        assertEquals(segunda.arrivedAt().plus(DeliveryStop.SERVICE_TIME).plus(Duration.ofHours(1)),
                segunda.completedAt(), "come después de atender C-2");
    }

    @Test
    void noSePostergaSiYaNoComeriaDentroDeSuVentana() {
        // Sale 11:00: C-1 llega 12:00 y termina 13:00. Sin refrigerio C-2 terminaría 15:00, después del cierre de la
        // ventana (14:00): el refrigerio no puede correrse y se toma en C-1 aunque C-2 llegue tarde (el evaluador
        // rechazará ese plan por SLA, pero el conductor no se queda sin comer).
        Instant salida = inicio.plus(Duration.ofHours(4));
        Order c1 = new Order("C-1", new Location(10, 0), 1, inicio, inicio.plusSeconds(20 * 3600));
        Order c2 = new Order("C-2", new Location(20, 0), 1, inicio, salida.plus(Duration.ofMinutes(210)));
        DeliveryRoute ruta = DeliveryRoute.startScenarioAtCentral("R1", auto, central, 2, salida)
                .withAppendedStop(new DeliveryStop(c1, 1))
                .withAppendedStop(new DeliveryStop(c2, 1))
                .returningTo(central);

        ScheduledDeliveryRoute programada = new RouteScheduler(new RoadNetwork()).schedule(ruta, snapshot(), List.of());

        var primera = programada.scheduledStops().get(0);
        assertEquals(primera.arrivedAt().plus(DeliveryStop.SERVICE_TIME).plus(Duration.ofHours(1)),
                primera.completedAt(), "come en C-1: es su última oportunidad dentro de la ventana");
    }

    @Test
    void sinRiesgoParaLaSiguienteEntregaElRefrigerioSeTomaEnseguida() {
        Order c1 = new Order("C-1", new Location(10, 0), 1, inicio, inicio.plusSeconds(20 * 3600));
        Order c2 = new Order("C-2", new Location(20, 0), 1, inicio, inicio.plusSeconds(20 * 3600));
        DeliveryRoute ruta = DeliveryRoute.startScenarioAtCentral("R1", auto, central, 2, inicio)
                .withAppendedStop(new DeliveryStop(c1, 1))
                .withAppendedStop(new DeliveryStop(c2, 1))
                .returningTo(central);

        ScheduledDeliveryRoute programada = new RouteScheduler(new RoadNetwork()).schedule(ruta, snapshot(), List.of());

        var primera = programada.scheduledStops().get(0);
        assertEquals(primera.arrivedAt().plus(DeliveryStop.SERVICE_TIME).plus(Duration.ofHours(1)),
                primera.completedAt());
    }
}
