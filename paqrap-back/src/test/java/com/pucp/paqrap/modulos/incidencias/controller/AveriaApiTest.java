package com.pucp.paqrap.modulos.incidencias.controller;

import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Horas en UTC; 15:00Z = 10:00 en Lima, dentro del turno de 07:00 a 15:00. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AveriaApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private VehicleRepository vehiculoRepository;

    @BeforeEach
    void cargarDatos() {
        vehiculoRepository.save(new VehicleEntity("TA01", VehicleType.CAR, VehicleOperationalStatus.AVAILABLE));
        vehiculoRepository.save(new VehicleEntity("TM01", VehicleType.MOTORCYCLE, VehicleOperationalStatus.AVAILABLE));
    }

    private ResultActions registrar(String json) throws Exception {
        return mvc.perform(post("/api/incidencias/averias").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void registraAveriaMenorYCalculaSuIndisponibilidad() throws Exception {
        registrar("""
                {"vehiculoId": "TA01", "tipo": "MINOR", "ocurridaEn": "2026-01-05T15:00:00Z", "x": 30, "y": 20}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.x").value(30))
                .andExpect(jsonPath("$.indisponibleHasta").value("2026-01-05T17:00:00Z"))
                .andExpect(jsonPath("$.regresaAlCentralEn").doesNotExist());
    }

    @Test
    void averiaMayorEsperaDosDiasHastaElSiguienteTurnoDeLaTarde() throws Exception {
        // +2 días = 07-ene 10:00 Lima -> siguiente inicio de turno tarde: 07-ene 15:00 Lima = 20:00Z
        registrar("""
                {"vehiculoId": "TA01", "tipo": "MAJOR", "ocurridaEn": "2026-01-05T15:00:00Z"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.indisponibleHasta").value("2026-01-07T20:00:00Z"))
                .andExpect(jsonPath("$.regresaAlCentralEn").value("2026-01-05T19:00:00Z"));
    }

    @Test
    void sinCoordenadasUsaLaPosicionActualDelVehiculo() throws Exception {
        registrar("""
                {"vehiculoId": "TM01", "tipo": "MINOR", "ocurridaEn": "2026-01-05T15:00:00Z"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.x").value(27))
                .andExpect(jsonPath("$.y").value(14));
    }

    @Test
    void rechazaUnaAveriaMientrasSigueInhabilitadoPorOtra() throws Exception {
        registrar("""
                {"vehiculoId": "TA01", "tipo": "MINOR", "ocurridaEn": "2026-01-05T15:00:00Z"}
                """);

        registrar("""
                {"vehiculoId": "TA01", "tipo": "MINOR", "ocurridaEn": "2026-01-05T16:00:00Z"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("sigue inhabilitado")));

        // A las 17:00Z la avería menor ya terminó.
        registrar("""
                {"vehiculoId": "TA01", "tipo": "MINOR", "ocurridaEn": "2026-01-05T17:00:00Z"}
                """)
                .andExpect(status().isCreated());
    }

    @Test
    void validaLosDatosDeLaAveria() throws Exception {
        registrar("""
                {"vehiculoId": "", "tipo": "LEVE", "x": 99}
                """)
                .andExpect(status().isBadRequest());

        registrar("""
                {"vehiculoId": "TA01", "tipo": "MINOR", "x": 10}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("ambas coordenadas")));

        registrar("""
                {"vehiculoId": "TA99", "tipo": "MINOR"}
                """)
                .andExpect(status().isNotFound());
    }

    @Test
    void filtraElHistorialPorVehiculoTipoYRango() throws Exception {
        registrar("""
                {"vehiculoId": "TA01", "tipo": "MINOR", "ocurridaEn": "2026-01-05T15:00:00Z"}
                """);
        registrar("""
                {"vehiculoId": "TM01", "tipo": "INTERMEDIATE", "ocurridaEn": "2026-01-06T15:00:00Z", "ejecucionId": 7}
                """);

        mvc.perform(get("/api/incidencias/averias"))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/incidencias/averias").param("vehiculoId", "TM01"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ejecucionId").value(7));
        mvc.perform(get("/api/incidencias/averias").param("tipo", "MINOR"))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/incidencias/averias")
                        .param("desde", "2026-01-06T00:00:00Z").param("hasta", "2026-01-07T00:00:00Z"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].vehiculoId").value("TM01"));
    }

    @Test
    void respondeNotFoundParaAveriaInexistente() throws Exception {
        mvc.perform(get("/api/incidencias/averias/999999"))
                .andExpect(status().isNotFound());
    }
}
