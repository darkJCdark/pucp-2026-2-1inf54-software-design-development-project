package pe.edu.pucp.paqrap.planner.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainRulesSmokeTest {
    @Test
    void enforcesPhaseOneDomainRules() {
        assertEquals(new Location(27, 14), PaqRapNetwork.CENTRAL_LOCATION);
        assertEquals(new Location(57, 27), PaqRapNetwork.EAST_LOCATION);
        assertEquals(1_000, PaqRapNetwork.defaultWarehouses().get(1).initialStock());
        assertEquals(40.0, FleetProfile.defaults().parametersFor(VehicleType.CAR).speedKmPerHour());
        assertEquals(35.0, new FleetProfile(Map.of(
                VehicleType.CAR, new VehicleParameters(24, 35.0, 8.0),
                VehicleType.MOTORCYCLE, new VehicleParameters(8, 25.0, 6.0),
                VehicleType.BICYCLE, new VehicleParameters(4, 12.0, 3.0)))
                .parametersFor(VehicleType.CAR).speedKmPerHour());
        assertTrue(new MaintenanceDay("TA01", LocalDate.of(2026, 9, 9))
                .contains(Instant.parse("2026-09-09T12:00:00Z"), ZoneId.of("America/Lima")));

        assertThrows(IllegalArgumentException.class, () -> new Location(71, 0));
        assertThrows(IllegalArgumentException.class, () -> new Vehicle("AUTO-1", VehicleType.CAR, true));
        assertThrows(IllegalArgumentException.class, () -> Warehouse.intermediate("BAD", new Location(0, 0), 1_001));
    }
}
