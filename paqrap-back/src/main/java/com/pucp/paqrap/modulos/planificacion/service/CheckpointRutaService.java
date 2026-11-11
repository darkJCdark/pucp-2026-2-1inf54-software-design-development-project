package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.redvial.entity.RoadLeg;

/**
 * Convierte una RutaVigente en un checkpoint seguro
 * para replanificación.
 */
final class CheckpointRutaService {

    private final LocalizadorRutaService localizador =
            new LocalizadorRutaService();

    CheckpointRuta crear(
            RutaVigente vigente,
            Instant ahora
    ) {

        Objects.requireNonNull(
                vigente,
                "vigente es requerida"
        );

        Objects.requireNonNull(
                ahora,
                "ahora es requerido"
        );

        ScheduledDeliveryRoute programada =
                vigente.programada();

        if (
                programada == null
        ) {
            throw new IllegalArgumentException(
                    "La ruta requiere programación"
            );
        }

        PosicionRutaEnInstante posicion =
                localizador.localizar(
                        programada,
                        ahora
                );

        List<ScheduledRouteStop> paradas =
                programada.scheduledStops();

        int cantidadPrefijo =
                cantidadParadasFijas(
                        posicion,
                        paradas.size()
                );

        List<ScheduledRouteStop> prefijo =
                new ArrayList<>(
                        paradas.subList(
                                0,
                                cantidadPrefijo
                        )
                );

        /*
         * Si todavía está atendiendo una entrega y
         * originalmente tenía refrigerio justo después,
         * el checkpoint queda al terminar el servicio.
         *
         * El refrigerio todavía no ocurrió, por lo que
         * podrá reprogramarse más adelante.
         */
        if (
                posicion.estado()
                        ==
                        EstadoRutaEnInstante
                                .EN_SERVICIO
                &&
                !prefijo.isEmpty()
        ) {

            int indice =
                    prefijo.size()
                            -
                            1;

            ScheduledRouteStop actual =
                    prefijo.get(
                            indice
                    );

            Instant finServicio =
                    finServicio(
                            actual
                    );

            prefijo.set(
                    indice,

                    new ScheduledRouteStop(
                            actual.stop(),
                            actual.approach(),
                            actual.arrivedAt(),
                            finServicio,
                            actual.loadBefore(),
                            actual.loadAfter()
                    )
            );
        }

        int carga =
                prefijo.isEmpty()

                        ? vigente
                                .ruta()
                                .initialLoad()

                        : prefijo
                                .getLast()
                                .loadAfter();

        List<RoadLeg> consumidos =
                tramosConsumidos(
                        posicion,
                        paradas
                );

        Optional<Instant> turnoConRefrigerio =
                turnoConRefrigerioTomado(
                        vigente,
                        prefijo
                );

        return new CheckpointRuta(
                posicion.estado(),

                posicion.proximoNodoSeguro(),

                posicion.desviableDesde(),

                carga,

                turnoConRefrigerio,

                cantidadPrefijo,

                prefijo,

                consumidos
        );
    }

    private int cantidadParadasFijas(
            PosicionRutaEnInstante posicion,
            int total
    ) {

        return switch (
                posicion.estado()
        ) {

            case ANTES_DE_SALIDA ->
                    0;

            /*
             * La parada comprometida todavía no
             * fue alcanzada.
             */
            case EN_TRANSITO,
                 ESPERA_VIAL ->

                    Math.max(
                            0,
                            posicion
                                    .indiceParadaComprometida()
                    );

            /*
             * La entrega ya ocurrió al llegar.
             *
             * El servicio debe terminar antes del
             * desvío, por lo que la parada pertenece
             * al prefijo fijo.
             */
            case EN_SERVICIO,
                 EN_REFRIGERIO ->

                    Math.min(
                            total,

                            posicion
                                    .indiceParadaComprometida()
                                    +
                                    1
                    );

            case TERMINADA ->
                    total;
        };
    }

    private List<RoadLeg> tramosConsumidos(
            PosicionRutaEnInstante posicion,
            List<ScheduledRouteStop> paradas
    ) {

        if (
                posicion.estado()
                        !=
                        EstadoRutaEnInstante.EN_TRANSITO
                &&
                posicion.estado()
                        !=
                        EstadoRutaEnInstante.ESPERA_VIAL
        ) {

            return List.of();
        }

        int indice =
                posicion
                        .indiceParadaComprometida();

        if (
                indice < 0
                ||
                indice >=
                        paradas.size()
        ) {

            return List.of();
        }

        List<RoadLeg> resultado =
                new ArrayList<>();

        for (
                RoadLeg leg :
                paradas
                        .get(indice)
                        .approach()
                        .legs()
        ) {

            if (
                    !leg
                            .arrivesAt()
                            .isAfter(
                                    posicion
                                            .desviableDesde()
                            )
            ) {

                resultado.add(
                        leg
                );

            } else {

                break;
            }
        }

        return List.copyOf(
                resultado
        );
    }

    private Optional<Instant> turnoConRefrigerioTomado(
            RutaVigente vigente,
            List<ScheduledRouteStop> prefijo
    ) {

        Instant encontrado =
                null;

        for (
                ScheduledRouteStop parada :
                prefijo
        ) {

            Instant finServicio =
                    finServicio(
                            parada
                    );

            /*
             * Tiempo extra después del servicio
             * implica refrigerio.
             */
            if (
                    parada
                            .completedAt()
                            .isAfter(
                                    finServicio
                            )
            ) {

                encontrado =
                        vigente
                                .snapshotOrigen()
                                .shiftSchedule()
                                .shiftAt(
                                        finServicio
                                )
                                .startsAt();
            }
        }

        return Optional.ofNullable(
                encontrado
        );
    }

    private Instant finServicio(
            ScheduledRouteStop parada
    ) {

        if (
                parada.stop()
                        instanceof
                        DeliveryStop
        ) {

            return parada
                    .arrivedAt()
                    .plus(
                            DeliveryStop
                                    .SERVICE_TIME
                    );
        }

        return parada.arrivedAt();
    }
}