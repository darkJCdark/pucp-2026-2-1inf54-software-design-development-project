package com.pucp.paqrap.modulos.pedidos.controller;

import com.jayway.jsonpath.JsonPath;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
import com.pucp.paqrap.modulos.almacenes.repository.InventoryMovementRepository;
import com.pucp.paqrap.modulos.almacenes.repository.WarehouseRepository;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioStatus;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioResultRepository;
import com.pucp.paqrap.modulos.escenarios.service.MotorEscenarios;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.pedidos.repository.OrderStatusHistoryRepository;
import com.pucp.paqrap.modulos.planificacion.service.PlanificadorPort;
import com.pucp.paqrap.modulos.planificacion.service.ReplanificacionService;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regla de coherencia (d) y CP-70/CP-75: un pedido manual anterior al inicio del escenario activo da 409 y no se
 * guarda. Escenario 5D desde el 01-ene 00:00 en Lima (05:00Z); el reloj real es falso y el motor no avanza solo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PedidoCoherenciaApiTest {

    private static final Instant INICIO = Instant.parse("2026-01-01T05:00:00Z");

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        RelojFijo relojFijo() {
            return new RelojFijo();
        }

        @Bean
        @Primary
        PlanificadorDePrueba planificadorDePrueba() {
            return new PlanificadorDePrueba();
        }
    }

    static final class RelojFijo extends Clock {
        @Override public Instant instant() { return Instant.parse("2026-10-08T12:00:00Z"); }
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

    @Autowired private MockMvc mvc;
    @Autowired private MotorEscenarios motor;
    @Autowired private ScenarioExecutionRepository ejecucionRepository;
    @Autowired private ScenarioResultRepository resultadoRepository;
    @Autowired private WarehouseRepository almacenRepository;
    @Autowired private InventoryMovementRepository movimientoRepository;
    @Autowired private VehicleRepository vehiculoRepository;
    @Autowired private OrderRepository pedidoRepository;
    @Autowired private OrderStatusHistoryRepository historialRepository;

    @BeforeEach
    void cargarDatos() {
        almacenRepository.save(new WarehouseEntity("CENTRAL", WarehouseKind.CENTRAL, 27, 14, null));
        vehiculoRepository.save(new VehicleEntity("TA01", VehicleType.CAR, VehicleOperationalStatus.AVAILABLE));
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
    }

    private long iniciarCincoDias() throws Exception {
        String respuesta = mvc.perform(post("/api/escenarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\": \"FIVE_DAY\", \"inicioSimulado\": \"" + INICIO + "\"}"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
        mvc.perform(post("/api/escenarios/" + id + "/iniciar")).andExpect(status().isOk());
        return id;
    }

    private ResultActions registrar(String registradoEn) throws Exception {
        return mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\": \"c123\", \"x\": 10, \"y\": 20, \"paquetes\": 5, \"tipoEntrega\": \"REGULAR\""
                        + ", \"registradoEn\": \"" + registradoEn + "\"}"));
    }

    private ResultActions registrarSinFecha(String clienteId, String tipoEntrega, Integer horasPrometidas)
            throws Exception {
        String horas = horasPrometidas == null ? "" : ", \"horasPrometidas\": " + horasPrometidas;
        return mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\": \"" + clienteId + "\", \"x\": 10, \"y\": 20, \"paquetes\": 5, "
                        + "\"tipoEntrega\": \"" + tipoEntrega + "\"" + horas + "}"));
    }

    @Test
    void unPedidoRegularSinFechaDuranteEscenarioEnMarchaUsaElRelojSimulado() throws Exception {
        iniciarCincoDias();

        registrarSinFecha("CP65REGULAR", "REGULAR", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registradoEn").value(INICIO.toString()))
                .andExpect(jsonPath("$.vence").value("2026-01-02T17:00:00Z"));
        OrderEntity pedido = pedidoRepository.findAll().getFirst();
        assertEquals(INICIO, pedido.getRegisteredAt());
        assertEquals(INICIO.plus(Duration.ofHours(36)), pedido.getDeadline());
    }

    @Test
    void unPedidoPrioritySinFechaDuranteEscenarioEnMarchaUsaElRelojSimuladoYSuPlazo() throws Exception {
        iniciarCincoDias();

        registrarSinFecha("CP65PRIORITY", "PRIORITY", 8)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registradoEn").value(INICIO.toString()))
                .andExpect(jsonPath("$.vence").value("2026-01-01T13:00:00Z"));
        OrderEntity pedido = pedidoRepository.findAll().getFirst();
        assertEquals(INICIO, pedido.getRegisteredAt());
        assertEquals(INICIO.plus(Duration.ofHours(8)), pedido.getDeadline());
    }

    @Test
    void unPedidoAnteriorAlInicioDelEscenarioActivoDa409YNoSeGuarda() throws Exception {
        iniciarCincoDias();

        registrar("2026-01-01T04:59:59Z")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("antes del inicio del escenario activo")));
        assertEquals(0, pedidoRepository.count());
    }

    @Test
    void unPedidoEnElInicioOPosteriorAlInicioSeAcepta() throws Exception {
        iniciarCincoDias();

        registrar(INICIO.toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registradoEn").value(INICIO.toString()));
        registrar("2026-01-01T06:00:00Z")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registradoEn").value("2026-01-01T06:00:00Z"));
        assertEquals(2, pedidoRepository.count());
    }

    @Test
    void sinEscenarioActivoUnPedidoConFechaAntiguaSeGuarda() throws Exception {
        registrar("2025-12-01T10:00:00Z").andExpect(status().isCreated());
    }

    @Test
    void conElEscenarioDetenidoUnPedidoAnteriorYaNoDa409() throws Exception {
        long id = iniciarCincoDias();
        mvc.perform(post("/api/escenarios/" + id + "/detener")).andExpect(status().isOk());

        registrar("2026-01-01T04:00:00Z").andExpect(status().isCreated());
    }
}
