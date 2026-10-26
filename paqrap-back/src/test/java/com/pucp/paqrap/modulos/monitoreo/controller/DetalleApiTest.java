package com.pucp.paqrap.modulos.monitoreo.controller;

import com.jayway.jsonpath.JsonPath;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
import com.pucp.paqrap.modulos.almacenes.repository.InventoryMovementRepository;
import com.pucp.paqrap.modulos.almacenes.repository.WarehouseRepository;
import com.pucp.paqrap.modulos.almacenes.service.InventarioService;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioStatus;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioResultRepository;
import com.pucp.paqrap.modulos.escenarios.service.MotorEscenarios;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.persistence.VehicleTypeParametersEntity;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.flota.repository.VehicleTypeParametersRepository;
import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.entity.OrderStatus;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderStatusHistoryEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.pedidos.repository.OrderStatusHistoryRepository;
import com.pucp.paqrap.modulos.planificacion.dto.ModoOperacion;
import com.pucp.paqrap.modulos.planificacion.dto.ParadaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.RutaResponse;
import com.pucp.paqrap.modulos.planificacion.service.PlanVigente;
import com.pucp.paqrap.modulos.planificacion.service.PlanificadorPort;
import com.pucp.paqrap.modulos.planificacion.service.ReplanificacionService;
import com.pucp.paqrap.modulos.redvial.entity.Location;
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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Detalle de unidad (P-05), de almacén (P-07) y de pedido (P-06). Escenario 5D (x160) desde el 01-ene 00:00 en Lima
 * = 05:00Z: 1 min real = 2 h 40 min simulados. El reloj real es falso y el motor no avanza solo; el plan vigente
 * lo fija cada test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DetalleApiTest {

    private static final Instant INICIO = Instant.parse("2026-01-01T05:00:00Z");

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        RelojManual relojManual() {
            return new RelojManual();
        }

        @Bean
        @Primary
        PlanificadorDePrueba planificadorDePrueba() {
            return new PlanificadorDePrueba();
        }

        @Bean
        @Primary
        PlanesDePrueba planesDePrueba() {
            return new PlanesDePrueba();
        }
    }

    static final class RelojManual extends Clock {
        private volatile Instant ahora = Instant.parse("2026-10-08T12:00:00Z");

        void avanzar(Duration duracion) { ahora = ahora.plus(duracion); }

        @Override public Instant instant() { return ahora; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    static final class PlanificadorDePrueba implements PlanificadorPort {
        @Override
        public ResultadoCiclo planificar(SolicitudCiclo solicitud) {
            return ResultadoCiclo.SIN_COLAPSO;
        }

        @Override
        public Optional<ReplanificacionService.MetricasPlanificacion> metricas(long ejecucionId) {
            return Optional.of(new ReplanificacionService.MetricasPlanificacion(0, 0));
        }
    }

    static final class PlanesDePrueba implements PlanVigente {
        final Map<Long, PlanResponse> planes = new ConcurrentHashMap<>();

        @Override
        public Optional<PlanResponse> ultimoPlan(long ejecucionId) {
            return Optional.ofNullable(planes.get(ejecucionId));
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private RelojManual reloj;
    @Autowired private PlanesDePrueba planes;
    @Autowired private MotorEscenarios motor;
    @Autowired private InventarioService inventario;
    @Autowired private ScenarioExecutionRepository ejecucionRepository;
    @Autowired private ScenarioResultRepository resultadoRepository;
    @Autowired private WarehouseRepository almacenRepository;
    @Autowired private InventoryMovementRepository movimientoRepository;
    @Autowired private VehicleRepository vehiculoRepository;
    @Autowired private VehicleTypeParametersRepository tipoRepository;
    @Autowired private OrderRepository pedidoRepository;
    @Autowired private OrderStatusHistoryRepository historialRepository;

    @BeforeEach
    void cargarDatos() {
        planes.planes.clear();
        tipoRepository.save(new VehicleTypeParametersEntity(VehicleType.CAR, 24, new BigDecimal("40.00"), new BigDecimal("8.00")));
        tipoRepository.save(new VehicleTypeParametersEntity(VehicleType.MOTORCYCLE, 8, new BigDecimal("25.00"), new BigDecimal("6.00")));
        almacenRepository.save(new WarehouseEntity("CENTRAL", WarehouseKind.CENTRAL, 27, 14, null));
        almacenRepository.save(new WarehouseEntity("EAST", WarehouseKind.INTERMEDIATE, 57, 27, 1000));
        vehiculoRepository.save(new VehicleEntity("TA01", VehicleType.CAR, VehicleOperationalStatus.AVAILABLE));
        vehiculoRepository.save(new VehicleEntity("TM01", VehicleType.MOTORCYCLE, VehicleOperationalStatus.AVAILABLE));
    }

    /** Ni el motor ni la BD se reinician entre tests: se detiene lo activo y se borra lo creado. */
    @AfterEach
    void limpiar() {
        ejecucionRepository.findByStatusIn(List.of(ScenarioStatus.RUNNING, ScenarioStatus.PAUSED))
                .forEach(e -> motor.detener(e.getExecutionId()));
        historialRepository.deleteAll();
        pedidoRepository.deleteAll();
        movimientoRepository.deleteAll();
        resultadoRepository.deleteAll();
        ejecucionRepository.deleteAll();
        vehiculoRepository.deleteAll();
        almacenRepository.deleteAll();
        tipoRepository.deleteAll();
    }

    private long iniciarCincoDias() throws Exception {
        String respuesta = mvc.perform(post("/api/escenarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\": \"FIVE_DAY\", \"inicioSimulado\": \"" + INICIO + "\"}"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
        mvc.perform(post("/api/escenarios/" + id + "/iniciar")).andExpect(status().isOk());
        return id;
    }

    private static Instant hora(String hhmm) {
        return Instant.parse("2026-01-01T" + hhmm + ":00Z");
    }

    private static ParadaResponse entrega(int orden, String pedidoId, String llegada) {
        return new ParadaResponse(orden, ParadaResponse.ENTREGA, pedidoId, null, 30, 14, 2, hora(llegada),
                hora(llegada), 5, 3);
    }

    private static PlanResponse plan(RutaResponse... rutas) {
        return new PlanResponse(ModoOperacion.SIMULACION_5D, INICIO, true, false, 0, 0, 1L, 0, 1500, false,
                List.of(rutas), List.of(), List.of());
    }

    // ---- P-05: detalle de unidad ----

    @Test
    void unaUnidadSinRutaMuestraSuCapacidadSuTurnoYNingunaEntrega() throws Exception {
        long id = iniciarCincoDias();

        // 05:00Z = 00:00 en Lima: turno de 23:00 a 07:00 (04:00Z a 12:00Z) y refrigerio entre 05:00Z y 11:00Z.
        mvc.perform(get("/api/flota/vehiculos/TA01/detalle").param("ejecucionId", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("TA01"))
                .andExpect(jsonPath("$.tipo").value("CAR"))
                .andExpect(jsonPath("$.estado").value("AVAILABLE"))
                .andExpect(jsonPath("$.capacidad").value(24))
                .andExpect(jsonPath("$.cargaActual").value(0))
                .andExpect(jsonPath("$.instante").value("2026-01-01T05:00:00Z"))
                .andExpect(jsonPath("$.turnoVigente.inicio").value("2026-01-01T04:00:00Z"))
                .andExpect(jsonPath("$.turnoVigente.fin").value("2026-01-01T12:00:00Z"))
                .andExpect(jsonPath("$.ventanaAlimentacion.inicio").value("2026-01-01T05:00:00Z"))
                .andExpect(jsonPath("$.ventanaAlimentacion.fin").value("2026-01-01T11:00:00Z"))
                .andExpect(jsonPath("$.rutaId").doesNotExist())
                .andExpect(jsonPath("$.proximasEntregas", hasSize(0)));
    }

    @Test
    void unaUnidadConRutaListaSoloLasEntregasQueAunNoOcurrieron() throws Exception {
        long id = iniciarCincoDias();
        planes.planes.put(id, plan(
                new RutaResponse("SA-TA01", "TA01", "CAR", hora("05:30"), hora("10:00"), 14.0, 112.0, List.of(
                        entrega(1, "P1", "06:00"),
                        entrega(2, "P2", "08:00"),
                        entrega(3, "P3", "09:00"),
                        new ParadaResponse(4, ParadaResponse.ALMACEN, null, "CENTRAL", 27, 14, 0, hora("10:00"),
                                hora("10:00"), 0, 0))),
                new RutaResponse("SA-TM01", "TM01", "MOTORCYCLE", hora("05:30"), hora("09:30"), 6.0, 36.0, List.of(
                        entrega(1, "P9", "08:30")))));

        // 07:40Z: P1 (06:00Z) ya se entregó; el almacén no cuenta como entrega.
        reloj.avanzar(Duration.ofMinutes(1));
        mvc.perform(get("/api/flota/vehiculos/TA01/detalle").param("ejecucionId", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instante").value("2026-01-01T07:40:00Z"))
                .andExpect(jsonPath("$.rutaId").value("SA-TA01"))
                .andExpect(jsonPath("$.proximasEntregas", hasSize(2)))
                .andExpect(jsonPath("$.proximasEntregas[0].pedidoId").value("P2"))
                .andExpect(jsonPath("$.proximasEntregas[0].llegadaPrevista").value("2026-01-01T08:00:00Z"))
                .andExpect(jsonPath("$.proximasEntregas[1].pedidoId").value("P3"));

        mvc.perform(get("/api/flota/vehiculos/TM01/detalle").param("ejecucionId", String.valueOf(id)))
                .andExpect(jsonPath("$.capacidad").value(8))
                .andExpect(jsonPath("$.proximasEntregas", hasSize(1)))
                .andExpect(jsonPath("$.proximasEntregas[0].pedidoId").value("P9"));
    }

    @Test
    void unaUnidadSinEntregasPendientesNoTieneRutaAunqueElPlanLaIncluya() throws Exception {
        long id = iniciarCincoDias();
        planes.planes.put(id, plan(new RutaResponse("SA-TA01", "TA01", "CAR", hora("05:30"), hora("06:30"), 4.0,
                32.0, List.of(entrega(1, "P1", "06:00")))));

        reloj.avanzar(Duration.ofMinutes(1)); // 07:40Z: todo lo previsto ya ocurrió.
        mvc.perform(get("/api/flota/vehiculos/TA01/detalle").param("ejecucionId", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rutaId").doesNotExist())
                .andExpect(jsonPath("$.proximasEntregas", hasSize(0)));
    }

    @Test
    void elDetalleDeUnaUnidadInexistenteOEjecucionInexistenteDa404() throws Exception {
        long id = iniciarCincoDias();
        mvc.perform(get("/api/flota/vehiculos/NOEXISTE/detalle").param("ejecucionId", String.valueOf(id)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/flota/vehiculos/TA01/detalle").param("ejecucionId", "999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void elDetalleDeUnaUnidadExigeLaEjecucion() throws Exception {
        mvc.perform(get("/api/flota/vehiculos/TA01/detalle")).andExpect(status().isBadRequest());
    }

    // ---- P-07: detalle de almacén ----

    @Test
    void unAlmacenIntermedioMuestraStockOcupacionConsumoHistorialYProximaRecarga() throws Exception {
        long id = iniciarCincoDias();
        inventario.registrarDespacho(id, "EAST", "P1", 300, hora("05:30"));
        inventario.registrarDespacho(id, "EAST", "P2", 100, hora("07:00"));

        reloj.avanzar(Duration.ofMinutes(1)); // 07:40Z
        mvc.perform(get("/api/almacenes/EAST/detalle").param("ejecucionId", String.valueOf(id)).param("horas", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("EAST"))
                .andExpect(jsonPath("$.instante").value("2026-01-01T07:40:00Z"))
                .andExpect(jsonPath("$.stock").value(600))
                .andExpect(jsonPath("$.capacidad").value(1000))
                .andExpect(jsonPath("$.ocupacionPct").value(60.0))
                .andExpect(jsonPath("$.ventanaHoras").value(3))
                .andExpect(jsonPath("$.consumoVentana").value(400))
                .andExpect(jsonPath("$.ultimaRecarga").doesNotExist())
                // 07:40Z = 02:40 en Lima: la próxima recarga es ese mismo día a las 23:59:59 (04:59:59Z del 02-ene).
                .andExpect(jsonPath("$.proximaRecarga").value("2026-01-02T04:59:59Z"))
                // Un punto por hora hacia atrás; el de 04:40Z es anterior al inicio del escenario y no se incluye.
                .andExpect(jsonPath("$.historial", hasSize(3)))
                .andExpect(jsonPath("$.historial[0].instante").value("2026-01-01T05:40:00Z"))
                .andExpect(jsonPath("$.historial[0].stock").value(700))
                .andExpect(jsonPath("$.historial[1].stock").value(700))
                .andExpect(jsonPath("$.historial[2].instante").value("2026-01-01T07:40:00Z"))
                .andExpect(jsonPath("$.historial[2].stock").value(600));
    }

    @Test
    void despuesDeLaRecargaDiariaElDetalleMuestraElStockRestablecidoYLaUltimaRecarga() throws Exception {
        long id = iniciarCincoDias();
        inventario.registrarDespacho(id, "EAST", "P1", 300, hora("05:30"));

        reloj.avanzar(Duration.ofMinutes(10)); // +26 h 40 min: 02-ene 07:40Z, ya pasó la recarga de 04:59:59Z.
        motor.tick();
        mvc.perform(get("/api/almacenes/EAST/detalle").param("ejecucionId", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(1000))
                .andExpect(jsonPath("$.ocupacionPct").value(100.0))
                .andExpect(jsonPath("$.ultimaRecarga").value("2026-01-02T04:59:59Z"))
                .andExpect(jsonPath("$.proximaRecarga").value("2026-01-03T04:59:59Z"));
    }

    @Test
    void elAlmacenCentralNoTieneStockCapacidadNiHistorial() throws Exception {
        long id = iniciarCincoDias();
        mvc.perform(get("/api/almacenes/CENTRAL/detalle").param("ejecucionId", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("CENTRAL"))
                .andExpect(jsonPath("$.stock").doesNotExist())
                .andExpect(jsonPath("$.capacidad").doesNotExist())
                .andExpect(jsonPath("$.ocupacionPct").doesNotExist())
                .andExpect(jsonPath("$.proximaRecarga").doesNotExist())
                .andExpect(jsonPath("$.historial", hasSize(0)));
    }

    @Test
    void elDetalleDeAlmacenValidaLaVentanaYLosRecursos() throws Exception {
        long id = iniciarCincoDias();
        mvc.perform(get("/api/almacenes/EAST/detalle").param("ejecucionId", String.valueOf(id)).param("horas", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/almacenes/EAST/detalle").param("ejecucionId", String.valueOf(id)).param("horas", "73"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/almacenes/NOEXISTE/detalle").param("ejecucionId", String.valueOf(id)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/almacenes/EAST/detalle").param("ejecucionId", "999999"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/almacenes/EAST/detalle")).andExpect(status().isBadRequest());
    }

    // ---- P-06: detalle de pedido ----

    private OrderEntity pedidoAsignadoA(String unidad) {
        OrderEntity pedido = pedidoRepository.save(
                new OrderEntity("P1", "c1", new Location(30, 14), 5, INICIO, DeliveryType.REGULAR, 36));
        historialRepository.save(OrderStatusHistoryEntity.registro(pedido));
        historialRepository.save(OrderStatusHistoryEntity.cambio(pedido, OrderStatus.ASSIGNED,
                INICIO.plusSeconds(600), unidad));
        return pedido;
    }

    @Test
    void elPedidoMuestraSuUnidadAsignadaYSinInstanteNoCalculaElTiempoRestante() throws Exception {
        pedidoAsignadoA("TA01");
        mvc.perform(get("/api/pedidos/P1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unidadAsignadaId").value("TA01"))
                .andExpect(jsonPath("$.incumplido").value(false))
                .andExpect(jsonPath("$.minutosRestantes").doesNotExist())
                .andExpect(jsonPath("$.historial", hasSize(2)));
    }

    @Test
    void unPedidoNoAsignadoNoTieneUnidad() throws Exception {
        OrderEntity pedido = pedidoRepository.save(
                new OrderEntity("P1", "c1", new Location(30, 14), 5, INICIO, DeliveryType.REGULAR, 36));
        historialRepository.save(OrderStatusHistoryEntity.registro(pedido));
        mvc.perform(get("/api/pedidos/P1"))
                .andExpect(jsonPath("$.unidadAsignadaId").doesNotExist());
    }

    @Test
    void conElInstanteDentroDelPlazoCalculaLosMinutosRestantes() throws Exception {
        pedidoAsignadoA("TA01");
        // Plazo de 36 h desde 05:00Z; 1 h después quedan 35 h = 2100 min.
        mvc.perform(get("/api/pedidos/P1").param("instante", "2026-01-01T06:00:00Z"))
                .andExpect(jsonPath("$.incumplido").value(false))
                .andExpect(jsonPath("$.minutosRestantes").value(2100));
    }

    @Test
    void conElInstanteDespuesDelPlazoSinEntregarEstaIncumplidoYLosMinutosSonNegativos() throws Exception {
        pedidoAsignadoA("TA01");
        mvc.perform(get("/api/pedidos/P1").param("instante", "2026-01-02T18:00:00Z"))
                .andExpect(jsonPath("$.incumplido").value(true))
                .andExpect(jsonPath("$.minutosRestantes").value(-60));
    }

    @Test
    void unPedidoEntregadoTardeEstaIncumplidoYUnoEntregadoATiempoNo() throws Exception {
        OrderEntity tarde = new OrderEntity("P-TARDE", "c1", new Location(30, 14), 5, INICIO, DeliveryType.REGULAR, 36);
        tarde.entregar(INICIO.plus(Duration.ofHours(40)));
        OrderEntity aTiempo = new OrderEntity("P-OK", "c1", new Location(30, 14), 5, INICIO, DeliveryType.REGULAR, 36);
        aTiempo.entregar(INICIO.plus(Duration.ofHours(10)));
        pedidoRepository.save(tarde);
        pedidoRepository.save(aTiempo);

        mvc.perform(get("/api/pedidos/P-TARDE").param("instante", "2026-01-03T00:00:00Z"))
                .andExpect(jsonPath("$.incumplido").value(true))
                .andExpect(jsonPath("$.minutosRestantes").doesNotExist());
        mvc.perform(get("/api/pedidos/P-OK").param("instante", "2026-01-03T00:00:00Z"))
                .andExpect(jsonPath("$.incumplido").value(false))
                .andExpect(jsonPath("$.minutosRestantes").doesNotExist());
    }

    @Test
    void unPedidoInexistenteDa404() throws Exception {
        mvc.perform(get("/api/pedidos/NOEXISTE")).andExpect(status().isNotFound());
    }
}
