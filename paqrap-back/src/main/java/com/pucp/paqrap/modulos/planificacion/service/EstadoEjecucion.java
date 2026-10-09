package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.dto.ModoOperacion;
import com.pucp.paqrap.modulos.planificacion.dto.ReplanificacionResponse;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estado en memoria de una ejecución (simulación o día a día). Solo lo modifica
 * {@link ReplanificacionSaService} al confirmar una replanificación exitosa.
 */
final class EstadoEjecucion {
    final long ejecucionId;
    final ModoOperacion modo;
    final long semillaBase;
    final long presupuestoMs;

    Instant ultimaHora;
    int numeroReplanificacion = -1;
    Map<String, Order> pedidos = new LinkedHashMap<>();
    /** Paquetes ya entregados por rutas que salieron del plan (terminadas o interrumpidas). */
    Map<String, Integer> entregados = new HashMap<>();
    /** Paquetes ya retirados de almacenes intermedios por rutas que salieron del plan. */
    Map<String, Integer> retiros = new HashMap<>();
    List<RoadBlock> bloqueos = new ArrayList<>();
    List<BreakdownEvent> averias = new ArrayList<>();
    /** Estado de vehículos cuya ruta se interrumpió por avería. */
    Map<String, VehicleOperationalState> estadosForzados = new HashMap<>();
    List<RutaVigente> rutas = List.of();
    ReplanificacionResponse ultimaRespuesta;

    EstadoEjecucion(long ejecucionId, ModoOperacion modo, long semillaBase, long presupuestoMs, Instant hora) {
        this.ejecucionId = ejecucionId;
        this.modo = modo;
        this.semillaBase = semillaBase;
        this.presupuestoMs = presupuestoMs;
        this.ultimaHora = hora;
    }
}
