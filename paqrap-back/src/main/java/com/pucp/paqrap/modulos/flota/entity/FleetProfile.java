package com.pucp.paqrap.modulos.flota.entity;
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

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable parameter set used by one planning iteration.
 * A hot speed change creates a new profile for the next iteration.
 */
public final class FleetProfile {
    private final Map<VehicleType, VehicleParameters> parametersByType;

    public FleetProfile(Map<VehicleType, VehicleParameters> parametersByType) {
        Objects.requireNonNull(parametersByType, "parametersByType is required");
        EnumMap<VehicleType, VehicleParameters> copy = new EnumMap<>(VehicleType.class);
        for (VehicleType type : VehicleType.values()) {
            VehicleParameters parameters = parametersByType.get(type);
            if (parameters == null) {
                throw new IllegalArgumentException("Missing parameters for " + type);
            }
            copy.put(type, parameters);
        }
        this.parametersByType = Map.copyOf(copy);
    }

    public static FleetProfile defaults() {
        EnumMap<VehicleType, VehicleParameters> defaults = new EnumMap<>(VehicleType.class);
        for (VehicleType type : VehicleType.values()) {
            defaults.put(type, type.defaultParameters());
        }
        return new FleetProfile(defaults);
    }

    public VehicleParameters parametersFor(VehicleType type) {
        return parametersByType.get(Objects.requireNonNull(type, "type is required"));
    }

    public FleetProfile withSpeed(VehicleType type, double speedKmPerHour) {
        VehicleParameters current = parametersFor(type);
        EnumMap<VehicleType, VehicleParameters> updated = new EnumMap<>(parametersByType);
        updated.put(type, new VehicleParameters(current.capacity(), speedKmPerHour, current.costPerKm()));
        return new FleetProfile(updated);
    }
}
