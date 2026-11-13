package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.modulos.escenarios.entity.ScenarioStatus;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioResultRepository;
import com.pucp.paqrap.modulos.incidencias.entity.AveriaRegistrada;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.planificacion.service.PlanificadorPort;
import com.pucp.paqrap.modulos.planificacion.service.PlanificadorCandidato;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caracteriza el bloqueo que provoca 504: hoy tick() conserva el monitor del motor mientras planifica.
 * Los tres casos con prefijo deseado_ son la especificacion RED de la correccion y fallan en esta version.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MotorEscenariosBloqueoPlanificacionTest {

    private static final Instant INICIO = Instant.parse("2026-01-01T05:00:00Z");
    private static final Duration ESPERA = Duration.ofSeconds(3);
    private static final Duration RESPUESTA_DESEADA = Duration.ofMillis(500);
    private static final Duration DEMORA_PROLONGADA = Duration.ofSeconds(9);
    private static final Duration LIMITE_CALCULO_BLOQUEADO = Duration.ofSeconds(12);

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        RelojManual relojManualBloqueo() {
            return new RelojManual();
        }

        @Bean
        @Primary
        PlanificadorBloqueable planificadorBloqueable() {
            return new PlanificadorBloqueable();
        }
    }

    static final class RelojManual extends Clock {
        private volatile Instant ahora = Instant.parse("2026-10-08T12:00:00Z");

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override public Instant instant() { return ahora; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    /** El plan inicial es inmediato; el siguiente se bloquea hasta que el test libera el latch. */
    static final class PlanificadorBloqueable implements PlanificadorCandidato {
        private volatile boolean bloquear;
        private volatile CountDownLatch entroEnPlanificacion = new CountDownLatch(1);
        private volatile CountDownLatch liberarPlanificacion = new CountDownLatch(1);
        private volatile int ciclos;
        private volatile int confirmados;
        private volatile int descartados;
        private volatile SolicitudCiclo ultimaSolicitud;

        @Override
        public ResultadoCiclo planificar(SolicitudCiclo solicitud) {
            ciclos++;
            if (bloquear) {
                entroEnPlanificacion.countDown();
                try {
                    if (!liberarPlanificacion.await(LIMITE_CALCULO_BLOQUEADO.toMillis(), TimeUnit.MILLISECONDS)) {
                        throw new AssertionError("La prueba no libero la planificacion bloqueada");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Se interrumpio la planificacion bloqueada", e);
                }
            }
            return ResultadoCiclo.SIN_COLAPSO;
        }

        private record CandidatoBloqueable(ResultadoCiclo resultado) implements Candidato {
        }

        @Override
        public Candidato preparar(SolicitudCiclo solicitud) {
            ultimaSolicitud = solicitud;
            return new CandidatoBloqueable(planificar(solicitud));
        }

        @Override
        public ResultadoCiclo confirmar(Candidato candidato) {
            confirmados++;
            return ((CandidatoBloqueable) candidato).resultado();
        }

        @Override
        public void descartar(Candidato candidato) {
            descartados++;
        }

        @Override
        public Optional<ReplanificacionService.MetricasPlanificacion> metricas(long ejecucionId) {
            return Optional.of(new ReplanificacionService.MetricasPlanificacion(0, Math.max(0, ciclos - 1)));
        }

        void bloquearSiguienteCiclo() {
            entroEnPlanificacion = new CountDownLatch(1);
            liberarPlanificacion = new CountDownLatch(1);
            bloquear = true;
        }

        boolean esperarPlanificacion() throws InterruptedException {
            return entroEnPlanificacion.await(ESPERA.toMillis(), TimeUnit.MILLISECONDS);
        }

        void liberar() {
            bloquear = false;
            liberarPlanificacion.countDown();
        }

        void reiniciar() {
            liberar();
            ciclos = 0;
            confirmados = 0;
            descartados = 0;
            ultimaSolicitud = null;
            entroEnPlanificacion = new CountDownLatch(1);
            liberarPlanificacion = new CountDownLatch(1);
        }
    }

    private record CicloBloqueado(long ejecucionId, Future<?> tick) { }

    @Autowired private MotorEscenarios motor;
    @Autowired private EscenarioService escenarioService;
    @Autowired private RelojManual reloj;
    @Autowired private PlanificadorBloqueable planificador;
    @Autowired private ScenarioExecutionRepository ejecucionRepository;
    @Autowired private ScenarioResultRepository resultadoRepository;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private ExecutorService hilos;

    @BeforeEach
    void preparar() {
        planificador.reiniciar();
        hilos = Executors.newFixedThreadPool(3);
    }

    @AfterEach
    void limpiar() throws InterruptedException {
        planificador.liberar();
        if (hilos != null) {
            hilos.shutdown();
            hilos.awaitTermination(ESPERA.toMillis(), TimeUnit.MILLISECONDS);
            hilos.shutdownNow();
        }
        ejecucionRepository.findByStatusIn(List.of(ScenarioStatus.RUNNING, ScenarioStatus.PAUSED))
                .forEach(e -> motor.detener(e.getExecutionId()));
        resultadoRepository.deleteAll();
        ejecucionRepository.deleteAll();
    }

    // La caracterización queda como regresión: ahora las tres operaciones deben responder durante el cálculo.

    @Test
    void caracterizacion_laConsultaDelInstanteEsperaMientrasTickPlanifica() throws Exception {
        CicloBloqueado ciclo = iniciarYBloquearCicloPeriodico();
        CountDownLatch consultaIniciada = new CountDownLatch(1);
        Future<Optional<Instant>> consulta = hilos.submit(() -> {
            consultaIniciada.countDown();
            return motor.instanteSimulado(ciclo.ejecucionId());
        });

        try {
            assertTrue(consultaIniciada.await(ESPERA.toMillis(), TimeUnit.MILLISECONDS));
            assertTrue(terminaDentroDe(consulta, RESPUESTA_DESEADA), "el monitoreo debe responder durante el cálculo");
        } finally {
            liberarYEsperar(ciclo);
        }

        assertTrue(consulta.get(ESPERA.toMillis(), TimeUnit.MILLISECONDS).isPresent());
    }

    @Test
    void caracterizacion_laPausaEsperaMientrasTickPlanifica() throws Exception {
        CicloBloqueado ciclo = iniciarYBloquearCicloPeriodico();
        CountDownLatch pausaIniciada = new CountDownLatch(1);
        Future<?> pausa = hilos.submit(() -> {
            pausaIniciada.countDown();
            escenarioService.pausar(ciclo.ejecucionId());
        });

        try {
            assertTrue(pausaIniciada.await(ESPERA.toMillis(), TimeUnit.MILLISECONDS));
            assertTrue(terminaDentroDe(pausa, RESPUESTA_DESEADA), "la pausa debe responder durante el cálculo");
        } finally {
            liberarYEsperar(ciclo);
        }

        pausa.get(ESPERA.toMillis(), TimeUnit.MILLISECONDS);
        assertEquals(ScenarioStatus.PAUSED, ejecucionRepository.findById(ciclo.ejecucionId()).orElseThrow().getStatus());
        assertEquals(0, planificador.confirmados);
        assertEquals(1, planificador.descartados);
        escenarioService.reanudar(ciclo.ejecucionId());
        motor.tick();
        assertEquals(ScenarioStatus.RUNNING, ejecucionRepository.findById(ciclo.ejecucionId()).orElseThrow().getStatus());
        assertEquals(1, planificador.confirmados);
    }

    @Test
    void caracterizacion_laDetencionEsperaMientrasTickPlanifica() throws Exception {
        CicloBloqueado ciclo = iniciarYBloquearCicloPeriodico();
        CountDownLatch detencionIniciada = new CountDownLatch(1);
        Future<?> detencion = hilos.submit(() -> {
            detencionIniciada.countDown();
            escenarioService.detener(ciclo.ejecucionId());
        });

        try {
            assertTrue(detencionIniciada.await(ESPERA.toMillis(), TimeUnit.MILLISECONDS));
            assertTrue(terminaDentroDe(detencion, RESPUESTA_DESEADA), "la detención debe responder durante el cálculo");
        } finally {
            liberarYEsperar(ciclo);
        }

        detencion.get(ESPERA.toMillis(), TimeUnit.MILLISECONDS);
        assertEquals(ScenarioStatus.STOPPED, ejecucionRepository.findById(ciclo.ejecucionId()).orElseThrow().getStatus());
        assertEquals(0, planificador.confirmados);
        assertEquals(1, planificador.descartados);
    }

    // Especificación de comportamiento: conserva una segunda formulación de las garantías de respuesta.

    @Test
    void deseado_monitoreoRespondeSinEsperarElCalculoDelPlan() throws Exception {
        CicloBloqueado ciclo = iniciarYBloquearCicloPeriodico();
        Future<Optional<Instant>> consulta = hilos.submit(() -> motor.instanteSimulado(ciclo.ejecucionId()));
        boolean respondio;
        try {
            respondio = terminaDentroDe(consulta, RESPUESTA_DESEADA);
        } finally {
            liberarYEsperar(ciclo);
        }
        assertTrue(respondio, "el monitoreo debe poder leer un snapshot mientras se calcula el candidato");
    }

    @Test
    void deseado_pausaRespondeSinEsperarElCalculoDelPlan() throws Exception {
        CicloBloqueado ciclo = iniciarYBloquearCicloPeriodico();
        Future<?> pausa = hilos.submit(() -> escenarioService.pausar(ciclo.ejecucionId()));
        boolean respondio;
        try {
            respondio = terminaDentroDe(pausa, RESPUESTA_DESEADA);
        } finally {
            liberarYEsperar(ciclo);
        }
        assertTrue(respondio, "la pausa debe invalidar el candidato sin esperar a SA");
        assertEquals(ScenarioStatus.PAUSED, ejecucionRepository.findById(ciclo.ejecucionId()).orElseThrow().getStatus());
        assertEquals(0, planificador.confirmados);
        assertEquals(1, planificador.descartados);
    }

    @Test
    void deseado_detencionRespondeSinEsperarElCalculoDelPlan() throws Exception {
        CicloBloqueado ciclo = iniciarYBloquearCicloPeriodico();
        Future<?> detencion = hilos.submit(() -> escenarioService.detener(ciclo.ejecucionId()));
        boolean respondio;
        try {
            respondio = terminaDentroDe(detencion, RESPUESTA_DESEADA);
        } finally {
            liberarYEsperar(ciclo);
        }
        assertTrue(respondio, "la detencion debe invalidar el candidato sin esperar a SA");
        assertEquals(ScenarioStatus.STOPPED, ejecucionRepository.findById(ciclo.ejecucionId()).orElseThrow().getStatus());
        assertEquals(0, planificador.confirmados);
        assertEquals(1, planificador.descartados);
    }

  @Test
    void unaAveriaNuevaDescartaElCandidatoYSeConservaParaElSiguienteCiclo() throws Exception {
        CicloBloqueado ciclo = iniciarYBloquearCicloPeriodico();
        motor.alRegistrarAveria(new AveriaRegistrada(ciclo.ejecucionId(),
                new BreakdownEvent("TA01", BreakdownType.MINOR, INICIO, new Location(0, 0))));
        liberarYEsperar(ciclo);

        assertEquals(0, planificador.confirmados);
        assertEquals(1, planificador.descartados);
        motor.tick();
        assertEquals(1, planificador.confirmados);
        assertEquals(1, planificador.ultimaSolicitud.averias().size());
    }
    @Test
    void apiMonitoreoYPausaRespondenDuranteCalculoProlongadoYDescartanElCandidato() throws Exception {
        long id = ejecucionRepository.save(new ScenarioExecutionEntity(ScenarioType.FIVE_DAY, INICIO))
                .getExecutionId();
        motor.iniciar(id);
        // Warm up MVC/Jackson/Hibernate before measuring concurrency; preserve the 500 ms threshold.
        long frio = System.nanoTime();
        mvc.perform(get("/api/monitoreo/{id}", id)).andExpect(status().isOk());
        long monitoreoFrioMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - frio);
        frio = System.nanoTime();
        mvc.perform(post("/api/escenarios/{id}/pausar", id)).andExpect(status().isOk());
        long pausaFriaMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - frio);
        escenarioService.reanudar(id);
        long planes = jdbc.queryForObject("select count(*) from plans", Long.class);
        long rutas = jdbc.queryForObject("select count(*) from routes", Long.class);
        long paradas = jdbc.queryForObject("select count(*) from route_stops", Long.class);
        planificador.bloquearSiguienteCiclo();
        reloj.avanzar(Duration.ofSeconds(30));
        CicloBloqueado ciclo = new CicloBloqueado(id, hilos.submit(motor::tick));
        assertTrue(planificador.esperarPlanificacion());
        long inicioBloqueo = System.nanoTime();
        Future<Long> monitoreo = hilos.submit(() -> {
            long inicio = System.nanoTime();
            mvc.perform(get("/api/monitoreo/{id}", id)).andExpect(status().isOk());
            return System.nanoTime() - inicio;
        });
        Future<Long> pausa = hilos.submit(() -> {
            long inicio = System.nanoTime();
            mvc.perform(post("/api/escenarios/{id}/pausar", id)).andExpect(status().isOk());
            return System.nanoTime() - inicio;
        });
        long monitoreoNs;
        long pausaNs;
        try {
            // Deadlock guard only; acceptance remains below 500 ms for each complete request.
            monitoreoNs = monitoreo.get(8, TimeUnit.SECONDS);
            pausaNs = pausa.get(8, TimeUnit.SECONDS);
            assertFalse(ciclo.tick().isDone(), "ambas solicitudes deben terminar antes de liberar el calculo");
            long restante = DEMORA_PROLONGADA.toNanos() - (System.nanoTime() - inicioBloqueo);
            if (restante > 0) {
                TimeUnit.NANOSECONDS.sleep(restante);
            }
            assertFalse(ciclo.tick().isDone(), "el calculo debe seguir bloqueado tras nueve segundos");
            assertEquals(0, planificador.confirmados);
            assertEquals(planes, jdbc.queryForObject("select count(*) from plans", Long.class));
        } finally {
            liberarYEsperar(ciclo);
        }
        System.out.printf("504_MEDICION frioMonitoreoMs=%d frioPausaMs=%d monitoreoMs=%.3f pausaMs=%.3f bloqueoMs=%.3f%n",
                monitoreoFrioMs, pausaFriaMs, monitoreoNs / 1_000_000.0, pausaNs / 1_000_000.0,
                (System.nanoTime() - inicioBloqueo) / 1_000_000.0);
        assertTrue(monitoreoNs < RESPUESTA_DESEADA.toNanos(), "monitoreo debe responder en menos de 500 ms");
        assertTrue(pausaNs < RESPUESTA_DESEADA.toNanos(), "pausa debe responder en menos de 500 ms");
        assertEquals(planes, jdbc.queryForObject("select count(*) from plans", Long.class));
        assertEquals(rutas, jdbc.queryForObject("select count(*) from routes", Long.class));
        assertEquals(paradas, jdbc.queryForObject("select count(*) from route_stops", Long.class));
        assertEquals(ScenarioStatus.PAUSED, ejecucionRepository.findById(id).orElseThrow().getStatus());
        assertEquals(0, planificador.confirmados, "un candidato invalidado no se publica");
        assertEquals(1, planificador.descartados, "el candidato pendiente debe descartarse");
    }
    private CicloBloqueado iniciarYBloquearCicloPeriodico() throws Exception {
        long ejecucionId = ejecucionRepository.save(new ScenarioExecutionEntity(ScenarioType.FIVE_DAY, INICIO))
                .getExecutionId();
        motor.iniciar(ejecucionId);
        planificador.bloquearSiguienteCiclo();
        reloj.avanzar(Duration.ofSeconds(30)); // FIVE_DAY x160: 80 min simulados, supera el intervalo de una hora.
        Future<?> tick = hilos.submit(motor::tick);
        try {
            assertTrue(planificador.esperarPlanificacion(), "el ciclo periodico debia entrar al planificador bloqueable");
            assertFalse(tick.isDone(), "tick sigue reteniendo el monitor hasta que la planificacion retorne");
            return new CicloBloqueado(ejecucionId, tick);
        } catch (AssertionError error) {
            planificador.liberar();
            tick.get(ESPERA.toMillis(), TimeUnit.MILLISECONDS);
            throw error;
        }
    }

    private void liberarYEsperar(CicloBloqueado ciclo) throws Exception {
        planificador.liberar();
        ciclo.tick().get(ESPERA.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static boolean terminaDentroDe(Future<?> tarea, Duration limite)
            throws InterruptedException, ExecutionException {
        try {
            tarea.get(limite.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException esperadoMientrasNoExistaLaCorreccion) {
            return false;
        }
    }
}