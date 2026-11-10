package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Duration;
import java.time.Instant;

import com.pucp.paqrap.modulos.planificacion.dto.ParadaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.RutaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.TramoRutaResponse;
import com.pucp.paqrap.modulos.redvial.entity.Location;

/**
 * Estima la posición utilizando exactamente
 * las mismas calles y tiempos enviados al mapa.
 *
 * No reconstruye caminos artificiales en L.
 */
public final class PosicionRuta {

    private PosicionRuta() {
    }

    public static Location estimar(
            RutaResponse ruta,
            int xActual,
            int yActual,
            Instant instante
    ) {

        Location confirmada =
                new Location(
                        xActual,
                        yActual
                );

        if (
                ruta.salida() == null ||
                instante.isBefore(
                        ruta.salida()
                )
        ) {

            return confirmada;
        }

        /*
         * Mientras está atendiendo una parada
         * o tomando el refrigerio posterior,
         * permanece físicamente en esa parada.
         *
         * llegada = llegada física.
         * fin = fin servicio + posible refrigerio.
         */
        for (
                ParadaResponse parada :
                ruta.paradas()
        ) {

            Instant llegada =
                    parada.llegada();

            if (
                    llegada != null &&
                    parada.fin() != null &&
                    !instante.isBefore(
                            llegada
                    ) &&
                    instante.isBefore(
                            parada.fin()
                    )
            ) {

                return new Location(
                        parada.x(),
                        parada.y()
                );
            }
        }

        /*
         * Movimiento por los RoadLeg reales.
         */
        for (
                TramoRutaResponse tramo :
                ruta.tramos()
        ) {

            if (
                    !instante.isBefore(
                            tramo.llegada()
                    )
            ) {
                continue;
            }

            /*
             * Si el siguiente RoadLeg todavía
             * no comenzó, el vehículo espera
             * en el nodo real.
             */
            if (
                    !instante.isAfter(
                            tramo.salida()
                    )
            ) {

                return new Location(
                        tramo.desdeX(),
                        tramo.desdeY()
                );
            }

            Duration duracion =
                    Duration.between(
                            tramo.salida(),
                            tramo.llegada()
                    );

            if (
                    duracion.isZero()
            ) {

                return new Location(
                        tramo.hastaX(),
                        tramo.hastaY()
                );
            }

            double progreso =
                    (double)
                            Duration.between(
                                    tramo.salida(),
                                    instante
                            )
                            .toNanos()
                            /
                            duracion.toNanos();

            progreso =
                    Math.min(
                            1,
                            Math.max(
                                    0,
                                    progreso
                            )
                    );

            return new Location(

                    (int)
                            Math.round(
                                    tramo.desdeX() +
                                            (
                                                    tramo.hastaX() -
                                                            tramo.desdeX()
                                            ) *
                                                    progreso
                            ),

                    (int)
                            Math.round(
                                    tramo.desdeY() +
                                            (
                                                    tramo.hastaY() -
                                                            tramo.desdeY()
                                            ) *
                                                    progreso
                            )
            );
        }

        /*
         * Si todos los tramos terminaron,
         * permanece en el último nodo.
         */
        if (
                !ruta.tramos().isEmpty()
        ) {

            TramoRutaResponse ultimo =
                    ruta
                            .tramos()
                            .getLast();

            return new Location(
                    ultimo.hastaX(),
                    ultimo.hastaY()
            );
        }

        /*
         * Una ruta antigua sin geometría
         * no autoriza inventar movimiento.
         */
        return confirmada;
    }
}