package com.pucp.paqrap.modulos.planificacion.algoritmo.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.redvial.entity.Location;

class WarehouseSelectionPolicyTest {

    private static final Instant T0 =
            Instant.parse(
                    "2028-12-01T00:00:00Z"
            );

    private final WarehouseSelectionPolicy policy =
            new WarehouseSelectionPolicy();

    @Test
    void eligeEsteCuandoReduceElTrayectoTotal() {

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",
                        new Location(27, 14)
                );

        Warehouse east =
                Warehouse.intermediate(
                        "EAST",
                        new Location(57, 27),
                        1_000
                );

        Warehouse northwest =
                Warehouse.intermediate(
                        "NORTHWEST",
                        new Location(12, 38),
                        1_000
                );

        InventorySnapshot inventory =
                InventorySnapshot.from(
                        List.of(
                                central,
                                east,
                                northwest
                        )
                );

        Order order =
                new Order(
                        "P1",
                        new Location(60, 27),
                        3,
                        T0,
                        T0.plus(
                                Duration.ofHours(8)
                        )
                );

        assertEquals(
                "EAST",
                policy
                        .bestSupplyWarehouse(
                                new Location(55, 27),
                                order,
                                3,
                                inventory
                        )
                        .id()
        );

        assertEquals(
                "EAST",
                policy
                        .bestTerminalWarehouse(
                                order.destination(),
                                inventory
                        )
                        .id()
        );
    }

    @Test
    void respetaStockYUsaCentralComoRespaldo() {

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",
                        new Location(27, 14)
                );

        Warehouse east =
                Warehouse.intermediate(
                        "EAST",
                        new Location(57, 27),
                        2
                );

        InventorySnapshot inventory =
                InventorySnapshot.from(
                        List.of(
                                central,
                                east
                        )
                );

        Order order =
                new Order(
                        "P2",
                        new Location(60, 27),
                        5,
                        T0,
                        T0.plus(
                                Duration.ofHours(8)
                        )
                );

        assertEquals(
                "CENTRAL",
                policy
                        .bestSupplyWarehouse(
                                new Location(55, 27),
                                order,
                                5,
                                inventory
                        )
                        .id()
        );
    }

    @Test
    void enEmpateConservaCentralParaNoDispersarSinBeneficio() {

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",
                        new Location(0, 0)
                );

        Warehouse intermediate =
                Warehouse.intermediate(
                        "INTERMEDIATE",
                        new Location(2, 0),
                        1_000
                );

        InventorySnapshot inventory =
                InventorySnapshot.from(
                        List.of(
                                central,
                                intermediate
                        )
                );

        assertEquals(
                "CENTRAL",
                policy
                        .bestTerminalWarehouse(
                                new Location(1, 0),
                                inventory
                        )
                        .id()
        );
    }
}
