package pe.edu.pucp.paqrap.planner.domain;

import java.util.Objects;

public record Vehicle(String id, VehicleType type, boolean available) {
    public Vehicle {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(type, "type is required");
        if (!id.matches(type.fleetCode() + "\\d{2}")) {
            throw new IllegalArgumentException("Vehicle id must use " + type.fleetCode() + " followed by two digits");
        }
    }
}
