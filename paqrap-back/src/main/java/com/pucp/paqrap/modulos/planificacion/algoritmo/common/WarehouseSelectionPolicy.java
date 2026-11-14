package com.pucp.paqrap.modulos.planificacion.algoritmo.common;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.Location;

/**
 * Política compartida para elegir puntos de abastecimiento y
 * posicionamiento.
 *
 * La selección no fuerza el almacén Central. Para una recarga ordena
 * únicamente almacenes con stock suficiente por la distancia:
 *
 *     origen -> almacén -> pedido
 *
 * Para el cierre de una ruta un almacén intermedio solo gana cuando reduce
 * materialmente el recorrido. Central gana los empates, y la ocupación de
 * los intermedios se usa como penalización gradual, nunca como veto.
 *
 * Las decisiones definitivas siguen siendo validadas por
 * {@link OperationalPlanEvaluator}; esta clase solo genera un orden
 * heurístico, determinista y barato.
 */
public final class WarehouseSelectionPolicy {

    /** Preferencia flexible, nunca una restricción dura de factibilidad. */
    public static final int MAX_ESPERA_INTERMEDIO = 3;
    /** Un intermedio solo debe atraer el cierre si ahorra recorrido de forma material. */
    public static final double AHORRO_MINIMO_TERMINAL_KM = 3.0;
    /** Penalización gradual por cada vehículo por encima de la preferencia de espera. */
    public static final double PENALIDAD_EXCESO_OCUPACION_KM = 8.0;
    /** Reserva táctica: un intermedio con poco stock deja de ganar por diferencias marginales. */
    public static final int RESERVA_MINIMA_INTERMEDIA = 50;
    public static final double PENALIDAD_STOCK_BAJO_KM = 6.0;
    public static final Duration VENTANA_URGENCIA = Duration.ofHours(6);

    /**
     * Destino de una ruta NUEVA. Contabiliza unidades disponibles en cada almacén
     * y vehículos que terminarán allí según el plan candidato de este ciclo.
     * La urgencia NO desactiva el control de ocupación del almacén terminal.
     * La ocupación se expresa como penalización y el intermedio solo atrae el cierre
     * cuando aporta un ahorro material frente al Central.
     */
    public Warehouse bestTerminalWarehouse(
            Location from,
            OperationalSnapshot snapshot,
            OperationalPlan partialPlan,
            Order order
    ) {
        return bestTerminalWarehouse(from, snapshot, partialPlan, order, null);
    }

    /** Excluye del conteo a la unidad cuya ruta candidata estamos reemplazando. */
    public Warehouse bestTerminalWarehouse(
            Location from,
            OperationalSnapshot snapshot,
            OperationalPlan partialPlan,
            Order order,
            String updatingVehicleId
    ) {
        Objects.requireNonNull(snapshot, "snapshot is required");
        Objects.requireNonNull(partialPlan, "partialPlan is required");
        Objects.requireNonNull(order, "order is required");
        Warehouse central = central(snapshot.inventory());
        double centralDistance = from.manhattanDistanceTo(central.location());
        Map<String, Integer> occupancy = projectedIdleByWarehouse(snapshot, partialPlan, updatingVehicleId);

        return snapshot.inventory().warehouses().stream()
                .filter(warehouse -> warehouse.isCentral()
                        || centralDistance - from.manhattanDistanceTo(warehouse.location())
                        >= AHORRO_MINIMO_TERMINAL_KM)
                .min(Comparator
                        .comparingDouble((Warehouse warehouse) ->
                                terminalScore(from, warehouse, occupancy))
                        /* Central gana el empate: el intermedio debe aportar un beneficio real. */
                        .thenComparingInt(warehouse -> warehouse.isCentral() ? 0 : 1)
                        .thenComparing(Warehouse::id))
                .orElse(central);
    }

    public boolean urgent(Order order, OperationalSnapshot snapshot) {
        return !order.deadline().isAfter(snapshot.planningTime().plus(VENTANA_URGENCIA));
    }

    public Map<String, Integer> projectedIdleByWarehouse(
            OperationalSnapshot snapshot,
            OperationalPlan partialPlan
    ) {
        return projectedIdleByWarehouse(snapshot, partialPlan, null);
    }

    private Map<String, Integer> projectedIdleByWarehouse(
            OperationalSnapshot snapshot,
            OperationalPlan partialPlan,
            String updatingVehicleId
    ) {
        Map<String, Integer> counts = new HashMap<>();
        for (VehicleOperationalState vehicle : snapshot.vehiclesById().values()) {
            if (vehicle.vehicle().id().equals(updatingVehicleId)) {
                continue;
            }
            // Si el vehículo ya tiene una ruta candidata, se contará SOLO su destino.
            if (partialPlan.routeForVehicle(vehicle.vehicle().id()).isPresent()) {
                continue;
            }
            if (vehicle.status() != VehicleStatus.AVAILABLE
                    || vehicle.availableAt().isAfter(snapshot.planningTime())) {
                continue;
            }
            for (Warehouse warehouse : snapshot.inventory().warehouses()) {
                if (vehicle.location().equals(warehouse.location())) {
                    counts.merge(warehouse.id(), 1, Integer::sum);
                    break;
                }
            }
        }
        for (DeliveryRoute route : partialPlan.routes()) {
            if (route.vehicle().id().equals(updatingVehicleId)) {
                continue;
            }
            if (!route.stops().isEmpty()
                    && route.stops().getLast() instanceof WarehouseVisit terminal
                    && terminal.pickupPackages() == 0) {
                counts.merge(terminal.warehouse().id(), 1, Integer::sum);
            }
        }
        return Map.copyOf(counts);
    }

    public List<Warehouse> rankedSupplyWarehouses(
            Location origin,
            Order order,
            int quantity,
            InventorySnapshot inventory
    ) {

        Objects.requireNonNull(
                origin,
                "origin is required"
        );

        Objects.requireNonNull(
                order,
                "order is required"
        );

        Objects.requireNonNull(
                inventory,
                "inventory is required"
        );

        if (
                quantity <= 0
        ) {

            throw new IllegalArgumentException(
                    "quantity must be positive"
            );
        }

        List<Warehouse> candidates =
                new ArrayList<>();

        for (
                Warehouse warehouse :
                inventory.warehouses()
        ) {

            if (
                    inventory.hasStockFor(
                            warehouse.id(),
                            quantity
                    )
            ) {

                candidates.add(
                        warehouse
                );
            }
        }

        candidates.sort(
                Comparator
                        .comparingDouble(
                                (Warehouse warehouse) ->
                                        supplyScore(
                                                origin,
                                                warehouse,
                                                order.destination(),
                                                quantity,
                                                inventory
                                        )
                        )
                        /*
                         * En empate gana Central. Un intermedio se usa por ahorro
                         * real o por necesidad de servicio, no para dispersar flota.
                         */
                        .thenComparingInt(
                                warehouse ->
                                        warehouse.isCentral()
                                                ? 0
                                                : 1
                        )
                        .thenComparing(
                                Warehouse::id
                        )
        );

        return List.copyOf(
                candidates
        );
    }

    public Warehouse bestSupplyWarehouse(
            Location origin,
            Order order,
            int quantity,
            InventorySnapshot inventory
    ) {

        List<Warehouse> candidates =
                rankedSupplyWarehouses(
                        origin,
                        order,
                        quantity,
                        inventory
                );

        if (
                candidates.isEmpty()
        ) {

            throw new IllegalStateException(
                    "No warehouse has enough stock for "
                            +
                            quantity
                            +
                            " packages"
            );
        }

        return candidates.getFirst();
    }

    public Warehouse bestTerminalWarehouse(
            Location from,
            InventorySnapshot inventory
    ) {

        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(inventory, "inventory is required");

        Warehouse central = central(inventory);
        double centralDistance = from.manhattanDistanceTo(central.location());

        return inventory.warehouses().stream()
                .filter(warehouse -> warehouse.isCentral()
                        || centralDistance - from.manhattanDistanceTo(warehouse.location())
                        >= AHORRO_MINIMO_TERMINAL_KM)
                .min(Comparator
                        .comparingDouble((Warehouse warehouse) ->
                                from.manhattanDistanceTo(warehouse.location()))
                        .thenComparingInt(warehouse -> warehouse.isCentral() ? 0 : 1)
                        .thenComparing(Warehouse::id))
                .orElse(central);
    }

    private Warehouse nearestWarehouse(Location from, InventorySnapshot inventory) {
        return inventory.warehouses().stream()
                .min(Comparator
                        .comparingDouble((Warehouse warehouse) ->
                                from.manhattanDistanceTo(warehouse.location()))
                        .thenComparingInt(warehouse -> warehouse.isCentral() ? 0 : 1)
                        .thenComparing(Warehouse::id))
                .orElseThrow(() -> new IllegalStateException(
                        "The snapshot requires at least one warehouse"));
    }

    private Warehouse central(InventorySnapshot inventory) {
        return inventory.warehouses().stream()
                .filter(Warehouse::isCentral)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "The snapshot requires a central warehouse"));
    }

    private double terminalScore(
            Location from,
            Warehouse warehouse,
            Map<String, Integer> occupancy
    ) {
        double score = from.manhattanDistanceTo(warehouse.location());
        if (!warehouse.isCentral()) {
            int currentOccupancy = occupancy.getOrDefault(warehouse.id(), 0);
            int resultingOccupancy = currentOccupancy + 1;
            int excess = Math.max(0, resultingOccupancy - MAX_ESPERA_INTERMEDIO);
            score += excess * excess * PENALIDAD_EXCESO_OCUPACION_KM;
        }
        return score;
    }

    private double supplyScore(
            Location origin,
            Warehouse warehouse,
            Location destination,
            int quantity,
            InventorySnapshot inventory
    ) {
        double score = supplyDistance(origin, warehouse, destination);
        if (!warehouse.isCentral()) {
            int remaining = inventory.availableStock(warehouse.id()) - quantity;
            if (remaining < RESERVA_MINIMA_INTERMEDIA) {
                score += PENALIDAD_STOCK_BAJO_KM;
            }
        }
        return score;
    }

    public double supplyDistance(
            Location origin,
            Warehouse warehouse,
            Location destination
    ) {

        Objects.requireNonNull(
                origin,
                "origin is required"
        );

        Objects.requireNonNull(
                warehouse,
                "warehouse is required"
        );

        Objects.requireNonNull(
                destination,
                "destination is required"
        );

        return origin
                .manhattanDistanceTo(
                        warehouse.location()
                )
                +
                warehouse
                        .location()
                        .manhattanDistanceTo(
                                destination
                        );
    }
}
