package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.Location;
import pe.edu.pucp.paqrap.planner.domain.OperationalSnapshot;
import pe.edu.pucp.paqrap.planner.domain.Order;
import pe.edu.pucp.paqrap.planner.domain.RoadBlock;
import pe.edu.pucp.paqrap.planner.domain.VehicleOperationalState;
import pe.edu.pucp.paqrap.planner.domain.Warehouse;

import java.util.ArrayList;
import pe.edu.pucp.paqrap.planner.search.SearchControl;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Creates a deterministic earliest-deadline seed, splitting orders and reloading at central when necessary. */
public final class InitialPlanBuilder {
    private final OperationalPlanEvaluator evaluator;

    public InitialPlanBuilder(OperationalPlanEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator is required");
    }

    public Optional<OperationalPlan> build(OperationalSnapshot snapshot, Collection<Order> orders,
                                           Warehouse central, List<RoadBlock> blocks) {
        Objects.requireNonNull(snapshot, "snapshot is required");
        Objects.requireNonNull(orders, "orders are required");
        Objects.requireNonNull(central, "central is required");
        if (!central.isCentral()) {
            throw new IllegalArgumentException("Initial plans must use the central warehouse");
        }
        List<RouteDraft> drafts = snapshot.vehiclesById().values().stream()
                .filter(state -> snapshot.isVehiclePlannableAt(state.vehicle().id(), snapshot.planningTime()))
                .filter(state -> state.location().equals(central.location()) && state.carriedPackages() == 0)
                .sorted(Comparator.comparing(state -> state.vehicle().id()))
                .map(state -> new RouteDraft(state, central, snapshot))
                .toList();
        if (drafts.isEmpty() && !orders.isEmpty()) {
            return Optional.empty();
        }

        List<Order> ordered = new ArrayList<>(orders);
        ordered.sort(Comparator.comparing(Order::deadline));
        int routeCursor = 0;
        for (Order order : ordered) {
            SearchControl.checkpoint();
            int remaining = order.packages();
            while (remaining > 0) {
                SearchControl.checkpoint();
                RouteDraft draft = drafts.get(routeCursor % drafts.size());
                routeCursor++;
                draft.ensureLoadFor(remaining);
                int quantity = Math.min(remaining, draft.currentLoad);
                draft.appendDelivery(order, quantity);
                remaining -= quantity;
            }
        }

        OperationalPlan plan = OperationalPlan.empty();
        for (RouteDraft draft : drafts) {
            if (draft.route != null) {
                plan = plan.withRoute(draft.route.returningTo(central));
            }
        }
        PlanEvaluation evaluation = evaluator.evaluate(plan, snapshot, orders, blocks);
        if (evaluation.isFeasible()) SearchControl.observe(true, 0, evaluation.totalCost());
        return evaluation.isFeasible() ? Optional.of(plan) : Optional.empty();
    }

    private static final class RouteDraft {
        private final VehicleOperationalState state;
        private final Warehouse central;
        private final OperationalSnapshot snapshot;
        private DeliveryRoute route;
        private int currentLoad;

        private RouteDraft(VehicleOperationalState state, Warehouse central, OperationalSnapshot snapshot) {
            this.state = state;
            this.central = central;
            this.snapshot = snapshot;
        }

        private void ensureLoadFor(int remainingOrderQuantity) {
            int capacity = snapshot.fleetProfile().parametersFor(state.vehicle().type()).capacity();
            if (route == null) {
                currentLoad = Math.min(capacity, remainingOrderQuantity);
                route = DeliveryRoute.startScenarioAtCentral("INITIAL-" + state.vehicle().id(), state.vehicle(),
                        central, currentLoad, snapshot.planningTime());
            } else if (currentLoad == 0) {
                currentLoad = Math.min(capacity, remainingOrderQuantity);
                route = route.withAppendedStop(new WarehouseVisit(central, currentLoad));
            }
        }

        private void appendDelivery(Order order, int quantity) {
            route = route.withAppendedStop(new DeliveryStop(order, quantity));
            currentLoad -= quantity;
        }
    }
}
