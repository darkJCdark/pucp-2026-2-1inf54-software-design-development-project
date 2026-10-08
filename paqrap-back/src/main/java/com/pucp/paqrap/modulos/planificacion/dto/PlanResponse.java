package com.pucp.paqrap.modulos.planificacion.dto;

import java.time.Instant;
import java.util.List;

/**
 * Salida del planificador, lista para serializarse como JSON.
 *
 * @param factible        true si el plan no tiene violaciones
 * @param colapso         true si quedó al menos un pedido sin atender
 * @param costoTotal      costo operativo total del plan
 * @param distanciaTotalKm suma de la distancia de todas las rutas
 * @param semilla         semilla usada (permite repetir la ejecución)
 * @param duracionMs      tiempo de cómputo del planificador
 */
public record PlanResponse(
        ModoOperacion modo,
        Instant planificadoEn,
        boolean factible,
        boolean colapso,
        double costoTotal,
        double distanciaTotalKm,
        long semilla,
        long duracionMs,
        List<RutaResponse> rutas,
        List<ViolacionResponse> violaciones,
        List<NoAtendidoResponse> noAtendidos
) {
}
