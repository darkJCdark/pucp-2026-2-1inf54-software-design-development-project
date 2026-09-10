package pe.edu.pucp.paqrap.planner.replan;

import pe.edu.pucp.paqrap.planner.domain.BreakdownAvailabilityCalculator;
import pe.edu.pucp.paqrap.planner.domain.BreakdownEvent;
import pe.edu.pucp.paqrap.planner.domain.FleetProfile;
import pe.edu.pucp.paqrap.planner.domain.OperationalSnapshot;
import pe.edu.pucp.paqrap.planner.domain.Order;
import pe.edu.pucp.paqrap.planner.domain.RoadBlock;
import pe.edu.pucp.paqrap.planner.domain.VehicleOperationalState;
import pe.edu.pucp.paqrap.planner.domain.VehicleStatus;
import pe.edu.pucp.paqrap.planner.domain.Warehouse;
import pe.edu.pucp.paqrap.planner.route.OperationProjection;
import pe.edu.pucp.paqrap.planner.route.OperationalPlan;
import pe.edu.pucp.paqrap.planner.route.ReplanningPlanBuilder;
import pe.edu.pucp.paqrap.planner.route.RouteProgressProjector;
import pe.edu.pucp.paqrap.planner.sa.AnnealingConfig;
import pe.edu.pucp.paqrap.planner.sa.OperationalSimulatedAnnealingPlanner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Rebuilds pending work after a speed, road-network or vehicle-breakdown event. */
public final class DynamicReplanningService {
    private final RouteProgressProjector projector;
    private final ReplanningPlanBuilder planBuilder;

    public DynamicReplanningService(RouteProgressProjector projector, ReplanningPlanBuilder planBuilder) {
        this.projector = Objects.requireNonNull(projector, "projector is required");
        this.planBuilder = Objects.requireNonNull(planBuilder, "planBuilder is required");
    }

    public ReplanningResult replan(OperationalPlan activePlan, OperationalSnapshot snapshot,
                                   Collection<Order> activeOrders, Warehouse central, List<RoadBlock> blocks,
                                   ReplanningEvent event) {
        Objects.requireNonNull(event, "event is required");
        if (event.occurredAt().isBefore(snapshot.planningTime())) {
            throw new IllegalArgumentException("Replanning event cannot precede the source snapshot");
        }
        OperationProjection projection = projector.project(activePlan, snapshot, blocks, event.occurredAt());
        FleetProfile nextProfile = applySpeedChange(snapshot.fleetProfile(), event);
        List<BreakdownEvent> nextBreakdowns = new ArrayList<>(snapshot.breakdowns());
        Map<String, VehicleOperationalState> nextStates = new HashMap<>(projection.vehicleStates());
        applyBreakdown(event, snapshot, nextBreakdowns, nextStates);
        applyHistoricalBreakdownEffects(event.occurredAt(), snapshot, nextBreakdowns, nextStates, central);
        OperationalSnapshot nextSnapshot = new OperationalSnapshot(event.occurredAt(), nextProfile,
                projection.inventory(), nextStates, snapshot.maintenanceCalendar(), snapshot.shiftSchedule(), nextBreakdowns);
        List<Order> pending = pendingOrders(activeOrders, projection.deliveredPackagesByOrderId());
        return new ReplanningResult(nextSnapshot, projection, pending,
                planBuilder.build(nextSnapshot, pending, central, blocks));
    }

    /** Rebuilds and then optimizes the new seed with the operational SA engine. */
    public ReplanningResult replanAndOptimize(OperationalPlan activePlan, OperationalSnapshot snapshot,
                                              Collection<Order> activeOrders, Warehouse central, List<RoadBlock> blocks,
                                              ReplanningEvent event, OperationalSimulatedAnnealingPlanner planner,
                                              AnnealingConfig config) {
        Objects.requireNonNull(planner, "planner is required");
        Objects.requireNonNull(config, "config is required");
        ReplanningResult seed = replan(activePlan, snapshot, activeOrders, central, blocks, event);
        Optional<OperationalPlan> optimized = seed.replannedPlan().map(plan ->
                planner.optimize(plan, seed.nextSnapshot(), seed.pendingOrders(), blocks, config).bestPlan());
        return new ReplanningResult(seed.nextSnapshot(), seed.projection(), seed.pendingOrders(), optimized);
    }

    private FleetProfile applySpeedChange(FleetProfile current, ReplanningEvent event) {
        return event instanceof SpeedChangeEvent speedChange
                ? current.withSpeed(speedChange.vehicleType(), speedChange.speedKmPerHour())
                : current;
    }

    private void applyBreakdown(ReplanningEvent event, OperationalSnapshot snapshot,
                                List<BreakdownEvent> breakdowns,
                                Map<String, VehicleOperationalState> states) {
        if (!(event instanceof VehicleBreakdownEvent breakdownEvent)) {
            return;
        }
        BreakdownEvent breakdown = breakdownEvent.breakdown();
        VehicleOperationalState previous = states.get(breakdown.vehicleId());
        if (previous == null) {
            throw new IllegalArgumentException("Breakdown refers to an unknown vehicle: " + breakdown.vehicleId());
        }
        Instant availableAt = new BreakdownAvailabilityCalculator(snapshot.shiftSchedule())
                .resolve(breakdown).unavailableUntil();
        states.put(breakdown.vehicleId(), new VehicleOperationalState(previous.vehicle(), VehicleStatus.OUT_OF_SERVICE,
                breakdown.location(), previous.carriedPackages(), availableAt));
        breakdowns.add(breakdown);
    }

    /** Applies the automatic return-to-central and post-repair availability effects of prior breakdowns. */
    private void applyHistoricalBreakdownEffects(Instant at, OperationalSnapshot snapshot,
                                                 List<BreakdownEvent> breakdowns,
                                                 Map<String, VehicleOperationalState> states, Warehouse central) {
        BreakdownAvailabilityCalculator calculator = new BreakdownAvailabilityCalculator(snapshot.shiftSchedule());
        for (BreakdownEvent breakdown : breakdowns) {
            VehicleOperationalState state = states.get(breakdown.vehicleId());
            if (state == null) {
                continue;
            }
            var resolution = calculator.resolve(breakdown);
            boolean returnedToCentral = resolution.returnsToCentralAt() != null
                    && !at.isBefore(resolution.returnsToCentralAt());
            boolean repaired = !at.isBefore(resolution.unavailableUntil());
            states.put(breakdown.vehicleId(), new VehicleOperationalState(state.vehicle(),
                    repaired ? VehicleStatus.AVAILABLE : VehicleStatus.OUT_OF_SERVICE,
                    returnedToCentral ? central.location() : state.location(),
                    returnedToCentral ? 0 : state.carriedPackages(),
                    repaired ? at : resolution.unavailableUntil()));
        }
    }

    private List<Order> pendingOrders(Collection<Order> activeOrders, Map<String, Integer> delivered) {
        List<Order> pending = new ArrayList<>();
        for (Order order : activeOrders) {
            int remaining = order.packages() - delivered.getOrDefault(order.id(), 0);
            if (remaining < 0) {
                throw new IllegalStateException("Delivered quantity exceeds order quantity: " + order.id());
            }
            if (remaining > 0) {
                pending.add(new Order(order.id(), order.destination(), remaining, order.registeredAt(), order.deadline()));
            }
        }
        return pending;
    }
}
