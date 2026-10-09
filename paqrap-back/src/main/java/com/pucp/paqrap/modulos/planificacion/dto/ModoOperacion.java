package com.pucp.paqrap.modulos.planificacion.dto;

/**
 * Modo de operación en el que se solicita un plan. El planificador no cambia su
 * algoritmo según el modo: quien lo invoca decide qué pedidos y qué hora entrega,
 * y el modo se devuelve en la respuesta para que el consumidor sepa a qué
 * ejecución corresponde el plan.
 */
public enum ModoOperacion {
    DIA_A_DIA,
    SIMULACION_5D,
    COLAPSO
}
