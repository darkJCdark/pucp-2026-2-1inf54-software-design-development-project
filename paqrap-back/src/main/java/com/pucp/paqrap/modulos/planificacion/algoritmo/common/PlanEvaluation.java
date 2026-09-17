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

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Evaluation result; only a result with no violations can enter the SA search space. */
public final class PlanEvaluation {
    private final Map<String, ScheduledDeliveryRoute> schedulesByRouteId;
    private final List<PlanViolation> violations;
    private final InventorySnapshot remainingInventory;
    private final double totalCost;

    public PlanEvaluation(Map<String, ScheduledDeliveryRoute> schedulesByRouteId, List<PlanViolation> violations,
                          InventorySnapshot remainingInventory, double totalCost) {
        this.schedulesByRouteId = Map.copyOf(Objects.requireNonNull(schedulesByRouteId, "schedules are required"));
        this.violations = List.copyOf(Objects.requireNonNull(violations, "violations are required"));
        this.remainingInventory = Objects.requireNonNull(remainingInventory, "remainingInventory is required");
        this.totalCost = totalCost;
    }

    public boolean isFeasible() { return violations.isEmpty(); }
    public Map<String, ScheduledDeliveryRoute> schedulesByRouteId() { return schedulesByRouteId; }
    public List<PlanViolation> violations() { return violations; }
    public InventorySnapshot remainingInventory() { return remainingInventory; }
    public double totalCost() { return totalCost; }
}
