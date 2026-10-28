package com.pucp.paqrap.modulos.incidencias.controller;

import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
import com.pucp.paqrap.modulos.incidencias.persistence.BreakdownEventEntity;
import com.pucp.paqrap.modulos.incidencias.repository.BreakdownEventRepository;
import com.pucp.paqrap.modulos.incidencias.service.ContextoSimulacion;
import com.pucp.paqrap.modulos.redvial.entity.Location;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Avería registrada durante el escenario 77, simulado: el reloj va en 12:00 y el plan ya se ejecutó hasta las 11:50;
 * en ese momento el mapa dibuja a TA01 en (33,20), lejos de su última parada confirmada (27,14).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AveriaEnEscenarioApiTest {

    private static final long EJECUCION = 77L;
    private static final Instant AHORA_SIMULADO = Instant.parse("2026-01-01T12:00:00Z");
    private static final Instant EJECUTADO_HASTA = Instant.parse("2026-01-01T11:50:00Z");
    /** Ejecuciones en curso; cada prueba parte solo con la 77. */
    private static volatile List<Long> activas = List.of(EJECUCION);

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        ContextoSimulacion contextoDePrueba() {
            return new ContextoSimulacion() {
                @Override
                public List<Long> ejecucionesActivas() {
                    return activas;
                }

                @Override
                public Optional<Instant> instanteSimulado(long ejecucionId) {
                    return activas.contains(ejecucionId) ? Optional.of(AHORA_SIMULADO) : Optional.empty();
                }

                @Override
                public Optional<Instant> ejecutadoHasta(long ejecucionId) {
                    return activas.contains(ejecucionId) ? Optional.of(EJECUTADO_HASTA) : Optional.empty();
                }

                @Override
                public Optional<Location> posicionEstimada(long ejecucionId, String vehiculoId, Instant instante) {
                    return ejecucionId == EJECUCION && vehiculoId.equals("TA01")
                            ? Optional.of(new Location(33, 20)) : Optional.empty();
                }
            };
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private VehicleRepository vehiculoRepository;
    @Autowired
    private BreakdownEventRepository averiaRepository;

    @BeforeEach
    void cargarDatos() {
        activas = List.of(EJECUCION);
        vehiculoRepository.save(new VehicleEntity("TA01", VehicleType.CAR, VehicleOperationalStatus.IN_ROUTE));
        vehiculoRepository.save(new VehicleEntity("TM01", VehicleType.MOTORCYCLE, VehicleOperationalStatus.AVAILABLE));
        vehiculoRepository.save(new VehicleEntity("TB01", VehicleType.BICYCLE, VehicleOperationalStatus.UNAVAILABLE));
    }

    private ResultActions registrar(String json) throws Exception {
        return mvc.perform(post("/api/incidencias/averias").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void sinHoraNiUbicacionUsaElRelojSimuladoYDondeLoDibujaElMapa() throws Exception {
        registrar("{\"vehiculoId\": \"TA01\", \"tipo\": \"MINOR\", \"ejecucionId\": 77}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ocurridaEn").value("2026-01-01T12:00:00Z"))
                .andExpect(jsonPath("$.x").value(33))
                .andExpect(jsonPath("$.y").value(20));
    }

    @Test
    void unaHoraYaEjecutadaSeRegistraDondeVaLaSimulacion() throws Exception {
        registrar("{\"vehiculoId\": \"TA01\", \"tipo\": \"MINOR\", \"ejecucionId\": 77,"
                + " \"ocurridaEn\": \"2026-01-01T09:00:00Z\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ocurridaEn").value("2026-01-01T11:50:00Z"));
    }

    @Test
    void lasCoordenadasEnviadasTienenPrioridad() throws Exception {
        registrar("{\"vehiculoId\": \"TA01\", \"tipo\": \"MINOR\", \"ejecucionId\": 77, \"x\": 10, \"y\": 11}")
                .andExpect(jsonPath("$.x").value(10))
                .andExpect(jsonPath("$.y").value(11));
    }

    @Test
    void rechazaAveriasDeUnEscenarioQueNoEstaEnCurso() throws Exception {
        registrar("{\"vehiculoId\": \"TA01\", \"tipo\": \"MINOR\", \"ejecucionId\": 78}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("no está en curso")));
    }

    @Test
    void unaAveriaDeOtroEscenarioDelMismoPeriodoNoLoInhabilita() throws Exception {
        averiaRepository.save(new BreakdownEventEntity(70L, new BreakdownEvent("TA01", BreakdownType.MAJOR,
                Instant.parse("2026-01-01T11:00:00Z"), new Location(30, 14))));

        registrar("{\"vehiculoId\": \"TA01\", \"tipo\": \"MINOR\", \"ejecucionId\": 77}")
                .andExpect(status().isCreated());
    }

    @Test
    void sinEjecucionUsaElUnicoEscenarioEnCursoYSiNoVaEnRutaSuPosicionConfirmada() throws Exception {
        registrar("{\"vehiculoId\": \"TM01\", \"tipo\": \"MINOR\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ejecucionId").value(77))
                .andExpect(jsonPath("$.ocurridaEn").value("2026-01-01T12:00:00Z"))
                .andExpect(jsonPath("$.x").value(27))
                .andExpect(jsonPath("$.y").value(14));
    }

    @Test
    void sinEjecucionRechazaSiNoHayEscenarioEnCurso() throws Exception {
        activas = List.of();

        registrar("{\"vehiculoId\": \"TM01\", \"tipo\": \"MINOR\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("No hay un escenario en curso")));
    }

    @Test
    void sinEjecucionRechazaSiHayVariosEscenariosEnCurso() throws Exception {
        activas = List.of(EJECUCION, 78L);

        registrar("{\"vehiculoId\": \"TM01\", \"tipo\": \"MINOR\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("indique 'ejecucionId'")));
    }

    @Test
    void rechazaLaAveriaDeUnVehiculoFueraDeServicio() throws Exception {
        registrar("{\"vehiculoId\": \"TB01\", \"tipo\": \"MINOR\", \"ejecucionId\": 77}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("fuera de servicio")));
    }
}
