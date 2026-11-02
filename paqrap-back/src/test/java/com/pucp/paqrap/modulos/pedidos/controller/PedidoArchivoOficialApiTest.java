package com.pucp.paqrap.modulos.pedidos.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP-01: carga del archivo mensual oficial completo (paqrap-data/ventas/ventas.202601.txt, 641 registros).
 * Una segunda carga del mismo archivo no duplica pedidos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PedidoArchivoOficialApiTest {

    private static final int REGISTROS_ENERO = 641;

    @Autowired
    private MockMvc mvc;

    private static byte[] archivoOficial() throws IOException {
        Path desdeModulo = Path.of("..", "paqrap-data", "ventas", "ventas.202601.txt");
        Path desdeRaiz = Path.of("paqrap-data", "ventas", "ventas.202601.txt");
        return Files.readAllBytes(Files.exists(desdeModulo) ? desdeModulo : desdeRaiz);
    }

    private ResultActions cargar() throws Exception {
        return mvc.perform(multipart("/api/pedidos/carga").file(
                new MockMultipartFile("archivo", "ventas.202601.txt", "text/plain", archivoOficial())));
    }

    @Test
    void cargaElArchivoOficialDeEneroCon641RegistrosYNingunOmitido() throws Exception {
        cargar()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodo").value("2026-01"))
                .andExpect(jsonPath("$.leidos").value(REGISTROS_ENERO))
                .andExpect(jsonPath("$.registrados").value(REGISTROS_ENERO))
                .andExpect(jsonPath("$.omitidos").value(0));

        mvc.perform(get("/api/pedidos"))
                .andExpect(jsonPath("$.totalElementos").value(REGISTROS_ENERO));
    }

    @Test
    void recargarElArchivoOficialOmiteLos641YNoDuplica() throws Exception {
        cargar().andExpect(status().isCreated());

        cargar()
                .andExpect(jsonPath("$.registrados").value(0))
                .andExpect(jsonPath("$.omitidos").value(REGISTROS_ENERO));

        mvc.perform(get("/api/pedidos"))
                .andExpect(jsonPath("$.totalElementos").value(REGISTROS_ENERO));
    }
}
