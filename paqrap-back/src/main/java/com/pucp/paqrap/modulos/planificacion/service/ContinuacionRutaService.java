package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ContextoProgramacionRuta;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.entity.RoadLeg;
import com.pucp.paqrap.modulos.redvial.entity.RoadPath;

/**
 * Reprograma únicamente el futuro de una ruta activa.
 *
 * Conserva:
 *
 * - mismo id;
 * - mismo vehículo;
 * - misma salida original;
 * - paradas ya ejecutadas;
 * - calles ya recorridas;
 * - carga actual;
 * - refrigerio ya tomado.
 *
 * Solo recalcula a partir del checkpoint vial seguro.
 */
final class ContinuacionRutaService {

    private final RouteScheduler scheduler;

    private final CheckpointRutaService checkpoints;

    ContinuacionRutaService(
            RouteScheduler scheduler
    ) {

        this.scheduler =
                Objects.requireNonNull(
                        scheduler,
                        "scheduler es requerido"
                );

        this.checkpoints =
                new CheckpointRutaService();
    }

    Resultado reprogramar(
            RutaVigente vigente,
            Instant ahora,
            List<RouteStop> nuevasParadasFuturas,
            OperationalSnapshot snapshotActual,
            List<RoadBlock> bloqueos
    ) {

        Objects.requireNonNull(
                vigente,
                "vigente es requerida"
        );

        Objects.requireNonNull(
                ahora,
                "ahora es requerido"
        );

        Objects.requireNonNull(
                nuevasParadasFuturas,
                "nuevasParadasFuturas son requeridas"
        );

        Objects.requireNonNull(
                snapshotActual,
                "snapshotActual es requerido"
        );

        Objects.requireNonNull(
                bloqueos,
                "bloqueos son requeridos"
        );

        CheckpointRuta checkpoint =
                checkpoints.crear(
                        vigente,
                        ahora
                );

        if (
                checkpoint.estado()
                        ==
                        EstadoRutaEnInstante.TERMINADA
        ) {

            throw new IllegalArgumentException(
                    "Una ruta terminada no puede continuarse"
            );
        }

        /*
         * =================================================
         * 1. RUTA TEMPORAL DEL SUFIJO
         * =================================================
         *
         * Esta ruta solo se utiliza para ejecutar
         * RouteScheduler desde el checkpoint.
         */

        DeliveryRoute sufijo =
                DeliveryRoute
                        .replanFromCurrentLocation(
                                vigente
                                        .ruta()
                                        .id(),

                                vigente
                                        .ruta()
                                        .vehicle(),

                                checkpoint
                                        .nodoSeguro(),

                                checkpoint
                                        .carga(),

                                checkpoint
                                        .disponibleDesde()
                        );

        for (
                RouteStop parada :
                nuevasParadasFuturas
        ) {

            sufijo =
                    sufijo.withAppendedStop(
                            parada
                    );
        }

        ContextoProgramacionRuta contexto =
                new ContextoProgramacionRuta(
                        checkpoint
                                .turnoConRefrigerioTomado()
                                .orElse(
                                        null
                                )
                );

        ScheduledDeliveryRoute sufijoProgramado =
                scheduler.schedule(
                        sufijo,
                        snapshotActual,
                        bloqueos,
                        contexto
                );

        /*
         * =================================================
         * 2. RUTA LÓGICA COMPLETA
         * =================================================
         *
         * Importante:
         *
         * partimos de la ruta ORIGINAL para conservar
         * id, vehículo, origen y hora de salida.
         */

        List<RouteStop> paradasCompletas =
                new ArrayList<>();

        for (
                ScheduledRouteStop parada :
                checkpoint.prefijoFijo()
        ) {

            paradasCompletas.add(
                    parada.stop()
            );
        }

        paradasCompletas.addAll(
                nuevasParadasFuturas
        );

        DeliveryRoute rutaCompleta =
                vigente
                        .ruta()
                        .withReplacedStops(
                                paradasCompletas
                        );

        /*
         * =================================================
         * 3. UNIR PROGRAMACIÓN HISTÓRICA + SUFIJO
         * =================================================
         */

        List<ScheduledRouteStop> programacionCompleta =
                new ArrayList<>(
                        checkpoint.prefijoFijo()
                );

        List<ScheduledRouteStop> nuevasProgramadas =
                new ArrayList<>(
                        sufijoProgramado
                                .scheduledStops()
                );

        /*
         * Si el vehículo estaba en mitad de un viaje,
         * la primera nueva aproximación debe incluir:
         *
         * recorrido ya realizado
         * +
         * camino desde checkpoint
         * hasta la nueva primera parada.
         */
        if (
                !nuevasProgramadas.isEmpty()
                &&
                !checkpoint
                        .tramosConsumidosDesdePrefijo()
                        .isEmpty()
        ) {

            ScheduledRouteStop primera =
                    nuevasProgramadas
                            .getFirst();

            RoadPath combinado =
                    combinarCaminoInicial(
                            vigente,
                            checkpoint,
                            primera
                    );

            nuevasProgramadas.set(
                    0,

                    new ScheduledRouteStop(
                            primera.stop(),
                            combinado,
                            primera.arrivedAt(),
                            primera.completedAt(),
                            primera.loadBefore(),
                            primera.loadAfter()
                    )
            );
        }

        programacionCompleta.addAll(
                nuevasProgramadas
        );

        /*
         * =================================================
         * 4. DISTANCIA Y COSTO
         * =================================================
         */

        double distancia =
                programacionCompleta
                        .stream()

                        .mapToDouble(
                                parada ->
                                        parada
                                                .approach()
                                                .distanceKm()
                        )

                        .sum();

        double costo =
                distancia
                        *
                        snapshotActual
                                .fleetProfile()
                                .parametersFor(
                                        rutaCompleta
                                                .vehicle()
                                                .type()
                                )
                                .costPerKm();

        Instant completadaEn =
                programacionCompleta
                        .isEmpty()

                        ? checkpoint
                                .disponibleDesde()

                        : programacionCompleta
                                .getLast()
                                .completedAt();

        ScheduledDeliveryRoute completa =
                new ScheduledDeliveryRoute(
                        rutaCompleta,
                        programacionCompleta,
                        completadaEn,
                        distancia,
                        costo
                );

        return new Resultado(
                rutaCompleta,
                completa,
                checkpoint
        );
    }

    /**
     * Une los RoadLeg ya recorridos con el camino
     * nuevo generado desde el checkpoint.
     */
    private RoadPath combinarCaminoInicial(
            RutaVigente vigente,
            CheckpointRuta checkpoint,
            ScheduledRouteStop primeraNueva
    ) {

        List<RoadLeg> legs =
                new ArrayList<>(
                        checkpoint
                                .tramosConsumidosDesdePrefijo()
                );

        legs.addAll(
                primeraNueva
                        .approach()
                        .legs()
        );

        /*
         * El origen es:
         *
         * - la última parada fija;
         * - o el origen original si todavía no hubo
         *   una parada completada.
         */

        var origen =
                checkpoint
                        .prefijoFijo()
                        .isEmpty()

                        ? vigente
                                .ruta()
                                .startLocation()

                        : checkpoint
                                .prefijoFijo()
                                .getLast()
                                .stop()
                                .location();

        Instant salida =
                checkpoint
                        .prefijoFijo()
                        .isEmpty()

                        ? vigente
                                .ruta()
                                .departureAt()

                        : checkpoint
                                .prefijoFijo()
                                .getLast()
                                .completedAt();

        return new RoadPath(
                origen,

                primeraNueva
                        .stop()
                        .location(),

                salida,

                legs
        );
    }

    record Resultado(
            DeliveryRoute ruta,
            ScheduledDeliveryRoute programada,
            CheckpointRuta checkpoint
    ) {

        Resultado {

            Objects.requireNonNull(
                    ruta,
                    "ruta es requerida"
            );

            Objects.requireNonNull(
                    programada,
                    "programada es requerida"
            );

            Objects.requireNonNull(
                    checkpoint,
                    "checkpoint es requerido"
            );
        }
    }
}