package com.pucp.paqrap.modulos.planificacion.dto;

/**
 * Evento que originó un plan de una ejecución. Si en un mismo ciclo ocurren varios, se informa el de mayor
 * impacto: AVERIA, luego BLOQUEO y luego NUEVO_PEDIDO; PERIODICO es el ciclo sin eventos.
 */
public enum MotivoReplanificacion {
    INICIO,
    NUEVO_PEDIDO,
    BLOQUEO,
    AVERIA,
    PERIODICO
}
