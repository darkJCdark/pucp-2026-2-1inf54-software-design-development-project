package com.pucp.paqrap.modulos.escenarios.controller;

import com.jayway.jsonpath.JsonPath;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioStatus;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioResultEntity;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioResultRepository;
import com.pucp.paqrap.modulos.escenarios.service.EjecutorPlanService;
import com.pucp.paqrap.modulos.escenarios.service.ResultadoEscenarioCsvExporter;
import com.pucp.paqrap.modulos.escenarios.service.ScenarioResultService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResultadoEscenarioApiTest {

    private static final Instant INICIO_SIMULADO = Instant.parse("2026-01-01T05:00:00Z");
    private static final Instant FIN_SIMULADO = Instant.parse("2026-01-06T05:00:00Z");
    private static final Instant INICIO_REAL = Instant.parse("2026-10-22T07:00:46.012374Z");
    private static final Instant FIN_REAL = Instant.parse("2026-10-22T07:45:46.926875Z");
    private static final MediaType CSV_UTF8 = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);

    @Autowired private MockMvc mvc;
    @Autowired private ScenarioResultService resultService;
    @Autowired private ScenarioResultRepository resultRepository;
    @Autowired private ScenarioExecutionRepository executionRepository;

    @AfterEach
    void limpiar() {
        resultRepository.deleteAll();
        executionRepository.deleteAll();
    }

    @Test
    void devuelveResultadoCompletedConLosValoresPersistidos() throws Exception {
        ScenarioExecutionEntity execution = crearFinalizada(ScenarioStatus.COMPLETED);
        ScenarioResultEntity resultado = persistirResultado(execution);

        assertEquals(0, new BigDecimal("12.50").compareTo(resultado.getTotalDistanceKm()));
        assertEquals(0, new BigDecimal("48.75").compareTo(resultado.getTotalCost()));

        mvc.perform(get("/api/escenarios/" + execution.getExecutionId() + "/resultado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value(execution.getExecutionId()))
                .andExpect(jsonPath("$.tipo").value("FIVE_DAY"))
                .andExpect(jsonPath("$.estado").value("COMPLETED"))
                .andExpect(jsonPath("$.inicioSimulado").value(INICIO_SIMULADO.toString()))
                .andExpect(jsonPath("$.finSimulado").value(FIN_SIMULADO.toString()))
                .andExpect(jsonPath("$.inicioReal").value(INICIO_REAL.toString()))
                .andExpect(jsonPath("$.finReal").value(FIN_REAL.toString()))
                .andExpect(jsonPath("$.colapsoEn").value(nullValue()))
                .andExpect(jsonPath("$.totalOrders").value(7))
                .andExpect(jsonPath("$.deliveredOrders").value(3))
                .andExpect(jsonPath("$.onTimeOrders").value(2))
                .andExpect(jsonPath("$.undeliveredOrders").value(4))
                .andExpect(jsonPath("$.totalDistanceKm").value(12.50))
                .andExpect(jsonPath("$.totalCost").value(48.75))
                .andExpect(jsonPath("$.computationTimeMs").value(321))
                .andExpect(jsonPath("$.breakdownCount").value(2))
                .andExpect(jsonPath("$.roadBlockCount").value(3))
                .andExpect(jsonPath("$.replanningCount").value(4));
    }

    @Test
    void devuelve404CuandoLaEjecucionNoExiste() throws Exception {
        mvc.perform(get("/api/escenarios/999999/resultado"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rechazaCon409LasEjecucionesSinResultado() throws Exception {
        long created = executionRepository.save(new ScenarioExecutionEntity(ScenarioType.FIVE_DAY, INICIO_SIMULADO))
                .getExecutionId();
        ScenarioExecutionEntity runningExecution = executionRepository.save(
                new ScenarioExecutionEntity(ScenarioType.FIVE_DAY, INICIO_SIMULADO));
        runningExecution.iniciar(INICIO_REAL);
        long running = executionRepository.save(runningExecution).getExecutionId();
        ScenarioExecutionEntity pausedExecution = executionRepository.save(
                new ScenarioExecutionEntity(ScenarioType.FIVE_DAY, INICIO_SIMULADO));
        pausedExecution.iniciar(INICIO_REAL);
        pausedExecution.pausar();
        long paused = executionRepository.save(pausedExecution).getExecutionId();
        ScenarioExecutionEntity failedExecution = executionRepository.save(
                new ScenarioExecutionEntity(ScenarioType.FIVE_DAY, INICIO_SIMULADO));
        failedExecution.terminar(ScenarioStatus.FAILED, FIN_REAL, FIN_SIMULADO);
        long failed = executionRepository.save(failedExecution).getExecutionId();

        for (long id : List.of(created, running, paused, failed)) {
            mvc.perform(get("/api/escenarios/" + id + "/resultado"))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void devuelveResultadosPersistidosParaTodosLosEstadosTerminales() throws Exception {
        for (ScenarioStatus estado : List.of(ScenarioStatus.COMPLETED, ScenarioStatus.COLLAPSED,
                ScenarioStatus.STOPPED, ScenarioStatus.FAILED)) {
            ScenarioExecutionEntity execution = crearFinalizada(estado);
            persistirResultado(execution);

            mvc.perform(get("/api/escenarios/" + execution.getExecutionId() + "/resultado"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value(estado.name()));
        }
    }

    @Test
    void exportaUnaFilaCsvDeterministaConEncabezadoYDosDecimales() throws Exception {
        ScenarioExecutionEntity execution = crearFinalizada(ScenarioStatus.COMPLETED);
        persistirResultado(execution);
        Locale localeOriginal = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            String csv = mvc.perform(get("/api/escenarios/" + execution.getExecutionId() + "/resultado/csv"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(CSV_UTF8))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"resultado-escenario-" + execution.getExecutionId() + ".csv\""))
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

            String fila = execution.getExecutionId() + ";FIVE_DAY;COMPLETED;" + INICIO_SIMULADO + ";" + FIN_SIMULADO
                    + ";" + INICIO_REAL + ";" + FIN_REAL + ";;7;3;2;4;12.50;48.75;321;2;3;4";
            assertEquals(ResultadoEscenarioCsvExporter.ENCABEZADO + "\r\n" + fila + "\r\n", csv);
        } finally {
            Locale.setDefault(localeOriginal);
        }
    }

    @Test
    void jsonYCsvRepresentanLosMismosValoresPersistidos() throws Exception {
        ScenarioExecutionEntity execution = crearFinalizada(ScenarioStatus.COMPLETED);
        persistirResultado(execution);

        String json = mvc.perform(get("/api/escenarios/" + execution.getExecutionId() + "/resultado"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String csv = mvc.perform(get("/api/escenarios/" + execution.getExecutionId() + "/resultado/csv"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String[] columnas = csv.split("\\r\\n")[1].split(";", -1);

        assertEquals(textoJson(json, "$.executionId"), columnas[0]);
        assertEquals(textoJson(json, "$.tipo"), columnas[1]);
        assertEquals(textoJson(json, "$.estado"), columnas[2]);
        assertEquals(textoJson(json, "$.inicioSimulado"), columnas[3]);
        assertEquals(textoJson(json, "$.finSimulado"), columnas[4]);
        assertEquals(textoJson(json, "$.inicioReal"), columnas[5]);
        assertEquals(textoJson(json, "$.finReal"), columnas[6]);
        assertEquals("", columnas[7]);
        assertEquals(textoJson(json, "$.totalOrders"), columnas[8]);
        assertEquals(textoJson(json, "$.deliveredOrders"), columnas[9]);
        assertEquals(textoJson(json, "$.onTimeOrders"), columnas[10]);
        assertEquals(textoJson(json, "$.undeliveredOrders"), columnas[11]);
        assertEquals(columnas[12], dosDecimales(JsonPath.read(json, "$.totalDistanceKm")));
        assertEquals(columnas[13], dosDecimales(JsonPath.read(json, "$.totalCost")));
        assertEquals(textoJson(json, "$.computationTimeMs"), columnas[14]);
        assertEquals(textoJson(json, "$.breakdownCount"), columnas[15]);
        assertEquals(textoJson(json, "$.roadBlockCount"), columnas[16]);
        assertEquals(textoJson(json, "$.replanningCount"), columnas[17]);
    }

    private ScenarioExecutionEntity crearFinalizada(ScenarioStatus estado) {
        ScenarioExecutionEntity execution = new ScenarioExecutionEntity(ScenarioType.FIVE_DAY, INICIO_SIMULADO);
        execution.iniciar(INICIO_REAL);
        execution.terminar(estado, FIN_REAL, FIN_SIMULADO);
        return executionRepository.save(execution);
    }

    private ScenarioResultEntity persistirResultado(ScenarioExecutionEntity execution) {
        return resultService.persistir(execution.getExecutionId(), 7,
                new EjecutorPlanService.Metricas(3, 2, 9, 12.5, 48.75),
                321, 4, 2, 3);
    }

    private static String textoJson(String json, String ruta) {
        return String.valueOf((Object) JsonPath.read(json, ruta));
    }

    private static String dosDecimales(Object valor) {
        return new BigDecimal(valor.toString()).setScale(2).toPlainString();
    }
}
