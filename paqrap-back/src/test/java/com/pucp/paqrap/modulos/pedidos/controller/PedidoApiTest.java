package com.pucp.paqrap.modulos.pedidos.controller;

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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PedidoApiTest {

    /** Primeras líneas de ventas.202601.txt (datos del profesor). Horas de Lima (UTC-5). */
    private static final String VENTAS = """
            01d01h30m:56,30,c4910,02,36
            01d04h55m:21,29,c9599,06,18
            01d08h52m:61,21,c8771,09,04
            """;

    @Autowired
    private MockMvc mvc;

    private ResultActions cargar(String nombre, String contenido) throws Exception {
        return mvc.perform(multipart("/api/pedidos/carga").file(
                new MockMultipartFile("archivo", nombre, "text/plain", contenido.getBytes(StandardCharsets.UTF_8))));
    }

    private ResultActions registrar(String json) throws Exception {
        return mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void cargaUnArchivoDeVentasYCalculaElDeadline() throws Exception {
        cargar("ventas.202601.txt", VENTAS)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodo").value("2026-01"))
                .andExpect(jsonPath("$.leidos").value(3))
                .andExpect(jsonPath("$.registrados").value(3));

        // 01d01h30m en Lima = 06:30Z; regular 36 h -> vence 02-ene 18:30Z
        mvc.perform(get("/api/pedidos/202601-00001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedido.clienteId").value("c4910"))
                .andExpect(jsonPath("$.pedido.paquetes").value(2))
                .andExpect(jsonPath("$.pedido.tipoEntrega").value("REGULAR"))
                .andExpect(jsonPath("$.pedido.registradoEn").value("2026-01-01T06:30:00Z"))
                .andExpect(jsonPath("$.pedido.vence").value("2026-01-02T18:30:00Z"))
                .andExpect(jsonPath("$.pedido.estado").value("REGISTERED"))
                .andExpect(jsonPath("$.historial", hasSize(1)))
                .andExpect(jsonPath("$.historial[0].estado").value("REGISTERED"));

        // 01d08h52m = 13:52Z; priorizado 4 h -> vence 17:52Z
        mvc.perform(get("/api/pedidos/202601-00003"))
                .andExpect(jsonPath("$.pedido.tipoEntrega").value("PRIORITY"))
                .andExpect(jsonPath("$.pedido.horasPrometidas").value(4))
                .andExpect(jsonPath("$.pedido.vence").value("2026-01-01T17:52:00Z"));
    }

    @Test
    void recargarElMismoArchivoOmiteLosPedidosExistentes() throws Exception {
        cargar("ventas.202601.txt", VENTAS);
        cargar("ventas.202601.txt", VENTAS)
                .andExpect(jsonPath("$.registrados").value(0))
                .andExpect(jsonPath("$.omitidos").value(3));

        mvc.perform(get("/api/pedidos"))
                .andExpect(jsonPath("$.totalElementos").value(3));
    }

    @Test
    void elMismoNumeroDeLineaEnOtroMesEsOtroPedido() throws Exception {
        cargar("ventas.202601.txt", VENTAS);
        cargar("ventas.202602.txt", VENTAS)
                .andExpect(jsonPath("$.registrados").value(3));

        mvc.perform(get("/api/pedidos/202602-00001"))
                .andExpect(jsonPath("$.pedido.registradoEn").value("2026-02-01T06:30:00Z"));
    }

    @Test
    void indicaLaLineaInvalidaYNoGuardaNada() throws Exception {
        cargar("ventas.202601.txt", "01d01h30m:56,30,c4910,02,36\n01d04h55m:21,29,c9599,06,20\n")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("linea 2")))
                .andExpect(jsonPath("$.mensaje").value(containsString("20 h")));

        mvc.perform(get("/api/pedidos"))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void registraUnPedidoPriorizadoManual() throws Exception {
        registrar("""
                {"clienteId": "c123", "x": 10, "y": 20, "paquetes": 5, "tipoEntrega": "PRIORITY",
                 "horasPrometidas": 8, "registradoEn": "2026-01-10T12:00:00Z"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(startsWith("M-")))
                .andExpect(jsonPath("$.vence").value("2026-01-10T20:00:00Z"))
                .andExpect(jsonPath("$.estado").value("REGISTERED"));
    }

    @Test
    void unPedidoRegularSiempreTiene36Horas() throws Exception {
        registrar("""
                {"clienteId": "c123", "x": 10, "y": 20, "paquetes": 5, "tipoEntrega": "REGULAR",
                 "registradoEn": "2026-01-10T12:00:00Z"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.horasPrometidas").value(36))
                .andExpect(jsonPath("$.vence").value("2026-01-12T00:00:00Z"));

        registrar("""
                {"clienteId": "c123", "x": 10, "y": 20, "paquetes": 5, "tipoEntrega": "REGULAR", "horasPrometidas": 8}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    void validaElPedidoManual() throws Exception {
        registrar("""
                {"clienteId": "c123", "x": 10, "y": 20, "paquetes": 5, "tipoEntrega": "PRIORITY"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("horasPrometidas")));

        registrar("""
                {"clienteId": "9abc", "x": 99, "y": 20, "paquetes": 0, "tipoEntrega": "REGULAR"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasSize(3)));
    }

    @Test
    void filtraYPaginaElListado() throws Exception {
        cargar("ventas.202601.txt", VENTAS);

        mvc.perform(get("/api/pedidos").param("tipoEntrega", "PRIORITY"))
                .andExpect(jsonPath("$.totalElementos").value(2));
        mvc.perform(get("/api/pedidos").param("clienteId", "c4910"))
                .andExpect(jsonPath("$.contenido", hasSize(1)));
        mvc.perform(get("/api/pedidos").param("venceHasta", "2026-01-01T18:00:00Z"))
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].id").value("202601-00003"));
        mvc.perform(get("/api/pedidos").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        mvc.perform(get("/api/pedidos").param("sort", "vence,asc"))
                .andExpect(jsonPath("$.contenido[0].id").value("202601-00003"));
    }

    @Test
    void rechazaOrdenarPorUnCampoDesconocido() throws Exception {
        mvc.perform(get("/api/pedidos").param("sort", "deadline"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("vence")));
    }

    @Test
    void respondeNotFoundParaPedidoInexistente() throws Exception {
        mvc.perform(get("/api/pedidos/202601-99999"))
                .andExpect(status().isNotFound());
    }
}
