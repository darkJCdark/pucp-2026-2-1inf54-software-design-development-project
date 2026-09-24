package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.search.SearchControl;
import pe.edu.pucp.paqrap.planner.domain.*;
import java.time.*;
import java.util.*;

/** Common physical-arrival / service / meal clock for both algorithms and the final audit. */
public final class RouteScheduler {
    private final RoadNetwork roadNetwork;
    public RouteScheduler(RoadNetwork roadNetwork) { this.roadNetwork = Objects.requireNonNull(roadNetwork); }

    public record TimedStop(RoadPath approach, Instant arrivedAt, Instant completedAt) {}

    public TimedStop timeStop(Location from, Instant departure, RouteStop stop, Vehicle vehicle,
                              OperationalSnapshot snapshot, List<RoadBlock> blocks) {
        double speed = snapshot.fleetProfile().parametersFor(vehicle.type()).speedKmPerHour();
        Duration perStreet = Duration.ofNanos(Math.max(1, Math.round(3_600_000_000_000.0 / speed)));
        RoadPath path = travel(from, stop.location(), departure, perStreet, snapshot.shiftSchedule(), blocks);
        Instant arrived = path.arrivesAt();
        Instant completed = stop instanceof DeliveryStop
                ? snapshot.shiftSchedule().nextWorkStart(arrived, DeliveryStop.SERVICE_TIME).plus(DeliveryStop.SERVICE_TIME)
                : arrived;
        return new TimedStop(path, arrived, completed);
    }

    /** Pauses only at street nodes. If a meal changes passage times, recompute the remainder
     * against the temporal road blocks instead of shifting an obsolete path. */
    private RoadPath travel(Location origin, Location destination, Instant requested, Duration perStreet,
                            ShiftSchedule shifts, List<RoadBlock> blocks) {
        Location current = origin;
        Instant time = requested;
        List<RoadLeg> completed = new ArrayList<>();
        while (true) {
            SearchControl.checkpoint();
            time = roadNetwork.firstUnblockedAt(current, time, blocks);
            RoadPath suffix = roadNetwork.shortestPath(current, destination, time, perStreet, blocks)
                    .orElseThrow(() -> new IllegalStateException("No feasible road path to the next route stop"));
            boolean replan = false;
            for (RoadLeg leg : suffix.legs()) {
                Instant workStart = shifts.nextWorkStart(leg.departsAt(), perStreet);
                if (!workStart.equals(leg.departsAt())) {
                    time = workStart;
                    replan = true;
                    break;
                }
                completed.add(leg);
                current = leg.to();
                time = leg.arrivesAt();
            }
            if (!replan) return new RoadPath(origin, destination, requested, completed, time);
        }
    }

    public ScheduledDeliveryRoute schedule(DeliveryRoute route, OperationalSnapshot snapshot, List<RoadBlock> blocks) {
        SearchControl.routeSchedule();
        Objects.requireNonNull(route); Objects.requireNonNull(snapshot); Objects.requireNonNull(blocks);
        List<ScheduledRouteStop> stops = new ArrayList<>();
        Location location = route.startLocation();
        Instant time = route.departureAt();
        int load = route.initialLoad();
        double distance = 0;
        for (RouteStop stop : route.stops()) {
            TimedStop timing = timeStop(location, time, stop, route.vehicle(), snapshot, blocks);
            int before = load;
            if (stop instanceof DeliveryStop d) load -= d.deliveredPackages();
            else if (stop instanceof WarehouseVisit w) load = Math.addExact(load, w.pickupPackages());
            else throw new IllegalStateException("Unknown route stop type");
            stops.add(new ScheduledRouteStop(stop, timing.approach(), timing.arrivedAt(), timing.completedAt(), before, load));
            location = stop.location(); time = timing.completedAt(); distance += timing.approach().distanceKm();
        }
        double cost = distance * snapshot.fleetProfile().parametersFor(route.vehicle().type()).costPerKm();
        return new ScheduledDeliveryRoute(route, stops, time, distance, cost);
    }
}
