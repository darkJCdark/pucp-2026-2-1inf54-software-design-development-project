package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

final class InsertionSupport {

    private final OperationalPlanEvaluator evaluator;

    private final int candidateVehicles;

    private final VehicleSelectionPolicy vehicleSelection =
            new VehicleSelectionPolicy();

    InsertionSupport(
            OperationalPlanEvaluator evaluator,
            int candidateVehicles
    ) {

        this.evaluator =
                Objects.requireNonNull(
                        evaluator,
                        "evaluator is required"
                );

        if (
                candidateVehicles <= 0
        ) {

            throw new IllegalArgumentException(
                    "candidateVehicles must be positive"
            );
        }

        this.candidateVehicles =
                candidateVehicles;
    }

    Optional<InsertionCandidate> bestValidatedInsertion(
            OperationalPlan basePlan,
            DeliveryDemand demand,
            OperationalSnapshot snapshot,
            Collection<Order> requiredOrders,
            List<RoadBlock> blocks,
            long deadlineNanos
    ) {

        List<VehicleOperationalState> candidates =
                rankedVehicles(
                        basePlan,
                        demand.order(),
                        snapshot
                );

        boolean slaRescue =
                vehicleSelection.isSlaPriority(
                        demand.order(),
                        snapshot
                );

        /*
         * La ventaja de SLA Rescue:
         *
         * no limitar la emergencia a candidateVehicles.
         */
        int limit =
                slaRescue
                        ? candidates.size()
                        : Math.min(
                                candidateVehicles,
                                candidates.size()
                        );

        InsertionCandidate best =
                null;

        for (
                int i = 0;
                i < limit;
                i++
        ) {

            if (
                    deadlineReached(
                            deadlineNanos
                    )
            ) {
                break;
            }

            VehicleOperationalState state =
                    candidates.get(i);

            OperationalPlan candidatePlan =
                    appendDemand(
                            basePlan,
                            state,
                            demand,
                            snapshot
                    );

            PlanEvaluation evaluation =
                    evaluator.evaluate(
                            candidatePlan,
                            snapshot,
                            requiredOrders,
                            blocks
                    );

            /*
             * OperationalPlanEvaluator ya exige SLA.
             *
             * Por tanto todo candidato que llega aquí
             * cumple los deadlines de las entregas
             * incluidas en el plan.
             */
            if (
                    !evaluation.isFeasible()
            ) {
                continue;
            }

            InsertionCandidate candidate =
                    new InsertionCandidate(
                            candidatePlan,
                            evaluation,
                            state.vehicle().id()
                    );

            if (
                    best == null
                    ||
                    better(
                            candidate,
                            best,
                            demand.order(),
                            slaRescue
                    )
            ) {

                best =
                        candidate;
            }
        }

        return Optional.ofNullable(
                best
        );
    }

    List<InsertionCandidate> validatedInsertions(
            OperationalPlan basePlan,
            DeliveryDemand demand,
            OperationalSnapshot snapshot,
            Collection<Order> requiredOrders,
            List<RoadBlock> blocks,
            long deadlineNanos
    ) {

        List<InsertionCandidate> valid =
                new ArrayList<>();

        List<VehicleOperationalState> candidates =
                rankedVehicles(
                        basePlan,
                        demand.order(),
                        snapshot
                );

        boolean slaRescue =
                vehicleSelection.isSlaPriority(
                        demand.order(),
                        snapshot
                );

        int limit =
                slaRescue
                        ? candidates.size()
                        : Math.min(
                                candidateVehicles,
                                candidates.size()
                        );

        for (
                int i = 0;
                i < limit;
                i++
        ) {

            if (
                    deadlineReached(
                            deadlineNanos
                    )
            ) {
                break;
            }

            VehicleOperationalState state =
                    candidates.get(i);

            OperationalPlan candidatePlan =
                    appendDemand(
                            basePlan,
                            state,
                            demand,
                            snapshot
                    );

            PlanEvaluation evaluation =
                    evaluator.evaluate(
                            candidatePlan,
                            snapshot,
                            requiredOrders,
                            blocks
                    );

            if (
                    evaluation.isFeasible()
            ) {

                valid.add(
                        new InsertionCandidate(
                                candidatePlan,
                                evaluation,
                                state.vehicle().id()
                        )
                );
            }
        }

        /*
         * Todos son factibles.
         *
         * Por eso el costo vuelve a ser el criterio
         * principal.
         *
         * Para SLA prioritario usamos llegada solamente
         * como desempate.
         */
        valid.sort(
                Comparator
                        .comparingDouble(
                                (InsertionCandidate candidate) ->
                                        candidate
                                                .evaluation()
                                                .totalCost()
                        )

                        .thenComparing(
                                (InsertionCandidate candidate) ->
                                        slaRescue
                                                ? arrivalForOrder(
                                                        candidate.evaluation(),
                                                        demand.order().id()
                                                )
                                                : Instant.MAX
                        )
        );

        return valid;
    }

    OperationalPlan appendHeuristically(
            OperationalPlan basePlan,
            DeliveryDemand demand,
            OperationalSnapshot snapshot
    ) {

        List<VehicleOperationalState> ranked =
                rankedVehicles(
                        basePlan,
                        demand.order(),
                        snapshot
                );

        if (
                ranked.isEmpty()
        ) {
            return basePlan;
        }

        return appendDemand(
                basePlan,
                ranked.getFirst(),
                demand,
                snapshot
        );
    }

    /*
     * =====================================================
     * COMPARACIÓN EXACTA
     * =====================================================
     */

    private boolean better(
            InsertionCandidate candidate,
            InsertionCandidate current,
            Order order,
            boolean slaRescue
    ) {

        int costComparison =
                Double.compare(
                        candidate
                                .evaluation()
                                .totalCost(),

                        current
                                .evaluation()
                                .totalCost()
                );

        if (
                costComparison < 0
        ) {
            return true;
        }

        if (
                costComparison > 0
        ) {
            return false;
        }

        /*
         * Mismo costo:
         *
         * para pedidos prioritarios preferimos el que
         * llegue antes.
         */
        if (
                slaRescue
        ) {

            Instant candidateArrival =
                    arrivalForOrder(
                            candidate.evaluation(),
                            order.id()
                    );

            Instant currentArrival =
                    arrivalForOrder(
                            current.evaluation(),
                            order.id()
                    );

            return candidateArrival.isBefore(
                    currentArrival
            );
        }

        return candidate
                .vehicleId()
                .compareTo(
                        current.vehicleId()
                )
                <
                0;
    }

    private Instant arrivalForOrder(
            PlanEvaluation evaluation,
            String orderId
    ) {

        return evaluation
                .schedulesByRouteId()
                .values()
                .stream()

                .flatMap(
                        schedule ->
                                schedule
                                        .scheduledStops()
                                        .stream()
                )

                .filter(
                        stop ->
                                stop.stop()
                                        instanceof
                                        DeliveryStop
                )

                .filter(
                        stop ->
                                (
                                        (
                                                DeliveryStop
                                        ) stop.stop()
                                )
                                        .order()
                                        .id()
                                        .equals(
                                                orderId
                                        )
                )

                /*
                 * Si hay entrega parcial, importa la
                 * llegada de la última fracción.
                 */
                .map(
                        ScheduledRouteStop::arrivedAt
                )

                .max(
                        Comparator.naturalOrder()
                )

                .orElse(
                        Instant.MAX
                );
    }

    private List<VehicleOperationalState> rankedVehicles(
            OperationalPlan plan,
            Order order,
            OperationalSnapshot snapshot
    ) {

        return vehicleSelection.rank(
                plan,
                order,
                snapshot
        );
    }

    /*
     * =====================================================
     * CONSTRUCCIÓN
     * =====================================================
     */

    private OperationalPlan appendDemand(
            OperationalPlan plan,
            VehicleOperationalState state,
            DeliveryDemand demand,
            OperationalSnapshot snapshot
    ) {

        Warehouse central =
                snapshot
                        .inventory()
                        .warehouses()
                        .stream()

                        .filter(
                                Warehouse::isCentral
                        )

                        .findFirst()

                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "The snapshot requires a central warehouse"
                                        )
                        );

        int capacity =
                snapshot
                        .fleetProfile()
                        .parametersFor(
                                state
                                        .vehicle()
                                        .type()
                        )
                        .capacity();

        OperationalPlan updated =
                plan;

        int remaining =
                demand.packages();

        while (
                remaining > 0
        ) {

            int quantity =
                    Math.min(
                            remaining,
                            capacity
                    );

            DeliveryRoute route =
                    updated
                            .routeForVehicle(
                                    state
                                            .vehicle()
                                            .id()
                            )

                            .map(
                                    existing ->
                                            appendAfterReturn(
                                                    existing,
                                                    central,
                                                    demand.order(),
                                                    quantity
                                            )
                            )

                            .orElseGet(
                                    () ->
                                            newRoute(
                                                    state,
                                                    central,
                                                    demand.order(),
                                                    quantity,
                                                    snapshot
                                            )
                            );

            updated =
                    updated.withRoute(
                            route
                    );

            remaining -=
                    quantity;
        }

        return updated;
    }

    private DeliveryRoute newRoute(
            VehicleOperationalState state,
            Warehouse central,
            Order order,
            int quantity,
            OperationalSnapshot snapshot
    ) {

        DeliveryRoute route;

        if (
                state
                        .location()
                        .equals(
                                central.location()
                        )
                &&
                state.carriedPackages()
                        ==
                        0
        ) {

            route =
                    DeliveryRoute
                            .startScenarioAtCentral(
                                    "ALNS-"
                                            +
                                            state
                                                    .vehicle()
                                                    .id(),

                                    state.vehicle(),

                                    central,

                                    quantity,

                                    snapshot.planningTime()
                            )

                            .withAppendedStop(
                                    new DeliveryStop(
                                            order,
                                            quantity
                                    )
                            );

        } else {

            route =
                    DeliveryRoute
                            .replanFromCurrentLocation(
                                    "ALNS-"
                                            +
                                            state
                                                    .vehicle()
                                                    .id(),

                                    state.vehicle(),

                                    state.location(),

                                    state.carriedPackages(),

                                    snapshot.planningTime()
                            )

                            .withAppendedStop(
                                    new WarehouseVisit(
                                            central,
                                            quantity
                                    )
                            )

                            .withAppendedStop(
                                    new DeliveryStop(
                                            order,
                                            quantity
                                    )
                            );
        }

        return route.returningTo(
                central
        );
    }

    private DeliveryRoute appendAfterReturn(
            DeliveryRoute route,
            Warehouse central,
            Order order,
            int quantity
    ) {

        List<RouteStop> stops =
                new ArrayList<>(
                        route.stops()
                );

        if (
                !stops.isEmpty()
                &&
                stops.getLast()
                        instanceof
                        WarehouseVisit
        ) {

            stops.removeLast();
        }

        return route
                .withReplacedStops(
                        stops
                )

                .withAppendedStop(
                        new WarehouseVisit(
                                central,
                                quantity
                        )
                )

                .withAppendedStop(
                        new DeliveryStop(
                                order,
                                quantity
                        )
                )

                .returningTo(
                        central
                );
    }

    private boolean deadlineReached(
            long deadlineNanos
    ) {

        return deadlineNanos
                !=
                Long.MAX_VALUE
                &&
                System.nanoTime()
                        >=
                        deadlineNanos;
    }

    record InsertionCandidate(
            OperationalPlan plan,
            PlanEvaluation evaluation,
            String vehicleId
    ) {
    }
}