package com.pucp.paqrap.modulos.planificacion.algoritmo.common;

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
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;

class WarehouseSelectionPolicyPressureTest {

    private static final Instant T0 =
            Instant.parse("2028-12-01T10:00:00Z");

    private final WarehouseSelectionPolicy policy =
            new WarehouseSelectionPolicy();

    private static OperationalSnapshot snapshot(
            int parked
    ) {

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",
                        new Location(0, 0)
                );

        Warehouse east =
                Warehouse.intermediate(
                        "EAST",
                        new Location(2, 0),
                        100
                );

        Warehouse northwest =
                Warehouse.intermediate(
                        "NORTHWEST",
                        new Location(4, 0),
                        100
                );

        Map<String, VehicleOperationalState> states =
                new LinkedHashMap<>();

        for (
                int n = 0;
                n < parked;
                n++
        ) {

            Vehicle vehicle =
                    new Vehicle(
                            "TA0" + (n + 1),
                            VehicleType.CAR,
                            true
                    );

            states.put(
                    vehicle.id(),
                    new VehicleOperationalState(
                            vehicle,
                            VehicleStatus.AVAILABLE,
                            east.location(),
                            0,
                            T0.minusSeconds(3600)
                    )
            );
        }

        return new OperationalSnapshot(
                T0,
                FleetProfile.defaults(),
                InventorySnapshot.from(
                        List.of(
                                central,
                                east,
                                northwest
                        )
                ),
                states,
                new MaintenanceCalendar(
                        ShiftSchedule.DEFAULT_ZONE,
                        List.of()
                ),
                ShiftSchedule.defaultSchedule(),
                List.of()
        );
    }

    /**
     * Cuando EAST ya tiene la cantidad preferida de vehículos,
     * no seguimos enviando unidades allí únicamente porque esté
     * un poco más cerca.
     */
    @Test
    void noMueveVehiculosSoloParaCumplirElCupoPreferente() {

        OperationalSnapshot snapshot =
                snapshot(3);

        Order regular =
                new Order(
                        "P1",
                        new Location(2, 0),
                        1,
                        T0,
                        T0.plus(
                                Duration.ofHours(12)
                        )
                );

        Warehouse terminal =
                policy.bestTerminalWarehouse(
                        regular.destination(),
                        snapshot,
                        OperationalPlan.empty(),
                        regular
                );

        assertEquals(
                "CENTRAL",
                terminal.id()
        );
    }

    /**
     * Un pedido urgente ya NO desactiva el control de ocupación.
     *
     * Esta es la corrección que evita que EAST o NORTHWEST
     * terminen acumulando 8-10 vehículos durante FIVE_DAY.
     */
    @Test
    void emergenciaTambienRespetaLaPresionDelAlmacen() {

        OperationalSnapshot snapshot =
                snapshot(3);

        Order urgent =
                new Order(
                        "P2",
                        new Location(2, 0),
                        1,
                        T0,
                        T0.plus(
                                Duration.ofHours(4)
                        )
                );

        Warehouse terminal =
                policy.bestTerminalWarehouse(
                        urgent.destination(),
                        snapshot,
                        OperationalPlan.empty(),
                        urgent
                );

        assertEquals(
                "CENTRAL",
                terminal.id()
        );
    }

    /**
     * La política sigue siendo blanda.
     *
     * Si Central está realmente muy lejos, un intermedio puede
     * continuar siendo la mejor alternativa incluso cuando ya
     * alcanzó la ocupación preferida.
     */
    @Test
    void siOtraBaseEstaMuyLejosSeConservaElAlmacenMasCercano() {

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",
                        new Location(0, 0)
                );

        Warehouse east =
                Warehouse.intermediate(
                        "EAST",
                        new Location(30, 0),
                        100
                );

        Warehouse northwest =
                Warehouse.intermediate(
                        "NORTHWEST",
                        new Location(60, 0),
                        100
                );

        Map<String, VehicleOperationalState> states =
                new LinkedHashMap<>();

        for (
                int n = 1;
                n <= 3;
                n++
        ) {

            Vehicle vehicle =
                    new Vehicle(
                            "TA0" + n,
                            VehicleType.CAR,
                            true
                    );

            states.put(
                    vehicle.id(),
                    new VehicleOperationalState(
                            vehicle,
                            VehicleStatus.AVAILABLE,
                            east.location(),
                            0,
                            T0.minusSeconds(3600)
                    )
            );
        }

        OperationalSnapshot snapshot =
                new OperationalSnapshot(
                        T0,
                        FleetProfile.defaults(),
                        InventorySnapshot.from(
                                List.of(
                                        central,
                                        east,
                                        northwest
                                )
                        ),
                        states,
                        new MaintenanceCalendar(
                                ShiftSchedule.DEFAULT_ZONE,
                                List.of()
                        ),
                        ShiftSchedule.defaultSchedule(),
                        List.of()
                );

        Order regular =
                new Order(
                        "P3",
                        new Location(30, 0),
                        1,
                        T0,
                        T0.plus(
                                Duration.ofHours(12)
                        )
                );

        Warehouse terminal =
                policy.bestTerminalWarehouse(
                        regular.destination(),
                        snapshot,
                        OperationalPlan.empty(),
                        regular
                );

        assertEquals(
                "EAST",
                terminal.id()
        );

        assertTrue(
                policy
                        .projectedIdleByWarehouse(
                                snapshot,
                                OperationalPlan.empty()
                        )
                        .get("EAST")
                        ==
                        3
        );
    }

    /**
     * Con poca ocupación y una ventaja geográfica suficientemente
     * grande, EAST sigue siendo válido.
     *
     * Esto comprueba que no hemos convertido la política en
     * "todo vuelve al Central".
     */
    @Test
    void intermedioSigueUsandoseCuandoRealmenteConviene() {

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",
                        new Location(0, 0)
                );

        Warehouse east =
                Warehouse.intermediate(
                        "EAST",
                        new Location(20, 0),
                        100
                );

        Warehouse northwest =
                Warehouse.intermediate(
                        "NORTHWEST",
                        new Location(50, 0),
                        100
                );

        OperationalSnapshot snapshot =
                new OperationalSnapshot(
                        T0,
                        FleetProfile.defaults(),
                        InventorySnapshot.from(
                                List.of(
                                        central,
                                        east,
                                        northwest
                                )
                        ),
                        Map.of(),
                        new MaintenanceCalendar(
                                ShiftSchedule.DEFAULT_ZONE,
                                List.of()
                        ),
                        ShiftSchedule.defaultSchedule(),
                        List.of()
                );

        Order order =
                new Order(
                        "P4",
                        new Location(20, 0),
                        1,
                        T0,
                        T0.plus(
                                Duration.ofHours(12)
                        )
                );

        Warehouse terminal =
                policy.bestTerminalWarehouse(
                        order.destination(),
                        snapshot,
                        OperationalPlan.empty(),
                        order
                );

        assertEquals(
                "EAST",
                terminal.id()
        );
    }
}