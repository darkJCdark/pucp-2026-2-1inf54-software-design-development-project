package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

class ContinuacionRutaServiceTest {

    private static final Instant T0 =
            Instant.parse(
                    "2026-01-01T05:00:00Z"
            );

    private final RouteScheduler scheduler =
            new RouteScheduler(
                    new RoadNetwork()
            );

    private final ContinuacionRutaService continuaciones =
            new ContinuacionRutaService(
                    scheduler
            );

    @Test
    void unDesvioEnTransitoConservaLosTramosYaRecorridos() {

        Fixture fixture =
                fixture();

        /*
         * CAR:
         *
         * 20 km/h
         *
         * cada calle tarda 3 minutos.
         *
         * 05:04:
         *
         * CENTRAL
         *
         * 27 -> 28
         * ya recorrido
         *
         * 28 -> 29
         * actualmente recorrido
         *
         * checkpoint:
         *
         * (29,14) a las 05:06.
         */

        Instant ahora =
                T0.plus(
                        Duration.ofMinutes(
                                4
                        )
                );

        Order urgente =
                new Order(
                        "URGENTE",

                        new Location(
                                29,
                                13
                        ),

                        1,

                        ahora,

                        T0.plus(
                                Duration.ofHours(
                                        4
                                )
                        )
                );

        List<RouteStop> futuro =
                List.of(
                        new DeliveryStop(
                                urgente,
                                1
                        ),

                        new DeliveryStop(
                                fixture.p1(),
                                4
                        ),

                        new WarehouseVisit(
                                fixture.central(),
                                0
                        )
                );

        ContinuacionRutaService.Resultado resultado =
                continuaciones.reprogramar(
                        fixture.vigente(),
                        ahora,
                        futuro,
                        fixture.snapshot(),
                        List.of()
                );

        /*
         * =================================================
         * IDENTIDAD
         * =================================================
         */

        assertEquals(
                fixture
                        .ruta()
                        .id(),

                resultado
                        .ruta()
                        .id()
        );

        assertEquals(
                fixture
                        .ruta()
                        .departureAt(),

                resultado
                        .ruta()
                        .departureAt()
        );

        /*
         * =================================================
         * CHECKPOINT
         * =================================================
         */

        assertEquals(
                new Location(
                        29,
                        14
                ),

                resultado
                        .checkpoint()
                        .nodoSeguro()
        );

        assertEquals(
                T0.plus(
                        Duration.ofMinutes(
                                6
                        )
                ),

                resultado
                        .checkpoint()
                        .disponibleDesde()
        );

        assertEquals(
                5,
                resultado
                        .checkpoint()
                        .carga()
        );

        /*
         * =================================================
         * NUEVO ORDEN
         * =================================================
         */

        DeliveryStop primeraEntrega =
                (DeliveryStop) resultado
                        .ruta()
                        .stops()
                        .get(0);

        DeliveryStop segundaEntrega =
                (DeliveryStop) resultado
                        .ruta()
                        .stops()
                        .get(1);

        assertEquals(
                "URGENTE",
                primeraEntrega
                        .order()
                        .id()
        );

        assertEquals(
                "P1",
                segundaEntrega
                        .order()
                        .id()
        );

        /*
         * =================================================
         * GEOMETRÍA HISTÓRICA
         * =================================================
         */

        ScheduledRouteStop primera =
                resultado
                        .programada()
                        .scheduledStops()
                        .getFirst();

        assertEquals(
                fixture
                        .central()
                        .location(),

                primera
                        .approach()
                        .origin()
        );

        assertTrue(
                primera
                        .approach()
                        .legs()
                        .size()
                        >=
                        3
        );

        assertEquals(
                new Location(
                        27,
                        14
                ),

                primera
                        .approach()
                        .legs()
                        .get(0)
                        .from()
        );

        assertEquals(
                new Location(
                        28,
                        14
                ),

                primera
                        .approach()
                        .legs()
                        .get(0)
                        .to()
        );

        assertEquals(
                new Location(
                        28,
                        14
                ),

                primera
                        .approach()
                        .legs()
                        .get(1)
                        .from()
        );

        assertEquals(
                new Location(
                        29,
                        14
                ),

                primera
                        .approach()
                        .legs()
                        .get(1)
                        .to()
        );
    }

    @Test
    void unaContinuacionRecuerdaRefrigerioFlexibleYaTomadoYNoLoDuplica() {

        Fixture fixture =
                fixtureConRefrigerioFlexible();

        /*
         * =================================================
         * PROGRAMACIÓN ORIGINAL
         * =================================================
         *
         * CAR = 20 km/h.
         *
         * CENTRAL (27)
         * ↓
         * P1 (30)
         *
         * 3 km = 9 min
         *
         * P1:
         *
         * 05:09 llegada
         * 06:09 fin servicio
         *
         * El scheduler NO toma refrigerio aquí.
         *
         * Luego:
         *
         * P1(30) -> P2(70)
         *
         * 40 km = 2 h
         *
         * P2:
         *
         * 08:09 llegada
         * 09:09 fin servicio
         *
         * P3 está en (69,14).
         *
         * Sin refrigerio:
         *
         * 09:12 llegada
         * 10:12 fin servicio
         *
         * Eso empuja el inicio del refrigerio más allá
         * del último inicio permitido.
         *
         * Por tanto el scheduler toma refrigerio
         * DESPUÉS DE P2:
         *
         * 09:09 -> 10:09.
         *
         * A las 10:10:
         *
         * el refrigerio ya ocurrió y el vehículo acaba
         * de empezar el viaje hacia P3.
         */

        Instant ahora =
                T0.plus(
                        Duration.ofMinutes(
                                310
                        )
                );

        /*
         * 05:00 + 5h10 = 10:10.
         */

        List<RouteStop> futuro =
                List.of(
                        new DeliveryStop(
                                fixture.p3(),
                                1
                        ),

                        new WarehouseVisit(
                                fixture.central(),
                                0
                        )
                );

        ContinuacionRutaService.Resultado resultado =
                continuaciones.reprogramar(
                        fixture.vigente(),
                        ahora,
                        futuro,
                        fixture.snapshot(),
                        List.of()
                );

        /*
         * El checkpoint debe detectar el refrigerio
         * que quedó dentro del prefijo ejecutado.
         */

        assertTrue(
                resultado
                        .checkpoint()
                        .turnoConRefrigerioTomado()
                        .isPresent(),
                "el checkpoint debe recordar el refrigerio flexible tomado después de P2"
        );

        /*
         * P3 se mantiene en el futuro.
         */

        ScheduledRouteStop p3 =
                resultado
                        .programada()
                        .scheduledStops()
                        .stream()

                        .filter(
                                parada ->
                                        parada.stop()
                                                instanceof
                                                DeliveryStop
                        )

                        .filter(
                                parada ->
                                        (
                                                (
                                                        DeliveryStop
                                                ) parada.stop()
                                        )
                                                .order()
                                                .id()
                                                .equals(
                                                        "P3"
                                                )
                        )

                        .findFirst()

                        .orElseThrow();

        /*
         * Como el refrigerio del turno ya ocurrió,
         * P3 debe contener únicamente su tiempo
         * normal de servicio.
         */

        assertEquals(
                DeliveryStop.SERVICE_TIME,

                Duration.between(
                        p3.arrivedAt(),
                        p3.completedAt()
                ),

                "la continuación no debe volver a programar refrigerio en P3"
        );
    }

    /*
     * =====================================================
     * FIXTURE BÁSICO
     * =====================================================
     */

    private Fixture fixture() {

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",

                        new Location(
                                27,
                                14
                        )
                );

        Vehicle vehiculo =
                new Vehicle(
                        "TA01",
                        VehicleType.CAR,
                        true
                );

        Order p1 =
                new Order(
                        "P1",

                        new Location(
                                30,
                                14
                        ),

                        4,

                        T0,

                        T0.plus(
                                Duration.ofHours(
                                        12
                                )
                        )
                );

        OperationalSnapshot snapshot =
                snapshot(
                        central,
                        vehiculo
                );

        DeliveryRoute ruta =
                DeliveryRoute
                        .startScenarioAtCentral(
                                "ALNS-TA01",
                                vehiculo,
                                central,
                                5,
                                T0
                        )

                        .withAppendedStop(
                                new DeliveryStop(
                                        p1,
                                        4
                                )
                        )

                        .returningTo(
                                central
                        );

        ScheduledDeliveryRoute programada =
                scheduler.schedule(
                        ruta,
                        snapshot,
                        List.of()
                );

        RutaVigente vigente =
                new RutaVigente(
                        ruta,
                        snapshot,
                        programada,
                        List.of()
                );

        return new Fixture(
                central,
                vehiculo,
                p1,
                null,
                null,
                ruta,
                snapshot,
                vigente
        );
    }

    /*
     * =====================================================
     * FIXTURE DE REFRIGERIO FLEXIBLE
     * =====================================================
     */

    private Fixture fixtureConRefrigerioFlexible() {

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",

                        new Location(
                                27,
                                14
                        )
                );

        Vehicle vehiculo =
                new Vehicle(
                        "TA01",
                        VehicleType.CAR,
                        true
                );

        Order p1 =
                new Order(
                        "P1",

                        new Location(
                                30,
                                14
                        ),

                        4,

                        T0,

                        T0.plus(
                                Duration.ofHours(
                                        12
                                )
                        )
                );

        /*
         * P2 está lejos.
         *
         * Esto hace que al terminar P2 quede poco
         * margen antes del último inicio de refrigerio.
         */
        Order p2 =
                new Order(
                        "P2",

                        new Location(
                                70,
                                14
                        ),

                        1,

                        T0,

                        T0.plus(
                                Duration.ofHours(
                                        12
                                )
                        )
                );

        /*
         * P3 está apenas una calle después de P2.
         *
         * El look-ahead desde P2 detecta:
         *
         * viajar a P3
         * +
         * atender P3
         *
         * haría demasiado tarde iniciar el refrigerio.
         */
        Order p3 =
                new Order(
                        "P3",

                        new Location(
                                69,
                                14
                        ),

                        1,

                        T0,

                        T0.plus(
                                Duration.ofHours(
                                        12
                                )
                        )
                );

        OperationalSnapshot snapshot =
                snapshot(
                        central,
                        vehiculo
                );

        DeliveryRoute ruta =
                DeliveryRoute
                        .startScenarioAtCentral(
                                "ALNS-TA01",
                                vehiculo,
                                central,
                                6,
                                T0
                        )

                        .withAppendedStop(
                                new DeliveryStop(
                                        p1,
                                        4
                                )
                        )

                        .withAppendedStop(
                                new DeliveryStop(
                                        p2,
                                        1
                                )
                        )

                        .withAppendedStop(
                                new DeliveryStop(
                                        p3,
                                        1
                                )
                        )

                        .returningTo(
                                central
                        );

        ScheduledDeliveryRoute programada =
                scheduler.schedule(
                        ruta,
                        snapshot,
                        List.of()
                );

        RutaVigente vigente =
                new RutaVigente(
                        ruta,
                        snapshot,
                        programada,
                        List.of()
                );

        return new Fixture(
                central,
                vehiculo,
                p1,
                p2,
                p3,
                ruta,
                snapshot,
                vigente
        );
    }

    /*
     * =====================================================
     * SNAPSHOT
     * =====================================================
     */

    private OperationalSnapshot snapshot(
            Warehouse central,
            Vehicle vehiculo
    ) {

        Map<String, VehicleOperationalState> estados =
                new LinkedHashMap<>();

        estados.put(
                vehiculo.id(),

                new VehicleOperationalState(
                        vehiculo,
                        VehicleStatus.AVAILABLE,
                        central.location(),
                        T0
                )
        );

        return new OperationalSnapshot(
                T0,

                FleetProfile.defaults(),

                InventorySnapshot.from(
                        List.of(
                                central
                        )
                ),

                estados,

                new MaintenanceCalendar(
                        ShiftSchedule.DEFAULT_ZONE,
                        List.of()
                ),

                ShiftSchedule.defaultSchedule(),

                List.of()
        );
    }

    private record Fixture(
            Warehouse central,
            Vehicle vehiculo,
            Order p1,
            Order p2,
            Order p3,
            DeliveryRoute ruta,
            OperationalSnapshot snapshot,
            RutaVigente vigente
    ) {
    }
}