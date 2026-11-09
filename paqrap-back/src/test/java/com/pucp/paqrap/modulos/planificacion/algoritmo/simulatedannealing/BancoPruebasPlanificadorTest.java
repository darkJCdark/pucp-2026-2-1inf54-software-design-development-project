package com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.pedidos.service.CargadorPedidos;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.MetricasPlanificador;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.service.FuenteDatosOperativos;
import com.pucp.paqrap.modulos.planificacion.service.FuenteDatosOperativosMock;
import com.pucp.paqrap.modulos.planificacion.service.PlanificadorSaService;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.service.CargadorBloqueos;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Banco de pruebas del planificador (Semana 13, paso 1).
 *
 * <p>Mide {@link OperationalSimulatedAnnealingPlanner} con pedidos y bloqueos reales de {@code paqrap-data},
 * con semilla fija, sobre lotes de tamaño creciente (los primeros N pedidos por orden de registro), y escribe un
 * CSV en {@code target/bancopruebas/}. Es el Modo A: planificador aislado, todos los vehículos libres en el
 * almacén central a la hora del último pedido del lote, como hace {@link PlanificadorSaService}.
 *
 * <p>La medición real solo corre con {@code -Dpaqrap.bench=true}; sin esa propiedad {@code mvn test} ejecuta
 * únicamente las pruebas unitarias de los métodos auxiliares.
 *
 * <p>Propiedades opcionales: {@code paqrap.datos} (carpeta de datos, por defecto {@code ../paqrap-data}),
 * {@code paqrap.bench.series} ({@code produccion}, {@code amplio} o ambas separadas por coma),
 * {@code paqrap.bench.lotes} (tamaños de lote separados por coma, por defecto {@code 25,50,100,200,400}),
 * {@code paqrap.bench.salida} (nombre del CSV, por defecto {@code base.csv}).
 *
 * <p>Cada repetición construye su propio {@link RoadNetwork}, por lo que mide el ciclo en frío; el servicio de
 * producción reutiliza uno entre ciclos.
 */
class BancoPruebasPlanificadorTest {

    static final long SEMILLA = 42L;
    static final int REPETICIONES_PRODUCCION = 3;
    static final int REPETICIONES_AMPLIO = 1;
    static final int[] LOTES = {25, 50, 100, 200, 400};
    static final long PRESUPUESTO_PRODUCCION_MS = 1_500L;
    static final long PRESUPUESTO_AMPLIO_MS = 600_000L;
    static final Duration HORIZONTE_BLOQUEOS = Duration.ofDays(5);
    static final ZoneId ZONA = ShiftSchedule.DEFAULT_ZONE;

    static final String CABECERA = "serie,dataset,lote,repeticion,pedidos,ms_total,ms_plan_inicial,costo_inicial,"
            + "costo_final,vecinos_evaluados,vecinos_aceptados,iteraciones,atendidos,no_atendidos,factible,"
            + "semilla_evaluaciones,semilla_ms_evaluaciones,semilla_caminos,semilla_ms_caminos,"
            + "semilla_pares_unicos,semilla_pares_hora_unicos,"
            + "total_evaluaciones,total_ms_evaluaciones,total_caminos,total_ms_caminos,"
            + "total_pares_unicos,total_pares_hora_unicos";

    private static final AnnealingConfig CONFIG_PRUEBA = new AnnealingConfig(100.0, 1.0, 0.90, 5, 30, 30);

    /** Un dataset del banco: el mes cuyos archivos de ventas y bloqueos se leen. */
    record Dataset(String nombre, YearMonth periodo) {
        String archivoVentas() {
            return String.format("ventas.%04d%02d.txt", periodo.getYear(), periodo.getMonthValue());
        }

        String archivoBloqueos() {
            return String.format("bloqueo.%02d%02d.txt", periodo.getYear() % 100, periodo.getMonthValue());
        }
    }

    /**
     * Una fila del CSV. {@code semilla} son las métricas de la llamada aparte a {@link InitialPlanBuilder};
     * {@code total}, las de la ejecución completa del SA (que incluye su propio plan inicial).
     */
    record Medicion(String serie, String dataset, int lote, int repeticion, int pedidos, long msTotal,
                    long msPlanInicial, double costoInicial, double costoFinal, int vecinosEvaluados,
                    int vecinosAceptados, int iteraciones, int atendidos, int noAtendidos, boolean factible,
                    MetricasPlanificador.Resumen semilla, MetricasPlanificador.Resumen total) {
        String aCsv() {
            return String.format(Locale.ROOT, "%s,%s,%d,%d,%d,%d,%d,%.4f,%.4f,%d,%d,%d,%d,%d,%b",
                    serie, dataset, lote, repeticion, pedidos, msTotal, msPlanInicial, costoInicial, costoFinal,
                    vecinosEvaluados, vecinosAceptados, iteraciones, atendidos, noAtendidos, factible)
                    + "," + aCsv(semilla) + "," + aCsv(total);
        }

        private static String aCsv(MetricasPlanificador.Resumen r) {
            return String.format(Locale.ROOT, "%d,%d,%d,%d,%d,%d", r.evaluaciones(), r.msEvaluaciones(),
                    r.caminos(), r.msCaminos(), r.paresUnicos(), r.paresConHoraUnicos());
        }
    }

    // ---------------------------------------------------------------- medición real (opt-in)

    @Test
    @EnabledIfSystemProperty(named = "paqrap.bench", matches = "true")
    void ejecutarBanco() throws IOException {
        Path datos = Path.of(System.getProperty("paqrap.datos", "../paqrap-data"));
        String series = System.getProperty("paqrap.bench.series", "produccion,amplio");
        Path salida = Path.of("target", "bancopruebas", System.getProperty("paqrap.bench.salida", "base.csv"));
        int[] lotes = lotesDe(System.getProperty("paqrap.bench.lotes"));
        FuenteDatosOperativos fuente = new FuenteDatosOperativosMock();
        List<Dataset> datasets = List.of(
                new Dataset("dic2028", YearMonth.of(2028, 12)),
                new Dataset("feb2026", YearMonth.of(2026, 2)));

        List<Medicion> mediciones = new ArrayList<>();
        boolean calentado = false;
        for (Dataset dataset : datasets) {
            List<Order> pedidos = pedidosDesde(leer(datos.resolve("ventas").resolve(dataset.archivoVentas())),
                    dataset.periodo());
            List<RoadBlock> bloqueos = new CargadorBloqueos().cargar(
                    leer(datos.resolve("bloqueos").resolve(dataset.archivoBloqueos())), dataset.periodo(), ZONA);

            if (!calentado) {
                medir("calentamiento", dataset.nombre(), 0, lote(pedidos, lotes[0]), bloqueos, fuente,
                        PlanificadorSaService.configuracionPorDefecto(), PRESUPUESTO_PRODUCCION_MS);
                calentado = true;
            }
            for (String serie : series.split(",")) {
                boolean amplio = serie.strip().equals("amplio");
                long presupuesto = amplio ? PRESUPUESTO_AMPLIO_MS : PRESUPUESTO_PRODUCCION_MS;
                int repeticiones = amplio ? REPETICIONES_AMPLIO : REPETICIONES_PRODUCCION;
                for (int n : lotes) {
                    List<Order> lote = lote(pedidos, n);
                    for (int r = 1; r <= repeticiones; r++) {
                        mediciones.add(medir(serie.strip(), dataset.nombre(), r, lote, bloqueos, fuente,
                                PlanificadorSaService.configuracionPorDefecto(), presupuesto));
                    }
                }
            }
        }

        Files.createDirectories(salida.getParent());
        List<String> lineas = new ArrayList<>();
        lineas.add(CABECERA);
        mediciones.forEach(m -> lineas.add(m.aCsv()));
        Files.write(salida, lineas, StandardCharsets.UTF_8);
        imprimirResumen(mediciones);

        assertEquals(mediciones.size() + 1, Files.readAllLines(salida).size());
        assertTrue(mediciones.stream().allMatch(m -> m.atendidos() + m.noAtendidos() == m.pedidos()));
    }

    // ---------------------------------------------------------------- pruebas unitarias de los auxiliares

    private static final List<String> LINEAS_FEB = List.of(
            "01d01h32m:60,24,c2588,05,36",
            "01d00h19m:35,02,c9232,08,12",
            "",
            "01d00h45m:55,24,c0422,03,04");

    @Test
    void pedidosDesde_ordenaPorRegistroYCalculaPlazo() {
        List<Order> pedidos = pedidosDesde(LINEAS_FEB, YearMonth.of(2026, 2));

        assertEquals(3, pedidos.size());
        assertEquals(List.of("202602-00002", "202602-00004", "202602-00001"),
                pedidos.stream().map(Order::id).toList());
        Order primero = pedidos.get(0);
        assertEquals(8, primero.packages());
        assertEquals(new Location(35, 2), primero.destination());
        assertEquals(primero.registeredAt().plus(Duration.ofHours(12)), primero.deadline());
        assertEquals(pedidos.get(2).registeredAt().plus(Duration.ofHours(36)), pedidos.get(2).deadline());
    }

    @Test
    void lote_devuelveLosPrimerosNOTodosSiHayMenos() {
        List<Order> pedidos = pedidosDesde(LINEAS_FEB, YearMonth.of(2026, 2));

        assertEquals(List.of("202602-00002", "202602-00004"), lote(pedidos, 2).stream().map(Order::id).toList());
        assertEquals(3, lote(pedidos, 400).size());
        assertThrows(IllegalArgumentException.class, () -> lote(pedidos, 0));
    }

    @Test
    void bloqueosRelevantes_filtraPorVentanaDeTiempo() {
        Instant hora = Instant.parse("2026-02-01T12:00:00Z");
        List<Location> tramo = List.of(new Location(0, 0), new Location(3, 0));
        RoadBlock terminado = new RoadBlock(hora.minusSeconds(7200), hora.minusSeconds(3600), tramo);
        RoadBlock activo = new RoadBlock(hora.minusSeconds(3600), hora.plusSeconds(3600), tramo);
        RoadBlock proximo = new RoadBlock(hora.plus(Duration.ofDays(2)), hora.plus(Duration.ofDays(2)).plusSeconds(60), tramo);
        RoadBlock lejano = new RoadBlock(hora.plus(Duration.ofDays(9)), hora.plus(Duration.ofDays(9)).plusSeconds(60), tramo);

        List<RoadBlock> relevantes = bloqueosRelevantes(List.of(terminado, activo, proximo, lejano), hora,
                HORIZONTE_BLOQUEOS);

        assertEquals(List.of(activo, proximo), relevantes);
    }

    @Test
    void mediana_calculaImparParYRechazaVacio() {
        assertEquals(2L, mediana(List.of(3L, 1L, 2L)));
        assertEquals(2L, mediana(List.of(4L, 1L, 3L, 2L)));
        assertEquals(7L, mediana(List.of(7L)));
        assertThrows(IllegalArgumentException.class, () -> mediana(List.of()));
    }

    @Test
    void lotesDe_usaValoresPorDefectoOLosIndicados() {
        assertEquals(5, lotesDe(null).length);
        assertEquals(5, lotesDe(" ").length);
        assertEquals(List.of(10, 20), java.util.Arrays.stream(lotesDe("10, 20")).boxed().toList());
        assertThrows(IllegalArgumentException.class, () -> lotesDe("10,0"));
        assertThrows(NumberFormatException.class, () -> lotesDe("a"));
    }

    @Test
    void aCsv_usaPuntoDecimalYTantasColumnasComoLaCabecera() {
        Medicion m = new Medicion("produccion", "dic2028", 25, 1, 25, 1500, 40, 1234.5, 1000.25, 10, 4, 7, 24, 1, true,
                new MetricasPlanificador.Resumen(925, 1400, 9000, 1200, 60, 8000),
                new MetricasPlanificador.Resumen(930, 1450, 9100, 1250, 61, 8100));

        String fila = m.aCsv();

        assertEquals("produccion,dic2028,25,1,25,1500,40,1234.5000,1000.2500,10,4,7,24,1,true,"
                + "925,1400,9000,1200,60,8000,930,1450,9100,1250,61,8100", fila);
        assertEquals(CABECERA.split(",").length, fila.split(",").length);
    }

    @Test
    void planInicial_evaluaUnaVezPorPedidoYVehiculoLibre() {
        List<Order> pedidos = pedidosDesde(LINEAS_FEB, YearMonth.of(2026, 2));
        Instant hora = pedidos.get(pedidos.size() - 1).registeredAt();
        OperationalSnapshot snapshot = snapshot(new FuenteDatosOperativosMock(), hora);
        long vehiculosLibres = snapshot.vehiclesById().values().stream()
                .filter(estado -> snapshot.isVehiclePlannableAt(estado.vehicle().id(), hora))
                .count();
        MetricasPlanificador metricas = MetricasPlanificador.activas();

        new InitialPlanBuilder(new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork(), metricas), 0, metricas))
                .build(snapshot, pedidos, List.of());

        assertTrue(vehiculosLibres > 0);
        assertEquals(pedidos.size() * vehiculosLibres, metricas.resumen().evaluaciones());
        assertTrue(metricas.resumen().caminos() > 0);
    }

    @Test
    void medir_conPocosPedidos_devuelveUnaFilaCoherente() {
        List<Order> pedidos = pedidosDesde(LINEAS_FEB, YearMonth.of(2026, 2));

        Medicion m = medir("prueba", "feb2026", 1, pedidos, List.of(), new FuenteDatosOperativosMock(),
                CONFIG_PRUEBA, PRESUPUESTO_AMPLIO_MS);

        assertEquals(3, m.pedidos());
        assertEquals(3, m.atendidos() + m.noAtendidos());
        assertTrue(m.msTotal() >= 0 && m.msPlanInicial() >= 0);
        assertTrue(m.vecinosEvaluados() > 0);
        assertTrue(Double.isFinite(m.costoInicial()) && Double.isFinite(m.costoFinal()));
        assertTrue(m.semilla().evaluaciones() > 0 && m.semilla().caminos() > 0);
        assertTrue(m.total().evaluaciones() >= m.semilla().evaluaciones());
        assertTrue(m.total().paresUnicos() <= m.total().paresConHoraUnicos());
    }

    @Test
    void medir_mismaSemilla_dadoMismoPlanMismoCosto() {
        List<Order> pedidos = pedidosDesde(LINEAS_FEB, YearMonth.of(2026, 2));
        FuenteDatosOperativos fuente = new FuenteDatosOperativosMock();

        Medicion a = medir("prueba", "feb2026", 1, pedidos, List.of(), fuente, CONFIG_PRUEBA, PRESUPUESTO_AMPLIO_MS);
        Medicion b = medir("prueba", "feb2026", 2, pedidos, List.of(), fuente, CONFIG_PRUEBA, PRESUPUESTO_AMPLIO_MS);

        assertEquals(a.costoInicial(), b.costoInicial());
        assertEquals(a.costoFinal(), b.costoFinal());
        assertEquals(a.vecinosEvaluados(), b.vecinosEvaluados());
        assertEquals(a.noAtendidos(), b.noAtendidos());
    }

    // ---------------------------------------------------------------- auxiliares

    static List<String> leer(Path archivo) throws IOException {
        return Files.readString(archivo, StandardCharsets.UTF_8).replace("﻿", "").lines().toList();
    }

    /** Pedidos del archivo ordenados por hora de registro; el plazo es registro + horas prometidas, como en BD. */
    static List<Order> pedidosDesde(List<String> lineas, YearMonth periodo) {
        List<Order> pedidos = new ArrayList<>();
        for (CargadorPedidos.PedidoArchivo leido : new CargadorPedidos().cargar(lineas, periodo, ZONA)) {
            pedidos.add(new Order(leido.id(), leido.destino(), leido.paquetes(), leido.registradoEn(),
                    leido.registradoEn().plus(Duration.ofHours(leido.horasPrometidas()))));
        }
        pedidos.sort(Comparator.comparing(Order::registeredAt));
        return List.copyOf(pedidos);
    }

    static List<Order> lote(List<Order> pedidos, int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("el lote debe ser positivo");
        }
        return List.copyOf(pedidos.subList(0, Math.min(n, pedidos.size())));
    }

    /** Bloqueos que terminan después de {@code hora} y empiezan antes de {@code hora + horizonte}. */
    static List<RoadBlock> bloqueosRelevantes(List<RoadBlock> bloqueos, Instant hora, Duration horizonte) {
        Instant limite = hora.plus(horizonte);
        return bloqueos.stream()
                .filter(b -> b.endsAt().isAfter(hora) && b.startsAt().isBefore(limite))
                .toList();
    }

    /** Tamaños de lote desde una lista separada por comas; sin valor usa {@link #LOTES}. */
    static int[] lotesDe(String propiedad) {
        if (propiedad == null || propiedad.isBlank()) {
            return LOTES;
        }
        int[] lotes = java.util.Arrays.stream(propiedad.split(",")).map(String::strip).mapToInt(Integer::parseInt).toArray();
        for (int n : lotes) {
            if (n <= 0) {
                throw new IllegalArgumentException("los lotes deben ser positivos");
            }
        }
        return lotes;
    }

    static long mediana(List<Long> valores) {
        if (valores.isEmpty()) {
            throw new IllegalArgumentException("no hay valores");
        }
        List<Long> ordenados = valores.stream().sorted().toList();
        int medio = ordenados.size() / 2;
        return ordenados.size() % 2 == 1 ? ordenados.get(medio)
                : (ordenados.get(medio - 1) + ordenados.get(medio)) / 2;
    }

    /** Snapshot como el de PlanificadorSaService: toda la flota disponible en el almacén central a {@code hora}. */
    static OperationalSnapshot snapshot(FuenteDatosOperativos fuente, Instant hora) {
        Warehouse central = fuente.almacenes().stream()
                .filter(Warehouse::isCentral)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("La fuente de datos no define un almacén central"));
        Map<String, VehicleOperationalState> estados = new LinkedHashMap<>();
        for (Vehicle vehiculo : fuente.flota()) {
            estados.put(vehiculo.id(),
                    new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, central.location(), hora));
        }
        return new OperationalSnapshot(hora, fuente.perfilFlota(), InventorySnapshot.from(fuente.almacenes()), estados,
                fuente.calendarioMantenimiento(), new ShiftSchedule(ShiftSchedule.DEFAULT_ZONE), fuente.averias(null));
    }

    /**
     * Una corrida: mide por separado la construcción del plan inicial y la ejecución completa del SA con la
     * semilla {@link #SEMILLA}. {@code ms_plan_inicial} se cronometra en una llamada aparte a
     * {@link InitialPlanBuilder}; {@code ms_total} es la ejecución del SA (que construye su propia semilla).
     */
    static Medicion medir(String serie, String dataset, int repeticion, List<Order> pedidos,
                          List<RoadBlock> bloqueos, FuenteDatosOperativos fuente, AnnealingConfig config,
                          long presupuestoMs) {
        Instant hora = pedidos.stream().map(Order::registeredAt).max(Comparator.naturalOrder()).orElseThrow();
        OperationalSnapshot snapshot = snapshot(fuente, hora);
        List<RoadBlock> relevantes = bloqueosRelevantes(bloqueos, hora, HORIZONTE_BLOQUEOS);
        MetricasPlanificador metricas = MetricasPlanificador.activas();
        OperationalPlanEvaluator evaluador = new OperationalPlanEvaluator(
                new RouteScheduler(new RoadNetwork(), metricas), 0, metricas);

        long inicioPlanInicial = System.nanoTime();
        new InitialPlanBuilder(evaluador).build(snapshot, pedidos, relevantes);
        long msPlanInicial = (System.nanoTime() - inicioPlanInicial) / 1_000_000L;
        MetricasPlanificador.Resumen semilla = metricas.resumen();
        metricas.reiniciar();

        OperationalSimulatedAnnealingPlanner planificador = new OperationalSimulatedAnnealingPlanner(evaluador);
        long inicio = System.nanoTime();
        OperationalSimulatedAnnealingPlanner.Ejecucion ejecucion = planificador.ejecutar(snapshot, pedidos, relevantes,
                config, new Random(SEMILLA), presupuestoMs);
        long msTotal = (System.nanoTime() - inicio) / 1_000_000L;

        ResultadoPlanificacion resultado = ejecucion.resultado();
        int noAtendidos = resultado.noAtendidos().size();
        return new Medicion(serie, dataset, pedidos.size(), repeticion, pedidos.size(), msTotal, msPlanInicial,
                ejecucion.initialCost(), resultado.costoTotal(), ejecucion.evaluatedNeighbors(),
                ejecucion.acceptedNeighbors(), ejecucion.iterations(), pedidos.size() - noAtendidos, noAtendidos,
                resultado.esFactible(), semilla, metricas.resumen());
    }

    private static void imprimirResumen(List<Medicion> mediciones) {
        Map<String, List<Medicion>> grupos = new LinkedHashMap<>();
        for (Medicion m : mediciones) {
            grupos.computeIfAbsent(m.serie() + "|" + m.dataset() + "|" + m.lote(), k -> new ArrayList<>()).add(m);
        }
        System.out.println("BANCO serie|dataset|lote -> mediana ms_total, mediana ms_plan_inicial, no_atendidos, "
                + "evaluaciones semilla, caminos semilla, pares unicos semilla, mediana ms caminos semilla");
        grupos.forEach((clave, filas) -> System.out.printf(Locale.ROOT, "BANCO %s -> %d ms, %d ms, %d, %d, %d, %d, %d ms%n",
                clave,
                mediana(filas.stream().map(Medicion::msTotal).toList()),
                mediana(filas.stream().map(Medicion::msPlanInicial).toList()),
                filas.get(0).noAtendidos(),
                filas.get(0).semilla().evaluaciones(),
                filas.get(0).semilla().caminos(),
                filas.get(0).semilla().paresUnicos(),
                mediana(filas.stream().map(m -> m.semilla().msCaminos()).toList())));
    }
}
