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
import com.pucp.paqrap.modulos.flota.persistence.MaintenanceDayEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.repository.MaintenanceDayRepository;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.incidencias.repository.BreakdownEventRepository;
import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.pedidos.repository.OrderStatusHistoryRepository;
import com.pucp.paqrap.modulos.planificacion.service.PlanificadorPort;
import com.pucp.paqrap.modulos.planificacion.service.ReplanificacionService;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Eventos dentro de la simulación (replanificación por bloqueo y por avería, recarga diaria) y monitoreo.
 * Escenario 5D (x160) desde el 01-ene 00:00 en Lima = 05:00Z. El reloj real es falso y el motor no avanza solo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventosYMonitoreoApiTest {

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
    }

    static final class RelojManual extends Clock {
        private volatile Instant ahora = Instant.parse("2026-10-08T12:00:00Z");

        void avanzar(Duration duracion) { ahora = ahora.plus(duracion); }

        @Override public Instant instant() { return ahora; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    static final class PlanificadorDePrueba implements PlanificadorPort {
        final List<SolicitudCiclo> solicitudes = new CopyOnWriteArrayList<>();

        @Override
        public ResultadoCiclo planificar(SolicitudCiclo solicitud) {
            solicitudes.add(solicitud);
            return ResultadoCiclo.SIN_COLAPSO;
        }

        List<Motivo> motivos() {
            return solicitudes.stream().map(SolicitudCiclo::motivo).toList();
        }

        @Override
        public Optional<ReplanificacionService.MetricasPlanificacion> metricas(long ejecucionId) {
            return Optional.of(new ReplanificacionService.MetricasPlanificacion(0,
                    Math.max(0, solicitudes.size() - 1)));
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private RelojManual reloj;
    @Autowired private PlanificadorDePrueba planificador;
    @Autowired private MotorEscenarios motor;
    @Autowired private InventarioService inventario;
    @Autowired private ScenarioExecutionRepository ejecucionRepository;
    @Autowired private ScenarioResultRepository resultadoRepository;
    @Autowired private WarehouseRepository almacenRepository;
    @Autowired private InventoryMovementRepository movimientoRepository;
    @Autowired private VehicleRepository vehiculoRepository;
    @Autowired private BreakdownEventRepository averiaRepository;
    @Autowired private RoadBlockRepository bloqueoRepository;
    @Autowired private OrderRepository pedidoRepository;
    @Autowired private OrderStatusHistoryRepository historialRepository;
    @Autowired private MaintenanceDayRepository mantenimientoRepository;

    @BeforeEach
    void cargarDatos() {
        planificador.solicitudes.clear();
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
        averiaRepository.deleteAll();
        mantenimientoRepository.deleteAll();
        bloqueoRepository.deleteAll();
        resultadoRepository.deleteAll();
        ejecucionRepository.deleteAll();
        vehiculoRepository.deleteAll();
        almacenRepository.deleteAll();
    }

    private long iniciarCincoDias() throws Exception {
        String respuesta = mvc.perform(post("/api/escenarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\": \"FIVE_DAY\", \"inicioSimulado\": \"" + INICIO + "\"}"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
        mvc.perform(post("/api/escenarios/" + id + "/iniciar")).andExpect(status().isOk());
        return id;
    }

    @Test
    void unBloqueoQueEmpiezaDuranteLaSimulacionDisparaUnaReplanificacion() throws Exception {
        bloqueoRepository.save(RoadBlockEntity.desde(new RoadBlock(INICIO.plusSeconds(30 * 60),
                INICIO.plusSeconds(5 * 3600), List.of(new Location(10, 10), new Location(10, 15)))));
        iniciarCincoDias();

        // 15 s reales x 160 = 40 min simulados: el bloqueo empezó a los 30 min; aún no toca el ciclo periódico.
        reloj.avanzar(Duration.ofSeconds(15));
        motor.tick();

        assertEquals(List.of(PlanificadorPort.Motivo.INICIAL, PlanificadorPort.Motivo.INCIDENCIA), planificador.motivos());
        assertEquals(List.of(INICIO.plusSeconds(30 * 60)),
                planificador.solicitudes.get(1).bloqueosIniciados().stream().map(RoadBlock::startsAt).toList());

        // Ya se atendió: el siguiente tick no vuelve a replanificar por el mismo bloqueo.
        reloj.avanzar(Duration.ofSeconds(1));
        motor.tick();
        assertEquals(2, planificador.solicitudes.size());
    }

    /** La avería replanifica cuando el reloj llega a su instante, con la avería en la solicitud. */
    @Test
    void unaAveriaRegistradaEnLaEjecucionDisparaUnaReplanificacionAlOcurrir() throws Exception {
        long id = iniciarCincoDias();

        mvc.perform(post("/api/incidencias/averias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehiculoId\": \"TA01\", \"tipo\": \"MINOR\", \"ocurridaEn\": \"2026-01-01T05:10:00Z\","
                                + " \"ejecucionId\": " + id + "}"))
                .andExpect(status().isCreated());
        // 1 s real = 2 min 40 s simulados (05:02:40Z): la avería aún no ocurre.
        reloj.avanzar(Duration.ofSeconds(1));
        motor.tick();
        assertEquals(List.of(PlanificadorPort.Motivo.INICIAL), planificador.motivos());

        // +3 s reales = 05:10:40Z: ya ocurrió.
        reloj.avanzar(Duration.ofSeconds(3));
        motor.tick();

        assertEquals(List.of(PlanificadorPort.Motivo.INICIAL, PlanificadorPort.Motivo.INCIDENCIA), planificador.motivos());
        PlanificadorPort.SolicitudCiclo solicitud = planificador.solicitudes.get(1);
        assertEquals(id, solicitud.ejecucionId());
        assertEquals(List.of("TA01"), solicitud.averias().stream().map(a -> a.vehicleId()).toList());
        assertEquals(Instant.parse("2026-01-01T05:10:00Z"), solicitud.averias().getFirst().occurredAt());

        // Ya se atendió: el siguiente tick no vuelve a replanificar por la misma avería.
        reloj.avanzar(Duration.ofSeconds(1));
        motor.tick();
        assertEquals(2, planificador.solicitudes.size());
    }

    /** Una avería registrada con un instante que el reloj ya pasó replanifica en el próximo tick. */
    @Test
    void unaAveriaRegistradaConUnInstanteYaPasadoReplanificaEnElProximoTick() throws Exception {
        long id = iniciarCincoDias();
        reloj.avanzar(Duration.ofSeconds(15));
        motor.tick(); // 05:40Z

        mvc.perform(post("/api/incidencias/averias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehiculoId\": \"TA01\", \"tipo\": \"MINOR\", \"ocurridaEn\": \"2026-01-01T05:20:00Z\","
                                + " \"ejecucionId\": " + id + "}"))
                .andExpect(status().isCreated());
        reloj.avanzar(Duration.ofSeconds(1));
        motor.tick();

        assertEquals(List.of(PlanificadorPort.Motivo.INICIAL, PlanificadorPort.Motivo.INCIDENCIA), planificador.motivos());
        assertEquals(List.of("TA01"), planificador.solicitudes.get(1).averias().stream().map(a -> a.vehicleId()).toList());
    }

    /** En 5D el colapso solo se informa: la ejecución sigue y el monitoreo muestra el momento y la causa. */
    @Test
    void enCincoDiasElColapsoSeInformaEnElMonitoreoSinTerminarLaEjecucion() throws Exception {
        pedidoRepository.save(new OrderEntity("P1", "c1", new Location(30, 14), 2, INICIO, DeliveryType.PRIORITY, 4));
        long id = iniciarCincoDias();
        mvc.perform(get("/api/monitoreo/" + id)).andExpect(jsonPath("$.colapso").doesNotExist());

        // 2 min reales = 5 h 20 min simulados: el plazo de las 09:00Z venció y el planificador de prueba no entrega.
        reloj.avanzar(Duration.ofMinutes(2));
        motor.tick();

        mvc.perform(get("/api/monitoreo/" + id))
                .andExpect(jsonPath("$.escenario.estado").value("RUNNING"))
                .andExpect(jsonPath("$.colapso.causa").value("PLAZO_VENCIDO"))
                .andExpect(jsonPath("$.colapso.instante").value("2026-01-01T09:00:00Z"))
                .andExpect(jsonPath("$.colapso.pedidoId").value("P1"))
                .andExpect(jsonPath("$.colapso.vencidosSinEntregar").value(1));
    }

    @Test
    void elMonitoreoMuestraElStockYLaRecargaDiariaLoRestablece() throws Exception {
        long id = iniciarCincoDias();
        inventario.registrarDespacho(id, "EAST", "P1", 300, INICIO.plusSeconds(30 * 60));

        // 1 min real = 2 h 40 min simulados (07:40Z): el despacho ya ocurrió.
        reloj.avanzar(Duration.ofMinutes(1));
        motor.tick();
        mvc.perform(get("/api/monitoreo/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instante").value("2026-01-01T07:40:00Z"))
                .andExpect(jsonPath("$.almacenes[?(@.id=='EAST')].stock").value(700))
                .andExpect(jsonPath("$.almacenes[?(@.id=='CENTRAL')].stock").value((Object) null));

        // +9 min reales = +24 h: pasó la recarga de las 23:59:59 en Lima (02-ene 04:59:59Z).
        reloj.avanzar(Duration.ofMinutes(9));
        motor.tick();
        mvc.perform(get("/api/monitoreo/" + id))
                .andExpect(jsonPath("$.almacenes[?(@.id=='EAST')].stock").value(1000));
        assertEquals(2, movimientoRepository.count(), "un despacho y una recarga");
    }

    @Test
    void elMonitoreoResumeLosPedidosLaFlotaYLasAverias() throws Exception {
        pedidoRepository.saveAll(List.of(
                new OrderEntity("P-ANTES", "c1", new Location(30, 14), 2, INICIO.minusSeconds(3600), DeliveryType.REGULAR, 36),
                new OrderEntity("P-LLEGA", "c1", new Location(30, 14), 2, INICIO.plusSeconds(3600), DeliveryType.PRIORITY, 4),
                new OrderEntity("P-LUEGO", "c1", new Location(30, 14), 2, INICIO.plusSeconds(20 * 3600), DeliveryType.REGULAR, 36)));
        long id = iniciarCincoDias();
        mvc.perform(post("/api/incidencias/averias").contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehiculoId\": \"TA01\", \"tipo\": \"MAJOR\", \"ocurridaEn\": \"2026-01-01T05:30:00Z\","
                        + " \"ejecucionId\": " + id + "}"));

        // 3 min reales = 8 h simuladas (13:00Z): P-LLEGA llegó a las 06:00Z y su plazo de 4 h ya venció.
        reloj.avanzar(Duration.ofMinutes(3));
        mvc.perform(get("/api/monitoreo/" + id))
                .andExpect(jsonPath("$.escenario.estado").value("RUNNING"))
                .andExpect(jsonPath("$.pedidos.llegados").value(1))
                // P-ANTES llegó antes del inicio del escenario: no es parte de esta simulación.
                .andExpect(jsonPath("$.pedidos.pendientes").value(0))
                .andExpect(jsonPath("$.pedidos.vencidosSinEntregar").value(1))
                .andExpect(jsonPath("$.vehiculos", hasSize(2)))
                .andExpect(jsonPath("$.vehiculos[?(@.id=='TA01')].averiadoHasta").exists())
                .andExpect(jsonPath("$.vehiculos[?(@.id=='TM01')].averiadoHasta").value((Object) null))
                .andExpect(jsonPath("$.averiasActivas", hasSize(1)));
    }

    @Test
    void informaElMotivoYLaHoraDeTerminoDeCadaVehiculoDetenido() throws Exception {
        // TM01 tiene mantenimiento el 01-ene (día local de Lima): detenido hasta la medianoche, 02-ene 05:00Z.
        mantenimientoRepository.save(new MaintenanceDayEntity("TM01", LocalDate.of(2026, 1, 1)));
        long id = iniciarCincoDias();
        mvc.perform(post("/api/incidencias/averias").contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehiculoId\": \"TA01\", \"tipo\": \"MINOR\", \"ocurridaEn\": \"2026-01-01T05:30:00Z\","
                        + " \"ejecucionId\": " + id + "}"));

        // 15 s reales = 40 min simulados (05:40Z): la avería menor dura 2 h, hasta las 07:30Z.
        reloj.avanzar(Duration.ofSeconds(15));
        mvc.perform(get("/api/monitoreo/" + id))
                .andExpect(jsonPath("$.vehiculos[?(@.id=='TA01')].motivoDetencion").value("AVERIA"))
                .andExpect(jsonPath("$.vehiculos[?(@.id=='TA01')].detenidoHasta").value("2026-01-01T07:30:00Z"))
                .andExpect(jsonPath("$.vehiculos[?(@.id=='TM01')].motivoDetencion").value("MANTENIMIENTO"))
                .andExpect(jsonPath("$.vehiculos[?(@.id=='TM01')].detenidoHasta").value("2026-01-02T05:00:00Z"));

        // +60 s reales = +160 min (08:20Z): la avería ya terminó; TA01 opera sin motivo de detención.
        reloj.avanzar(Duration.ofSeconds(60));
        mvc.perform(get("/api/monitoreo/" + id))
                .andExpect(jsonPath("$.vehiculos[?(@.id=='TA01')].motivoDetencion").value((Object) null))
                .andExpect(jsonPath("$.vehiculos[?(@.id=='TA01')].detenidoHasta").value((Object) null));
    }

    @Test
    void respondeNotFoundParaUnaEjecucionInexistente() throws Exception {
        mvc.perform(get("/api/monitoreo/999999")).andExpect(status().isNotFound());
    }
}
