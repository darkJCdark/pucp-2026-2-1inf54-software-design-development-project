package com.pucp.paqrap.modulos.pedidos.entity;
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

import java.time.Instant;
import java.util.Objects;

public record Order(String id, Location destination, int packages, Instant registeredAt, Instant deadline) {
    public Order {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(destination, "destination is required");
        Objects.requireNonNull(registeredAt, "registeredAt is required");
        Objects.requireNonNull(deadline, "deadline is required");
        if (id.isBlank() || packages <= 0 || !deadline.isAfter(registeredAt)) {
            throw new IllegalArgumentException("An order needs a non-empty id, positive packages and a future deadline");
        }
    }
}
