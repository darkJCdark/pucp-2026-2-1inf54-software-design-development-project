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

/** Runtime parameters for one vehicle type, frozen inside a planning iteration. */
public record VehicleParameters(int capacity, double speedKmPerHour, double costPerKm) {
    public VehicleParameters {
        if (capacity <= 0 || speedKmPerHour <= 0 || costPerKm < 0) {
            throw new IllegalArgumentException("Capacity and speed must be positive; cost cannot be negative");
        }
    }
}
