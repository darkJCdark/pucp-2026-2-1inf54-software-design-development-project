package com.pucp.paqrap.modulos.planificacion.algoritmo.common;
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

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/** Applies planned warehouse events chronologically and reloads intermediates at 23:59:59. */
final class InventoryTimeline {
    private final ZoneId zoneId;
    private InventorySnapshot inventory;
    private Instant appliedThrough;

    InventoryTimeline(InventorySnapshot inventory, Instant startAt, ZoneId zoneId) {
        this.inventory = Objects.requireNonNull(inventory, "inventory is required");
        this.appliedThrough = Objects.requireNonNull(startAt, "startAt is required");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId is required");
    }

    InventorySnapshot inventory() { return inventory; }

    boolean canVisit(String warehouseId, int pickupPackages, Instant at) {
        advanceTo(at);
        return pickupPackages == 0
                ? inventory.warehouse(warehouseId).isCentral() || inventory.availableStock(warehouseId) > 0
                : inventory.hasStockFor(warehouseId, pickupPackages);
    }

    void withdraw(String warehouseId, int pickupPackages, Instant at) {
        advanceTo(at);
        if (pickupPackages > 0) {
            inventory = inventory.withdraw(warehouseId, pickupPackages);
        }
    }

    private void advanceTo(Instant target) {
        if (target.isBefore(appliedThrough)) {
            throw new IllegalArgumentException("Inventory events must be processed chronologically");
        }
        Instant reload = nextReloadAfter(appliedThrough);
        while (!reload.isAfter(target)) {
            inventory = inventory.reloadIntermediateWarehouses();
            appliedThrough = reload;
            reload = nextReloadAfter(appliedThrough);
        }
        appliedThrough = target;
    }

    private Instant nextReloadAfter(Instant instant) {
        ZonedDateTime local = instant.atZone(zoneId);
        LocalDate candidateDate = local.toLocalDate();
        Instant candidate = ZonedDateTime.of(candidateDate, LocalTime.of(23, 59, 59), zoneId).toInstant();
        if (!candidate.isAfter(instant)) {
            candidate = ZonedDateTime.of(candidateDate.plusDays(1), LocalTime.of(23, 59, 59), zoneId).toInstant();
        }
        return candidate;
    }
}
