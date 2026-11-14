package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;

class VehicleIdleReactivationPolicyTest {
    private static final Instant T0 = Instant.parse("2028-12-01T10:00:00Z");

    @Test void incluyeUnaUnidadOciosaIntermediaEnLosPrimerosCandidatos() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(27, 14));
        Warehouse east = Warehouse.intermediate("EAST", new Location(57, 27), 100);
        Map<String, VehicleOperationalState> states = new LinkedHashMap<>();
        for (int n = 1; n <= 8; n++) {
            Vehicle vehicle = new Vehicle("TA0" + n, VehicleType.CAR, true);
            states.put(vehicle.id(), new VehicleOperationalState(vehicle, VehicleStatus.AVAILABLE,
                    central.location(), 0, T0));
        }
        Vehicle idle = new Vehicle("TA99", VehicleType.CAR, true);
        states.put(idle.id(), new VehicleOperationalState(idle, VehicleStatus.AVAILABLE,
                east.location(), 0, T0.minus(Duration.ofMinutes(90))));
        OperationalSnapshot snapshot = new OperationalSnapshot(T0, FleetProfile.defaults(),
                InventorySnapshot.from(List.of(central, east)), states,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()),
                ShiftSchedule.defaultSchedule(), List.of());
        Order order = new Order("P", new Location(40, 20), 1, T0, T0.plus(Duration.ofHours(12)));
        List<VehicleOperationalState> ranked =
                new VehicleSelectionPolicy().rank(OperationalPlan.empty(), order, snapshot);
        assertEquals("TA99", ranked.get(1).vehicle().id());
        assertTrue(ranked.stream().anyMatch(v -> v.vehicle().id().equals("TA01")));
    }
}
