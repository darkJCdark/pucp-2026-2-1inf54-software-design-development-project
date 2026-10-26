package com.pucp.paqrap.modulos.pedidos.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Casos de prueba del feedback del profesor para el registro de un pedido: fecha, hora, cantidad, tiempo de
 * entrega y dirección de entrega, cada uno con casos buenos y malos, y la consulta posterior del pedido registrado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PedidoRegistroFeedbackApiTest {

    @Autowired
    private MockMvc mvc;

    private ResultActions registrar(String json) throws Exception {
        return mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String regular(String fecha) {
        return "{\"clienteId\": \"c123\", \"x\": 10, \"y\": 20, \"paquetes\": 5, \"tipoEntrega\": \"REGULAR\""
                + ", \"registradoEn\": \"" + fecha + "\"}";
    }

    // ---- Fecha y hora ----

    @Test
    void laHoraConZonaDeLimaSeConvierteAUtcYElPlazoSeCuentaDesdeEsaHora() throws Exception {
        // 12:00 en Lima (UTC-5) = 17:00Z; 36 h después: 12-ene 05:00Z.
        registrar(regular("2026-01-10T12:00:00-05:00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registradoEn").value("2026-01-10T17:00:00Z"))
                .andExpect(jsonPath("$.vence").value("2026-01-12T05:00:00Z"));
    }

    @Test
    void unPlazoPrioritarioQueCruzaLaMedianocheCambiaDeDia() throws Exception {
        registrar("{\"clienteId\": \"c123\", \"x\": 10, \"y\": 20, \"paquetes\": 5, \"tipoEntrega\": \"PRIORITY\","
                + " \"horasPrometidas\": 4, \"registradoEn\": \"2026-01-10T23:30:00Z\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vence").value("2026-01-11T03:30:00Z"));
    }

    @Test
    void unaFechaSinHoraODeOtroFormatoDa400() throws Exception {
        registrar(regular("2026-01-10")).andExpect(status().isBadRequest());
        registrar(regular("manana")).andExpect(status().isBadRequest());
    }

    // ---- Cantidad ----

    @Test
    void unaCantidadMuyGrandeSeAceptaPorqueHoyNoHayTope() throws Exception {
        // Comportamiento actual [V]: solo se exige que sea mayor a cero; un tope máximo es una decisión pendiente.
        registrar("{\"clienteId\": \"c123\", \"x\": 10, \"y\": 20, \"paquetes\": 1000, \"tipoEntrega\": \"REGULAR\","
                + " \"registradoEn\": \"2026-01-10T12:00:00Z\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paquetes").value(1000));
    }

    @Test
    void unaCantidadQueNoEsUnEnteroValidoDa400() throws Exception {
        for (String paquetes : new String[]{"2147483648", "\"abc\""}) {
            registrar("{\"clienteId\": \"c123\", \"x\": 10, \"y\": 20, \"paquetes\": " + paquetes
                    + ", \"tipoEntrega\": \"REGULAR\"}")
                    .andExpect(status().isBadRequest());
        }
    }

    // ---- Tiempo de entrega ----

    @Test
    void unPedidoRegularAceptaExplicitamenteSuPlazoDe36Horas() throws Exception {
        registrar("{\"clienteId\": \"c123\", \"x\": 10, \"y\": 20, \"paquetes\": 5, \"tipoEntrega\": \"REGULAR\","
                + " \"horasPrometidas\": 36, \"registradoEn\": \"2026-01-10T12:00:00Z\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.horasPrometidas").value(36));
    }

    @Test
    void unPlazoPrioritarioCeroONegativoDa400() throws Exception {
        for (String horas : new String[]{"0", "-4"}) {
            registrar("{\"clienteId\": \"c123\", \"x\": 10, \"y\": 20, \"paquetes\": 5, \"tipoEntrega\": \"PRIORITY\","
                    + " \"horasPrometidas\": " + horas + "}")
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void elTipoDeEntregaOmitidoODesconocidoDa400() throws Exception {
        registrar("{\"clienteId\": \"c123\", \"x\": 10, \"y\": 20, \"paquetes\": 5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0].campo").value("tipoEntrega"));
        registrar("{\"clienteId\": \"c123\", \"x\": 10, \"y\": 20, \"paquetes\": 5, \"tipoEntrega\": \"EXPRESS\"}")
                .andExpect(status().isBadRequest());
    }

    // ---- Dirección de entrega ----

    @Test
    void unaDireccionIncompletaODeOtroTipoDa400() throws Exception {
        registrar("{\"clienteId\": \"c123\", \"y\": 20, \"paquetes\": 5, \"tipoEntrega\": \"REGULAR\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0].campo").value("x"));
        registrar("{\"clienteId\": \"c123\", \"x\": 10, \"paquetes\": 5, \"tipoEntrega\": \"REGULAR\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0].campo").value("y"));
        registrar("{\"clienteId\": \"c123\", \"x\": \"norte\", \"y\": 20, \"paquetes\": 5, \"tipoEntrega\": \"REGULAR\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void elClienteDebeEmpezarConUnaLetra() throws Exception {
        registrar("{\"clienteId\": \"9abc\", \"x\": 10, \"y\": 20, \"paquetes\": 5, \"tipoEntrega\": \"REGULAR\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0].campo").value("clienteId"));
    }

    // ---- Consulta del pedido registrado ----

    @Test
    void elPedidoRegistradoSeConsultaConLosMismosDatos() throws Exception {
        String respuesta = registrar("{\"clienteId\": \"c777\", \"x\": 33, \"y\": 44, \"paquetes\": 7,"
                + " \"tipoEntrega\": \"PRIORITY\", \"horasPrometidas\": 12, \"registradoEn\": \"2026-01-10T12:00:00Z\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(respuesta, "$.id");

        mvc.perform(get("/api/pedidos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedido.clienteId").value("c777"))
                .andExpect(jsonPath("$.pedido.x").value(33))
                .andExpect(jsonPath("$.pedido.y").value(44))
                .andExpect(jsonPath("$.pedido.paquetes").value(7))
                .andExpect(jsonPath("$.pedido.tipoEntrega").value("PRIORITY"))
                .andExpect(jsonPath("$.pedido.horasPrometidas").value(12))
                .andExpect(jsonPath("$.pedido.registradoEn").value("2026-01-10T12:00:00Z"))
                .andExpect(jsonPath("$.pedido.vence").value("2026-01-11T00:00:00Z"))
                .andExpect(jsonPath("$.pedido.estado").value("REGISTERED"))
                .andExpect(jsonPath("$.historial", hasSize(1)))
                .andExpect(jsonPath("$.historial[0].estado").value("REGISTERED"));
    }
}
