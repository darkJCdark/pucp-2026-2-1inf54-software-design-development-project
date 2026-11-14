package com.pucp.paqrap.modulos.planificacion.entity;

/**
 * Sentido de una transferencia física de paquetes entre dos vehículos.
 *
 * <p>DONATE descuenta la carga del vehículo donante y RECEIVE la incorpora
 * en el receptor. Ambas paradas deben compartir el mismo transferId.</p>
 */
public enum TransferDirection {
    DONATE,
    RECEIVE
}
