package com.pucp.paqrap.modulos.planificacion.algoritmo.common;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleParameters;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

/**
 * Verifica la regla actual de refrigerio:
 *
 * - es obligatorio una vez por turno;
 * - no tiene horario fijo;
 * - puede postergarse dentro de su ventana;
 * - no se toma automáticamente en la primera parada;
 * - nunca se repite dentro del mismo turno.
 */
class RouteSchedulerRefrigerioTest {

    private final ZoneId zona =
            ShiftSchedule.DEFAULT_ZONE;

    private OperationalSnapshot snapshotConVelocidad(
            double velocidadKmH,
            Instant horaInicio,
            Warehouse central,
            Vehicle vehiculo
    ) {

        Map<
                VehicleType,
                VehicleParameters
                > parametros =
                new EnumMap<>(
                        VehicleType.class
                );

        for (
                VehicleType tipo :
                VehicleType.values()
        ) {

            parametros.put(
                    tipo,
                    new VehicleParameters(
                            24,
                            velocidadKmH,
                            1.0
                    )
            );
        }

        Map<
                String,
                VehicleOperationalState
                > estados =
                Map.of(
                        vehiculo.id(),

                        new VehicleOperationalState(
                                vehiculo,
                                VehicleStatus.AVAILABLE,
                                central.location(),
                                horaInicio
                        )
                );

        return new OperationalSnapshot(
                horaInicio,

                new FleetProfile(
                        parametros
                ),

                InventorySnapshot.from(
                        List.of(
                                central
                        )
                ),

                estados,

                new MaintenanceCalendar(
                        zona,
                        List.of()
                ),

                new ShiftSchedule(
                        zona
                ),

                List.of()
        );
    }

    @Test
    void elRefrigerioPuedePostergarseDentroDeLaVentanaYSeTomaUnaSolaVez() {

        /*
         * 07:00 hora local.
         *
         * Lima = UTC-5
         *
         * por tanto:
         *
         * inicio = 12:00Z.
         */
        Instant horaInicio =
                ZonedDateTime.of(
                                LocalDate.of(
                                        2026,
                                        9,
                                        9
                                ),

                                LocalTime.of(
                                        7,
                                        0
                                ),

                                zona
                        )
                        .toInstant();

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",

                        new Location(
                                0,
                                0
                        )
                );

        Vehicle vehiculo =
                new Vehicle(
                        "TA01",
                        VehicleType.CAR,
                        true
                );

        OperationalSnapshot snapshot =
                snapshotConVelocidad(
                        10.0,
                        horaInicio,
                        central,
                        vehiculo
                );

        /*
         * =================================================
         * P1
         * =================================================
         *
         * Distancia:
         *
         * 15 km
         *
         * Velocidad:
         *
         * 10 km/h
         *
         * Viaje:
         *
         * 1 h 30 min
         *
         * Línea temporal:
         *
         * 12:00Z salida
         * 13:30Z llegada
         * 14:30Z fin del servicio
         *
         * Como todavía hay margen suficiente para
         * atender otra parada antes del límite del
         * refrigerio, NO debe tomarlo aquí.
         */

        Order pedido1 =
                new Order(
                        "C-1",

                        new Location(
                                15,
                                0
                        ),

                        1,

                        horaInicio,

                        horaInicio.plus(
                                Duration.ofHours(
                                        20
                                )
                        )
                );

        /*
         * =================================================
         * P2
         * =================================================
         *
         * Distancia desde P1:
         *
         * 5 km
         *
         * Viaje:
         *
         * 30 minutos
         *
         * Línea temporal:
         *
         * 14:30Z salida desde P1
         * 15:00Z llegada a P2
         * 16:00Z fin del servicio
         *
         * P2 es la última parada de esta ruta.
         *
         * Como el refrigerio sigue pendiente y la
         * ventana está abierta, se toma después del
         * servicio:
         *
         * 16:00Z -> 17:00Z
         */

        Order pedido2 =
                new Order(
                        "C-2",

                        new Location(
                                20,
                                0
                        ),

                        1,

                        horaInicio,

                        horaInicio.plus(
                                Duration.ofHours(
                                        20
                                )
                        )
                );

        DeliveryRoute ruta =
                DeliveryRoute
                        .startScenarioAtCentral(
                                "R1",
                                vehiculo,
                                central,
                                2,
                                horaInicio
                        )

                        .withAppendedStop(
                                new DeliveryStop(
                                        pedido1,
                                        1
                                )
                        )

                        .withAppendedStop(
                                new DeliveryStop(
                                        pedido2,
                                        1
                                )
                        );

        ScheduledDeliveryRoute programada =
                new RouteScheduler(
                        new RoadNetwork()
                )
                        .schedule(
                                ruta,
                                snapshot,
                                List.of()
                        );

        ScheduledRouteStop primera =
                programada
                        .scheduledStops()
                        .get(0);

        ScheduledRouteStop segunda =
                programada
                        .scheduledStops()
                        .get(1);

        /*
         * =================================================
         * P1: SIN REFRIGERIO
         * =================================================
         */

        Instant llegada1 =
                horaInicio.plus(
                        Duration.ofMinutes(
                                90
                        )
                );

        Instant finServicio1 =
                llegada1.plus(
                        DeliveryStop.SERVICE_TIME
                );

        assertEquals(
                llegada1,
                primera.arrivedAt(),
                "P1 debe llegar a las 08:30 hora local"
        );

        assertEquals(
                finServicio1,
                primera.completedAt(),
                "el refrigerio debe postergarse porque todavía existe margen"
        );

        assertEquals(
                DeliveryStop.SERVICE_TIME,

                Duration.between(
                        primera.arrivedAt(),
                        primera.completedAt()
                ),

                "P1 debe contener únicamente su hora de servicio"
        );

        /*
         * =================================================
         * P2
         * =================================================
         */

        Instant llegada2 =
                finServicio1.plus(
                        Duration.ofMinutes(
                                30
                        )
                );

        assertEquals(
                llegada2,
                segunda.arrivedAt()
        );

        /*
         * =================================================
         * P2: SERVICIO + REFRIGERIO
         * =================================================
         */

        Instant finServicio2 =
                llegada2.plus(
                        DeliveryStop.SERVICE_TIME
                );

        Instant finRefrigerio =
                finServicio2.plus(
                        Duration.ofHours(
                                1
                        )
                );

        assertEquals(
                finRefrigerio,
                segunda.completedAt(),
                "el refrigerio debe realizarse después de P2"
        );

        assertEquals(
                Duration.ofHours(
                        2
                ),

                Duration.between(
                        segunda.arrivedAt(),
                        segunda.completedAt()
                ),

                "P2 contiene una hora de servicio más una hora de refrigerio"
        );
    }
}