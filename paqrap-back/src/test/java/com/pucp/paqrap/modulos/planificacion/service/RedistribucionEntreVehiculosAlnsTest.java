package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

class RedistribucionEntreVehiculosAlnsTest {

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
    void pedidoDesplazadoDeTa01EsReasignadoATb01EnElMismoCiclo() {

        /*
         * =================================================
         * INFRAESTRUCTURA
         * =================================================
         */

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
         * IMPORTANTE:
         *
         * Las bicicletas deben usar IDs TB##.
         *
         * Usamos bicicleta porque:
         *
         * - puede transportar P1 = 4 paquetes;
         * - es demasiado lenta para salvar al CRITICO;
         * - sí puede absorber P1 posteriormente.
         */
        Vehicle tb01 =
                new Vehicle(
                        "TB01",
                        VehicleType.BICYCLE,
                        true
                );

        OperationalSnapshot snapshot =
                snapshot(
                        central,
                        ta01,
                        tb01
                );

        /*
         * =================================================
         * PEDIDOS DE TA01
         * =================================================
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
         * P1:
         *
         * todavía no está cargado en TA01.
         *
         * Podrá ser desplazado y posteriormente
         * absorbido por TB01.
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
                                        2
                                )
                        )
                );

        /*
         * =================================================
         * RUTA TA01
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

        DeliveryRoute rutaTa01 =
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

        ScheduledDeliveryRoute programadaTa01 =
                scheduler.schedule(
                        rutaTa01,
                        snapshot,
                        List.of()
                );

        /*
         * =================================================
         * PEDIDO Y RUTA TB01
         * =================================================
         */

        Order p2 =
                new Order(
                        "P2",

                        new Location(
                                27,
                                16
                        ),

                        1,

                        T0,

                        T0.plus(
                                Duration.ofHours(
                                        10
                                )
                        )
                );

        DeliveryRoute rutaTb01 =
                DeliveryRoute
                        .startScenarioAtCentral(
                                "ALNS-TB01",
                                tb01,
                                central,
                                1,
                                T0
                        )

                        .withAppendedStop(
                                new DeliveryStop(
                                        p2,
                                        1
                                )
                        )

                        .returningTo(
                                central
                        );

        ScheduledDeliveryRoute programadaTb01 =
                scheduler.schedule(
                        rutaTb01,
                        snapshot,
                        List.of()
                );

        RutaVigente vigenteTa01 =
                new RutaVigente(
                        rutaTa01,
                        snapshot,
                        programadaTa01,
                        List.of()
                );

        RutaVigente vigenteTb01 =
                new RutaVigente(
                        rutaTb01,
                        snapshot,
                        programadaTb01,
                        List.of()
                );

        /*
         * =================================================
         * LLEGA PEDIDO CRÍTICO
         * =================================================
         */

        Instant ahora =
                T0.plus(
                        Duration.ofMinutes(
                                2
                        )
                );

        /*
         * TA01 sí puede salvarlo.
         *
         * TB01 es demasiado lenta para hacerlo dentro
         * del deadline.
         */
        Order critico =
                new Order(
                        "CRITICO",

                        new Location(
                                24,
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
         * REPLANIFICACIÓN
         * =================================================
         */

        InsercionRutasActivasAlns.Resultado resultado =
                insercion.insertar(
                        ahora,

                        List.of(
                                vigenteTa01,
                                vigenteTb01
                        ),

                        List.of(
                                critico
                        ),

                        central,

                        List.of(),

                        Long.MAX_VALUE
                );

        /*
         * =================================================
         * NADA PENDIENTE
         * =================================================
         */

        assertTrue(
                resultado
                        .pendientes()
                        .isEmpty(),

                () ->
                        "pendientes: "
                                +
                                resultado
                                        .pendientes()
                                        .stream()
                                        .map(
                                                Order::id
                                        )
                                        .toList()
        );

        /*
         * =================================================
         * IDENTIFICAR RUTAS
         * =================================================
         */

        RutaVigente nuevaTa01 =
                resultado
                        .rutas()
                        .stream()

                        .filter(
                                ruta ->
                                        ruta
                                                .ruta()
                                                .vehicle()
                                                .id()
                                                .equals(
                                                        "TA01"
                                                )
                        )

                        .findFirst()
                        .orElseThrow();

        RutaVigente nuevaTb01 =
                resultado
                        .rutas()
                        .stream()

                        .filter(
                                ruta ->
                                        ruta
                                                .ruta()
                                                .vehicle()
                                                .id()
                                                .equals(
                                                        "TB01"
                                                )
                        )

                        .findFirst()
                        .orElseThrow();

        /*
         * =================================================
         * TA01
         * =================================================
         */

        assertTrue(
                contienePedido(
                        nuevaTa01,
                        "CRITICO"
                ),
                "TA01 debe atender el pedido crítico"
        );

        assertTrue(
                contienePedido(
                        nuevaTa01,
                        "P0"
                ),
                "P0 debe permanecer en TA01"
        );

        assertFalse(
                contienePedido(
                        nuevaTa01,
                        "P1"
                ),
                "P1 debe salir de TA01"
        );

        /*
         * La carga exclusiva +4 de P1 también debe
         * desaparecer de TA01.
         */
        assertFalse(
                nuevaTa01
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
         * TB01
         * =================================================
         */

        assertTrue(
                contienePedido(
                        nuevaTb01,
                        "P2"
                ),
                "P2 debe permanecer en TB01"
        );

        /*
         * Propiedad principal:
         *
         * P1 sale de TA01 y entra en TB01 en la misma
         * llamada a insertar(...).
         */
        assertTrue(
                contienePedido(
                        nuevaTb01,
                        "P1"
                ),
                "P1 debe redistribuirse de TA01 a TB01 en el mismo ciclo"
        );

        /*
         * TB01 debe ir al almacén por los 4 paquetes.
         */
        assertTrue(
                nuevaTb01
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
                        ),
                "TB01 debe recoger físicamente los 4 paquetes de P1"
        );

        /*
         * =================================================
         * RESULTADO DEL CICLO
         * =================================================
         */

        assertTrue(
                resultado
                        .pedidosInsertados()
                        .contains(
                                "CRITICO"
                        )
        );

        assertTrue(
                resultado
                        .pedidosInsertados()
                        .contains(
                                "P1"
                        )
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
            Vehicle... vehiculos
    ) {

        Map<
                String,
                VehicleOperationalState
                > estados =
                new LinkedHashMap<>();

        for (
                Vehicle vehiculo :
                vehiculos
        ) {

            estados.put(
                    vehiculo.id(),

                    new VehicleOperationalState(
                            vehiculo,
                            VehicleStatus.AVAILABLE,
                            central.location(),
                            T0
                    )
            );
        }

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