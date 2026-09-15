package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.Location;
import pe.edu.pucp.paqrap.planner.domain.Vehicle;
import pe.edu.pucp.paqrap.planner.domain.Warehouse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable operational route. Scenario-start routes begin at the central warehouse;
 * subsequent replans can start from a vehicle's real location and current load.
 */
public final class DeliveryRoute {
    private final String id;
    private final Vehicle vehicle;
    private final Location startLocation;
    private final Warehouse initialWarehouse;
    private final int initialLoad;
    private final Instant departureAt;
    private final List<RouteStop> stops;

    private DeliveryRoute(String id, Vehicle vehicle, Location startLocation, Warehouse initialWarehouse,
                          int initialLoad, Instant departureAt, List<RouteStop> stops) {
        this.id = requireId(id);
        this.vehicle = Objects.requireNonNull(vehicle, "vehicle is required");
        this.startLocation = Objects.requireNonNull(startLocation, "startLocation is required");
        this.initialWarehouse = initialWarehouse;
        if (initialLoad < 0 || initialLoad > vehicle.type().capacity()) {
            throw new IllegalArgumentException("Initial load must fit the vehicle capacity");
        }
        this.initialLoad = initialLoad;
        this.departureAt = Objects.requireNonNull(departureAt, "departureAt is required");
        this.stops = List.copyOf(Objects.requireNonNull(stops, "stops are required"));
    }

    public static DeliveryRoute startScenarioAtCentral(String id, Vehicle vehicle, Warehouse central,
                                                       int initialLoad, Instant departureAt) {
        Objects.requireNonNull(central, "central warehouse is required");
        if (!central.isCentral()) {
            throw new IllegalArgumentException("Every scenario-start route must depart from the central warehouse");
        }
        return new DeliveryRoute(id, vehicle, central.location(), central, initialLoad, departureAt, List.of());
    }

    public static DeliveryRoute replanFromCurrentLocation(String id, Vehicle vehicle, Location currentLocation,
                                                          int currentLoad, Instant departureAt) {
        return new DeliveryRoute(id, vehicle, currentLocation, null, currentLoad, departureAt, List.of());
    }

    public String id() { return id; }
    public Vehicle vehicle() { return vehicle; }
    public Location startLocation() { return startLocation; }
    public Optional<Warehouse> initialWarehouse() { return Optional.ofNullable(initialWarehouse); }
    public int initialLoad() { return initialLoad; }
    public Instant departureAt() { return departureAt; }
    public List<RouteStop> stops() { return stops; }

    public DeliveryRoute withAppendedStop(RouteStop stop) {
        List<RouteStop> updated = new ArrayList<>(stops);
        updated.add(Objects.requireNonNull(stop, "stop is required"));
        return new DeliveryRoute(id, vehicle, startLocation, initialWarehouse, initialLoad, departureAt, updated);
    }

    public DeliveryRoute withInsertedStop(int position, RouteStop stop) {
        if (position < 0 || position > stops.size()) {
            throw new IndexOutOfBoundsException("Invalid stop insertion position");
        }
        List<RouteStop> updated = new ArrayList<>(stops);
        updated.add(position, Objects.requireNonNull(stop, "stop is required"));
        return new DeliveryRoute(id, vehicle, startLocation, initialWarehouse, initialLoad, departureAt, updated);
    }

    public DeliveryRoute withReplacedStops(List<RouteStop> updatedStops) {
        return new DeliveryRoute(id, vehicle, startLocation, initialWarehouse, initialLoad, departureAt, updatedStops);
    }

    public DeliveryRoute returningTo(Warehouse warehouse) {
        return withAppendedStop(new WarehouseVisit(warehouse, 0));
    }

    public boolean endsAtWarehouse() {
        return !stops.isEmpty() && stops.getLast() instanceof WarehouseVisit;
    }

    private static String requireId(String id) {
        Objects.requireNonNull(id, "id is required");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Route id cannot be blank");
        }
        return id;
    }
}
