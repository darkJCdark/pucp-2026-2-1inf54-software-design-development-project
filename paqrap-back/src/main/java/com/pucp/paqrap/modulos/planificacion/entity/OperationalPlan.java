package com.pucp.paqrap.modulos.planificacion.entity;
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
import java.util.Optional;

/** Immutable assignment of at most one active operational route to every vehicle. */
public final class OperationalPlan {
    private final Map<String, DeliveryRoute> routesByVehicleId;

    private OperationalPlan(Map<String, DeliveryRoute> routesByVehicleId) {
        this.routesByVehicleId = Map.copyOf(routesByVehicleId);
    }

    public static OperationalPlan empty() {
        return new OperationalPlan(Map.of());
    }

    public Collection<DeliveryRoute> routes() {
        return routesByVehicleId.values();
    }

    public Optional<DeliveryRoute> routeForVehicle(String vehicleId) {
        return Optional.ofNullable(routesByVehicleId.get(vehicleId));
    }

    public OperationalPlan withRoute(DeliveryRoute route) {
        Objects.requireNonNull(route, "route is required");
        Map<String, DeliveryRoute> updated = new LinkedHashMap<>(routesByVehicleId);
        boolean routeIdBelongsToAnotherVehicle = updated.values().stream()
                .anyMatch(existing -> existing.id().equals(route.id())
                        && !existing.vehicle().id().equals(route.vehicle().id()));
        if (routeIdBelongsToAnotherVehicle) {
            throw new IllegalArgumentException("Route id is already assigned to another vehicle: " + route.id());
        }
        updated.put(route.vehicle().id(), route);
        return new OperationalPlan(updated);
    }

    public OperationalPlan withoutRoute(String vehicleId) {
        Map<String, DeliveryRoute> updated = new LinkedHashMap<>(routesByVehicleId);
        updated.remove(vehicleId);
        return new OperationalPlan(updated);
    }
}
