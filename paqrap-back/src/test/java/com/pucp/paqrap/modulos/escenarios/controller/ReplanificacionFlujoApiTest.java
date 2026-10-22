package com.pucp.paqrap.modulos.escenarios.controller;

import com.jayway.jsonpath.JsonPath;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
import com.pucp.paqrap.modulos.almacenes.repository.InventoryMovementRepository;
import com.pucp.paqrap.modulos.almacenes.repository.WarehouseRepository;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioStatus;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioResultRepository;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioResultEntity;
import com.pucp.paqrap.modulos.escenarios.service.EjecutorPlanService;
import com.pucp.paqrap.modulos.escenarios.service.MotorEscenarios;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.persistence.VehicleTypeParametersEntity;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.flota.repository.VehicleTypeParametersRepository;
import com.pucp.paqrap.modulos.incidencias.repository.BreakdownEventRepository;
import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.entity.OrderStatus;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderStatusHistoryEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.pedidos.repository.OrderStatusHistoryRepository;
import com.pucp.paqrap.modulos.planificacion.persistence.PlanEntity;
import com.pucp.paqrap.modulos.planificacion.persistence.RouteEntity;
import com.pucp.paqrap.modulos.planificacion.persistence.RouteStopEntity;
import com.pucp.paqrap.modulos.planificacion.repository.PlanRepository;
import com.pucp.paqrap.modulos.planificacion.repository.RouteRepository;
import com.pucp.paqrap.modulos.planificacion.repository.RouteStopRepository;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.persistence.RoadBlockEntity;
import com.pucp.paqrap.modulos.redvial.repository.RoadBlockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo completo con el planificador real (adapter, replanificación con SA y ejecutor del plan) sobre H2:
 * inicio, pedido nuevo, avería, bloqueo y ciclo periódico generan un plan nuevo sin perder lo ya ejecutado.
 * Escenario 5D (x160) desde el 01-ene 00:00 en Lima = 05:00Z; 1 s real = 2 min 40 s simulados.
 */
@SpringBootTest(properties = "paqrap.planificacion.presupuesto-ms=200")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReplanificacionFlujoApiTest {

    private static final Instant INICIO = Instant.parse("2026-01-01T05:00:00Z");

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        RelojManual relojManual() {
            return new RelojManual();
        }
    }

    static final class RelojManual extends Clock {
        private volatile Instant ahora = Instant.parse("2026-10-08T12:00:00Z");

        void avanzar(Duration duracion) { ahora = ahora.plus(duracion); }

        @Override public Instant instant() { return ahora; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    @Autowired private MockMvc mvc;
    @Autowired private RelojManual reloj;
    @Autowired private MotorEscenarios motor;
    @Autowired private EjecutorPlanService ejecutor;
    @Autowired private ScenarioExecutionRepository ejecucionRepository;
    @Autowired private ScenarioResultRepository resultadoRepository;
    @Autowired private WarehouseRepository almacenRepository;
    @Autowired private InventoryMovementRepository movimientoRepository;
    @Autowired private VehicleRepository vehiculoRepository;
    @Autowired private VehicleTypeParametersRepository tipoRepository;
    @Autowired private BreakdownEventRepository averiaRepository;
    @Autowired private RoadBlockRepository bloqueoRepository;
    @Autowired private OrderRepository pedidoRepository;
    @Autowired private OrderStatusHistoryRepository historialRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private RouteStopRepository routeStopRepository;

    @BeforeEach
    void cargarDatos() {
        tipoRepository.save(new VehicleTypeParametersEntity(VehicleType.CAR, 24, new BigDecimal("40.00"), new BigDecimal("8.00")));
        tipoRepository.save(new VehicleTypeParametersEntity(VehicleType.MOTORCYCLE, 8, new BigDecimal("25.00"), new BigDecimal("6.00")));
        tipoRepository.save(new VehicleTypeParametersEntity(VehicleType.BICYCLE, 4, new BigDecimal("12.00"), new BigDecimal("3.00")));
        almacenRepository.save(new WarehouseEntity("CENTRAL", WarehouseKind.CENTRAL, 27, 14, null));
        for (String id : List.of("TA01", "TA02", "TA03")) {
            vehiculoRepository.save(new VehicleEntity(id, VehicleType.CAR, VehicleOperationalStatus.AVAILABLE));
        }
    }

    @AfterEach
    void limpiar() {
        ejecucionRepository.findByStatusIn(List.of(ScenarioStatus.RUNNING, ScenarioStatus.PAUSED))
                .forEach(e -> motor.detener(e.getExecutionId()));
        historialRepository.deleteAll();
        routeStopRepository.deleteAll();
        routeRepository.deleteAll();
        planRepository.deleteAll();
        pedidoRepository.deleteAll();
        movimientoRepository.deleteAll();
        averiaRepository.deleteAll();
        bloqueoRepository.deleteAll();
        resultadoRepository.deleteAll();
        ejecucionRepository.deleteAll();
        vehiculoRepository.deleteAll();
        tipoRepository.deleteAll();
        almacenRepository.deleteAll();
    }

    private void pedido(String id, int x, int y, int paquetes, Instant registro) {
        OrderEntity pedido = new OrderEntity(id, "c-" + id, new Location(x, y), paquetes, registro, DeliveryType.REGULAR, 36);
        pedidoRepository.save(pedido);
        historialRepository.save(OrderStatusHistoryEntity.registro(pedido));
    }

    private long iniciarCincoDias() throws Exception {
        String respuesta = mvc.perform(post("/api/escenarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\": \"FIVE_DAY\", \"inicioSimulado\": \"" + INICIO + "\"}"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
        mvc.perform(post("/api/escenarios/" + id + "/iniciar")).andExpect(status().isOk());
        return id;
    }

    private void avanzar(Duration real) {
        reloj.avanzar(real);
        motor.tick();
    }

    private String replanificacion(long id) throws Exception {
        return mvc.perform(get("/api/planificacion/" + id + "/replanificacion"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static <T> T leer(String json, String ruta) {
        return JsonPath.read(json, ruta);
    }

    /** Vehículo de la ruta que entrega el pedido en el plan indicado. */
    private static String vehiculoQueEntrega(String json, String pedidoId) {
        List<Map<String, Object>> rutas = leer(json, "$.plan.rutas");
        List<String> vehiculos = rutas.stream()
                .filter(ruta -> ((List<?>) ruta.get("paradas")).stream()
                        .anyMatch(parada -> pedidoId.equals(((Map<?, ?>) parada).get("pedidoId"))))
                .map(ruta -> (String) ruta.get("vehiculoId"))
                .toList();
        assertEquals(1, vehiculos.size(), () -> "el pedido " + pedidoId + " debe ir en una sola ruta: " + json);
        return vehiculos.getFirst();
    }

    private ScenarioStatus estado(long id) {
        return ejecucionRepository.findById(id).orElseThrow().getStatus();
    }

    @Test
    void cadaEventoGeneraUnPlanNuevoSinPerderLoEjecutado() throws Exception {
        pedido("P1", 30, 14, 5, INICIO);
        pedido("P2", 24, 14, 3, INICIO.plusSeconds(20 * 60));
        long id = iniciarCincoDias();

        // Inicio: plan inicial con P1; P2 todavía no llega.
        String inicial = replanificacion(id);
        assertEquals("INICIO", leer(inicial, "$.motivo"));
        String vehiculoP1 = vehiculoQueEntrega(inicial, "P1");
        String rutaP1 = leer(inicial, "$.plan.rutas[?(@.vehiculoId=='" + vehiculoP1 + "')].id").toString();

        // 05:13:20: la ruta de P1 ya salió (el vehículo no vuelve al central por arte de magia).
        avanzar(Duration.ofSeconds(5));
        assertEquals(VehicleOperationalStatus.IN_ROUTE,
                vehiculoRepository.findById(vehiculoP1).orElseThrow().getOperationalStatus());

        // 05:26:40: llegó P2 (05:20) -> replanificación con la ruta de P1 conservada y P2 en otro vehículo.
        avanzar(Duration.ofSeconds(5));
        String conPedido = replanificacion(id);
        assertEquals("NUEVO_PEDIDO", leer(conPedido, "$.motivo"));
        assertEquals(1, (int) leer(conPedido, "$.numeroReplanificacion"));
        assertEquals(rutaP1, leer(conPedido, "$.rutasConservadas").toString());
        String vehiculoP2 = vehiculoQueEntrega(conPedido, "P2");
        assertNotEquals(vehiculoP1, vehiculoP2, "el vehículo de P1 sigue ocupado en su ruta");

        // Avería del vehículo de P2 un minuto después de que sale: su ruta se interrumpe y P2 va en otro.
        Instant instanteAveria = Instant.parse(leer(conPedido, "$.hora")).plusSeconds(60);
        mvc.perform(post("/api/incidencias/averias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehiculoId\": \"" + vehiculoP2 + "\", \"tipo\": \"MINOR\", \"ocurridaEn\": \""
                                + instanteAveria + "\", \"ejecucionId\": " + id + "}"))
                .andExpect(status().isCreated());
        avanzar(Duration.ofSeconds(1));
        String conAveria = replanificacion(id);
        assertEquals("AVERIA", leer(conAveria, "$.motivo"));
        List<String> noDisponibles = leer(conAveria, "$.vehiculosNoDisponibles");
        assertTrue(noDisponibles.contains(vehiculoP2), conAveria);
        String reemplazo = vehiculoQueEntrega(conAveria, "P2");
        assertFalse(List.of(vehiculoP1, vehiculoP2).contains(reemplazo), conAveria);
        assertTrue(leer(conAveria, "$.rutasConservadas").toString().contains(rutaP1), conAveria);

        // Inicio de un bloqueo lejos de las rutas: nuevo plan que conserva las rutas en marcha.
        Instant ahora = Instant.parse(leer(conAveria, "$.hora"));
        bloqueoRepository.save(RoadBlockEntity.desde(new RoadBlock(ahora.plusSeconds(60), ahora.plusSeconds(3 * 3600),
                List.of(new Location(60, 45), new Location(65, 45)))));
        avanzar(Duration.ofSeconds(1));
        String conBloqueo = replanificacion(id);
        assertEquals("BLOQUEO", leer(conBloqueo, "$.motivo"));
        assertEquals(3, (int) leer(conBloqueo, "$.numeroReplanificacion"));

        // Una hora simulada sin eventos (22,5 s reales): ciclo periódico.
        avanzar(Duration.ofSeconds(23));
        String periodico = replanificacion(id);
        assertEquals("PERIODICO", leer(periodico, "$.motivo"));
        assertEquals(4, (int) leer(periodico, "$.numeroReplanificacion"));

        // Hasta el día siguiente: los dos pedidos se entregan una sola vez y la ejecución sigue en marcha.
        avanzar(Duration.ofMinutes(10));
        assertEquals(ScenarioStatus.RUNNING, estado(id));
        assertEquals(OrderStatus.DELIVERED, pedidoRepository.findById("P1").orElseThrow().getStatus());
        assertEquals(OrderStatus.DELIVERED, pedidoRepository.findById("P2").orElseThrow().getStatus());
        EjecutorPlanService.Metricas metricas = ejecutor.metricas(id).orElseThrow();
        assertEquals(2, metricas.pedidosEntregados());
        assertEquals(8, metricas.paquetesEntregados(), "ningún paquete se entrega dos veces");
        Map<String, Long> entregas = Map.of(
                "P1", historialRepository.findByOrderIdOrderByChangedAtAscStatusHistoryIdAsc("P1").stream()
                        .filter(h -> h.getStatus() == OrderStatus.DELIVERED).count(),
                "P2", historialRepository.findByOrderIdOrderByChangedAtAscStatusHistoryIdAsc("P2").stream()
                        .filter(h -> h.getStatus() == OrderStatus.DELIVERED).count());
        assertEquals(Map.of("P1", 1L, "P2", 1L), entregas);
        mvc.perform(get("/api/monitoreo/" + id))
                .andExpect(jsonPath("$.colapso").doesNotExist());
    }

    @Test
    void persisteSnapshotsInmutablesYResultadoFinalDeUnaEjecucionSaReal() throws Exception {
        pedido("P1", 30, 14, 5, INICIO);
        pedido("P2", 24, 14, 3, INICIO.plusSeconds(20 * 60));
        long id = iniciarCincoDias();

        String inicialJson = replanificacion(id);
        long duracionInicial = ((Number) leer(inicialJson, "$.plan.duracionMs")).longValue();
        List<PlanEntity> planesIniciales = planRepository.findByExecutionExecutionIdOrderByPlanIdAsc(id);
        assertEquals(1, planesIniciales.size());

        PlanEntity inicial = planesIniciales.getFirst();
        List<SnapshotRuta> rutasIniciales = snapshotRutas(inicial.getPlanId());
        assertFalse(rutasIniciales.isEmpty(), "el snapshot inicial debe contener rutas");
        assertTrue(rutasIniciales.stream().anyMatch(ruta -> !ruta.paradas().isEmpty()),
                "el snapshot inicial debe contener route_stops");

        // P2 llega al minuto 20; a los 26:40 simulados se procesa y provoca una replanificación real.
        avanzar(Duration.ofSeconds(10));
        String replanificadoJson = replanificacion(id);
        long duracionReplanificacion = ((Number) leer(replanificadoJson, "$.plan.duracionMs")).longValue();
        int replanificacionesObservadas = ((Number) leer(replanificadoJson, "$.numeroReplanificacion")).intValue();

        List<PlanEntity> planesTrasReplanificacion = planRepository.findByExecutionExecutionIdOrderByPlanIdAsc(id);
        assertTrue(planesTrasReplanificacion.size() >= 2);
        assertEquals(inicial.getPlanId(), planesTrasReplanificacion.getFirst().getPlanId());
        assertEquals(rutasIniciales, snapshotRutas(inicial.getPlanId()),
                "la replanificación no puede sobrescribir rutas ni paradas del snapshot inicial");

        // El salto final llega al límite de cinco días; no introduce un ciclo SA adicional.
        avanzar(Duration.ofMinutes(45));

        assertEquals(ScenarioStatus.COMPLETED, estado(id));
        assertEquals(1, resultadoRepository.count());
        ScenarioResultEntity resultado = resultadoRepository.findById(id).orElseThrow();

        EjecutorPlanService.Metricas ejecutadas = ejecutor.metricas(id).orElseThrow();
        assertEquals(2, resultado.getTotalOrders());
        assertEquals(ejecutadas.pedidosEntregados(), resultado.getDeliveredOrders());
        assertEquals(ejecutadas.entregadosATiempo(), resultado.getOnTimeOrders());
        assertEquals(resultado.getTotalOrders() - ejecutadas.pedidosEntregados(), resultado.getUndeliveredOrders());
        assertEquals(ejecutadas.distanciaKm(), resultado.getTotalDistanceKm().doubleValue(), 0.000001);
        assertEquals(ejecutadas.costo(), resultado.getTotalCost().doubleValue(), 0.000001);
        assertEquals(duracionInicial + duracionReplanificacion, resultado.getComputationTimeMs());
        assertEquals(replanificacionesObservadas, resultado.getReplanningCount());
        assertEquals(0, resultado.getBreakdownCount());
        assertEquals(0, resultado.getRoadBlockCount());
    }

    private List<SnapshotRuta> snapshotRutas(long planId) {
        return routeRepository.findByPlanPlanIdOrderByRouteIdAsc(planId).stream()
                .map(ruta -> new SnapshotRuta(ruta.getRouteId(), ruta.getOperationalRouteId(), ruta.getOriginX(),
                        ruta.getOriginY(), ruta.getInitialLoad(), ruta.getDepartureAt(), ruta.getTotalDistanceKm().doubleValue(),
                        ruta.getTotalCost().doubleValue(), routeStopRepository.findByRouteRouteIdOrderBySequenceNoAsc(
                                ruta.getRouteId()).stream().map(this::snapshotParada).toList()))
                .toList();
    }

    private SnapshotParada snapshotParada(RouteStopEntity parada) {
        return new SnapshotParada(parada.getRouteStopId(), parada.getSequenceNo(), parada.getStopType(),
                parada.getDeliveredPackages(), parada.getX(), parada.getY(), parada.getPlannedArrivalAt());
    }

    private record SnapshotRuta(long id, String operationalId, int origenX, int origenY, int cargaInicial,
                                Instant salida, double distancia, double costo, List<SnapshotParada> paradas) {
    }

    private record SnapshotParada(long id, int secuencia, String tipo, Integer paquetesEntregados, int x, int y,
                                  Instant llegadaPlanificada) {
    }

    @Test
    void alDetenerseLaEjecucionElUltimoPlanSigueConsultable() throws Exception {
        pedido("P1", 30, 14, 5, INICIO);
        long id = iniciarCincoDias();

        mvc.perform(post("/api/escenarios/" + id + "/detener")).andExpect(status().isOk());

        assertEquals("INICIO", leer(replanificacion(id), "$.motivo"));
        mvc.perform(get("/api/planificacion/" + id + "/plan")).andExpect(status().isOk());
    }
}
