package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.FleetProfile;
import pe.edu.pucp.paqrap.planner.domain.OperationalSnapshot;
import pe.edu.pucp.paqrap.planner.domain.RoadBlock;
import pe.edu.pucp.paqrap.planner.domain.RoadNetwork;
import pe.edu.pucp.paqrap.planner.domain.RoadPath;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Transforms a logical route into timed legs, using the snapshot speed profile and active road blocks. */
public final class RouteScheduler {
    private final RoadNetwork roadNetwork;

    public RouteScheduler(RoadNetwork roadNetwork) {
        this.roadNetwork = Objects.requireNonNull(roadNetwork, "roadNetwork is required");
    }

    public ScheduledDeliveryRoute schedule(DeliveryRoute route, OperationalSnapshot snapshot, List<RoadBlock> blocks) {
        Objects.requireNonNull(route, "route is required");
        Objects.requireNonNull(snapshot, "snapshot is required");
        Objects.requireNonNull(blocks, "blocks are required");
        FleetProfile profile = snapshot.fleetProfile();
        double speed = profile.parametersFor(route.vehicle().type()).speedKmPerHour();
        Duration travelPerStreet = Duration.ofMillis(Math.round(3_600_000.0 / speed));

        List<ScheduledRouteStop> scheduledStops = new ArrayList<>();
        var currentLocation = route.startLocation();
        Instant currentTime = route.departureAt();
        int currentLoad = route.initialLoad();
        double distance = 0;

        for (RouteStop stop : route.stops()) {
            RoadPath approach = roadNetwork.shortestPath(currentLocation, stop.location(), currentTime,
                            travelPerStreet, blocks)
                    .orElseThrow(() -> new IllegalStateException("No feasible road path to the next route stop"));
            Instant arrivedAt = approach.arrivesAt();
            int loadBefore = currentLoad;
            if (stop instanceof DeliveryStop delivery) {
                currentLoad -= delivery.deliveredPackages();
                currentTime = arrivedAt.plus(DeliveryStop.SERVICE_TIME);
            } else if (stop instanceof WarehouseVisit visit) {
                currentLoad += visit.pickupPackages();
                currentTime = arrivedAt;
            } else {
                throw new IllegalStateException("Unknown route stop type");
            }
            scheduledStops.add(new ScheduledRouteStop(stop, approach, arrivedAt, currentTime,
                    loadBefore, currentLoad));
            currentLocation = stop.location();
            distance += approach.distanceKm();
        }
        double cost = distance * profile.parametersFor(route.vehicle().type()).costPerKm();
        return new ScheduledDeliveryRoute(route, scheduledStops, currentTime, distance, cost);
    }
}
