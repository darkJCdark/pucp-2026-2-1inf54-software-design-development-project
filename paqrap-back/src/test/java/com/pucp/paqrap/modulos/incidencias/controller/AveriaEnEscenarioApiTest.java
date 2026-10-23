package com.pucp.paqrap.modulos.incidencias.controller;

import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
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
import java.util.Optional;

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

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        ContextoSimulacion contextoDePrueba() {
            return new ContextoSimulacion() {
                @Override
                public Optional<Instant> instanteSimulado(long ejecucionId) {
                    return ejecucionId == EJECUCION ? Optional.of(AHORA_SIMULADO) : Optional.empty();
                }

                @Override
                public Optional<Instant> ejecutadoHasta(long ejecucionId) {
                    return ejecucionId == EJECUCION ? Optional.of(EJECUTADO_HASTA) : Optional.empty();
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

    @BeforeEach
    void cargarDatos() {
        vehiculoRepository.save(new VehicleEntity("TA01", VehicleType.CAR, VehicleOperationalStatus.IN_ROUTE));
        vehiculoRepository.save(new VehicleEntity("TM01", VehicleType.MOTORCYCLE, VehicleOperationalStatus.AVAILABLE));
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
    void sinEscenarioUsaLaPosicionConfirmada() throws Exception {
        registrar("{\"vehiculoId\": \"TM01\", \"tipo\": \"MINOR\", \"ocurridaEn\": \"2026-01-01T09:00:00Z\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ocurridaEn").value("2026-01-01T09:00:00Z"))
                .andExpect(jsonPath("$.x").value(27))
                .andExpect(jsonPath("$.y").value(14));
    }
}
