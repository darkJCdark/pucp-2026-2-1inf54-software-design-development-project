package com.pucp.paqrap.modulos.planificacion.dto;

import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Entrada del planificador. Los pedidos y bloqueos los arma quien invoca (servicios de
 * pedidos y red vial); el planificador solo aporta flota, almacenes, mantenimientos y averías.
 *
 * @param modo               modo de operación (se devuelve sin interpretar)
 * @param horaPlanificacion  instante de planificación (reloj real o de la simulación)
 * @param pedidosPendientes  pedidos que deben planificarse
 * @param bloqueosActivos    bloqueos viales a considerar
 * @param semilla            semilla del SA; si es null el servicio genera una y la devuelve
 */
public record SolicitudPlanificacion(
        ModoOperacion modo,
        Instant horaPlanificacion,
        List<Order> pedidosPendientes,
        List<RoadBlock> bloqueosActivos,
        Long semilla
) {
    public SolicitudPlanificacion {
        Objects.requireNonNull(modo, "modo es requerido");
        Objects.requireNonNull(horaPlanificacion, "horaPlanificacion es requerida");
        pedidosPendientes = pedidosPendientes == null ? List.of() : List.copyOf(pedidosPendientes);
        bloqueosActivos = bloqueosActivos == null ? List.of() : List.copyOf(bloqueosActivos);
    }
}
