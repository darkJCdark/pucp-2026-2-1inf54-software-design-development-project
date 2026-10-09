package com.pucp.paqrap.modulos.escenarios.controller;

import com.jayway.jsonpath.JsonPath;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioStatus;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.escenarios.service.MotorEscenarios;
import com.pucp.paqrap.modulos.planificacion.service.PlanificadorPort;
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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El reloj real es falso y el motor no avanza solo ({@code tick: 0s}): el test mueve el tiempo y llama a tick(). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EscenarioApiTest {

    private static final String INICIO = "2026-01-01T05:00:00Z";

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

    /** Registra cada solicitud y reporta colapso cuando el test lo indica. */
    static final class PlanificadorDePrueba implements PlanificadorPort {
        final List<SolicitudCiclo> solicitudes = new CopyOnWriteArrayList<>();
        volatile boolean reportarColapso;

        @Override
        public ResultadoCiclo planificar(SolicitudCiclo solicitud) {
            solicitudes.add(solicitud);
            return new ResultadoCiclo(reportarColapso);
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RelojManual reloj;
    @Autowired
    private PlanificadorDePrueba planificador;
    @Autowired
    private MotorEscenarios motor;
    @Autowired
    private ScenarioExecutionRepository ejecucionRepository;

    @BeforeEach
    void reiniciarPlanificador() {
        planificador.solicitudes.clear();
        planificador.reportarColapso = false;
    }

    /** El motor es un singleton: se detienen las ejecuciones activas para que no pasen al siguiente test. */
    @AfterEach
    void limpiar() {
        ejecucionRepository.findByStatusIn(List.of(ScenarioStatus.RUNNING, ScenarioStatus.PAUSED))
                .forEach(e -> motor.detener(e.getExecutionId()));
        ejecucionRepository.deleteAll();
    }

    private ResultActions crear(String json) throws Exception {
        return mvc.perform(post("/api/escenarios").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long crearCincoDias() throws Exception {
        String respuesta = crear("{\"tipo\": \"FIVE_DAY\", \"inicioSimulado\": \"" + INICIO + "\"}")
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    private ResultActions accion(long id, String accion) throws Exception {
        return mvc.perform(post("/api/escenarios/" + id + "/" + accion));
    }

    @Test
    void creaUnEscenarioCincoDiasSinIniciarlo() throws Exception {
        crear("{\"tipo\": \"FIVE_DAY\", \"inicioSimulado\": \"" + INICIO + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CREATED"))
                .andExpect(jsonPath("$.factorAceleracion").value(160.0))
                .andExpect(jsonPath("$.inicioSimulado").value(INICIO))
                .andExpect(jsonPath("$.instanteSimulado").doesNotExist());
    }

    @Test
    void validaElInicioSegunElTipo() throws Exception {
        crear("{\"tipo\": \"FIVE_DAY\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("inicioSimulado")));
        crear("{\"tipo\": \"DAY_TO_DAY\", \"inicioSimulado\": \"" + INICIO + "\"}")
                .andExpect(status().isBadRequest());
        crear("{\"tipo\": \"DAY_TO_DAY\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inicioSimulado").value(reloj.instant().toString()))
                .andExpect(jsonPath("$.factorAceleracion").value(1.0));
    }

    @Test
    void recorreElCicloDeVidaConElRelojSimulado() throws Exception {
        long id = crearCincoDias();

        accion(id, "iniciar")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RUNNING"))
                .andExpect(jsonPath("$.instanteSimulado").value(INICIO));

        // 3 min reales x 160 = 8 h simuladas
        reloj.avanzar(Duration.ofMinutes(3));
        mvc.perform(get("/api/escenarios/" + id))
                .andExpect(jsonPath("$.instanteSimulado").value("2026-01-01T13:00:00Z"));

        accion(id, "pausar").andExpect(jsonPath("$.estado").value("PAUSED"));
        reloj.avanzar(Duration.ofHours(1));
        mvc.perform(get("/api/escenarios/" + id))
                .andExpect(jsonPath("$.instanteSimulado").value("2026-01-01T13:00:00Z"));

        accion(id, "reanudar").andExpect(jsonPath("$.estado").value("RUNNING"));
        reloj.avanzar(Duration.ofSeconds(90)); // + 4 h simuladas

        accion(id, "detener")
                .andExpect(jsonPath("$.estado").value("STOPPED"))
                .andExpect(jsonPath("$.finSimulado").value("2026-01-01T17:00:00Z"))
                .andExpect(jsonPath("$.instanteSimulado").doesNotExist())
                .andExpect(jsonPath("$.finReal").exists());
    }

    @Test
    void cincoDiasTerminaSoloAlCumplirLosCincoDiasSimulados() throws Exception {
        long id = crearCincoDias();
        accion(id, "iniciar");

        reloj.avanzar(Duration.ofMinutes(44));
        motor.tick();
        mvc.perform(get("/api/escenarios/" + id)).andExpect(jsonPath("$.estado").value("RUNNING"));

        reloj.avanzar(Duration.ofMinutes(2));
        motor.tick();
        mvc.perform(get("/api/escenarios/" + id))
                .andExpect(jsonPath("$.estado").value("COMPLETED"))
                .andExpect(jsonPath("$.finSimulado").value("2026-01-06T05:00:00Z"));
    }

    @Test
    void pidePlanesAlIniciarYCadaIntervaloSimulado() throws Exception {
        long id = crearCincoDias();
        accion(id, "iniciar");
        assertEquals(1, planificador.solicitudes.size());
        assertEquals(PlanificadorPort.Motivo.INICIAL, planificador.solicitudes.get(0).motivo());
        assertEquals(id, planificador.solicitudes.get(0).ejecucionId());

        // 15 s reales x 160 = 40 min simulados: aún no toca (intervalo de 1 h)
        reloj.avanzar(Duration.ofSeconds(15));
        motor.tick();
        assertEquals(1, planificador.solicitudes.size());

        // 30 s reales = 80 min simulados: toca un ciclo periódico
        reloj.avanzar(Duration.ofSeconds(15));
        motor.tick();
        assertEquals(2, planificador.solicitudes.size());
        assertEquals(PlanificadorPort.Motivo.PERIODICO, planificador.solicitudes.get(1).motivo());
        assertEquals(Instant.parse("2026-01-01T06:20:00Z"), planificador.solicitudes.get(1).instante());
    }

    @Test
    void elEscenarioDeColapsoTerminaCuandoElPlanificadorLoReporta() throws Exception {
        String respuesta = crear("{\"tipo\": \"COLLAPSE\", \"inicioSimulado\": \"" + INICIO + "\"}")
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
        accion(id, "iniciar");

        planificador.reportarColapso = true;
        reloj.avanzar(Duration.ofMinutes(1)); // x 1440 = 1 día simulado
        motor.tick();

        mvc.perform(get("/api/escenarios/" + id))
                .andExpect(jsonPath("$.estado").value("COLLAPSED"))
                .andExpect(jsonPath("$.colapsoEn").value("2026-01-02T05:00:00Z"));
    }

    @Test
    void soloPuedeHaberUnEscenarioActivo() throws Exception {
        long primero = crearCincoDias();
        long segundo = crearCincoDias();
        accion(primero, "iniciar");

        accion(segundo, "iniciar")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("Ya hay un escenario")));
    }

    @Test
    void rechazaTransicionesInvalidas() throws Exception {
        long id = crearCincoDias();

        accion(id, "pausar").andExpect(status().isConflict());
        accion(id, "detener").andExpect(status().isConflict());

        accion(id, "iniciar");
        accion(id, "iniciar").andExpect(status().isConflict());

        mvc.perform(post("/api/escenarios/999999/iniciar")).andExpect(status().isNotFound());
    }

    @Test
    void listaDeLaMasRecienteALaMasAntigua() throws Exception {
        long primero = crearCincoDias();
        long segundo = crearCincoDias();

        mvc.perform(get("/api/escenarios"))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].id").value(segundo))
                .andExpect(jsonPath("$.contenido[1].id").value(primero));
    }
}
