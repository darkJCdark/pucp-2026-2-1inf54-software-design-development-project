package com.pucp.paqrap.modulos.pedidos.controller;

import com.jayway.jsonpath.JsonPath;
import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * J1 y J4 (semana 12): {@code GET /api/pedidos/periodos-cargados} y la validación de cobertura al crear un escenario.
 * Usa meses de 2027 para no cruzarse con los datos de otras pruebas; cada test se revierte (@Transactional).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PedidoCoberturaArchivoApiTest {

    /** Dos pedidos por archivo, en el formato del curso (día, hora y minuto en hora de Lima). */
    private static final String VENTAS = """
            01d01h30m:56,30,c4910,02,36
            27d08h52m:61,21,c8771,09,04
            """;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private OrderRepository pedidoRepository;

    private ResultActions cargar(String nombre) throws Exception {
        return mvc.perform(multipart("/api/pedidos/carga").file(
                new MockMultipartFile("archivo", nombre, "text/plain", VENTAS.getBytes(StandardCharsets.UTF_8))));
    }

    private String periodosCargados() throws Exception {
        return mvc.perform(get("/api/pedidos/periodos-cargados"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions crear(String json) throws Exception {
        return mvc.perform(post("/api/escenarios").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void sinArchivosDelMesNoLoListaComoCargado() throws Exception {
        String cuerpo = periodosCargados();

        assertTrue(JsonPath.<List<?>>read(cuerpo, "$[?(@.periodo == '2027-05')]").isEmpty());
    }

    @Test
    void listaLosMesesCargadosConSuCantidadSinContarPedidosManuales() throws Exception {
        cargar("ventas.202702.txt").andExpect(status().isCreated());
        cargar("ventas.202703.txt").andExpect(status().isCreated());
        pedidoRepository.save(new OrderEntity("M-PRUEBA-COBERTURA", "c1", new Location(30, 14), 2,
                Instant.parse("2027-04-10T15:00:00Z"), DeliveryType.REGULAR, 36));

        String cuerpo = periodosCargados();

        assertEquals(List.of(2), JsonPath.<List<Integer>>read(cuerpo, "$[?(@.periodo == '2027-02')].pedidos"));
        assertEquals(List.of(2), JsonPath.<List<Integer>>read(cuerpo, "$[?(@.periodo == '2027-03')].pedidos"));
        assertTrue(JsonPath.<List<?>>read(cuerpo, "$[?(@.periodo == '2027-04')]").isEmpty());
    }

    @Test
    void cincoDiasEnUnMesSinPedidosResponde409ConElArchivoQueFalta() throws Exception {
        crear("{\"tipo\": \"FIVE_DAY\", \"inicioSimulado\": \"2027-05-10T05:00:00Z\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("2027-05")))
                .andExpect(jsonPath("$.mensaje").value(containsString("ventas.202705.txt")));
    }

    @Test
    void cincoDiasQueCruzanDeMesExigenTambienElMesSiguiente() throws Exception {
        cargar("ventas.202702.txt").andExpect(status().isCreated());

        // 28-feb 00:00 Lima + 5 días termina el 5-mar: falta marzo.
        crear("{\"tipo\": \"FIVE_DAY\", \"inicioSimulado\": \"2027-02-28T05:00:00Z\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("2027-03")))
                .andExpect(jsonPath("$.mensaje").value(not(containsString("2027-02"))));
    }

    @Test
    void cincoDiasConAmbosMesesCargadosSeCrea() throws Exception {
        cargar("ventas.202702.txt").andExpect(status().isCreated());
        cargar("ventas.202703.txt").andExpect(status().isCreated());

        crear("{\"tipo\": \"FIVE_DAY\", \"inicioSimulado\": \"2027-02-28T05:00:00Z\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CREATED"));
    }

    @Test
    void colapsoSinPedidosEnElMesDeInicioResponde409() throws Exception {
        crear("{\"tipo\": \"COLLAPSE\", \"inicioSimulado\": \"2027-06-01T05:00:00Z\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("ventas.202706.txt")));
    }

    @Test
    void diaADiaNoSeValidaPorqueParteDeLaHoraActual() throws Exception {
        crear("{\"tipo\": \"DAY_TO_DAY\"}")
                .andExpect(status().isCreated());
    }
}
