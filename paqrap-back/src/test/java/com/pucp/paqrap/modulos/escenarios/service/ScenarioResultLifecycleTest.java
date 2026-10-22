package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.modulos.escenarios.entity.ScenarioStatus;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioResultEntity;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioResultRepository;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
import com.pucp.paqrap.modulos.incidencias.persistence.BreakdownEventEntity;
import com.pucp.paqrap.modulos.incidencias.repository.BreakdownEventRepository;
import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.entity.PedidoRegistrado;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ScenarioResultLifecycleTest {

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
        PlanificadorControlado planificadorControlado() {
            return new PlanificadorControlado();
        }
    }

    static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-10-08T12:00:00Z");

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override public Instant instant() { return ahora; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    static final class PlanificadorControlado implements PlanificadorPort {
        private final Map<Long, Integer> ciclos = new ConcurrentHashMap<>();
        private volatile boolean fallarCiclosPosteriores;

        @Override
        public ResultadoCiclo planificar(SolicitudCiclo solicitud) {
            int ciclosAnteriores = ciclos.getOrDefault(solicitud.ejecucionId(), 0);
            if (fallarCiclosPosteriores && ciclosAnteriores > 0) {
                throw new IllegalStateException("Fallo controlado del planificador");
            }
            ciclos.put(solicitud.ejecucionId(), ciclosAnteriores + 1);
            return ResultadoCiclo.SIN_COLAPSO;
        }

        @Override
        public Optional<ReplanificacionService.MetricasPlanificacion> metricas(long executionId) {
            int ciclosEjecutados = ciclos.getOrDefault(executionId, 0);
            return Optional.of(new ReplanificacionService.MetricasPlanificacion(0,
                    Math.max(0, ciclosEjecutados - 1)));
        }

        void reiniciar() {
            ciclos.clear();
            fallarCiclosPosteriores = false;
        }
    }

    @Autowired private MotorEscenarios motor;
    @Autowired private RelojManual reloj;
    @Autowired private PlanificadorControlado planificador;
    @Autowired private ScenarioExecutionRepository executionRepository;
    @Autowired private ScenarioResultRepository resultRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private RoadBlockRepository roadBlockRepository;
    @Autowired private BreakdownEventRepository breakdownRepository;

    @BeforeEach
    void reiniciar() {
        planificador.reiniciar();
    }

    @AfterEach
    void limpiar() {
        executionRepository.findByStatusIn(java.util.List.of(ScenarioStatus.RUNNING, ScenarioStatus.PAUSED))
                .forEach(e -> motor.detener(e.getExecutionId()));
        resultRepository.deleteAll();
        breakdownRepository.deleteAll();
        roadBlockRepository.deleteAll();
        orderRepository.deleteAll();
        executionRepository.deleteAll();
    }

    private long crear(ScenarioType tipo, Instant inicio) {
        return executionRepository.save(new ScenarioExecutionEntity(tipo, inicio)).getExecutionId();
    }

    private void guardarPedido(String id, Instant registradoEn) {
        orderRepository.save(new OrderEntity(id, "cliente-" + id, new Location(20, 20), 1,
                registradoEn, DeliveryType.REGULAR, 36));
    }

    private ScenarioResultEntity resultado(long id) {
        return resultRepository.findById(id).orElseThrow();
    }

    @Test
    void persisteResultadoAlCompletar() {
        long id = crear(ScenarioType.FIVE_DAY, INICIO);
        motor.iniciar(id);

        reloj.avanzar(Duration.ofMinutes(46));
        motor.tick();

        assertEquals(ScenarioStatus.COMPLETED, executionRepository.findById(id).orElseThrow().getStatus());
        assertTrue(resultRepository.existsById(id));
        assertEquals(0, resultado(id).getTotalOrders());
    }

    @Test
    void detenerNoAvanzaElRelojProcesadoPeroPersisteResultado() {
        guardarPedido("P-INICIAL", INICIO);
        long id = crear(ScenarioType.FIVE_DAY, INICIO);
        motor.iniciar(id);

        reloj.avanzar(Duration.ofMinutes(10));
        motor.detener(id);

        ScenarioResultEntity result = resultado(id);
        assertEquals(ScenarioStatus.STOPPED, executionRepository.findById(id).orElseThrow().getStatus());
        assertEquals(1, result.getTotalOrders());
        assertEquals(0, result.getDeliveredOrders());
        assertEquals(1, result.getUndeliveredOrders());
    }

    @Test
    void persisteResultadoAlColapsar() {
        orderRepository.save(new OrderEntity("P-COLAPSO", "cliente", new Location(30, 14), 1,
                INICIO, DeliveryType.PRIORITY, 4));
        long id = crear(ScenarioType.COLLAPSE, INICIO);
        motor.iniciar(id);

        reloj.avanzar(Duration.ofMinutes(1));
        motor.tick();

        assertEquals(ScenarioStatus.COLLAPSED, executionRepository.findById(id).orElseThrow().getStatus());
        assertEquals(1, resultado(id).getTotalOrders());
    }

    @Test
    void persisteResultadoAlFallarConEstadoActivoEnMemoria() {
        long id = crear(ScenarioType.FIVE_DAY, INICIO);
        motor.iniciar(id);
        planificador.fallarCiclosPosteriores = true;

        reloj.avanzar(Duration.ofSeconds(23));
        motor.tick();

        assertEquals(ScenarioStatus.FAILED, executionRepository.findById(id).orElseThrow().getStatus());
        assertTrue(resultRepository.existsById(id));
    }

    @Test
    void unaEjecucionHuerfanaNoCreaResultado() {
        long id = crear(ScenarioType.FIVE_DAY, INICIO);
        ScenarioExecutionEntity execution = executionRepository.findById(id).orElseThrow();
        execution.iniciar(reloj.instant());
        executionRepository.save(execution);

        motor.cerrarEjecucionesHuerfanas();

        assertEquals(ScenarioStatus.FAILED, executionRepository.findById(id).orElseThrow().getStatus());
        assertFalse(resultRepository.existsById(id));
    }

    @Test
    void acumulaPedidosNuevosYTardiosYDeduplicaEventosProcesados() {
        guardarPedido("P-INICIAL", INICIO);
        guardarPedido("P-NUEVO", INICIO.plusSeconds(300));
        long id = crear(ScenarioType.FIVE_DAY, INICIO);
        roadBlockRepository.save(RoadBlockEntity.desde(new RoadBlock(INICIO.plusSeconds(300),
                INICIO.plusSeconds(3600), java.util.List.of(new Location(10, 10), new Location(10, 11)))));
        breakdownRepository.save(new BreakdownEventEntity(id,
                new BreakdownEvent("TA01", BreakdownType.MINOR, INICIO.plusSeconds(300), new Location(10, 10))));
        motor.iniciar(id);

        reloj.avanzar(Duration.ofSeconds(5));
        motor.tick();
        motor.tick();

        guardarPedido("P-TARDIO", INICIO.plusSeconds(60));
        motor.alRegistrarPedido(new PedidoRegistrado("P-TARDIO", INICIO.plusSeconds(60)));
        motor.detener(id);

        ScenarioResultEntity result = resultado(id);
        assertEquals(3, result.getTotalOrders());
        assertEquals(1, result.getRoadBlockCount());
        assertEquals(1, result.getBreakdownCount());
    }

    @Test
    void losAcumuladoresDePedidosNoSeMezclanEntreEjecuciones() {
        guardarPedido("P-PRIMERA", INICIO);
        long primera = crear(ScenarioType.FIVE_DAY, INICIO);
        motor.iniciar(primera);
        motor.detener(primera);

        Instant segundoInicio = INICIO.plus(Duration.ofDays(1));
        guardarPedido("P-SEGUNDA", segundoInicio);
        long segunda = crear(ScenarioType.FIVE_DAY, segundoInicio);
        motor.iniciar(segunda);
        motor.detener(segunda);

        assertEquals(1, resultado(primera).getTotalOrders());
        assertEquals(1, resultado(segunda).getTotalOrders());
    }
}
