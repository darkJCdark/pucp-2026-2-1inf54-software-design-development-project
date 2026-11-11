package com.pucp.paqrap.modulos.planificacion.service;

/**
 * Grado de mutabilidad de una parada dentro
 * del plan vigente.
 */
public enum EstadoCompromisoParada {

    /**
     * La parada terminó completamente.
     *
     * No debe volver a planificarse.
     */
    EJECUTADA,

    /**
     * El vehículo ya empezó a desplazarse hacia
     * esta parada o se encuentra realizándola.
     *
     * Normalmente debe respetarse.
     */
    COMPROMETIDA,

    /**
     * Todavía no comenzó el desplazamiento hacia
     * esta parada.
     *
     * ALNS puede moverla, reasignarla o reordenarla.
     */
    PLANIFICADA
}