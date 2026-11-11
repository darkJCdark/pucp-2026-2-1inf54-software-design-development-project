package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadLeg;

/**
 * Localiza físicamente una ruta temporizada y
 * determina qué parte del futuro puede modificar
 * el optimizador.
 *
 * Reglas:
 *
 * 1. completedAt <= ahora
 *      -> EJECUTADA.
 *
 * 2. El viaje hacia la parada ya empezó
 *      -> COMPROMETIDA.
 *
 * 3. Todavía no comenzó el viaje hacia ella
 *      -> PLANIFICADA.
 *
 * 4. En mitad de una calle:
 *      el desvío se permite desde el siguiente nodo.
 *
 * 5. Durante servicio:
 *      debe terminar el servicio.
 *
 * 6. Durante refrigerio:
 *      debe terminar el refrigerio.
 */
public final class LocalizadorRutaService {

    public PosicionRutaEnInstante localizar(
            ScheduledDeliveryRoute programada,
            Instant ahora
    ) {

        Objects.requireNonNull(
                programada,
                "programada es requerida"
        );

        Objects.requireNonNull(
                ahora,
                "ahora es requerido"
        );

        DeliveryRoute ruta =
                programada.route();

        List<ScheduledRouteStop> paradas =
                programada.scheduledStops();

        List<EstadoCompromisoParada> estados =
                clasificarParadas(
                        programada,
                        ahora
                );

        int comprometida =
                indiceComprometido(
                        estados
                );

        int primeraPlanificada =
                primeraPlanificada(
                        estados
                );

        /*
         * =================================================
         * ANTES DE SALIR
         * =================================================
         */

        if (
                ahora.isBefore(
                        ruta.departureAt()
                )
        ) {

            return new PosicionRutaEnInstante(
                    EstadoRutaEnInstante
                            .ANTES_DE_SALIDA,

                    ruta.startLocation(),

                    ruta.startLocation(),

                    ahora,

                    comprometida,

                    primeraPlanificada,

                    estados,

                    null
            );
        }

        /*
         * =================================================
         * RUTA TERMINADA
         * =================================================
         */

        if (
                !programada
                        .completedAt()
                        .isAfter(
                                ahora
                        )
        ) {

            Location finalLocation =
                    paradas.isEmpty()

                            ? ruta.startLocation()

                            : paradas
                                    .getLast()
                                    .stop()
                                    .location();

            return new PosicionRutaEnInstante(
                    EstadoRutaEnInstante
                            .TERMINADA,

                    finalLocation,

                    finalLocation,

                    ahora,

                    -1,

                    paradas.size(),

                    estados,

                    null
            );
        }

        /*
         * =================================================
         * RECORRER LA PROGRAMACIÓN
         * =================================================
         */

        Location nodoAnterior =
                ruta.startLocation();

        for (
                int i = 0;
                i < paradas.size();
                i++
        ) {

            ScheduledRouteStop parada =
                    paradas.get(i);

            /*
             * =================================================
             * TODAVÍA VIAJA HACIA LA PARADA
             * =================================================
             */

            if (
                    ahora.isBefore(
                            parada.arrivedAt()
                    )
            ) {

                return localizarEnAproximacion(
                        parada,
                        nodoAnterior,
                        ahora,
                        comprometida,
                        primeraPlanificada,
                        estados
                );
            }

            /*
             * =================================================
             * SERVICIO
             * =================================================
             */

            Instant finServicio =
                    finServicio(
                            parada
                    );

            if (
                    ahora.isBefore(
                            finServicio
                    )
            ) {

                Location ubicacion =
                        parada
                                .stop()
                                .location();

                return new PosicionRutaEnInstante(
                        EstadoRutaEnInstante
                                .EN_SERVICIO,

                        ubicacion,

                        ubicacion,

                        finServicio,

                        i,

                        primeraPlanificada,

                        estados,

                        null
                );
            }

            /*
             * =================================================
             * REFRIGERIO
             * =================================================
             *
             * RouteScheduler ya coloca el refrigerio
             * después del servicio.
             *
             * Si completedAt > finServicio, el periodo
             * restante corresponde al refrigerio.
             */

            if (
                    ahora.isBefore(
                            parada.completedAt()
                    )
            ) {

                Location ubicacion =
                        parada
                                .stop()
                                .location();

                return new PosicionRutaEnInstante(
                        EstadoRutaEnInstante
                                .EN_REFRIGERIO,

                        ubicacion,

                        ubicacion,

                        parada.completedAt(),

                        i,

                        primeraPlanificada,

                        estados,

                        null
                );
            }

            /*
             * Esta parada terminó.
             *
             * El siguiente approach comienza desde aquí.
             */
            nodoAnterior =
                    parada
                            .stop()
                            .location();
        }

        /*
         * Protección final.
         */
        Location finalLocation =
                paradas.isEmpty()

                        ? ruta.startLocation()

                        : paradas
                                .getLast()
                                .stop()
                                .location();

        return new PosicionRutaEnInstante(
                EstadoRutaEnInstante.TERMINADA,
                finalLocation,
                finalLocation,
                ahora,
                -1,
                paradas.size(),
                estados,
                null
        );
    }

    /*
     * =====================================================
     * CLASIFICACIÓN DE PARADAS
     * =====================================================
     */

    public List<EstadoCompromisoParada> clasificarParadas(
            ScheduledDeliveryRoute programada,
            Instant ahora
    ) {

        Objects.requireNonNull(
                programada,
                "programada es requerida"
        );

        Objects.requireNonNull(
                ahora,
                "ahora es requerido"
        );

        List<EstadoCompromisoParada> estados =
                new ArrayList<>();

        /*
         * El viaje hacia la primera parada comienza
         * con departureAt.
         */
        Instant salidaHaciaParada =
                programada
                        .route()
                        .departureAt();

        for (
                ScheduledRouteStop parada :
                programada.scheduledStops()
        ) {

            /*
             * La parada terminó completamente.
             */
            if (
                    !parada
                            .completedAt()
                            .isAfter(
                                    ahora
                            )
            ) {

                estados.add(
                        EstadoCompromisoParada
                                .EJECUTADA
                );

            }

            /*
             * El viaje hacia ella ya comenzó.
             *
             * Puede estar:
             *
             * - esperando en un nodo;
             * - viajando;
             * - atendiendo;
             * - tomando refrigerio.
             */
            else if (
                    !salidaHaciaParada
                            .isAfter(
                                    ahora
                            )
            ) {

                estados.add(
                        EstadoCompromisoParada
                                .COMPROMETIDA
                );

            }

            /*
             * Aún no comenzó el viaje hacia ella.
             */
            else {

                estados.add(
                        EstadoCompromisoParada
                                .PLANIFICADA
                );
            }

            /*
             * El desplazamiento hacia la siguiente
             * parada empieza después de completar ésta.
             */
            salidaHaciaParada =
                    parada.completedAt();
        }

        return List.copyOf(
                estados
        );
    }

    /*
     * =====================================================
     * MOVIMIENTO ENTRE NODOS
     * =====================================================
     */

    private PosicionRutaEnInstante localizarEnAproximacion(
            ScheduledRouteStop parada,
            Location nodoAnterior,
            Instant ahora,
            int comprometida,
            int primeraPlanificada,
            List<EstadoCompromisoParada> estados
    ) {

        List<RoadLeg> legs =
                parada
                        .approach()
                        .legs();

        /*
         * Mismo origen y destino.
         */
        if (
                legs.isEmpty()
        ) {

            Location destino =
                    parada
                            .stop()
                            .location();

            return new PosicionRutaEnInstante(
                    EstadoRutaEnInstante
                            .ESPERA_VIAL,

                    destino,

                    destino,

                    parada.arrivedAt(),

                    comprometida,

                    primeraPlanificada,

                    estados,

                    null
            );
        }

        Location ultimoSeguro =
                nodoAnterior;

        for (
                RoadLeg leg :
                legs
        ) {

            /*
             * =================================================
             * ESPERA ANTES DE UNA CALLE
             * =================================================
             *
             * RoadNetwork puede introducir una espera antes
             * de atravesar un tramo bloqueado.
             *
             * En ese caso el vehículo ya está físicamente
             * parado en leg.from().
             *
             * Puede replanificarse inmediatamente desde allí.
             */

            if (
                    ahora.isBefore(
                            leg.departsAt()
                    )
            ) {

                return new PosicionRutaEnInstante(
                        EstadoRutaEnInstante
                                .ESPERA_VIAL,

                        leg.from(),

                        leg.from(),

                        ahora,

                        comprometida,

                        primeraPlanificada,

                        estados,

                        null
                );
            }

            /*
             * =================================================
             * EN MITAD DE UNA CALLE
             * =================================================
             */

            if (
                    ahora.isBefore(
                            leg.arrivesAt()
                    )
            ) {

                return new PosicionRutaEnInstante(
                        EstadoRutaEnInstante
                                .EN_TRANSITO,

                        leg.from(),

                        leg.to(),

                        /*
                         * No puede girar en medio de
                         * una calle.
                         *
                         * Debe alcanzar el nodo siguiente.
                         */
                        leg.arrivesAt(),

                        comprometida,

                        primeraPlanificada,

                        estados,

                        leg
                );
            }

            /*
             * Este leg ya fue recorrido.
             */
            ultimoSeguro =
                    leg.to();
        }

        /*
         * Caso defensivo:
         *
         * terminó todos los legs pero todavía no alcanzó
         * arrivedAt por alguna espera temporal.
         */
        return new PosicionRutaEnInstante(
                EstadoRutaEnInstante
                        .ESPERA_VIAL,

                ultimoSeguro,

                ultimoSeguro,

                parada.arrivedAt(),

                comprometida,

                primeraPlanificada,

                estados,

                null
        );
    }

    /*
     * =====================================================
     * SERVICIO
     * =====================================================
     */

    private Instant finServicio(
            ScheduledRouteStop parada
    ) {

        if (
                parada.stop()
                        instanceof DeliveryStop
        ) {

            return parada
                    .arrivedAt()
                    .plus(
                            DeliveryStop.SERVICE_TIME
                    );
        }

        /*
         * WarehouseVisit no tiene tiempo de servicio
         * adicional en el modelo actual.
         */
        return parada.arrivedAt();
    }

    /*
     * =====================================================
     * ÍNDICES
     * =====================================================
     */

    private int indiceComprometido(
            List<EstadoCompromisoParada> estados
    ) {

        for (
                int i = 0;
                i < estados.size();
                i++
        ) {

            if (
                    estados.get(i)
                            ==
                            EstadoCompromisoParada
                                    .COMPROMETIDA
            ) {

                return i;
            }
        }

        return -1;
    }

    private int primeraPlanificada(
            List<EstadoCompromisoParada> estados
    ) {

        for (
                int i = 0;
                i < estados.size();
                i++
        ) {

            if (
                    estados.get(i)
                            ==
                            EstadoCompromisoParada
                                    .PLANIFICADA
            ) {

                return i;
            }
        }

        return estados.size();
    }
}