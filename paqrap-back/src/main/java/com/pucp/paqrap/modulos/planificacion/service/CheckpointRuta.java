package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadLeg;

/**
 * Estado exacto desde el cual puede reconstruirse
 * únicamente el futuro de una ruta.
 */
record CheckpointRuta(

        EstadoRutaEnInstante estado,

        /**
         * Nodo vial desde el que comenzará la nueva
         * planificación.
         */
        Location nodoSeguro,

        /**
         * Primer instante físicamente posible para
         * abandonar el compromiso actual.
         */
        Instant disponibleDesde,

        /**
         * Carga que lleva el vehículo en ese nodo.
         */
        int carga,

        /**
         * Turno donde ya tomó refrigerio, si existe.
         */
        Optional<Instant> turnoConRefrigerioTomado,

        /**
         * Índice original de la primera parada que
         * todavía puede reordenarse.
         */
        int primeraParadaFutura,

        /**
         * Paradas cuya ejecución se mantiene
         * exactamente como estaba.
         */
        List<ScheduledRouteStop> prefijoFijo,

        /**
         * Calles ya recorridas desde la última parada
         * fija hasta el checkpoint.
         *
         * Son necesarias para no cambiar visualmente
         * ni contablemente el recorrido pasado.
         */
        List<RoadLeg> tramosConsumidosDesdePrefijo
) {

    CheckpointRuta {

        Objects.requireNonNull(
                estado,
                "estado es requerido"
        );

        Objects.requireNonNull(
                nodoSeguro,
                "nodoSeguro es requerido"
        );

        Objects.requireNonNull(
                disponibleDesde,
                "disponibleDesde es requerido"
        );

        Objects.requireNonNull(
                turnoConRefrigerioTomado,
                "turnoConRefrigerioTomado es requerido"
        );

        prefijoFijo =
                List.copyOf(
                        Objects.requireNonNull(
                                prefijoFijo,
                                "prefijoFijo es requerido"
                        )
                );

        tramosConsumidosDesdePrefijo =
                List.copyOf(
                        Objects.requireNonNull(
                                tramosConsumidosDesdePrefijo,
                                "tramosConsumidosDesdePrefijo es requerido"
                        )
                );

        if (
                carga < 0
        ) {
            throw new IllegalArgumentException(
                    "carga no puede ser negativa"
            );
        }

        if (
                primeraParadaFutura < 0
        ) {
            throw new IllegalArgumentException(
                    "primeraParadaFutura no puede ser negativa"
            );
        }
    }
}