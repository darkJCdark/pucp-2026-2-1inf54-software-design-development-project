package com.pucp.paqrap.modulos.incidencias.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.*;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.incidencias.entity.*;
import com.pucp.paqrap.modulos.incidencias.persistence.BreakdownEventEntity;
import com.pucp.paqrap.modulos.incidencias.repository.BreakdownEventRepository;
import com.pucp.paqrap.modulos.incidencias.service.ContextoSimulacion;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConfiguracionAveriaApiTest {
    private static final String URL = "/api/incidencias/averias/configuracion/MINOR";
    private static final Instant HORA = Instant.parse("2026-01-05T15:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired VehicleRepository vehiculos;
    @Autowired BreakdownEventRepository averias;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @MockBean ContextoSimulacion contexto;

    private long registrar(String vehiculo) throws Exception {
        vehiculos.save(new VehicleEntity(vehiculo, VehicleType.CAR, VehicleOperationalStatus.AVAILABLE));
        when(contexto.instanteSimulado(5L)).thenReturn(Optional.of(HORA));
        when(contexto.ejecutadoHasta(5L)).thenReturn(Optional.empty());
        when(contexto.posicionEstimada(5L, vehiculo, HORA)).thenReturn(Optional.empty());
        String contenido = mvc.perform(post("/api/incidencias/averias").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("vehiculoId", vehiculo, "tipo", "MINOR", "ejecucionId", 5))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(contenido).get("id").asLong();
    }

    @Test
    void predeterminadoSeCongelaEnElEventoYLosCambiosSoloAfectanFuturasAverias() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.duracionMinutos").value(120));
        long anterior = registrar("TA01");
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("duracionMinutos", 60))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duracionMinutos").value(60));
        long siguiente = registrar("TA02");
        entityManager.flush();
        entityManager.clear();
        assertEquals(120, jdbc.queryForObject("select minor_repair_minutes from breakdown_events where breakdown_id=?", Integer.class, anterior));
        assertEquals(60, jdbc.queryForObject("select minor_repair_minutes from breakdown_events where breakdown_id=?", Integer.class, siguiente));
        mvc.perform(get("/api/incidencias/averias/{id}", anterior))
                .andExpect(status().isOk()).andExpect(jsonPath("$.indisponibleHasta").value("2026-01-05T17:00:00Z"))
                .andExpect(jsonPath("$.duracionReparacionMinutos").value(120));
        mvc.perform(get("/api/incidencias/averias/{id}", siguiente))
                .andExpect(status().isOk()).andExpect(jsonPath("$.indisponibleHasta").value("2026-01-05T16:00:00Z"))
                .andExpect(jsonPath("$.regresaAlCentralEn").doesNotExist());
    }

    @Test
    void registroHistoricoNullableConserva120Minutos() throws Exception {
        BreakdownEventEntity historica = averias.save(new BreakdownEventEntity(null,
                new BreakdownEvent("TA01", BreakdownType.MINOR, HORA, new Location(27, 14))));
        entityManager.flush();
        long id = historica.getBreakdownId();
        entityManager.clear();
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("duracionMinutos", 30))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/incidencias/averias/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.duracionReparacionMinutos").value(120))
                .andExpect(jsonPath("$.indisponibleHasta").value("2026-01-05T17:00:00Z"));
        assertNull(jdbc.queryForObject("select minor_repair_minutes from breakdown_events where breakdown_id=?", Integer.class, id));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "null", "2147483648"})
    void rechazaValoresInvalidosSinCambiarConfiguracion(String valor) throws Exception {
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{%cduracionMinutos%c:%s}", 34, 34, valor))).andExpect(status().isBadRequest());
        mvc.perform(get(URL)).andExpect(jsonPath("$.duracionMinutos").value(120));
        assertEquals(0, averias.count());
    }
}