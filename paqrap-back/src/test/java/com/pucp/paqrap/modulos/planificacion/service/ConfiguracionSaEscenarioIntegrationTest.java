package com.pucp.paqrap.modulos.planificacion.service;

import com.jayway.jsonpath.JsonPath;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
import com.pucp.paqrap.modulos.almacenes.repository.InventoryMovementRepository;
import com.pucp.paqrap.modulos.almacenes.repository.WarehouseRepository;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioStatus;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioResultRepository;
import com.pucp.paqrap.modulos.escenarios.service.MotorEscenarios;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.persistence.VehicleTypeParametersEntity;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.flota.repository.VehicleTypeParametersRepository;
import com.pucp.paqrap.modulos.incidencias.repository.BreakdownEventRepository;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.pedidos.repository.OrderStatusHistoryRepository;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.AnnealingConfig;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.persistence.PlanEntity;
import com.pucp.paqrap.modulos.planificacion.repository.PlanRepository;
import com.pucp.paqrap.modulos.planificacion.repository.RouteRepository;
import com.pucp.paqrap.modulos.planificacion.repository.RouteStopRepository;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.repository.RoadBlockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica el recorrido HTTP -> entidad persistida -> adaptador -> SA inicial y de replanificaciÃ³n. */
@SpringBootTest(properties = "paqrap.planificacion.presupuesto-ms=50")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfiguracionSaEscenarioIntegrationTest {

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
        PlanificadorCapturador planificadorCapturador(FuenteDatosOperativos fuente,
                                                      @Value("${paqrap.planificacion.max-tramo-km:0}") double maxTramoKm) {
            return new PlanificadorCapturador(fuente, maxTramoKm);
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

    /** Captura la llamada que ya entra al SA y luego ejecuta el SA real. */
    static final class PlanificadorCapturador extends PlanificadorSaService {
        private final List<InvocacionSa> invocaciones = new CopyOnWriteArrayList<>();

        PlanificadorCapturador(FuenteDatosOperativos fuente, double maxTramoKm) {
            super(fuente, maxTramoKm);
        }

        @Override
        EjecucionSa ejecutarSa(OperationalSnapshot snapshot, List<com.pucp.paqrap.modulos.pedidos.entity.Order> pedidos,
                               List<RoadBlock> bloqueos, AnnealingConfig configuracion, long semilla,
                               long presupuestoMs) {
            invocaciones.add(new InvocacionSa(semilla, configuracion));
            return super.ejecutarSa(snapshot, pedidos, bloqueos, configuracion, semilla, presupuestoMs);
        }

        void reiniciar() {
            invocaciones.clear();
        }

        List<InvocacionSa> invocaciones() {
            return List.copyOf(invocaciones);
        }
    }

    private record InvocacionSa(long semilla, AnnealingConfig configuracion) {
    }

    @Autowired private MockMvc mvc;
    @Autowired private RelojManual reloj;
    @Autowired private MotorEscenarios motor;
    @Autowired private PlanificadorCapturador planificador;
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
    void cargarDatosOperativos() {
        planificador.reiniciar();
        tipoRepository.save(new VehicleTypeParametersEntity(VehicleType.CAR, 24,
                new BigDecimal("40.00"), new BigDecimal("8.00")));
        tipoRepository.save(new VehicleTypeParametersEntity(VehicleType.MOTORCYCLE, 8,
                new BigDecimal("25.00"), new BigDecimal("6.00")));
        tipoRepository.save(new VehicleTypeParametersEntity(VehicleType.BICYCLE, 4,
                new BigDecimal("12.00"), new BigDecimal("3.00")));
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

    @Test
    void configuracionPersonalizadaDelEscenarioLlegaAlInicialYAlPrimerReplan() throws Exception {
        String respuesta = mvc.perform(post("/api/escenarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"FIVE_DAY\",\"inicioSimulado\":\"" + INICIO + "\","
                                + "\"configuracionSa\":{\"semilla\":42,\"temperaturaInicial\":200.0,"
                                + "\"temperaturaMinima\":2.0,\"factorEnfriamiento\":0.90,"
                                + "\"iteracionesPorTemperatura\":7}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(respuesta, "$.id")).longValue();

        ScenarioExecutionEntity entidad = ejecucionRepository.findById(id).orElseThrow();
        assertEquals(42L, entidad.getSaSeed());
        assertEquals(200.0, entidad.getSaInitialTemperature());
        assertEquals(2.0, entidad.getSaMinimumTemperature());
        assertEquals(0.90, entidad.getSaCoolingFactor());
        assertEquals(7, entidad.getSaIterationsPerTemperature());

        mvc.perform(post("/api/escenarios/" + id + "/iniciar")).andExpect(status().isOk());
        assertEquals(1, planificador.invocaciones().size());
        assertInvocacion(planificador.invocaciones().getFirst(), 42L);

        reloj.avanzar(Duration.ofSeconds(30)); // 80 min simulados: activa el ciclo periÃ³dico de una hora.
        motor.tick();

        assertEquals(2, planificador.invocaciones().size());
        assertInvocacion(planificador.invocaciones().get(1), 43L);
    }

    private static void assertInvocacion(InvocacionSa invocacion, long semillaEsperada) {
        assertEquals(semillaEsperada, invocacion.semilla());
        assertEquals(200.0, invocacion.configuracion().initialTemperature());
        assertEquals(2.0, invocacion.configuracion().minimumTemperature());
        assertEquals(0.90, invocacion.configuracion().coolingFactor());
        assertEquals(7, invocacion.configuracion().iterationsPerTemperature());
    }
}