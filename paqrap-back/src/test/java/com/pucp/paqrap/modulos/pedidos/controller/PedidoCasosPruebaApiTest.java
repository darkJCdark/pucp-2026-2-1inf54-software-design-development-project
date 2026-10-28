package com.pucp.paqrap.modulos.pedidos.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registro manual de pedidos: casos buenos, de borde y malos del catálogo (CP-61 a CP-69). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PedidoCasosPruebaApiTest {

    @Autowired
    private MockMvc mvc;

    private ResultActions registrar(String json) throws Exception {
        return mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String pedido(int x, int y, String paquetes, String tipo, String horas, String fecha) {
        return "{\"clienteId\": \"c123\", \"x\": " + x + ", \"y\": " + y
                + (paquetes != null ? ", \"paquetes\": " + paquetes : "")
                + ", \"tipoEntrega\": \"" + tipo + "\""
                + (horas != null ? ", \"horasPrometidas\": " + horas : "")
                + (fecha != null ? ", \"registradoEn\": \"" + fecha + "\"" : "") + "}";
    }

    @Test
    void cp61_unPedidoRegularQuedaRegistradoConSuPlazoDe36Horas() throws Exception {
        registrar(pedido(10, 20, "5", "REGULAR", null, "2026-01-10T12:00:00Z"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vence").value("2026-01-12T00:00:00Z"))
                .andExpect(jsonPath("$.estado").value("REGISTERED"));
    }

    @Test
    void cp62_losCuatroPlazosPrioritariosCalculanSuVencimiento() throws Exception {
        String[][] casos = {{"4", "2026-01-10T16:00:00Z"}, {"8", "2026-01-10T20:00:00Z"},
                {"12", "2026-01-11T00:00:00Z"}, {"18", "2026-01-11T06:00:00Z"}};
        for (String[] caso : casos) {
            registrar(pedido(10, 20, "5", "PRIORITY", caso[0], "2026-01-10T12:00:00Z"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.vence").value(caso[1]));
        }
    }

    @Test
    void cp63_laCantidadMinimaYMuyGrandeSeAceptan() throws Exception {
        for (String paquetes : new String[]{"1", "24", "25"}) {
            registrar(pedido(10, 20, paquetes, "REGULAR", null, "2026-01-10T12:00:00Z"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.paquetes").value(Integer.parseInt(paquetes)));
        }
    }

    @Test
    void cp64_lasEsquinasDelMapaSeAceptan() throws Exception {
        registrar(pedido(0, 0, "5", "REGULAR", null, "2026-01-10T12:00:00Z")).andExpect(status().isCreated());
        registrar(pedido(70, 50, "5", "REGULAR", null, "2026-01-10T12:00:00Z")).andExpect(status().isCreated());
    }

    @Test
    void cp65_sinFechaDeRegistroUsaElMomentoDelRegistro() throws Exception {
        registrar(pedido(10, 20, "5", "REGULAR", null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registradoEn").exists());
    }

    @Test
    void cp66_laCantidadCeroNegativaOOmitidaDa400ConElCampoSenalado() throws Exception {
        for (String paquetes : new String[]{"0", "-3", null}) {
            registrar(pedido(10, 20, paquetes, "REGULAR", null, "2026-01-10T12:00:00Z"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detalles[0].campo").value("paquetes"));
        }
    }

    @Test
    void cp67_unaDireccionFueraDelMapaDa400() throws Exception {
        registrar(pedido(71, 20, "5", "REGULAR", null, "2026-01-10T12:00:00Z")).andExpect(status().isBadRequest());
        registrar(pedido(10, 51, "5", "REGULAR", null, "2026-01-10T12:00:00Z")).andExpect(status().isBadRequest());
        registrar(pedido(-1, 0, "5", "REGULAR", null, "2026-01-10T12:00:00Z")).andExpect(status().isBadRequest());
    }

    @Test
    void cp68_unPlazoPrioritarioQueNoEs4_8_12o18Da400() throws Exception {
        registrar(pedido(10, 20, "5", "PRIORITY", "6", "2026-01-10T12:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("horasPrometidas")));
    }

    @Test
    void cp69_unaFechaQueNoEsIsoDa400() throws Exception {
        registrar(pedido(10, 20, "5", "REGULAR", null, "10/01/2026 12:00")).andExpect(status().isBadRequest());
    }
}
