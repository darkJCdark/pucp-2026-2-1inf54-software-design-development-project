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

public enum VehicleType {
    // Velocidades oficiales de la hoja Aux-Flota del Q&A publicado (Autos 20, Motos 40, Bicicletas 14 km/h).
    CAR("TA", new VehicleParameters(24, 20.0, 8.0)),
    MOTORCYCLE("TM", new VehicleParameters(8, 40.0, 6.0)),
    BICYCLE("TB", new VehicleParameters(4, 14.0, 3.0));

    private final String fleetCode;
    private final VehicleParameters defaultParameters;

    VehicleType(String fleetCode, VehicleParameters defaultParameters) {
        this.fleetCode = fleetCode;
        this.defaultParameters = defaultParameters;
    }

    public String fleetCode() { return fleetCode; }
    public VehicleParameters defaultParameters() { return defaultParameters; }

    // Compatibility accessors. Route evaluation will consume FleetProfile in phase 2.
    public int capacity() { return defaultParameters.capacity(); }
    public double speedKmPerHour() { return defaultParameters.speedKmPerHour(); }
    public double costPerKm() { return defaultParameters.costPerKm(); }
}
