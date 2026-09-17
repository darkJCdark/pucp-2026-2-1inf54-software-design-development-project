package com.pucp.paqrap.modulos.redvial.entity;
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

/** A node in PaqRap's 70 km by 50 km orthogonal street grid. */
public record Location(int x, int y) {
    public static final int MAX_X = 70;
    public static final int MAX_Y = 50;

    public Location {
        if (x < 0 || x > MAX_X || y < 0 || y > MAX_Y) {
            throw new IllegalArgumentException("Location must be inside the 0..70 by 0..50 city grid");
        }
    }

    public double manhattanDistanceTo(Location other) {
        Objects.requireNonNull(other, "other location is required");
        return Math.abs(x - other.x) + Math.abs(y - other.y);
    }
}
