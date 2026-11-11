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
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

class InsercionRutasActivasAlnsDesvioTest {

    private static final Instant T0 =
            Instant.parse(
                    "2026-01-01T05:00:00Z"
            );

    private final RouteScheduler scheduler =
            new RouteScheduler(
                    new RoadNetwork()
            );

    private final OperationalPlanEvaluator evaluator =
            new OperationalPlanEvaluator(
                    scheduler
            );

    private final InsercionRutasActivasAlns insercion =
            new InsercionRutasActivasAlns(
                    evaluator
            );

    @Test
    void pedidoCriticoPuedeDesviarDesdeElSiguienteNodoSeguro() {

        Fixture fixture =
                fixture();

        /*
         * Auto a 20 km/h.
         *
         * CENTRAL -> P1:
         *
         * 27 -> 28 : 05:00 - 05:03
         * 28 -> 29 : 05:03 - 05:06
         * 29 -> 30 : 05:06 - 05:09
         *
         * A las 05:04 está en mitad del segundo tramo.
         */
        Instant ahora =
                T0.plus(
                        Duration.ofMinutes(
                                4
                        )
                );

        /*
         * El pedido tiene slack negativo/critico.
         *
         * Si espera a P1, ya no llega.
         *
         * Si se desvía desde (29,14), sí llega.
         */
        Order critico =
                new Order(
                        "CRITICO",

                        new Location(
                                29,
                                13
                        ),

                        1,

                        ahora,

                        T0.plus(
                                Duration.ofHours(
                                        1
                                )
                        )
                );

        InsercionRutasActivasAlns.Resultado resultado =
                insercion.insertar(
                        ahora,

                        List.of(
                                fixture.vigente()
                        ),

                        List.of(
                                critico
                        ),

                        fixture.central(),

                        List.of(),

                        Long.MAX_VALUE
                );

        assertTrue(
                resultado
                        .pendientes()
                        .isEmpty()
        );

        assertEquals(
                List.of(
                        "CRITICO"
                ),

                resultado
                        .pedidosInsertados()
        );

        RutaVigente nueva =
                resultado
                        .rutas()
                        .getFirst();

        /*
         * La ruta sigue siendo la misma operación.
         */
        assertEquals(
                fixture
                        .ruta()
                        .id(),

                nueva
                        .ruta()
                        .id()
        );

        assertEquals(
                fixture
                        .ruta()
                        .departureAt(),

                nueva
                        .ruta()
                        .departureAt()
        );

        /*
         * El pedido crítico pasa antes de P1.
         */
        DeliveryStop primera =
                (DeliveryStop) nueva
                        .ruta()
                        .stops()
                        .get(0);

        DeliveryStop segunda =
                (DeliveryStop) nueva
                        .ruta()
                        .stops()
                        .get(1);

        assertEquals(
                "CRITICO",
                primera
                        .order()
                        .id()
        );

        assertEquals(
                "P1",
                segunda
                        .order()
                        .id()
        );

        Instant llegadaCritico =
                nueva
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
                                                        "CRITICO"
                                                )
                        )

                        .findFirst()

                        .orElseThrow()

                        .arrivedAt();

        assertTrue(
                !llegadaCritico.isAfter(
                        critico.deadline()
                )
        );

        /*
         * El recorrido pasado continúa presente.
         *
         * No hay teletransporte.
         */
        assertEquals(
                fixture
                        .central()
                        .location(),

                nueva
                        .programada()
                        .scheduledStops()
                        .getFirst()
                        .approach()
                        .origin()
        );

        assertEquals(
                new Location(
                        27,
                        14
                ),

                nueva
                        .programada()
                        .scheduledStops()
                        .getFirst()
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

                nueva
                        .programada()
                        .scheduledStops()
                        .getFirst()
                        .approach()
                        .legs()
                        .get(0)
                        .to()
        );
    }

    @Test
    void pedidoNormalNoRompeLaParadaComprometida() {

        Fixture fixture =
                fixture();

        Instant ahora =
                T0.plus(
                        Duration.ofMinutes(
                                4
                        )
                );

        Order normal =
                new Order(
                        "NORMAL",

                        new Location(
                                29,
                                13
                        ),

                        1,

                        ahora,

                        T0.plus(
                                Duration.ofHours(
                                        10
                                )
                        )
                );

        InsercionRutasActivasAlns.Resultado resultado =
                insercion.insertar(
                        ahora,

                        List.of(
                                fixture.vigente()
                        ),

                        List.of(
                                normal
                        ),

                        fixture.central(),

                        List.of(),

                        Long.MAX_VALUE
                );

        assertTrue(
                resultado
                        .pendientes()
                        .isEmpty()
        );

        RutaVigente nueva =
                resultado
                        .rutas()
                        .getFirst();

        DeliveryStop primera =
                (DeliveryStop) nueva
                        .ruta()
                        .stops()
                        .get(0);

        DeliveryStop segunda =
                (DeliveryStop) nueva
                        .ruta()
                        .stops()
                        .get(1);

        /*
         * P1 sigue comprometido.
         */
        assertEquals(
                "P1",
                primera
                        .order()
                        .id()
        );

        assertEquals(
                "NORMAL",
                segunda
                        .order()
                        .id()
        );
    }

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

        /*
         * Lleva 5:
         *
         * 4 para P1
         * 1 que puede utilizar el pedido crítico.
         */
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

        return new Fixture(
                central,

                ruta,

                new RutaVigente(
                        ruta,
                        snapshot,
                        programada,
                        List.of()
                )
        );
    }

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
            DeliveryRoute ruta,
            RutaVigente vigente
    ) {
    }
}