package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.service.InicializadorFlota;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

/**
 * Benchmark manual de escalabilidad para ALNS.
 *
 * Este archivo termina en Benchmark para que no se
 * ejecute accidentalmente con toda la suite normal.
 *
 * Ejecutar manualmente:
 *
 * mvn test "-Dtest=EscalabilidadAlnsBenchmark"
 *
 * Presupuesto alternativo:
 *
 * mvn test "-Dtest=EscalabilidadAlnsBenchmark"
 *          "-Dpaqrap.benchmark.alns.presupuestoMs=5000"
 */
class EscalabilidadAlnsBenchmark {

    /*
     * =====================================================
     * ESCENARIO
     * =====================================================
     */

    private static final Instant INICIO =
            Instant.parse(
                    "2028-12-01T05:00:00Z"
            );

    private static final Warehouse CENTRAL =
            Warehouse.central(
                    "CENTRAL",
                    new Location(
                            27,
                            14
                    )
            );

    private static final Warehouse NOROESTE =
            Warehouse.intermediate(
                    "NOROESTE",
                    new Location(
                            12,
                            38
                    ),
                    1_000
            );

    private static final Warehouse ESTE =
            Warehouse.intermediate(
                    "ESTE",
                    new Location(
                            57,
                            27
                    ),
                    1_000
            );

    /*
     * Escalas objetivo.
     */
    private static final int[] ESCALAS = {
            100,
            500,
            1_000,
            2_500,
            5_000,
            10_000
    };

    /*
     * Semilla reproducible.
     */
    private static final long SEMILLA =
            20281201L;

    /*
     * =====================================================
     * BENCHMARK
     * =====================================================
     */

    @Test
    void benchmarkEscalabilidadDe100A10000Pedidos() {

        long presupuestoMs =
                presupuestoMs();

        AlnsConfig config =
                configuracion();

        System.out.println();
        System.out.println(
                "=============================================================================================================="
        );

        System.out.println(
                "                         BENCHMARK ESCALABILIDAD ALNS - PAQRAP"
        );

        System.out.println(
                "=============================================================================================================="
        );

        System.out.printf(
                "Fecha simulada:            %s%n",
                INICIO
        );

        System.out.printf(
                "Flota:                     %d vehículos%n",
                InicializadorFlota
                        .crearFlotaInicial()
                        .size()
        );

        System.out.printf(
                "Presupuesto por escala:    %d ms%n",
                presupuestoMs
        );

        System.out.printf(
                "Initial batch size:        %d%n",
                config.initialBatchSize()
        );

        System.out.printf(
                "Candidate vehicles:        %d%n",
                config.candidateVehicles()
        );

        System.out.printf(
                "Máximo iteraciones:        %d%n",
                config.maximumIterations()
        );

        System.out.println(
                "=============================================================================================================="
        );

        /*
         * Warm-up pequeño para reducir el efecto
         * de compilación JIT sobre la primera escala.
         */
        ejecutarWarmup(
                config
        );

        List<FilaBenchmark> resultados =
                new ArrayList<>();

        for (
                int cantidadPedidos :
                ESCALAS
        ) {

            System.out.println();
            System.out.printf(
                    ">>> Ejecutando %,d pedidos...%n",
                    cantidadPedidos
            );

            FilaBenchmark resultado =
                    ejecutarEscala(
                            cantidadPedidos,
                            presupuestoMs,
                            config
                    );

            resultados.add(
                    resultado
            );

            imprimirResultadoIndividual(
                    resultado
            );
        }

        imprimirTabla(
                resultados
        );

        imprimirCsv(
                resultados
        );

        assertEquals(
                ESCALAS.length,
                resultados.size()
        );
    }

    /*
     * =====================================================
     * EJECUCIÓN DE UNA ESCALA
     * =====================================================
     */

    private FilaBenchmark ejecutarEscala(
            int cantidadPedidos,
            long presupuestoMs,
            AlnsConfig config
    ) {

        /*
         * Sugiere GC antes de la medición.
         *
         * No garantiza memoria exacta, por eso las
         * métricas de heap son aproximadas.
         */
        System.gc();

        long memoriaAntesBytes =
                memoriaUsada();

        List<Order> pedidos =
                generarPedidos(
                        cantidadPedidos
                );

        OperationalSnapshot snapshot =
                snapshot();

        /*
         * IMPORTANTE:
         *
         * Utilizamos exactamente los constructores
         * disponibles en tu backend actual.
         */
        RouteScheduler scheduler =
                new RouteScheduler(
                        new RoadNetwork()
                );

        OperationalPlanEvaluator evaluator =
                new OperationalPlanEvaluator(
                        scheduler,
                        0
                );

        OperationalAlnsPlanner planner =
                new OperationalAlnsPlanner(
                        evaluator
                );

        long inicioNanos =
                System.nanoTime();

        OperationalAlnsPlanner.Ejecucion ejecucion =
                planner.ejecutar(
                        snapshot,
                        pedidos,
                        List.of(),
                        config,
                        new Random(
                                SEMILLA
                        ),
                        presupuestoMs
                );

        long duracionMs =
                (
                        System.nanoTime() -
                                inicioNanos
                )
                        /
                        1_000_000L;

        long memoriaDespuesBytes =
                memoriaUsada();

        int noAtendidos =
                ejecucion
                        .resultado()
                        .noAtendidos()
                        .size();

        int atendidos =
                cantidadPedidos -
                        noAtendidos;

        int rutas =
                ejecucion
                        .resultado()
                        .plan()
                        .routes()
                        .size();

        /*
         * =================================================
         * VALIDACIONES
         * =================================================
         */

        assertTrue(
                ejecucion
                        .resultado()
                        .esFactible(),
                () ->
                        "Plan no factible con "
                                +
                                cantidadPedidos
                                +
                                " pedidos. Violaciones: "
                                +
                                ejecucion
                                        .resultado()
                                        .evaluacion()
                                        .violations()
        );

        assertEquals(
                cantidadPedidos,
                atendidos +
                        noAtendidos,
                "La contabilidad de pedidos debe cerrar"
        );

        /*
         * Una ruta por vehículo como máximo.
         */
        assertTrue(
                rutas <=
                        InicializadorFlota
                                .crearFlotaInicial()
                                .size(),
                () ->
                        "Hay más rutas que vehículos: "
                                +
                                rutas
        );

        /*
         * El mejor global empieza siendo el seed,
         * por lo que ALNS no puede devolver peor coste.
         */
        assertTrue(
                ejecucion
                        .resultado()
                        .costoTotal()
                        <=
                        ejecucion.initialCost()
                                +
                                1e-9,
                () ->
                        "El coste final supera al seed con "
                                +
                                cantidadPedidos
                                +
                                " pedidos"
        );

        /*
         * =================================================
         * MÉTRICAS
         * =================================================
         */

        double throughput =
                duracionMs <= 0
                        ? atendidos
                        : atendidos *
                        1000.0 /
                        duracionMs;

        double porcentajeAtendido =
                cantidadPedidos == 0
                        ? 0
                        : atendidos *
                        100.0 /
                        cantidadPedidos;

        double memoriaAntesMb =
                bytesAMegabytes(
                        memoriaAntesBytes
                );

        double memoriaDespuesMb =
                bytesAMegabytes(
                        memoriaDespuesBytes
                );

        double deltaMemoriaMb =
                memoriaDespuesMb -
                        memoriaAntesMb;

        double mejoraCostoPct =
                ejecucion.initialCost() <= 0
                        ? 0
                        : (
                                ejecucion.initialCost()
                                        -
                                        ejecucion
                                                .resultado()
                                                .costoTotal()
                        )
                        *
                        100.0
                        /
                        ejecucion.initialCost();

        return new FilaBenchmark(
                cantidadPedidos,
                duracionMs,

                atendidos,
                noAtendidos,
                porcentajeAtendido,

                rutas,

                ejecucion.initialCost(),

                ejecucion
                        .resultado()
                        .costoTotal(),

                mejoraCostoPct,

                ejecucion.iterations(),

                ejecucion
                        .evaluatedCandidates(),

                ejecucion
                        .acceptedCandidates(),

                ejecucion
                        .globalImprovements(),

                ejecucion.finalTemperature(),

                throughput,

                memoriaAntesMb,
                memoriaDespuesMb,
                deltaMemoriaMb,

                ejecucion.destroyWeights()
                        .toString(),

                ejecucion.repairWeights()
                        .toString()
        );
    }

    /*
     * =====================================================
     * WARM-UP
     * =====================================================
     */

    private void ejecutarWarmup(
            AlnsConfig config
    ) {

        RouteScheduler scheduler =
                new RouteScheduler(
                        new RoadNetwork()
                );

        OperationalPlanEvaluator evaluator =
                new OperationalPlanEvaluator(
                        scheduler,
                        0
                );

        OperationalAlnsPlanner planner =
                new OperationalAlnsPlanner(
                        evaluator
                );

        planner.ejecutar(
                snapshot(),

                generarPedidos(
                        50
                ),

                List.of(),

                config,

                new Random(
                        SEMILLA
                ),

                300L
        );
    }

    /*
     * =====================================================
     * GENERACIÓN DE PEDIDOS
     * =====================================================
     */

    /**
     * Generación completamente determinista.
     *
     * Los primeros 100 pedidos del benchmark 500
     * son exactamente los mismos que los del
     * benchmark de 100.
     *
     * Esto hace comparables las escalas.
     */
    private List<Order> generarPedidos(
            int cantidad
    ) {

        List<Order> pedidos =
                new ArrayList<>(
                        cantidad
                );

        for (
                int i = 0;
                i < cantidad;
                i++
        ) {

            /*
             * Distribución determinista dentro
             * de la cuadrícula 0..70 x 0..50.
             */
            int x =
                    (
                            i *
                                    37
                                    +
                                    11
                    )
                            %
                            (
                                    Location.MAX_X +
                                            1
                            );

            int y =
                    (
                            i *
                                    19
                                    +
                                    7
                    )
                            %
                            (
                                    Location.MAX_Y +
                                            1
                            );

            /*
             * Evitamos colocar exactamente un pedido
             * encima del almacén central.
             */
            if (
                    x ==
                            CENTRAL
                                    .location()
                                    .x()
                            &&
                    y ==
                            CENTRAL
                                    .location()
                                    .y()
            ) {

                x =
                        (
                                x +
                                        1
                        )
                                %
                                (
                                        Location.MAX_X +
                                                1
                                );
            }

            /*
             * Cantidad:
             *
             * 1 a 12 paquetes.
             */
            int paquetes =
                    1 +
                    (
                            i *
                                    7
                    )
                            %
                            12;

            /*
             * Deadline:
             *
             * entre +24 h y +120 h.
             */
            long horasPlazo =
                    24L +
                    (
                            i *
                                    13L
                    )
                            %
                            97L;

            pedidos.add(
                    new Order(
                            String.format(
                                    "B%05d",
                                    i +
                                            1
                            ),

                            new Location(
                                    x,
                                    y
                            ),

                            paquetes,

                            INICIO,

                            INICIO.plus(
                                    Duration.ofHours(
                                            horasPlazo
                                    )
                            )
                    )
            );
        }

        return List.copyOf(
                pedidos
        );
    }

    /*
     * =====================================================
     * SNAPSHOT
     * =====================================================
     */

    private OperationalSnapshot snapshot() {

        Map<
                String,
                VehicleOperationalState
                > estados =
                new LinkedHashMap<>();

        List<Vehicle> flota =
                InicializadorFlota
                        .crearFlotaInicial();

        for (
                Vehicle vehiculo :
                flota
        ) {

            estados.put(
                    vehiculo.id(),

                    new VehicleOperationalState(
                            vehiculo,
                            VehicleStatus.AVAILABLE,
                            CENTRAL.location(),
                            INICIO
                    )
            );
        }

        return new OperationalSnapshot(
                INICIO,

                FleetProfile.defaults(),

                InventorySnapshot.from(
                        List.of(
                                CENTRAL,
                                NOROESTE,
                                ESTE
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

    /*
     * =====================================================
     * CONFIGURACIÓN ALNS
     * =====================================================
     */

    private AlnsConfig configuracion() {

        return new AlnsConfig(
                /*
                 * Construcción inicial por lotes.
                 */
                32,

                /*
                 * Máximo 6 vehículos candidatos.
                 */
                6,

                /*
                 * Destroy mínimo.
                 */
                1,

                /*
                 * Destroy máximo.
                 */
                6,

                /*
                 * Iteraciones máximas.
                 */
                800,

                /*
                 * Iteraciones sin mejora.
                 */
                200,

                /*
                 * Temperatura inicial.
                 */
                50.0,

                /*
                 * Temperatura mínima.
                 */
                0.5,

                /*
                 * Enfriamiento.
                 */
                0.995,

                /*
                 * Reaction factor.
                 */
                0.20
        );
    }

    /*
     * =====================================================
     * PRESUPUESTO
     * =====================================================
     */

    private long presupuestoMs() {

        long valor =
                Long.getLong(
                        "paqrap.benchmark.alns.presupuestoMs",
                        1_500L
                );

        if (
                valor <= 0 ||
                valor >
                        OperationalAlnsPlanner
                                .PRESUPUESTO_MAXIMO_MS
        ) {

            throw new IllegalArgumentException(
                    "paqrap.benchmark.alns.presupuestoMs debe estar entre 1 y "
                            +
                            OperationalAlnsPlanner
                                    .PRESUPUESTO_MAXIMO_MS
            );
        }

        return valor;
    }

    /*
     * =====================================================
     * MEMORIA
     * =====================================================
     */

    private long memoriaUsada() {

        Runtime runtime =
                Runtime.getRuntime();

        return runtime.totalMemory()
                -
                runtime.freeMemory();
    }

    private double bytesAMegabytes(
            long bytes
    ) {

        return bytes /
                (
                        1024.0 *
                                1024.0
                );
    }

    /*
     * =====================================================
     * RESULTADO INDIVIDUAL
     * =====================================================
     */

    private void imprimirResultadoIndividual(
            FilaBenchmark fila
    ) {

        System.out.printf(
                "    Tiempo:                %,d ms%n",
                fila.duracionMs()
        );

        System.out.printf(
                "    Atendidos:             %,d / %,d (%.2f%%)%n",
                fila.atendidos(),
                fila.pedidos(),
                fila.porcentajeAtendido()
        );

        System.out.printf(
                "    No atendidos:          %,d%n",
                fila.noAtendidos()
        );

        System.out.printf(
                "    Rutas:                 %d%n",
                fila.rutas()
        );

        System.out.printf(
                "    Costo seed:            S/ %,.2f%n",
                fila.costoSeed()
        );

        System.out.printf(
                "    Costo final:           S/ %,.2f%n",
                fila.costoFinal()
        );

        System.out.printf(
                "    Mejora costo:          %.2f%%%n",
                fila.mejoraCostoPct()
        );

        System.out.printf(
                "    Iteraciones:           %,d%n",
                fila.iteraciones()
        );

        System.out.printf(
                "    Candidatos evaluados:  %,d%n",
                fila.candidatosEvaluados()
        );

        System.out.printf(
                "    Candidatos aceptados:  %,d%n",
                fila.candidatosAceptados()
        );

        System.out.printf(
                "    Mejoras globales:      %,d%n",
                fila.mejorasGlobales()
        );

        System.out.printf(
                "    Throughput:            %,.2f pedidos/s%n",
                fila.pedidosPorSegundo()
        );

        System.out.printf(
                "    Heap antes:            %.2f MB%n",
                fila.memoriaAntesMb()
        );

        System.out.printf(
                "    Heap después:          %.2f MB%n",
                fila.memoriaDespuesMb()
        );

        System.out.printf(
                "    Delta heap:            %+.2f MB%n",
                fila.deltaMemoriaMb()
        );

        System.out.printf(
                "    Destroy weights:       %s%n",
                fila.destroyWeights()
        );

        System.out.printf(
                "    Repair weights:        %s%n",
                fila.repairWeights()
        );
    }

    /*
     * =====================================================
     * TABLA FINAL
     * =====================================================
     */

    private void imprimirTabla(
            List<FilaBenchmark> filas
    ) {

        System.out.println();
        System.out.println(
                "=================================================================================================================================================="
        );

        System.out.println(
                "RESULTADOS"
        );

        System.out.println(
                "=================================================================================================================================================="
        );

        System.out.printf(
                "%8s | %8s | %8s | %8s | %7s | %5s | %12s | %12s | %7s | %6s | %7s | %7s | %6s | %10s | %8s%n",
                "Pedidos",
                "ms",
                "Atendid",
                "NoAtend",
                "Atend%",
                "Rutas",
                "Seed",
                "Final",
                "Mej%",
                "Iter",
                "Cand",
                "Acept",
                "Mej",
                "Ped/s",
                "HeapMB"
        );

        System.out.println(
                "--------------------------------------------------------------------------------------------------------------------------------------------------"
        );

        for (
                FilaBenchmark fila :
                filas
        ) {

            System.out.printf(
                    "%8d | %8d | %8d | %8d | %6.2f%% | %5d | %12.2f | %12.2f | %6.2f%% | %6d | %7d | %7d | %6d | %10.2f | %8.2f%n",

                    fila.pedidos(),
                    fila.duracionMs(),

                    fila.atendidos(),
                    fila.noAtendidos(),
                    fila.porcentajeAtendido(),

                    fila.rutas(),

                    fila.costoSeed(),
                    fila.costoFinal(),
                    fila.mejoraCostoPct(),

                    fila.iteraciones(),

                    fila.candidatosEvaluados(),
                    fila.candidatosAceptados(),
                    fila.mejorasGlobales(),

                    fila.pedidosPorSegundo(),

                    fila.memoriaDespuesMb()
            );
        }

        System.out.println(
                "=================================================================================================================================================="
        );

        System.out.println();

        System.out.println(
                "MEMORIA APROXIMADA"
        );

        for (
                FilaBenchmark fila :
                filas
        ) {

            System.out.printf(
                    "%,6d pedidos -> antes %.2f MB | después %.2f MB | delta %+.2f MB%n",
                    fila.pedidos(),
                    fila.memoriaAntesMb(),
                    fila.memoriaDespuesMb(),
                    fila.deltaMemoriaMb()
            );
        }
    }

    /*
     * =====================================================
     * CSV
     * =====================================================
     */

    private void imprimirCsv(
            List<FilaBenchmark> filas
    ) {

        System.out.println();
        System.out.println(
                "============================== CSV =============================="
        );

        System.out.println(
                "pedidos,duracion_ms,atendidos,no_atendidos,porcentaje_atendido,rutas,costo_seed,costo_final,mejora_costo_pct,iteraciones,candidatos_evaluados,candidatos_aceptados,mejoras_globales,temperatura_final,pedidos_por_segundo,heap_antes_mb,heap_despues_mb,heap_delta_mb"
        );

        for (
                FilaBenchmark fila :
                filas
        ) {

            System.out.printf(
                    "%d,%d,%d,%d,%.4f,%d,%.4f,%.4f,%.4f,%d,%d,%d,%d,%.6f,%.4f,%.4f,%.4f,%.4f%n",

                    fila.pedidos(),
                    fila.duracionMs(),

                    fila.atendidos(),
                    fila.noAtendidos(),
                    fila.porcentajeAtendido(),

                    fila.rutas(),

                    fila.costoSeed(),
                    fila.costoFinal(),
                    fila.mejoraCostoPct(),

                    fila.iteraciones(),

                    fila.candidatosEvaluados(),
                    fila.candidatosAceptados(),
                    fila.mejorasGlobales(),

                    fila.temperaturaFinal(),

                    fila.pedidosPorSegundo(),

                    fila.memoriaAntesMb(),
                    fila.memoriaDespuesMb(),
                    fila.deltaMemoriaMb()
            );
        }

        System.out.println(
                "================================================================="
        );
    }

    /*
     * =====================================================
     * FILA
     * =====================================================
     */

    private record FilaBenchmark(
            int pedidos,
            long duracionMs,

            int atendidos,
            int noAtendidos,
            double porcentajeAtendido,

            int rutas,

            double costoSeed,
            double costoFinal,
            double mejoraCostoPct,

            int iteraciones,
            int candidatosEvaluados,
            int candidatosAceptados,
            int mejorasGlobales,

            double temperaturaFinal,

            double pedidosPorSegundo,

            double memoriaAntesMb,
            double memoriaDespuesMb,
            double deltaMemoriaMb,

            String destroyWeights,
            String repairWeights
    ) {
    }
}