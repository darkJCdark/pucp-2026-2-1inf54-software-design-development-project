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

/** One undirected, one-kilometre street segment in the city grid. */
public record StreetSegment(Location first, Location second) {
    public StreetSegment {
        Objects.requireNonNull(first, "first location is required");
        Objects.requireNonNull(second, "second location is required");
        if (first.manhattanDistanceTo(second) != 1) {
            throw new IllegalArgumentException("A street segment must join orthogonally adjacent nodes");
        }
        if (compare(first, second) > 0) {
            Location originalFirst = first;
            first = second;
            second = originalFirst;
        }
    }

    private static int compare(Location left, Location right) {
        int xComparison = Integer.compare(left.x(), right.x());
        return xComparison != 0 ? xComparison : Integer.compare(left.y(), right.y());
    }
}
