package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.dto.ReplanificacionResponse;
import com.pucp.paqrap.modulos.planificacion.dto.SolicitudPlanificacion;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Replanificación de una ejecución ante eventos. Quien controla el reloj invoca los disparadores con la
 * hora actual; las rutas en marcha se conservan completas y solo se replanifica lo pendiente con los
 * vehículos libres.
 */
public interface ReplanificacionService {

    /** Métricas acumuladas de los ciclos SA ya ejecutados para una ejecución viva. */
    record MetricasPlanificacion(long totalComputationMs, int replanningCount) {
    }

    /** Crea el plan inicial de la ejecución. */
    ReplanificacionResponse iniciar(long ejecucionId, SolicitudPlanificacion solicitud);

    /**
     * Aplica en una sola replanificación los eventos ocurridos desde la anterior. Sin eventos es el ciclo
     * periódico: replanifica lo pendiente conservando las rutas en marcha.
     */
    ReplanificacionResponse replanificar(long ejecucionId, Eventos eventos, Instant ahora);

    default ReplanificacionResponse registrarPedido(long ejecucionId, Order pedido, Instant ahora) {
        return replanificar(ejecucionId, new Eventos(List.of(pedido), List.of(), List.of()), ahora);
    }

    /** El bloqueo rige desde {@code max(inicio del bloqueo, ahora)}; uno ya vencido no altera las rutas. */
    default ReplanificacionResponse activarBloqueo(long ejecucionId, RoadBlock bloqueo, Instant ahora) {
        return replanificar(ejecucionId, new Eventos(List.of(), List.of(bloqueo), List.of()), ahora);
    }

    /** Interrumpe la ruta del vehículo en el instante de la avería; sus paquetes no entregados vuelven a planificarse. */
    default ReplanificacionResponse registrarAveria(long ejecucionId, BreakdownEvent averia, Instant ahora) {
        return replanificar(ejecucionId, new Eventos(List.of(), List.of(), List.of(averia)), ahora);
    }

    /** True si la ejecución está iniciada y el pedido ya forma parte de ella. */
    boolean conocePedido(long ejecucionId, String pedidoId);

    boolean estaIniciada(long ejecucionId);

    Optional<ReplanificacionResponse> planVigente(long ejecucionId);

    Optional<MetricasPlanificacion> metricas(long ejecucionId);

    void finalizar(long ejecucionId);

    /** Eventos que se aplican juntos en una replanificación; cualquiera de las listas puede venir vacía. */
    record Eventos(List<Order> pedidos, List<RoadBlock> bloqueos, List<RoadBlock> bloqueosTerminados,
                   List<BreakdownEvent> averias, List<VehicleType> tiposFlotaActualizados) {
        public static final Eventos NINGUNO = new Eventos(List.of(), List.of(), List.of(), List.of(), List.of());

        public Eventos {
            pedidos = pedidos == null ? List.of() : List.copyOf(pedidos);
            bloqueos = bloqueos == null ? List.of() : List.copyOf(bloqueos);
            bloqueosTerminados = bloqueosTerminados == null ? List.of() : List.copyOf(bloqueosTerminados);
            averias = averias == null ? List.of() : List.copyOf(averias);
            tiposFlotaActualizados = tiposFlotaActualizados == null ? List.of() : List.copyOf(tiposFlotaActualizados);
        }

        public Eventos(List<Order> pedidos, List<RoadBlock> bloqueos, List<RoadBlock> bloqueosTerminados,
                       List<BreakdownEvent> averias) {
            this(pedidos, bloqueos, bloqueosTerminados, averias, List.of());
        }

        public Eventos(List<Order> pedidos, List<RoadBlock> bloqueos, List<BreakdownEvent> averias) {
            this(pedidos, bloqueos, List.of(), averias, List.of());
        }

        public boolean vacios() {
            return pedidos.isEmpty() && bloqueos.isEmpty() && bloqueosTerminados.isEmpty() && averias.isEmpty();
        }
    }
}
