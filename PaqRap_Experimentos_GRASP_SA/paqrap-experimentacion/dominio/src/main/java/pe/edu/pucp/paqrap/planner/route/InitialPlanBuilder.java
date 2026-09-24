package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.OperationalSnapshot;
import pe.edu.pucp.paqrap.planner.domain.Order;
import pe.edu.pucp.paqrap.planner.domain.RoadBlock;
import pe.edu.pucp.paqrap.planner.domain.VehicleOperationalState;
import pe.edu.pucp.paqrap.planner.domain.Warehouse;
import pe.edu.pucp.paqrap.planner.search.SearchControl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Creates a deterministic earliest-deadline seed. Orders are admitted atomically:
 * the first feasible round-robin rotation is retained and an infeasible order does
 * not invalidate previously admitted work.
 */
public final class InitialPlanBuilder {
    private final OperationalPlanEvaluator evaluator;

    public InitialPlanBuilder(OperationalPlanEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator is required");
    }

    public SeedPlan build(OperationalSnapshot snapshot, Collection<Order> orders,
                          Warehouse central, List<RoadBlock> blocks) {
        Objects.requireNonNull(snapshot, "snapshot is required");
        Objects.requireNonNull(orders, "orders are required");
        Objects.requireNonNull(central, "central is required");
        Objects.requireNonNull(blocks, "blocks are required");
        if (!central.isCentral()) {
            throw new IllegalArgumentException("Initial plans must use the central warehouse");
        }

        List<VehicleOperationalState> vehicles = snapshot.vehiclesById().values().stream()
                .filter(state -> snapshot.isVehiclePlannableAt(state.vehicle().id(), snapshot.planningTime()))
                .filter(state -> state.location().equals(central.location()) && state.carriedPackages() == 0)
                .sorted(Comparator.comparing(state -> state.vehicle().id()))
                .toList();
        List<Order> ordered = new ArrayList<>(orders);
        ordered.sort(Comparator.comparing(Order::deadline).thenComparing(Order::id));
        if (vehicles.isEmpty()) {
            return new SeedPlan(OperationalPlan.empty(), List.of(), ordered);
        }

        OperationalPlan acceptedPlan = OperationalPlan.empty();
        List<Order> attended = new ArrayList<>();
        List<Order> unattended = new ArrayList<>();
        int preferredVehicle = 0;
        for (Order order : ordered) {
            SearchControl.checkpoint();
            OperationalPlan acceptedCandidate = null;
            PlanEvaluation acceptedEvaluation = null;
            for (int alternative = 0; alternative < vehicles.size(); alternative++) {
                SearchControl.checkpoint();
                int firstVehicle = (preferredVehicle + alternative) % vehicles.size();
                OperationalPlan candidate = appendWholeOrder(acceptedPlan, order, firstVehicle,
                        vehicles, central, snapshot);
                PlanEvaluation evaluation = evaluator.evaluate(candidate, snapshot,
                        withOrder(attended, order), blocks);
                if (evaluation.isFeasible()) {
                    acceptedCandidate = candidate;
                    acceptedEvaluation = evaluation;
                    break;
                }
            }
            if (acceptedCandidate == null) {
                unattended.add(order);
            } else {
                acceptedPlan = acceptedCandidate;
                attended.add(order);
                SearchControl.observe(true, ordered.size() - attended.size(), acceptedEvaluation.totalCost());
            }
            preferredVehicle = (preferredVehicle + 1) % vehicles.size();
        }
        return new SeedPlan(acceptedPlan, attended, unattended);
    }

    private static List<Order> withOrder(List<Order> attended, Order order) {
        List<Order> candidateOrders = new ArrayList<>(attended.size() + 1);
        candidateOrders.addAll(attended);
        candidateOrders.add(order);
        return candidateOrders;
    }

    private static OperationalPlan appendWholeOrder(OperationalPlan base, Order order, int firstVehicle,
                                                     List<VehicleOperationalState> vehicles, Warehouse central,
                                                     OperationalSnapshot snapshot) {
        OperationalPlan candidate = base;
        int remaining = order.packages();
        int vehicleOffset = 0;
        while (remaining > 0) {
            SearchControl.checkpoint();
            VehicleOperationalState state = vehicles.get((firstVehicle + vehicleOffset) % vehicles.size());
            int capacity = snapshot.fleetProfile().parametersFor(state.vehicle().type()).capacity();
            int quantity = Math.min(remaining, capacity);
            DeliveryRoute route = candidate.routeForVehicle(state.vehicle().id())
                    .map(existing -> reopenAtCentral(existing, central, quantity))
                    .orElseGet(() -> DeliveryRoute.startScenarioAtCentral("INITIAL-" + state.vehicle().id(),
                            state.vehicle(), central, quantity, snapshot.planningTime()));
            route = route.withAppendedStop(new DeliveryStop(order, quantity)).returningTo(central);
            candidate = candidate.withRoute(route);
            remaining -= quantity;
            vehicleOffset++;
        }
        return candidate;
    }

    private static DeliveryRoute reopenAtCentral(DeliveryRoute route, Warehouse central, int pickupPackages) {
        if (!route.endsAtWarehouse()) {
            throw new IllegalStateException("Accepted seed routes must end at a warehouse");
        }
        List<RouteStop> stops = new ArrayList<>(route.stops());
        stops.removeLast();
        return route.withReplacedStops(stops).withAppendedStop(new WarehouseVisit(central, pickupPackages));
    }
}
