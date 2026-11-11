package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.planificacion.policy.PoliticaPlanificacion;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

class UrgencyRemovalAlnsServiceTest {

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

    private final UrgencyRemovalAlnsService servicio =
            new UrgencyRemovalAlnsService(
                    evaluator,
                    new PoliticaPlanificacion()
            );

    @Test
    void criticoPuedeDesplazarPedidoFuturoQueTodaviaNoFueCargado() {

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",
                        new Location(
                                27,
                                14
                        )
                );

        Vehicle ta01 =
                new Vehicle(
                        "TA01",
                        VehicleType.CAR,
                        true
                );

        /*
         * =================================================
         * PEDIDO YA CARGADO Y COMPROMETIDO
         * =================================================
         *
         * El vehículo sale con exactamente los 2 paquetes
         * de P0.
         */
        Order p0 =
                new Order(
                        "P0",

                        new Location(
                                30,
                                14
                        ),

                        2,

                        T0,

                        T0.plus(
                                Duration.ofHours(
                                        12
                                )
                        )
                );

        /*
         * =================================================
         * PEDIDO FUTURO TODAVÍA NO CARGADO
         * =================================================
         *
         * P1 se carga recién después de atender P0.
         *
         * Deadline = 08:40.
         *
         * La ruta original puede atenderlo.
         *
         * Pero después de introducir el pedido crítico,
         * mantener P1 en TA01 vuelve muy difícil cumplir
         * ambos compromisos.
         */
        Order p1 =
                new Order(
                        "P1",

                        new Location(
                                34,
                                14
                        ),

                        4,

                        T0,

                        T0.plus(
                                Duration.ofMinutes(
                                        220
                                )
                        )
                );

        OperationalSnapshot snapshot =
                snapshot(
                        central,
                        ta01
                );

        /*
         * =================================================
         * RUTA ORIGINAL
         * =================================================
         *
         * CENTRAL
         * ↓ carga inicial 2
         * P0
         * ↓
         * CENTRAL +4
         * ↓
         * P1
         * ↓
         * CENTRAL
         */

        DeliveryRoute ruta =
                DeliveryRoute
                        .startScenarioAtCentral(
                                "ALNS-TA01",
                                ta01,
                                central,
                                2,
                                T0
                        )

                        .withAppendedStop(
                                new DeliveryStop(
                                        p0,
                                        2
                                )
                        )

                        /*
                         * P1 todavía NO está cargado.
                         */
                        .withAppendedStop(
                                new WarehouseVisit(
                                        central,
                                        4
                                )
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

        /*
         * Confirmamos que la ruta original sí podía
         * entregar P1 a tiempo.
         */
        Instant llegadaOriginalP1 =
                programada
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
                                                        "P1"
                                                )
                        )

                        .findFirst()
                        .orElseThrow()
                        .arrivedAt();

        assertTrue(
                !llegadaOriginalP1.isAfter(
                        p1.deadline()
                ),
                "P1 debe ser factible en la ruta original"
        );

        RutaVigente vigente =
                new RutaVigente(
                        ruta,
                        snapshot,
                        programada,
                        List.of()
                );

        /*
         * =================================================
         * INSTANTE DEL EVENTO
         * =================================================
         *
         * CAR = 20 km/h.
         *
         * Cada calle de 1 km tarda 3 minutos.
         *
         * A las 05:02:
         *
         * CENTRAL(27,14)
         *      ↓
         * vehículo todavía viaja hacia (28,14)
         *
         * El siguiente nodo seguro será alcanzado
         * a las 05:03.
         */

        Instant ahora =
                T0.plus(
                        Duration.ofMinutes(
                                2
                        )
                );

        /*
         * =================================================
         * PEDIDO CRÍTICO
         * =================================================
         *
         * Está cerca del próximo checkpoint.
         *
         * Desde (28,14):
         *
         * ir al central:
         * 3 min
         *
         * central → (26,14):
         * 3 min
         *
         * llegada aproximada:
         * 05:09
         *
         * Deadline:
         * 05:20
         *
         * Por tanto físicamente sí puede salvarse.
         */

        Order critico =
                new Order(
                        "CRITICO",

                        new Location(
                                26,
                                14
                        ),

                        1,

                        ahora,

                        T0.plus(
                                Duration.ofMinutes(
                                        20
                                )
                        )
                );

        /*
         * =================================================
         * URGENCY REMOVAL
         * =================================================
         */

        var resultado =
                servicio.intentar(
                        ahora,

                        List.of(
                                vigente
                        ),

                        0,

                        critico,

                        central,

                        List.of(),

                        Long.MAX_VALUE
                );

        assertTrue(
                resultado.isPresent(),
                "Debe existir una redistribución factible"
        );

        UrgencyRemovalAlnsService.Resultado redistribuido =
                resultado.orElseThrow();

        /*
         * =================================================
         * P1 FUE DESPLAZADO
         * =================================================
         */

        assertEquals(
                "P1",
                redistribuido
                        .desplazado()
                        .id()
        );

        /*
         * =================================================
         * CRÍTICO QUEDA EN TA01
         * =================================================
         */

        assertTrue(
                contienePedido(
                        redistribuido.ruta(),
                        "CRITICO"
                )
        );

        /*
         * =================================================
         * P0 SIGUE PRESENTE
         * =================================================
         *
         * Ya estaba cargado y comprometido.
         */

        assertTrue(
                contienePedido(
                        redistribuido.ruta(),
                        "P0"
                )
        );

        /*
         * =================================================
         * P1 SALE COMPLETAMENTE DE TA01
         * =================================================
         */

        assertFalse(
                contienePedido(
                        redistribuido.ruta(),
                        "P1"
                )
        );

        /*
         * =================================================
         * TAMBIÉN DESAPARECE SU CARGA +4
         * =================================================
         *
         * No tendría sentido mantener:
         *
         * CENTRAL +4
         *
         * si P1 ya no pertenece a TA01.
         */

        assertFalse(
                redistribuido
                        .ruta()
                        .ruta()
                        .stops()
                        .stream()

                        .filter(
                                WarehouseVisit
                                        .class::
                                        isInstance
                        )

                        .map(
                                WarehouseVisit
                                        .class::
                                        cast
                        )

                        .anyMatch(
                                visita ->
                                        visita
                                                .pickupPackages()
                                                ==
                                                4
                        )
        );

        /*
         * =================================================
         * EL CRÍTICO CUMPLE SLA
         * =================================================
         */

        assertTrue(
                !redistribuido
                        .llegadaCritico()
                        .isAfter(
                                critico.deadline()
                        )
        );
    }

    @Test
    void noDesplazaPedidoQueYaEstaCargado() {

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",
                        new Location(
                                27,
                                14
                        )
                );

        Vehicle ta01 =
                new Vehicle(
                        "TA01",
                        VehicleType.CAR,
                        true
                );

        /*
         * P1 ya salió físicamente cargado.
         */
        Order p1 =
                new Order(
                        "P1",

                        new Location(
                                34,
                                14
                        ),

                        4,

                        T0,

                        T0.plus(
                                Duration.ofHours(
                                        6
                                )
                        )
                );

        OperationalSnapshot snapshot =
                snapshot(
                        central,
                        ta01
                );

        /*
         * La carga inicial ya contiene los 4 paquetes
         * de P1.
         *
         * No existe una WarehouseVisit(+4) futura.
         */
        DeliveryRoute ruta =
                DeliveryRoute
                        .startScenarioAtCentral(
                                "ALNS-TA01",
                                ta01,
                                central,
                                4,
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

        RutaVigente vigente =
                new RutaVigente(
                        ruta,

                        snapshot,

                        scheduler.schedule(
                                ruta,
                                snapshot,
                                List.of()
                        ),

                        List.of()
                );

        Instant ahora =
                T0.plus(
                        Duration.ofMinutes(
                                2
                        )
                );

        /*
         * También utilizamos un crítico físicamente
         * alcanzable.
         *
         * Así el resultado vacío se debe realmente a
         * que P1 ya está cargado, no a que el crítico
         * sea imposible.
         */
        Order critico =
                new Order(
                        "CRITICO",

                        new Location(
                                26,
                                14
                        ),

                        1,

                        ahora,

                        T0.plus(
                                Duration.ofMinutes(
                                        20
                                )
                        )
                );

        var resultado =
                servicio.intentar(
                        ahora,

                        List.of(
                                vigente
                        ),

                        0,

                        critico,

                        central,

                        List.of(),

                        Long.MAX_VALUE
                );

        assertTrue(
                resultado.isEmpty(),
                "P1 ya está físicamente cargado y no debe reasignarse mágicamente"
        );
    }

    /*
     * =====================================================
     * HELPERS
     * =====================================================
     */

    private boolean contienePedido(
            RutaVigente ruta,
            String pedidoId
    ) {

        return ruta
                .ruta()
                .stops()
                .stream()

                .filter(
                        DeliveryStop
                                .class::
                                isInstance
                )

                .map(
                        DeliveryStop
                                .class::
                                cast
                )

                .anyMatch(
                        entrega ->
                                entrega
                                        .order()
                                        .id()
                                        .equals(
                                                pedidoId
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
}