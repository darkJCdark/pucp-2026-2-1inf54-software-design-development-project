package com.pucp.paqrap.modulos.planificacion.service;

/**
 * Estado físico-operacional de una ruta
 * en un instante concreto.
 */
public enum EstadoRutaEnInstante {

    /**
     * La ruta todavía no empezó.
     */
    ANTES_DE_SALIDA,

    /**
     * El vehículo está detenido en un nodo vial
     * esperando poder continuar.
     *
     * Por ejemplo, puede existir espera por un bloqueo.
     */
    ESPERA_VIAL,

    /**
     * El vehículo está recorriendo actualmente
     * un RoadLeg.
     */
    EN_TRANSITO,

    /**
     * El vehículo llegó a una entrega y está
     * realizando el servicio.
     */
    EN_SERVICIO,

    /**
     * El vehículo está realizando el refrigerio.
     */
    EN_REFRIGERIO,

    /**
     * La ruta terminó completamente.
     */
    TERMINADA
}