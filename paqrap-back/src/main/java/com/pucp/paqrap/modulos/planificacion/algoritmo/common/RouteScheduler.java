package com.pucp.paqrap.modulos.planificacion.algoritmo.common;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.entity.RoadPath;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

/**
 * Programa temporalmente una DeliveryRoute.
 *
 * Orden operacional:
 *
 * viaje
 * -> llegada
 * -> servicio
 * -> refrigerio, cuando corresponde
 * -> siguiente parada
 *
 * REGLA DE REFRIGERIO
 * -------------------
 *
 * El refrigerio es obligatorio una vez por turno,
 * pero NO tiene una hora fija.
 *
 * Por ello no se toma automáticamente en la primera
 * parada posible.
 *
 * El scheduler intenta postergarlo mientras todavía
 * exista margen suficiente dentro de la ventana.
 *
 * Se toma cuando:
 *
 * 1. ya se alcanzó la última hora posible de inicio;
 * 2. esperar una parada adicional haría imposible
 *    iniciar el refrigerio a tiempo;
 * 3. estamos en la última parada de la ruta y la
 *    ventana ya está abierta;
 * 4. la ventana ya terminó y el refrigerio sigue
 *    pendiente: en ese caso se toma cuanto antes como
 *    salvaguarda operacional.
 *
 * Esto permite que pedidos urgentes/críticos sean
 * atendidos antes del descanso cuando todavía exista
 * margen legal/operacional para postergarlo.
 */
public final class RouteScheduler {

    private static final Duration DURACION_REFRIGERIO =
            Duration.ofHours(
                    1
            );

    private final CacheCaminosMinimos caminos;

    /*
     * =====================================================
     * CONSTRUCTORES
     * =====================================================
     */

    public RouteScheduler(
            RoadNetwork roadNetwork
    ) {

        this(
                roadNetwork,
                MetricasPlanificador.NOOP
        );
    }

    public RouteScheduler(
            RoadNetwork roadNetwork,
            MetricasPlanificador metricas
    ) {

        this(
                roadNetwork,
                metricas,
                CacheCaminosMinimos
                        .CAPACIDAD_POR_DEFECTO
        );
    }

    public RouteScheduler(
            RoadNetwork roadNetwork,
            MetricasPlanificador metricas,
            int capacidadCacheCaminos
    ) {

        this.caminos =
                new CacheCaminosMinimos(
                        Objects.requireNonNull(
                                roadNetwork,
                                "roadNetwork is required"
                        ),

                        Objects.requireNonNull(
                                metricas,
                                "metricas is required"
                        ),

                        capacidadCacheCaminos
                );
    }

    /*
     * =====================================================
     * API
     * =====================================================
     */

    /**
     * Programación normal.
     */
    public ScheduledDeliveryRoute schedule(
            DeliveryRoute route,
            OperationalSnapshot snapshot,
            List<RoadBlock> blocks
    ) {

        return schedule(
                route,
                snapshot,
                blocks,
                ContextoProgramacionRuta.vacio()
        );
    }

    /**
     * Programación con contexto heredado.
     *
     * Se utiliza, por ejemplo, cuando estamos
     * reprogramando el futuro de una ruta desde
     * un checkpoint y el conductor ya tomó su
     * refrigerio anteriormente.
     */
    public ScheduledDeliveryRoute schedule(
            DeliveryRoute route,
            OperationalSnapshot snapshot,
            List<RoadBlock> blocks,
            ContextoProgramacionRuta contexto
    ) {

        Objects.requireNonNull(
                route,
                "route is required"
        );

        Objects.requireNonNull(
                snapshot,
                "snapshot is required"
        );

        Objects.requireNonNull(
                blocks,
                "blocks are required"
        );

        Objects.requireNonNull(
                contexto,
                "contexto is required"
        );

        FleetProfile profile =
                snapshot.fleetProfile();

        double speed =
                profile
                        .parametersFor(
                                route
                                        .vehicle()
                                        .type()
                        )
                        .speedKmPerHour();

        Duration travelPerStreet =
                Duration.ofMillis(
                        Math.round(
                                3_600_000.0
                                        /
                                        speed
                        )
                );

        List<ScheduledRouteStop> scheduledStops =
                new ArrayList<>();

        LocationState current =
                new LocationState(
                        route.startLocation(),
                        route.departureAt(),
                        route.initialLoad()
                );

        double distance =
                0.0;

        /*
         * Si estamos continuando una ruta, podemos
         * heredar que el refrigerio de este turno
         * ya fue tomado.
         */
        Instant turnoConRefrigerioTomado =
                contexto
                        .turnoConRefrigerioTomado();

        List<RouteStop> stops =
                route.stops();

        for (
                int indice = 0;
                indice < stops.size();
                indice++
        ) {

            RouteStop stop =
                    stops.get(
                            indice
                    );

            /*
             * =================================================
             * 1. VIAJE
             * =================================================
             */

            Optional<RoadPath> camino =
                    caminos.obtener(
                            current.location(),
                            stop.location(),
                            current.time(),
                            travelPerStreet,
                            blocks
                    );

            RoadPath approach =
                    camino.orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "No feasible road path to the next route stop"
                                    )
                    );

            Instant arrivedAt =
                    approach.arrivesAt();

            int loadBefore =
                    current.load();

            int loadAfter =
                    loadBefore;

            /*
             * =================================================
             * 2. SERVICIO
             * =================================================
             */

            Instant serviceCompletedAt;

            if (
                    stop instanceof
                            DeliveryStop delivery
            ) {

                loadAfter -=
                        delivery
                                .deliveredPackages();

                serviceCompletedAt =
                        arrivedAt.plus(
                                DeliveryStop
                                        .SERVICE_TIME
                        );

            } else if (
                    stop instanceof
                            WarehouseVisit visit
            ) {

                loadAfter +=
                        visit.pickupPackages();

                serviceCompletedAt =
                        arrivedAt;

            } else {

                throw new IllegalStateException(
                        "Unknown route stop type"
                );
            }

            /*
             * =================================================
             * 3. REFRIGERIO
             * =================================================
             */

            Instant completedAt =
                    serviceCompletedAt;

            ShiftSchedule.ShiftWindow turno =
                    snapshot
                            .shiftSchedule()
                            .shiftAt(
                                    serviceCompletedAt
                            );

            ShiftSchedule.ShiftWindow ventanaRefrigerio =
                    snapshot
                            .shiftSchedule()
                            .mealWindow(
                                    serviceCompletedAt
                            );

            boolean yaTomoRefrigerio =
                    turno
                            .startsAt()
                            .equals(
                                    turnoConRefrigerioTomado
                            );

            if (
                    !yaTomoRefrigerio
                    &&
                    debeTomarRefrigerioAhora(
                            indice,
                            stops,
                            stop,
                            serviceCompletedAt,
                            ventanaRefrigerio,
                            travelPerStreet,
                            blocks
                    )
            ) {

                completedAt =
                        serviceCompletedAt.plus(
                                DURACION_REFRIGERIO
                        );

                turnoConRefrigerioTomado =
                        turno.startsAt();
            }

            /*
             * =================================================
             * 4. GUARDAR PARADA
             * =================================================
             */

            scheduledStops.add(
                    new ScheduledRouteStop(
                            stop,
                            approach,
                            arrivedAt,
                            completedAt,
                            loadBefore,
                            loadAfter
                    )
            );

            current =
                    new LocationState(
                            stop.location(),
                            completedAt,
                            loadAfter
                    );

            distance +=
                    approach.distanceKm();
        }

        /*
         * =====================================================
         * COSTO
         * =====================================================
         */

        double cost =
                distance
                        *
                        profile
                                .parametersFor(
                                        route
                                                .vehicle()
                                                .type()
                                )
                                .costPerKm();

        return new ScheduledDeliveryRoute(
                route,
                scheduledStops,
                current.time(),
                distance,
                cost
        );
    }

    /*
     * =====================================================
     * DECISIÓN DE REFRIGERIO
     * =====================================================
     */

    /**
     * Decide si conviene tomar el refrigerio después
     * de la parada actual.
     *
     * La decisión es greedy con look-ahead de una parada.
     *
     * Eso es suficiente para evitar la regla antigua:
     *
     * "ventana abierta -> comer inmediatamente"
     *
     * y al mismo tiempo garantiza que no sigamos
     * postergando el descanso cuando una parada adicional
     * consumiría el margen restante.
     */
    private boolean debeTomarRefrigerioAhora(
            int indiceActual,
            List<RouteStop> stops,
            RouteStop stopActual,
            Instant serviceCompletedAt,
            ShiftSchedule.ShiftWindow ventana,
            Duration travelPerStreet,
            List<RoadBlock> blocks
    ) {

        /*
         * =================================================
         * LA VENTANA TODAVÍA NO EMPEZÓ
         * =================================================
         */

        if (
                serviceCompletedAt
                        .isBefore(
                                ventana.startsAt()
                        )
        ) {

            return false;
        }

        /*
         * =================================================
         * LA VENTANA YA TERMINÓ
         * =================================================
         *
         * Salvaguarda:
         *
         * si por algún evento excepcional llegamos
         * tarde y todavía no se tomó el refrigerio,
         * se realiza cuanto antes.
         */

        if (
                !serviceCompletedAt
                        .isBefore(
                                ventana.endsAt()
                        )
        ) {

            return true;
        }

        /*
         * =================================================
         * ÚLTIMO INSTANTE POSIBLE PARA EMPEZAR
         * =================================================
         */

        Instant ultimoInicioPermitido =
                ventana
                        .endsAt()
                        .minus(
                                DURACION_REFRIGERIO
                        );

        /*
         * Ya no podemos postergarlo.
         */
        if (
                !serviceCompletedAt
                        .isBefore(
                                ultimoInicioPermitido
                        )
        ) {

            return true;
        }

        /*
         * =================================================
         * ÚLTIMA PARADA DE LA RUTA
         * =================================================
         *
         * La ventana ya abrió y todavía falta tomar
         * refrigerio.
         *
         * Lo programamos aquí para que el vehículo
         * no aparezca inmediatamente disponible
         * habiendo omitido su descanso obligatorio.
         */

        if (
                indiceActual ==
                        stops.size()
                                -
                                1
        ) {

            return true;
        }

        /*
         * =================================================
         * LOOK-AHEAD A LA SIGUIENTE PARADA
         * =================================================
         */

        RouteStop siguiente =
                stops.get(
                        indiceActual +
                                1
                );

        Optional<RoadPath> caminoSiguiente =
                caminos.obtener(
                        stopActual.location(),
                        siguiente.location(),
                        serviceCompletedAt,
                        travelPerStreet,
                        blocks
                );

        /*
         * Si ahora no existe camino pero después de
         * una hora podría abrirse un bloqueo, tomar
         * el refrigerio aquí es una alternativa
         * razonable y además evita omitirlo.
         */
        if (
                caminoSiguiente.isEmpty()
        ) {

            return true;
        }

        Instant llegadaSiguiente =
                caminoSiguiente
                        .orElseThrow()
                        .arrivesAt();

        Instant finServicioSiguiente =
                finServicioEstimado(
                        siguiente,
                        llegadaSiguiente
                );

        /*
         * Si completar una parada adicional nos lleva
         * más allá del último instante en que podría
         * empezar el refrigerio, debemos tomarlo AHORA.
         *
         * Si termina exactamente en ultimoInicioPermitido,
         * todavía puede empezarlo allí.
         */
        return finServicioSiguiente
                .isAfter(
                        ultimoInicioPermitido
                );
    }

    /*
     * =====================================================
     * HELPERS
     * =====================================================
     */

    private Instant finServicioEstimado(
            RouteStop stop,
            Instant llegada
    ) {

        if (
                stop instanceof
                        DeliveryStop
        ) {

            return llegada.plus(
                    DeliveryStop
                            .SERVICE_TIME
            );
        }

        /*
         * WarehouseVisit no tiene tiempo de
         * servicio adicional.
         */
        return llegada;
    }

    /**
     * Estado mutable representado de manera inmutable
     * entre una parada y la siguiente.
     */
    private record LocationState(
            com.pucp.paqrap.modulos.redvial.entity.Location location,
            Instant time,
            int load
    ) {
    }
}