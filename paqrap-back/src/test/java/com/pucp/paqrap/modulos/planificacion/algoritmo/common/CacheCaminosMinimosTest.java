package com.pucp.paqrap.modulos.planificacion.algoritmo.common;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.AnnealingConfig;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.OperationalSimulatedAnnealingPlanner;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.service.FuenteDatosOperativos;
import com.pucp.paqrap.modulos.planificacion.service.FuenteDatosOperativosMock;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.entity.RoadPath;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Caché de caminos mínimos (Semana 13, paso 6): mismo resultado que sin caché y menos cálculos. */
class CacheCaminosMinimosTest {

    private static final Instant HORA = Instant.parse("2026-09-09T12:00:00Z");
    private static final Duration POR_CALLE = Duration.ofMinutes(3);
    private static final Location A = new Location(0, 0);
    private static final Location B = new Location(5, 0);
    private static final Location C = new Location(5, 5);
    private static final AnnealingConfig CONFIG_PRUEBA = new AnnealingConfig(100.0, 1.0, 0.90, 5, 30, 30);

    /** Calculador que cuenta sus llamadas y delega en la red real. */
    private static final class Contador implements CacheCaminosMinimos.CalculadorCamino {
        private final AtomicInteger llamadas = new AtomicInteger();
        private final RoadNetwork red = new RoadNetwork();

        @Override
        public Optional<RoadPath> calcular(Location origen, Location destino, Instant salida, Duration porCalle,
                                           List<RoadBlock> bloqueos) {
            llamadas.incrementAndGet();
            return red.shortestPath(origen, destino, salida, porCalle, bloqueos);
        }
    }

    private static RoadBlock bloqueo(int x) {
        return new RoadBlock(HORA, HORA.plusSeconds(3600), List.of(new Location(x, 0), new Location(x, 1)));
    }

    @Test
    void mismaConsulta_laSegundaEsAciertoYDevuelveElMismoCamino() {
        Contador contador = new Contador();
        MetricasPlanificador metricas = MetricasPlanificador.activas();
        CacheCaminosMinimos cache = new CacheCaminosMinimos(contador, metricas, 10);

        Optional<RoadPath> primero = cache.obtener(A, B, HORA, POR_CALLE, List.of());
        Optional<RoadPath> segundo = cache.obtener(A, B, HORA, POR_CALLE, List.of());

        assertTrue(primero.isPresent() && primero.get() == segundo.get());
        assertEquals(1, contador.llamadas.get());
        assertEquals(1L, metricas.resumen().caminos());
        assertEquals(1L, metricas.resumen().aciertosCache());
        assertEquals(1, cache.tamanio());
    }

    @Test
    void capacidadCero_calculaSiempreYNoGuarda() {
        Contador contador = new Contador();
        CacheCaminosMinimos cache = new CacheCaminosMinimos(contador, MetricasPlanificador.NOOP, 0);

        cache.obtener(A, B, HORA, POR_CALLE, List.of());
        cache.obtener(A, B, HORA, POR_CALLE, List.of());

        assertEquals(2, contador.llamadas.get());
        assertEquals(0, cache.tamanio());
    }

    @Test
    void cadaComponenteDeLaClave_distingueLaConsulta() {
        Contador contador = new Contador();
        CacheCaminosMinimos cache = new CacheCaminosMinimos(contador, MetricasPlanificador.NOOP, 100);

        cache.obtener(A, B, HORA, POR_CALLE, List.of());
        cache.obtener(B, A, HORA, POR_CALLE, List.of());
        cache.obtener(A, C, HORA, POR_CALLE, List.of());
        cache.obtener(A, B, HORA.plusSeconds(60), POR_CALLE, List.of());
        cache.obtener(A, B, HORA, Duration.ofMinutes(4), List.of());
        cache.obtener(A, B, HORA, POR_CALLE, List.of(bloqueo(2)));
        cache.obtener(A, B, HORA, POR_CALLE, List.of(bloqueo(3)));

        assertEquals(7, contador.llamadas.get());
        assertEquals(7, cache.tamanio());
    }

    @Test
    void bloqueosIgualesEnOtraLista_compartenEntrada() {
        Contador contador = new Contador();
        CacheCaminosMinimos cache = new CacheCaminosMinimos(contador, MetricasPlanificador.NOOP, 10);
        List<RoadBlock> inmutable = List.of(bloqueo(2));
        List<RoadBlock> mutable = new ArrayList<>(inmutable);

        Optional<RoadPath> primero = cache.obtener(A, B, HORA, POR_CALLE, inmutable);
        Optional<RoadPath> segundo = cache.obtener(A, B, HORA, POR_CALLE, mutable);

        assertEquals(1, contador.llamadas.get());
        assertTrue(primero.get() == segundo.get());
    }

    @Test
    void listaMutableModificadaDespues_noCorrompeLaEntradaGuardada() {
        Contador contador = new Contador();
        CacheCaminosMinimos cache = new CacheCaminosMinimos(contador, MetricasPlanificador.NOOP, 10);
        List<RoadBlock> mutable = new ArrayList<>();

        cache.obtener(A, B, HORA, POR_CALLE, mutable);
        mutable.add(bloqueo(2));
        cache.obtener(A, B, HORA, POR_CALLE, mutable);
        cache.obtener(A, B, HORA, POR_CALLE, List.of());

        assertEquals(2, contador.llamadas.get());
        assertEquals(2, cache.tamanio());
    }

    @Test
    void sinCamino_seGuardaYNoSeRecalcula() {
        AtomicInteger llamadas = new AtomicInteger();
        CacheCaminosMinimos cache = new CacheCaminosMinimos((o, d, s, p, b) -> {
            llamadas.incrementAndGet();
            return Optional.empty();
        }, MetricasPlanificador.NOOP, 10);

        assertTrue(cache.obtener(A, B, HORA, POR_CALLE, List.of()).isEmpty());
        assertTrue(cache.obtener(A, B, HORA, POR_CALLE, List.of()).isEmpty());
        assertEquals(1, llamadas.get());
    }

    @Test
    void lru_desalojaLaEntradaMenosUsada() {
        Contador contador = new Contador();
        CacheCaminosMinimos cache = new CacheCaminosMinimos(contador, MetricasPlanificador.NOOP, 2);

        cache.obtener(A, B, HORA, POR_CALLE, List.of());
        cache.obtener(A, C, HORA, POR_CALLE, List.of());
        cache.obtener(A, B, HORA, POR_CALLE, List.of());
        cache.obtener(B, C, HORA, POR_CALLE, List.of());
        assertEquals(3, contador.llamadas.get());
        assertEquals(2, cache.tamanio());

        cache.obtener(A, B, HORA, POR_CALLE, List.of());
        assertEquals(3, contador.llamadas.get());
        cache.obtener(A, C, HORA, POR_CALLE, List.of());
        assertEquals(4, contador.llamadas.get());
    }

    @Test
    void constructor_validaArgumentos() {
        assertThrows(IllegalArgumentException.class,
                () -> new CacheCaminosMinimos(new RoadNetwork(), MetricasPlanificador.NOOP, -1));
        assertThrows(NullPointerException.class,
                () -> new CacheCaminosMinimos((RoadNetwork) null, MetricasPlanificador.NOOP, 1));
        assertThrows(NullPointerException.class, () -> new CacheCaminosMinimos(new RoadNetwork(), null, 1));
        assertEquals(CacheCaminosMinimos.CAPACIDAD_POR_DEFECTO,
                new CacheCaminosMinimos(new RoadNetwork(), MetricasPlanificador.NOOP,
                        CacheCaminosMinimos.CAPACIDAD_POR_DEFECTO).capacidad());
    }

    @Test
    void consultasConcurrentes_devuelvenElMismoCaminoPorClave() {
        Contador contador = new Contador();
        CacheCaminosMinimos cache = new CacheCaminosMinimos(contador, MetricasPlanificador.NOOP, 100);
        List<Location> destinos = List.of(B, C, new Location(9, 9), new Location(3, 7));

        Map<Location, List<Integer>> llegadas = IntStream.range(0, 400).parallel()
                .mapToObj(i -> destinos.get(i % destinos.size()))
                .collect(Collectors.groupingByConcurrent(d -> d, Collectors.mapping(
                        d -> cache.obtener(A, d, HORA, POR_CALLE, List.of()).orElseThrow().distanceKm(),
                        Collectors.toList())));

        for (List<Integer> distancias : llegadas.values()) {
            assertEquals(1L, distancias.stream().distinct().count());
        }
        assertEquals(4, cache.tamanio());
        assertTrue(contador.llamadas.get() >= 4);
    }

    @Test
    void routeScheduler_conYSinCache_programaLasMismasRutas() {
        OperationalSnapshot snapshot = snapshot(new FuenteDatosOperativosMock());
        List<RoadBlock> bloqueos = List.of(
                new RoadBlock(HORA, HORA.plusSeconds(8 * 3600), List.of(new Location(28, 14), new Location(31, 14))));
        MetricasPlanificador metricas = MetricasPlanificador.activas();
        OperationalSimulatedAnnealingPlanner sinCache = new OperationalSimulatedAnnealingPlanner(
                new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork(), MetricasPlanificador.NOOP, 0)));
        OperationalSimulatedAnnealingPlanner conCache = new OperationalSimulatedAnnealingPlanner(
                new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork(), metricas), 0, metricas));

        ResultadoPlanificacion esperado = sinCache.planificar(snapshot, pedidos(), bloqueos, CONFIG_PRUEBA, new Random(11));
        ResultadoPlanificacion real = conCache.planificar(snapshot, pedidos(), bloqueos, CONFIG_PRUEBA, new Random(11));

        assertEquals(esperado.costoTotal(), real.costoTotal());
        assertEquals(esperado.esFactible(), real.esFactible());
        assertEquals(esperado.noAtendidos().stream().map(Order::id).toList(),
                real.noAtendidos().stream().map(Order::id).toList());
        assertEquals(programacion(esperado), programacion(real));
        assertTrue(metricas.resumen().aciertosCache() > 0);
    }

    // ---------------------------------------------------------------- auxiliares

    /** Rutas programadas como texto: cada parada con su llegada, fin, cargas y tramos del camino. */
    private static List<String> programacion(ResultadoPlanificacion resultado) {
        return resultado.evaluacion().schedulesByRouteId().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "|" + e.getValue().completedAt() + "|" + e.getValue().totalDistanceKm()
                        + "|" + e.getValue().totalCost() + "|" + e.getValue().scheduledStops().stream()
                        .map(p -> p.stop().location() + "@" + p.arrivedAt() + "-" + p.completedAt() + ":"
                                + p.loadBefore() + ">" + p.loadAfter() + ":" + p.approach().legs())
                        .toList())
                .toList();
    }

    private static List<Order> pedidos() {
        Instant plazo = HORA.plusSeconds(10 * 3600);
        return List.of(
                new Order("P01", new Location(31, 14), 12, HORA, plazo),
                new Order("P02", new Location(27, 18), 8, HORA, plazo),
                new Order("P03", new Location(29, 16), 4, HORA, plazo));
    }

    private static OperationalSnapshot snapshot(FuenteDatosOperativos fuente) {
        Warehouse central = fuente.almacenes().stream().filter(Warehouse::isCentral).findFirst().orElseThrow();
        Map<String, VehicleOperationalState> estados = new LinkedHashMap<>();
        for (Vehicle vehiculo : fuente.flota()) {
            estados.put(vehiculo.id(),
                    new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, central.location(), HORA));
        }
        return new OperationalSnapshot(HORA, fuente.perfilFlota(), InventorySnapshot.from(fuente.almacenes()), estados,
                fuente.calendarioMantenimiento(), new ShiftSchedule(ShiftSchedule.DEFAULT_ZONE), fuente.averias(null));
    }
}
