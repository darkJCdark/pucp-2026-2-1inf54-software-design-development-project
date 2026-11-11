package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

class OperationalAlnsPlannerTest {

    private static final Instant INICIO =
            Instant.parse(
                    "2026-09-09T12:00:00Z"
            );

    @Test
    void generaUnPlanFactibleYResultadoComun() {

        Escenario escenario =
                escenarioNormal();

        ResultadoPlanificacion resultado =
                planificador().planificar(
                        escenario.snapshot(),
                        escenario.orders(),
                        List.of(),
                        configuracion(40),
                        random(7L)
                );

        assertInstanceOf(
                ResultadoPlanificacion.class,
                resultado
        );

        assertTrue(
                resultado.esFactible(),
                () ->
                        "violaciones: " +
                                resultado
                                        .evaluacion()
                                        .violations()
        );

        assertTrue(
                resultado
                        .noAtendidos()
                        .isEmpty()
        );

        assertFalse(
                resultado
                        .plan()
                        .routes()
                        .isEmpty()
        );
    }

    /**
     * Con el refrigerio flexible, ambos pedidos son factibles
     * si el urgente se atiende primero.
     *
     * Antes el refrigerio se tomaba en la primera oportunidad
     * y FLEXIBLE quedaba fuera.
     *
     * Ahora el descanso puede postergarse dentro de su ventana,
     * por lo que se atienden ambos.
     */
    @Test
    void priorizaElUrgenteYConRefrigerioFlexibleAtiendeAmbos() {

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",
                        new Location(
                                0,
                                0
                        )
                );

        Vehicle auto =
                new Vehicle(
                        "TA01",
                        VehicleType.CAR,
                        true
                );

        Order urgente =
                new Order(
                        "URGENTE",
                        new Location(
                                0,
                                50
                        ),
                        24,
                        INICIO,
                        INICIO.plusSeconds(
                                4 * 3600
                        )
                );

        Order flexible =
                new Order(
                        "FLEXIBLE",
                        new Location(
                                0,
                                50
                        ),
                        24,
                        INICIO,
                        INICIO.plusSeconds(
                                5 * 3600
                        )
                );

        ResultadoPlanificacion resultado =
                planificador().planificar(
                        snapshot(
                                central,
                                List.of(
                                        auto
                                )
                        ),

                        /*
                         * Se entregan intencionalmente
                         * en orden contrario.
                         */
                        List.of(
                                flexible,
                                urgente
                        ),

                        List.of(),

                        configuracion(20),

                        random(57L)
                );

        assertTrue(
                resultado.esFactible(),
                () ->
                        "violaciones: " +
                                resultado
                                        .evaluacion()
                                        .violations()
        );

        /*
         * Con refrigerio flexible ya no necesitamos
         * sacrificar FLEXIBLE.
         */
        assertTrue(
                resultado
                        .noAtendidos()
                        .isEmpty()
        );

        /*
         * Aunque ambos se atiendan, URGENTE debe
         * permanecer antes de FLEXIBLE.
         *
         * Si FLEXIBLE fuese primero, URGENTE ya
         * no cumpliría su deadline.
         */
        assertEquals(
                List.of(
                        "URGENTE",
                        "FLEXIBLE"
                ),

                resultado
                        .plan()
                        .routes()
                        .stream()

                        .flatMap(
                                ruta ->
                                        ruta
                                                .stops()
                                                .stream()
                        )

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

                        .map(
                                entrega ->
                                        entrega
                                                .order()
                                                .id()
                        )

                        .toList()
        );
    }

    @Test
    void nuncaEmpeoraElMejorPlanRespectoAlSeed() {

        Escenario escenario =
                escenarioNormal();

        OperationalAlnsPlanner.Ejecucion ejecucion =
                planificador().ejecutar(
                        escenario.snapshot(),
                        escenario.orders(),
                        List.of(),
                        configuracion(80),
                        random(19L),
                        -1L
                );

        assertTrue(
                ejecucion
                        .resultado()
                        .esFactible()
        );

        assertTrue(
                ejecucion
                        .resultado()
                        .costoTotal()
                        <=
                        ejecucion.initialCost()
                                +
                                1e-9,
                () ->
                        "seed=" +
                                ejecucion.initialCost()
                                +
                                ", final="
                                +
                                ejecucion
                                        .resultado()
                                        .costoTotal()
        );
    }

    @Test
    void mismaSemillaProduceElMismoResultado() {

        Escenario escenario =
                escenarioNormal();

        OperationalAlnsPlanner.Ejecucion primero =
                planificador().ejecutar(
                        escenario.snapshot(),
                        escenario.orders(),
                        List.of(),
                        configuracion(60),
                        random(77L),
                        -1L
                );

        OperationalAlnsPlanner.Ejecucion segundo =
                planificador().ejecutar(
                        escenario.snapshot(),
                        escenario.orders(),
                        List.of(),
                        configuracion(60),
                        random(77L),
                        -1L
                );

        assertEquals(
                primero
                        .resultado()
                        .costoTotal(),

                segundo
                        .resultado()
                        .costoTotal()
        );

        assertEquals(
                firma(
                        primero.resultado()
                ),

                firma(
                        segundo.resultado()
                )
        );

        assertEquals(
                primero
                        .resultado()
                        .noAtendidos(),

                segundo
                        .resultado()
                        .noAtendidos()
        );

        assertEquals(
                primero.iterations(),
                segundo.iterations()
        );

        assertEquals(
                primero.destroyWeights(),
                segundo.destroyWeights()
        );

        assertEquals(
                primero.repairWeights(),
                segundo.repairWeights()
        );
    }

    @Test
    void actualizaPesosDeOperadores() {

        Escenario escenario =
                escenarioNormal();

        OperationalAlnsPlanner.Ejecucion ejecucion =
                planificador().ejecutar(
                        escenario.snapshot(),
                        escenario.orders(),
                        List.of(),
                        configuracion(50),
                        random(21L),
                        -1L
                );

        assertTrue(
                ejecucion
                        .destroyWeights()
                        .containsKey(
                                "random-route-removal"
                        )
        );

        assertTrue(
                ejecucion
                        .destroyWeights()
                        .containsKey(
                                "worst-route-removal"
                        )
        );

        assertTrue(
                ejecucion
                        .repairWeights()
                        .containsKey(
                                "cheapest-insertion"
                        )
        );

        assertTrue(
                ejecucion
                        .repairWeights()
                        .containsKey(
                                "regret-2-insertion"
                        )
        );

        assertTrue(
                ejecucion
                        .destroyWeights()
                        .values()
                        .stream()
                        .allMatch(
                                peso ->
                                        peso > 0
                        )
        );

        assertTrue(
                ejecucion
                        .repairWeights()
                        .values()
                        .stream()
                        .allMatch(
                                peso ->
                                        peso > 0
                        )
        );
    }

    @Test
    void aceptaSiempreUnaSolucionMejor() {

        assertTrue(
                OperationalAlnsPlanner.accept(
                        -1.0,
                        10.0,
                        random(1L)
                )
        );
    }

    @Test
    void puedeAceptarUnaSolucionLigeramentePeor() {

        assertTrue(
                OperationalAlnsPlanner.accept(
                        Double.MIN_VALUE,
                        1.0,
                        random(2L)
                )
        );
    }

    @Test
    void ejecucionExponeMetricasDelAlns() {

        Escenario escenario =
                escenarioNormal();

        OperationalAlnsPlanner.Ejecucion ejecucion =
                planificador().ejecutar(
                        escenario.snapshot(),
                        escenario.orders(),
                        List.of(),
                        configuracion(50),
                        random(19L),
                        -1L
                );

        System.out.println();
        System.out.println(
                "===================================================="
        );

        System.out.println(
                "ALNS"
        );

        System.out.println(
                "===================================================="
        );

        System.out.printf(
                "Costo seed: S/ %.2f%n",
                ejecucion.initialCost()
        );

        System.out.printf(
                "Iteraciones: %d%n",
                ejecucion.iterations()
        );

        System.out.printf(
                "Candidatos evaluados: %d%n",
                ejecucion.evaluatedCandidates()
        );

        System.out.printf(
                "Candidatos aceptados: %d%n",
                ejecucion.acceptedCandidates()
        );

        System.out.printf(
                "Mejoras globales: %d%n",
                ejecucion.globalImprovements()
        );

        System.out.printf(
                "Temperatura final: %.4f%n",
                ejecucion.finalTemperature()
        );

        System.out.printf(
                "Costo final: S/ %.2f%n",
                ejecucion
                        .resultado()
                        .costoTotal()
        );

        System.out.printf(
                "Destroy weights: %s%n",
                ejecucion.destroyWeights()
        );

        System.out.printf(
                "Repair weights: %s%n",
                ejecucion.repairWeights()
        );

        System.out.printf(
                "No atendidos: %s%n",
                ejecucion
                        .resultado()
                        .noAtendidos()
                        .stream()
                        .map(
                                Order::id
                        )
                        .toList()
        );

        System.out.println(
                "===================================================="
        );

        assertTrue(
                ejecucion
                        .resultado()
                        .esFactible()
        );
    }

    private OperationalAlnsPlanner planificador() {

        return new OperationalAlnsPlanner(
                evaluador()
        );
    }

    private OperationalPlanEvaluator evaluador() {

        return new OperationalPlanEvaluator(
                new RouteScheduler(
                        new RoadNetwork()
                )
        );
    }

    private Escenario escenarioNormal() {

        Warehouse central =
                Warehouse.central(
                        "CENTRAL",
                        new Location(
                                0,
                                0
                        )
                );

        Vehicle auto =
                new Vehicle(
                        "TA01",
                        VehicleType.CAR,
                        true
                );

        Vehicle moto =
                new Vehicle(
                        "TM01",
                        VehicleType.MOTORCYCLE,
                        true
                );

        return new Escenario(
                snapshot(
                        central,
                        List.of(
                                auto,
                                moto
                        )
                ),

                List.of(
                        new Order(
                                "P01",
                                new Location(
                                        0,
                                        3
                                ),
                                12,
                                INICIO,
                                INICIO.plusSeconds(
                                        8 * 3600
                                )
                        ),

                        new Order(
                                "P02",
                                new Location(
                                        3,
                                        0
                                ),
                                2,
                                INICIO,
                                INICIO.plusSeconds(
                                        8 * 3600
                                )
                        )
                )
        );
    }

    private OperationalSnapshot snapshot(
            Warehouse central,
            List<Vehicle> flota
    ) {

        Map<
                String,
                VehicleOperationalState
                > states =
                flota
                        .stream()
                        .collect(
                                java.util.stream
                                        .Collectors
                                        .toMap(
                                                Vehicle::id,

                                                vehicle ->
                                                        new VehicleOperationalState(
                                                                vehicle,
                                                                VehicleStatus.AVAILABLE,
                                                                central.location(),
                                                                INICIO
                                                        )
                                        )
                        );

        Map<
                VehicleType,
                VehicleParameters
                > parameters =
                new EnumMap<>(
                        VehicleType.class
                );

        parameters.put(
                VehicleType.CAR,
                new VehicleParameters(
                        24,
                        40.0,
                        8.0
                )
        );

        parameters.put(
                VehicleType.MOTORCYCLE,
                new VehicleParameters(
                        8,
                        25.0,
                        6.0
                )
        );

        parameters.put(
                VehicleType.BICYCLE,
                new VehicleParameters(
                        4,
                        12.0,
                        3.0
                )
        );

        return new OperationalSnapshot(
                INICIO,

                new FleetProfile(
                        parameters
                ),

                InventorySnapshot.from(
                        List.of(
                                central
                        )
                ),

                states,

                new MaintenanceCalendar(
                        ShiftSchedule.DEFAULT_ZONE,
                        List.of()
                ),

                new ShiftSchedule(
                        ShiftSchedule.DEFAULT_ZONE
                ),

                List.of()
        );
    }

    private AlnsConfig configuracion(
            int maximumIterations
    ) {

        return new AlnsConfig(
                4,
                4,
                1,
                2,
                maximumIterations,
                maximumIterations,
                100.0,
                1.0,
                0.95,
                0.20
        );
    }

    private RandomGenerator random(
            long seed
    ) {

        return RandomGeneratorFactory
                .<RandomGenerator>of(
                        "L64X128MixRandom"
                )
                .create(
                        seed
                );
    }

    private List<String> firma(
            ResultadoPlanificacion resultado
    ) {

        return resultado
                .plan()
                .routes()
                .stream()

                .sorted(
                        Comparator.comparing(
                                route ->
                                        route
                                                .vehicle()
                                                .id()
                        )
                )

                .map(
                        route ->
                                route
                                        .vehicle()
                                        .id()
                                        +
                                        route
                                                .stops()
                )

                .toList();
    }

    private record Escenario(
            OperationalSnapshot snapshot,
            List<Order> orders
    ) {
    }
}