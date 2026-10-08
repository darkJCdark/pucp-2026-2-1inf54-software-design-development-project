package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.dto.ReplanificacionResponse;
import com.pucp.paqrap.modulos.planificacion.dto.SolicitudPlanificacion;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

import java.time.Instant;
import java.util.Optional;

/**
 * Replanificación de una ejecución ante eventos. Quien controla el reloj invoca los disparadores con la
 * hora actual; las rutas en marcha se conservan completas y solo se replanifica lo pendiente con los
 * vehículos libres.
 */
public interface ReplanificacionService {

    /** Crea el plan inicial de la ejecución. */
    ReplanificacionResponse iniciar(long ejecucionId, SolicitudPlanificacion solicitud);

    ReplanificacionResponse registrarPedido(long ejecucionId, Order pedido, Instant ahora);

    /** El bloqueo rige desde {@code max(inicio del bloqueo, ahora)}; uno ya vencido no altera las rutas. */
    ReplanificacionResponse activarBloqueo(long ejecucionId, RoadBlock bloqueo, Instant ahora);

    /** Interrumpe la ruta del vehículo; sus paquetes no entregados vuelven a planificarse. */
    ReplanificacionResponse registrarAveria(long ejecucionId, BreakdownEvent averia, Instant ahora);

    Optional<ReplanificacionResponse> planVigente(long ejecucionId);

    void finalizar(long ejecucionId);
}
