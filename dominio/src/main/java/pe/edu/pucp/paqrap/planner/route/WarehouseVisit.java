package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.Location;
import pe.edu.pucp.paqrap.planner.domain.Warehouse;

import java.util.Objects;

/** A zero-time return or reload at a warehouse. A quantity of zero denotes a return without reloading. */
public record WarehouseVisit(Warehouse warehouse, int pickupPackages) implements RouteStop {
    public WarehouseVisit {
        Objects.requireNonNull(warehouse, "warehouse is required");
        if (pickupPackages < 0) {
            throw new IllegalArgumentException("Pickup quantity cannot be negative");
        }
    }

    @Override
    public Location location() {
        return warehouse.location();
    }
}
