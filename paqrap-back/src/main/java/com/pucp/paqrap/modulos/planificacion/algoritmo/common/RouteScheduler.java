package com.pucp.paqrap.modulos.planificacion.algoritmo.common;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
 * Transforma una ruta lógica en una ruta temporizada.
 *
 * Reglas relevantes:
 *
 * 1. El vehículo viaja hasta la parada.
 * 2. La llegada física es la hora de atención del pedido.
 * 3. Si es una entrega, realiza primero su hora de servicio.
 * 4. Si corresponde refrigerio, se realiza DESPUÉS del servicio.
 * 5. Recién después continúa hacia la siguiente parada.
 *
 * De esta forma el refrigerio nunca retrasa artificialmente
 * la hora de llegada utilizada para validar el SLA del pedido.
 */
public final class RouteScheduler {

    private static final Duration DURACION_REFRIGERIO =
            Duration.ofHours(1);

    private final RoadNetwork roadNetwork;

    public RouteScheduler(
            RoadNetwork roadNetwork
    ) {
        this.roadNetwork =
                Objects.requireNonNull(
                        roadNetwork,
                        "roadNetwork is required"
                );
    }

    public ScheduledDeliveryRoute schedule(
            DeliveryRoute route,
            OperationalSnapshot snapshot,
            List<RoadBlock> blocks
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

        FleetProfile profile =
                snapshot.fleetProfile();

        double speed =
                profile
                        .parametersFor(
                                route.vehicle().type()
                        )
                        .speedKmPerHour();

        Duration travelPerStreet =
                Duration.ofMillis(
                        Math.round(
                                3_600_000.0 / speed
                        )
                );

        List<ScheduledRouteStop> scheduledStops =
                new ArrayList<>();

        var currentLocation =
                route.startLocation();

        Instant currentTime =
                route.departureAt();

        int currentLoad =
                route.initialLoad();

        double distance = 0;

        /*
         * Se guarda el INICIO del turno en el
         * que el conductor ya tomó refrigerio.
         *
         * Así puede tomar como máximo un
         * refrigerio por turno.
         */
        Instant turnoConRefrigerioTomado =
                null;

        for (
                RouteStop stop :
                route.stops()
        ) {

            /*
             * =====================================================
             * VIAJE
             * =====================================================
             */

            RoadPath approach =
                    roadNetwork
                            .shortestPath(
                                    currentLocation,
                                    stop.location(),
                                    currentTime,
                                    travelPerStreet,
                                    blocks
                            )
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "No feasible road path to the next route stop"
                                            )
                            );

            /*
             * Esta es la llegada FÍSICA.
             *
             * Esta hora es la que debe usarse
             * para comprobar el deadline.
             */
            Instant arrivedAt =
                    approach.arrivesAt();

            int loadBefore =
                    currentLoad;

            /*
             * =====================================================
             * SERVICIO DE LA PARADA
             * =====================================================
             */

            Instant serviceCompletedAt;

            if (
                    stop instanceof
                            DeliveryStop delivery
            ) {

                currentLoad -=
                        delivery
                                .deliveredPackages();

                /*
                 * Una entrega requiere una hora.
                 */
                serviceCompletedAt =
                        arrivedAt.plus(
                                DeliveryStop.SERVICE_TIME
                        );

            } else if (
                    stop instanceof
                            WarehouseVisit visit
            ) {

                currentLoad +=
                        visit.pickupPackages();

                /*
                 * La visita al almacén no agrega
                 * una hora de servicio en el
                 * modelo actual.
                 */
                serviceCompletedAt =
                        arrivedAt;

            } else {

                throw new IllegalStateException(
                        "Unknown route stop type"
                );
            }

            /*
             * =====================================================
             * REFRIGERIO
             * =====================================================
             *
             * IMPORTANTE:
             *
             * Se evalúa DESPUÉS de terminar
             * el servicio de la parada.
             *
             * Antes el código hacía:
             *
             * llegada
             * -> refrigerio
             * -> servicio
             *
             * Eso podía convertir una entrega
             * puntual en una entrega tardía.
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

            boolean refrigerioPendiente =
                    !turno
                            .startsAt()
                            .equals(
                                    turnoConRefrigerioTomado
                            );

            boolean ventanaYaInicio =
                    !serviceCompletedAt
                            .isBefore(
                                    ventanaRefrigerio
                                            .startsAt()
                            );

            if (
                    refrigerioPendiente &&
                    ventanaYaInicio
            ) {

                /*
                 * Primero terminó la entrega.
                 * Después toma su refrigerio.
                 */
                completedAt =
                        serviceCompletedAt.plus(
                                DURACION_REFRIGERIO
                        );

                turnoConRefrigerioTomado =
                        turno.startsAt();
            }

            /*
             * La siguiente calle se calcula
             * recién después del servicio y,
             * cuando corresponda, del refrigerio.
             */
            currentTime =
                    completedAt;

            scheduledStops.add(
                    new ScheduledRouteStop(
                            stop,
                            approach,
                            arrivedAt,
                            completedAt,
                            loadBefore,
                            currentLoad
                    )
            );

            currentLocation =
                    stop.location();

            distance +=
                    approach.distanceKm();
        }

        double cost =
                distance *
                        profile
                                .parametersFor(
                                        route.vehicle().type()
                                )
                                .costPerKm();

        return new ScheduledDeliveryRoute(
                route,
                scheduledStops,
                currentTime,
                distance,
                cost
        );
    }
}