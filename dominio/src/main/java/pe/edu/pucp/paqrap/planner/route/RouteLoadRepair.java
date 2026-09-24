package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.*;
import java.util.*;

/** Repairs load accounting after route edits; never repairs SLA/inventory by hiding a violation.
 * Quantities are conserved, initial carried load on replans is immutable, and extra central
 * reloads are inserted when a segment no longer fits. The common evaluator remains authoritative. */
public final class RouteLoadRepair {
    private RouteLoadRepair() {}

    public static DeliveryRoute repair(DeliveryRoute route, OperationalSnapshot snapshot) {
        int cap = snapshot.fleetProfile().parametersFor(route.vehicle().type()).capacity();
        Warehouse central = snapshot.inventory().warehouses().stream().filter(Warehouse::isCentral).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing central warehouse"));
        List<RouteStop> expanded = new ArrayList<>();
        int used = 0;
        int segmentCapacity = route.initialWarehouse().isPresent() ? cap : route.initialLoad();
        for (RouteStop stop : route.stops()) {
            if (stop instanceof WarehouseVisit visit) {
                expanded.add(new WarehouseVisit(visit.warehouse(), 0));
                used = 0; segmentCapacity = cap;
            } else if (stop instanceof DeliveryStop delivery) {
                int left = delivery.deliveredPackages();
                while (left > 0) {
                    if (used == segmentCapacity) {
                        expanded.add(new WarehouseVisit(central, 0));
                        used = 0; segmentCapacity = cap;
                    }
                    int q = Math.min(left, segmentCapacity - used);
                    expanded.add(new DeliveryStop(delivery.order(), q));
                    used += q; left -= q;
                }
            }
        }
        // Adjacent parts at exactly the same visit are one effective delivery, not two fictitious services.
        List<RouteStop> stops = new ArrayList<>();
        for (RouteStop stop : expanded) {
            if (stop instanceof DeliveryStop d && !stops.isEmpty() && stops.getLast() instanceof DeliveryStop prev
                    && prev.order().equals(d.order())) {
                stops.set(stops.size()-1, new DeliveryStop(d.order(), Math.addExact(prev.deliveredPackages(), d.deliveredPackages())));
            } else stops.add(stop);
        }
        int initial = route.initialWarehouse().isPresent() ? segmentDemand(stops, 0) : route.initialLoad();
        int load = initial;
        for (int i=0; i<stops.size(); i++) {
            RouteStop stop = stops.get(i);
            if (stop instanceof DeliveryStop d) load -= d.deliveredPackages();
            else if (stop instanceof WarehouseVisit w) {
                int pickup = Math.max(0, segmentDemand(stops, i+1)-load);
                stops.set(i, new WarehouseVisit(w.warehouse(), pickup));
                load += pickup;
            }
            if (load < 0 || load > cap) throw new IllegalArgumentException("Cannot repair load consistently");
        }
        DeliveryRoute result = route.initialWarehouse().isPresent()
                ? DeliveryRoute.startScenarioAtCentral(route.id(), route.vehicle(), route.initialWarehouse().orElseThrow(), initial, route.departureAt())
                : DeliveryRoute.replanFromCurrentLocation(route.id(), route.vehicle(), route.startLocation(), initial, route.departureAt());
        return result.withReplacedStops(stops);
    }

    public static OperationalPlan repair(OperationalPlan plan, OperationalSnapshot snapshot) {
        OperationalPlan result = OperationalPlan.empty();
        for (DeliveryRoute route : plan.routes()) {
            if (route.stops().stream().noneMatch(DeliveryStop.class::isInstance) && route.initialLoad() == 0) continue;
            result = result.withRoute(repair(route, snapshot));
        }
        return result;
    }

    /** Preserve identities of unchanged routes so the common schedule cache remains effective. */
    public static OperationalPlan repairChanges(OperationalPlan before, OperationalPlan after, OperationalSnapshot snapshot) {
        OperationalPlan result = after;
        for (DeliveryRoute route : after.routes()) {
            if (before.routeForVehicle(route.vehicle().id()).orElse(null) == route) continue;
            DeliveryRoute repaired = repair(route, snapshot);
            if (repaired.stops().stream().noneMatch(DeliveryStop.class::isInstance) && repaired.initialLoad() == 0)
                result = result.withoutRoute(route.vehicle().id());
            else result = result.withRoute(repaired);
        }
        return result;
    }

    private static int segmentDemand(List<RouteStop> stops, int first) {
        int total = 0;
        for (int i=first; i<stops.size() && !(stops.get(i) instanceof WarehouseVisit); i++)
            if (stops.get(i) instanceof DeliveryStop d) total = Math.addExact(total, d.deliveredPackages());
        return total;
    }
}
