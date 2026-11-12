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
import com.pucp.paqrap.modulos.planificacion.entity.TransferStop;
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

    /** Experimento Semana 13 (medir Ta): {@code -Dpaqrap.refrigerio.postergar=false} lo desactiva. */
    static final boolean POSTERGAR_REFRIGERIO =
            Boolean.parseBoolean(System.getProperty("paqrap.refrigerio.postergar", "true"));

    /**
     * Caminos mínimos memorizados (Semana 13, paso 6). shortestPath es pura,
     * así que el caché no cambia el resultado; con capacidad 0 se apaga.
     */
    private final CacheCaminosMinimos caminos;

    public RouteScheduler(
            RoadNetwork roadNetwork
    ) {
        this(roadNetwork, MetricasPlanificador.NOOP);
    }

    public RouteScheduler(
            RoadNetwork roadNetwork,
            MetricasPlanificador metricas
    ) {
        this(roadNetwork, metricas, CacheCaminosMinimos.CAPACIDAD_POR_DEFECTO);
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

        List<RouteStop> paradas =
                route.stops();

        /* Nodo desde el que se llegó a la ubicación actual. */
        com.pucp.paqrap.modulos.redvial.entity.Location llegoDesde =
                null;

        for (
                int indice = 0;
                indice < paradas.size();
                indice++
        ) {

            RouteStop stop =
                    paradas.get(indice);

            /*
             * =====================================================
             * VIAJE
             * =====================================================
             */

            /*
             * Si la parada anterior está sobre un bloqueo,
             * la unidad sale por la calle por la que llegó.
             */
            Optional<RoadPath> camino =
                    caminos
                            .obtener(
                                    currentLocation,
                                    stop.location(),
                                    currentTime,
                                    travelPerStreet,
                                    blocks,
                                    llegoDesde
                            );

            RoadPath approach =
                    camino
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

            } else if (
                    stop instanceof
                            TransferStop trasvase
            ) {

                /*
                 * Trasvase: recibe la carga del vehículo
                 * averiado; el transbordo toma 30 min.
                 */
                currentLoad +=
                        trasvase.packages();

                serviceCompletedAt =
                        arrivedAt.plus(
                                TransferStop.TRANSFER_TIME
                        );

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

            /*
             * Semana 13: el refrigerio se puede
             * postergar a la parada siguiente solo si
             * (1) esa parada es una entrega y, al
             * terminarla, el refrigerio todavía
             * empieza dentro de su ventana; y
             * (2) tomarlo aquí haría llegar tarde a
             * esa entrega, o la parada actual es un
             * trasvase (la carga rescatada sigue de
             * inmediato). Nunca se omite: solo se
             * corre a una parada posterior.
             */
            boolean postergar =
                    POSTERGAR_REFRIGERIO &&
                    refrigerioPendiente &&
                    ventanaYaInicio &&
                    puedePostergarRefrigerio(
                            paradas,
                            indice,
                            serviceCompletedAt,
                            ventanaRefrigerio,
                            travelPerStreet,
                            blocks,
                            !(stop instanceof TransferStop)
                    );

            if (
                    refrigerioPendiente &&
                    ventanaYaInicio &&
                    !postergar
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

            if (!approach.legs().isEmpty()) {
                llegoDesde =
                        approach.legs().getLast().from();
            }

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

    /**
     * True si el refrigerio pendiente se puede correr hasta después de la próxima entrega (pasando por las recargas en
     * almacén, sin servicio, y los trasvases, de 30 min, que haya antes): el conductor la termina a más tardar al cierre de la ventana de
     * refrigerio (así come dentro de ella) y, si {@code exigirRetraso}, tomarlo ahora haría que esa entrega llegue
     * después de su plazo (sin refrigerio llegaría a tiempo).
     */
    private boolean puedePostergarRefrigerio(
            List<RouteStop> paradas,
            int indice,
            Instant libreDesde,
            ShiftSchedule.ShiftWindow ventana,
            Duration porCalle,
            List<RoadBlock> bloqueos,
            boolean exigirRetraso
    ) {

        int entrega = indice + 1;
        while (entrega < paradas.size() && !(paradas.get(entrega) instanceof DeliveryStop)) {
            entrega++;
        }
        if (
                entrega >= paradas.size() ||
                !(paradas.get(entrega) instanceof DeliveryStop siguiente)
        ) {
            return false;
        }

        Instant sinRefrigerio =
                llegada(paradas, indice, entrega, libreDesde, porCalle, bloqueos);

        if (
                sinRefrigerio == null ||
                sinRefrigerio
                        .plus(DeliveryStop.SERVICE_TIME)
                        .isAfter(ventana.endsAt())
        ) {
            return false; // ya no comería dentro de la ventana
        }

        if (!exigirRetraso) {
            return true;
        }

        Instant plazo =
                siguiente.order().deadline();

        if (sinRefrigerio.isAfter(plazo)) {
            return false; // llega tarde igual: postergar no ayuda
        }

        Instant conRefrigerio =
                llegada(paradas, indice, entrega, libreDesde.plus(DURACION_REFRIGERIO), porCalle, bloqueos);

        return conRefrigerio != null && conRefrigerio.isAfter(plazo);
    }

    /** Llegada a la parada {@code hasta} saliendo de {@code desde} en {@code salida}; las recargas no tienen servicio. */
    private Instant llegada(
            List<RouteStop> paradas,
            int desde,
            int hasta,
            Instant salida,
            Duration porCalle,
            List<RoadBlock> bloqueos
    ) {
        Instant hora = salida;
        for (int i = desde; i < hasta; i++) {
            Optional<RoadPath> tramo =
                    caminos.obtener(
                            paradas.get(i).location(),
                            paradas.get(i + 1).location(),
                            hora,
                            porCalle,
                            bloqueos
                    );
            if (tramo.isEmpty()) {
                return null;
            }
            hora = tramo.get().arrivesAt();
            if (i + 1 < hasta && paradas.get(i + 1) instanceof TransferStop) {
                hora = hora.plus(TransferStop.TRANSFER_TIME);
            }
        }
        return hora;
    }
}