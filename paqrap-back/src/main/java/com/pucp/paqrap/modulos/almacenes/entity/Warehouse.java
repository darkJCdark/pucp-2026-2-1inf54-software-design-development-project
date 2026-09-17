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

import java.util.Objects;

/** Immutable warehouse definition. Per-iteration stock belongs to the operational state, not this entity. */
public final class Warehouse {
    private final String id;
    private final Location location;
    private final boolean central;
    private final int initialStock;

    private Warehouse(String id, Location location, boolean central, int availableStock) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.location = Objects.requireNonNull(location, "location is required");
        this.central = central;
        this.initialStock = availableStock;
    }

    public static Warehouse central(String id, Location location) {
        return new Warehouse(id, location, true, Integer.MAX_VALUE);
    }

    public static Warehouse intermediate(String id, Location location, int availableStock) {
        if (availableStock < 0 || availableStock > 1_000) {
            throw new IllegalArgumentException("Intermediate stock must be between 0 and 1,000");
        }
        return new Warehouse(id, location, false, availableStock);
    }

    public String id() { return id; }
    public Location location() { return location; }
    public boolean isCentral() { return central; }
    public int capacity() { return central ? Integer.MAX_VALUE : 1_000; }
    public int initialStock() { return initialStock; }

    /** Compatibility API until InventorySnapshot replaces direct stock checks in phase 2. */
    public int availableStock() { return initialStock; }
    public boolean hasStockFor(int packages) { return packages > 0 && (central || initialStock >= packages); }
}
