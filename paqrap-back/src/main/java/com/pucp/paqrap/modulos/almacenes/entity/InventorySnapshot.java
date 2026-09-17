package com.pucp.paqrap.modulos.almacenes.entity;
import com.pucp.paqrap.modulos.almacenes.entity.*;
import com.pucp.paqrap.modulos.flota.entity.*;
import com.pucp.paqrap.modulos.flota.service.*;
import com.pucp.paqrap.modulos.incidencias.entity.*;
import com.pucp.paqrap.modulos.incidencias.service.*;
import com.pucp.paqrap.modulos.pedidos.entity.*;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.*;
import com.pucp.paqrap.modulos.planificacion.entity.*;
import com.pucp.paqrap.modulos.redvial.entity.*;
import com.pucp.paqrap.modulos.redvial.service.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable stock allocation state for one planning iteration. */
public final class InventorySnapshot {
    private final Map<String, Warehouse> warehousesById;
    private final Map<String, Integer> stockByWarehouseId;

    private InventorySnapshot(Map<String, Warehouse> warehousesById, Map<String, Integer> stockByWarehouseId) {
        this.warehousesById = Map.copyOf(warehousesById);
        this.stockByWarehouseId = Map.copyOf(stockByWarehouseId);
    }

    public static InventorySnapshot from(Collection<Warehouse> warehouses) {
        Objects.requireNonNull(warehouses, "warehouses are required");
        Map<String, Warehouse> byId = new LinkedHashMap<>();
        Map<String, Integer> stock = new LinkedHashMap<>();
        for (Warehouse warehouse : warehouses) {
            if (byId.put(warehouse.id(), warehouse) != null) {
                throw new IllegalArgumentException("Duplicate warehouse id: " + warehouse.id());
            }
            if (!warehouse.isCentral()) {
                stock.put(warehouse.id(), warehouse.initialStock());
            }
        }
        return new InventorySnapshot(byId, stock);
    }

    public Warehouse warehouse(String warehouseId) {
        Warehouse warehouse = warehousesById.get(warehouseId);
        if (warehouse == null) {
            throw new IllegalArgumentException("Unknown warehouse: " + warehouseId);
        }
        return warehouse;
    }

    public Collection<Warehouse> warehouses() {
        return warehousesById.values();
    }

    public int availableStock(String warehouseId) {
        Warehouse warehouse = warehouse(warehouseId);
        return warehouse.isCentral() ? Integer.MAX_VALUE : stockByWarehouseId.get(warehouseId);
    }

    public boolean hasStockFor(String warehouseId, int quantity) {
        return quantity > 0 && availableStock(warehouseId) >= quantity;
    }

    public InventorySnapshot withdraw(String warehouseId, int quantity) {
        if (!hasStockFor(warehouseId, quantity)) {
            throw new IllegalArgumentException("Insufficient stock at " + warehouseId);
        }
        Warehouse warehouse = warehouse(warehouseId);
        if (warehouse.isCentral()) {
            return this;
        }
        Map<String, Integer> updated = new LinkedHashMap<>(stockByWarehouseId);
        updated.put(warehouseId, updated.get(warehouseId) - quantity);
        return new InventorySnapshot(warehousesById, updated);
    }

    /** Applies the daily 23:59:59 replenishment event to every intermediate warehouse. */
    public InventorySnapshot reloadIntermediateWarehouses() {
        Map<String, Integer> replenished = new LinkedHashMap<>(stockByWarehouseId);
        warehousesById.values().stream()
                .filter(warehouse -> !warehouse.isCentral())
                .forEach(warehouse -> replenished.put(warehouse.id(), warehouse.capacity()));
        return new InventorySnapshot(warehousesById, replenished);
    }
}
