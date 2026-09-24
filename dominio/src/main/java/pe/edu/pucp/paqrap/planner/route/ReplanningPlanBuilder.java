package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.OperationalSnapshot;
import pe.edu.pucp.paqrap.planner.domain.Order;
import pe.edu.pucp.paqrap.planner.domain.RoadBlock;
import pe.edu.pucp.paqrap.planner.domain.VehicleOperationalState;
import pe.edu.pucp.paqrap.planner.domain.Warehouse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Greedy reconstruction from live vehicle positions and carried loads after an operational event. */
public final class ReplanningPlanBuilder {
    private final OperationalPlanEvaluator evaluator;

    public ReplanningPlanBuilder(OperationalPlanEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator is required");
    }

    public Optional<OperationalPlan> build(OperationalSnapshot snapshot, Collection<Order> pendingOrders,
                                           Warehouse central, List<RoadBlock> blocks) {
        List<RouteDraft> drafts = snapshot.vehiclesById().values().stream()
                .filter(state -> snapshot.isVehiclePlannableAt(state.vehicle().id(), snapshot.planningTime()))
                .sorted(Comparator.comparing(state -> state.vehicle().id()))
                .map(state -> new RouteDraft(state, snapshot, central))
                .toList();
        if (drafts.isEmpty() && !pendingOrders.isEmpty()) {
            return Optional.empty();
        }
        List<Order> ordered = new ArrayList<>(pendingOrders);
        ordered.sort(Comparator.comparing(Order::deadline));
        int cursor = 0;
        for (Order order : ordered) {
            int remaining = order.packages();
            while (remaining > 0) {
                RouteDraft draft = drafts.get(cursor++ % drafts.size());
                draft.ensureLoadFor(remaining);
                int quantity = Math.min(remaining, draft.currentLoad);
                draft.append(order, quantity);
                remaining -= quantity;
            }
        }
        OperationalPlan plan = OperationalPlan.empty();
        for (RouteDraft draft : drafts) {
            if (draft.route != null) {
                plan = plan.withRoute(draft.route.returningTo(central));
            }
        }
        return evaluator.evaluate(plan, snapshot, pendingOrders, blocks).isFeasible() ? Optional.of(plan) : Optional.empty();
    }

    private static final class RouteDraft {
        private final VehicleOperationalState state;
        private final OperationalSnapshot snapshot;
        private final Warehouse central;
        private DeliveryRoute route;
        private int currentLoad;

        private RouteDraft(VehicleOperationalState state, OperationalSnapshot snapshot, Warehouse central) {
            this.state = state;
            this.snapshot = snapshot;
            this.central = central;
            this.currentLoad = state.carriedPackages();
        }

        private void ensureLoadFor(int required) {
            int capacity = snapshot.fleetProfile().parametersFor(state.vehicle().type()).capacity();
            if (route == null) {
                route = DeliveryRoute.replanFromCurrentLocation("REPLAN-" + state.vehicle().id(), state.vehicle(),
                        state.location(), currentLoad, snapshot.planningTime());
            }
            if (currentLoad == 0) {
                currentLoad = Math.min(capacity, required);
                route = route.withAppendedStop(new WarehouseVisit(central, currentLoad));
            }
        }

        private void append(Order order, int quantity) {
            route = route.withAppendedStop(new DeliveryStop(order, quantity));
            currentLoad -= quantity;
        }
    }
}
