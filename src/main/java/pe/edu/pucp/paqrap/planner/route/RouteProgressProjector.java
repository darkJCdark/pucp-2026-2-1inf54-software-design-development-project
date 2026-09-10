package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.InventorySnapshot;
import pe.edu.pucp.paqrap.planner.domain.OperationalSnapshot;
import pe.edu.pucp.paqrap.planner.domain.RoadBlock;
import pe.edu.pucp.paqrap.planner.domain.RoadLeg;
import pe.edu.pucp.paqrap.planner.domain.VehicleOperationalState;
import pe.edu.pucp.paqrap.planner.domain.VehicleStatus;
import pe.edu.pucp.paqrap.planner.domain.Warehouse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Projects planned routes into real vehicle positions, loads and inventory at an event instant. */
public final class RouteProgressProjector {
    private final RouteScheduler scheduler;

    public RouteProgressProjector(RouteScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler is required");
    }

    public OperationProjection project(OperationalPlan plan, OperationalSnapshot snapshot,
                                       List<RoadBlock> blocks, Instant at) {
        Objects.requireNonNull(plan, "plan is required");
        Objects.requireNonNull(snapshot, "snapshot is required");
        Objects.requireNonNull(blocks, "blocks are required");
        Objects.requireNonNull(at, "at is required");
        if (at.isBefore(snapshot.planningTime())) {
            throw new IllegalArgumentException("Projection cannot precede its source snapshot");
        }

        Map<String, VehicleOperationalState> projectedStates = new HashMap<>(snapshot.vehiclesById());
        Map<String, Integer> delivered = new HashMap<>();
        List<InventoryEvent> inventoryEvents = new ArrayList<>();
        for (DeliveryRoute route : plan.routes()) {
            if (route.departureAt().isAfter(at)) {
                continue;
            }
            ScheduledDeliveryRoute schedule = scheduler.schedule(route, snapshot, blocks);
            route.initialWarehouse().ifPresent(warehouse -> {
                if (route.initialLoad() > 0) {
                    inventoryEvents.add(new InventoryEvent(route.departureAt(), warehouse, route.initialLoad()));
                }
            });
            projectedStates.put(route.vehicle().id(), projectRoute(route, schedule, at, delivered, inventoryEvents));
        }
        InventorySnapshot inventory = applyInventory(snapshot, inventoryEvents);
        return new OperationProjection(inventory, projectedStates, delivered);
    }

    private VehicleOperationalState projectRoute(DeliveryRoute route, ScheduledDeliveryRoute schedule, Instant at,
                                                 Map<String, Integer> delivered,
                                                 List<InventoryEvent> inventoryEvents) {
        var location = route.startLocation();
        int load = route.initialLoad();
        for (ScheduledRouteStop scheduledStop : schedule.scheduledStops()) {
            if (!at.isBefore(scheduledStop.completedAt())) {
                location = scheduledStop.stop().location();
                load = scheduledStop.loadAfter();
                if (scheduledStop.stop() instanceof DeliveryStop delivery) {
                    delivered.merge(delivery.order().id(), delivery.deliveredPackages(), Integer::sum);
                } else if (scheduledStop.stop() instanceof WarehouseVisit visit && visit.pickupPackages() > 0) {
                    inventoryEvents.add(new InventoryEvent(scheduledStop.arrivedAt(), visit.warehouse(),
                            visit.pickupPackages()));
                }
                continue;
            }
            for (RoadLeg leg : scheduledStop.approach().legs()) {
                if (!at.isBefore(leg.arrivesAt())) {
                    location = leg.to();
                }
            }
            if (!at.isBefore(scheduledStop.arrivedAt())) {
                location = scheduledStop.stop().location();
            }
            return new VehicleOperationalState(route.vehicle(), VehicleStatus.IN_ROUTE, location, load, at);
        }
        return new VehicleOperationalState(route.vehicle(), VehicleStatus.AVAILABLE, location, load, at);
    }

    private InventorySnapshot applyInventory(OperationalSnapshot snapshot, List<InventoryEvent> events) {
        events.sort(Comparator.comparing(InventoryEvent::at));
        InventoryTimeline timeline = new InventoryTimeline(snapshot.inventory(), snapshot.planningTime(),
                snapshot.shiftSchedule().zoneId());
        for (InventoryEvent event : events) {
            if (!timeline.canVisit(event.warehouse().id(), event.quantity(), event.at())) {
                throw new IllegalStateException("Active plan consumes unavailable warehouse stock");
            }
            timeline.withdraw(event.warehouse().id(), event.quantity(), event.at());
        }
        return timeline.inventory();
    }

    private record InventoryEvent(Instant at, Warehouse warehouse, int quantity) {
    }
}
