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

import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.pedidos.entity.Order;

import java.time.Duration;
import java.util.Objects;

/** One complete or partial delivery. Each stop takes one hour, independently of quantity. */
public record DeliveryStop(Order order, int deliveredPackages) implements RouteStop {
    public static final Duration SERVICE_TIME = Duration.ofHours(1);

    public DeliveryStop {
        Objects.requireNonNull(order, "order is required");
        if (deliveredPackages <= 0 || deliveredPackages > order.packages()) {
            throw new IllegalArgumentException("A delivery quantity must be within the order quantity");
        }
    }

    @Override
    public Location location() {
        return order.destination();
    }
}
