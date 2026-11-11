package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadLeg;

/**
 * Foto operacional de una ruta en un instante.
 *
 * IMPORTANTE:
 *
 * Si el vehículo está en mitad de una calle no
 * inventamos una posición válida para replanificar.
 *
 * Conservamos:
 *
 * - último nodo seguro alcanzado;
 * - próximo nodo seguro;
 * - instante desde el que puede desviarse.
 *
 * Así el algoritmo nunca teletransporta un vehículo.
 */
public record PosicionRutaEnInstante(

        EstadoRutaEnInstante estado,

        /**
         * Último nodo de la red cuya llegada ya está
         * físicamente confirmada.
         */
        Location ultimoNodoSeguro,

        /**
         * Primer nodo desde el que puede construirse
         * una ruta nueva sin interrumpir una calle.
         *
         * Si está detenido en un nodo coincide con
         * ultimoNodoSeguro.
         */
        Location proximoNodoSeguro,

        /**
         * Momento más temprano en que el vehículo
         * puede abandonar su compromiso actual.
         */
        Instant desviableDesde,

        /**
         * Índice, base cero, de la parada actualmente
         * comprometida.
         *
         * -1 significa que no hay ninguna.
         */
        int indiceParadaComprometida,

        /**
         * Primera parada completamente libre para
         * reoptimización.
         *
         * Puede ser igual a estadosParadas.size()
         * cuando no queda ninguna.
         */
        int primeraParadaPlanificada,

        List<EstadoCompromisoParada> estadosParadas,

        /**
         * RoadLeg actualmente recorrido.
         *
         * null cuando no está en mitad de una calle.
         */
        RoadLeg tramoEnCurso
) {

    public PosicionRutaEnInstante {

        Objects.requireNonNull(
                estado,
                "estado es requerido"
        );

        Objects.requireNonNull(
                ultimoNodoSeguro,
                "ultimoNodoSeguro es requerido"
        );

        Objects.requireNonNull(
                proximoNodoSeguro,
                "proximoNodoSeguro es requerido"
        );

        Objects.requireNonNull(
                desviableDesde,
                "desviableDesde es requerido"
        );

        estadosParadas =
                List.copyOf(
                        Objects.requireNonNull(
                                estadosParadas,
                                "estadosParadas son requeridos"
                        )
                );

        if (
                indiceParadaComprometida < -1
                ||
                indiceParadaComprometida >=
                        estadosParadas.size()
        ) {

            throw new IllegalArgumentException(
                    "indiceParadaComprometida fuera de rango"
            );
        }

        if (
                primeraParadaPlanificada < 0
                ||
                primeraParadaPlanificada >
                        estadosParadas.size()
        ) {

            throw new IllegalArgumentException(
                    "primeraParadaPlanificada fuera de rango"
            );
        }
    }

    public Optional<RoadLeg> tramoActual() {

        return Optional.ofNullable(
                tramoEnCurso
        );
    }

    /**
     * Indica si físicamente ya puede iniciarse
     * una ruta diferente.
     */
    public boolean puedeDesviarseAhora(
            Instant ahora
    ) {

        Objects.requireNonNull(
                ahora,
                "ahora es requerido"
        );

        return !ahora.isBefore(
                desviableDesde
        );
    }

    public boolean estaEnMovimiento() {

        return estado ==
                EstadoRutaEnInstante.EN_TRANSITO;
    }
}