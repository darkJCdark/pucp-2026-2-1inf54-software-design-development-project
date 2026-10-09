package com.pucp.paqrap.modulos.planificacion.dto;

import java.time.Instant;
import java.util.List;

/**
 * Plan vigente de una ejecución tras un evento, con lo que la GUI necesita para mostrar el cambio.
 * Los campos son una propuesta a validar con el frontend.
 *
 * @param numeroReplanificacion 0 para el plan inicial; aumenta en 1 con cada evento
 * @param plan                  plan completo vigente: rutas conservadas más rutas nuevas
 * @param rutasConservadas      rutas en marcha que se mantuvieron (ids de ruta)
 * @param rutasNuevas           rutas generadas en esta replanificación (ids de ruta)
 * @param pedidosReasignados    pedidos que ya estaban en el plan anterior y ahora van en una ruta nueva
 * @param vehiculosNoDisponibles vehículos que no pueden recibir rutas en este instante (avería o mantenimiento)
 */
public record ReplanificacionResponse(
        long ejecucionId,
        int numeroReplanificacion,
        MotivoReplanificacion motivo,
        Instant hora,
        PlanResponse plan,
        List<String> rutasConservadas,
        List<String> rutasNuevas,
        List<String> pedidosReasignados,
        List<String> vehiculosNoDisponibles
) {
}
