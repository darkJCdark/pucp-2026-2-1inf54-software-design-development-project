package com.pucp.paqrap.modulos.redvial.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BloqueoApiTest {

    /** Primeras líneas de bloqueo.2601.txt (datos del profesor), con una línea en blanco al final. */
    private static final String CONTENIDO = """
            01d02h22m-01d04h42m:25,45,45,45,45,40
            01d02h42m-01d06h15m:25,25,30,25,30,30,35,30

            """;

    @Autowired
    private MockMvc mvc;

    private MockMultipartFile archivo(String nombre, String contenido) {
        return new MockMultipartFile("archivo", nombre, "text/plain", contenido.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void cargaUnArchivoMensualYDeduceElPeriodoDelNombre() throws Exception {
        mvc.perform(multipart("/api/red-vial/bloqueos/carga").file(archivo("bloqueo.2601.txt", CONTENIDO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodo").value("2026-01"))
                .andExpect(jsonPath("$.registrados").value(2))
                .andExpect(jsonPath("$.reemplazados").value(0));

        // 01d02h22m en Lima (UTC-5) = 2026-01-01T07:22Z
        mvc.perform(get("/api/red-vial/bloqueos"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].inicio").value("2026-01-01T07:22:00Z"))
                .andExpect(jsonPath("$[0].fin").value("2026-01-01T09:42:00Z"))
                .andExpect(jsonPath("$[0].nodos", hasSize(3)))
                .andExpect(jsonPath("$[1].nodos[3].x").value(35));
    }

    @Test
    void recargarElMismoMesReemplazaEnVezDeDuplicar() throws Exception {
        mvc.perform(multipart("/api/red-vial/bloqueos/carga").file(archivo("bloqueo.2601.txt", CONTENIDO)));
        mvc.perform(multipart("/api/red-vial/bloqueos/carga").file(archivo("bloqueo.2601.txt", CONTENIDO)))
                .andExpect(jsonPath("$.reemplazados").value(2))
                .andExpect(jsonPath("$.registrados").value(2));

        mvc.perform(get("/api/red-vial/bloqueos"))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void filtraLosBloqueosVigentesEnUnInstante() throws Exception {
        mvc.perform(multipart("/api/red-vial/bloqueos/carga").file(archivo("bloqueo.2601.txt", CONTENIDO)));

        // A las 10:00Z el primero (hasta 09:42Z) ya terminó; el segundo sigue hasta 11:15Z.
        mvc.perform(get("/api/red-vial/bloqueos").param("vigenteEn", "2026-01-01T10:00:00Z"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fin").value("2026-01-01T11:15:00Z"));
    }

    @Test
    void aceptaElPeriodoExplicitoSiElNombreNoSigueElFormato() throws Exception {
        mvc.perform(multipart("/api/red-vial/bloqueos/carga")
                        .file(archivo("mis-bloqueos.txt", CONTENIDO))
                        .param("periodo", "2026-03"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodo").value("2026-03"));
    }

    @Test
    void rechazaNombreSinPeriodo() throws Exception {
        mvc.perform(multipart("/api/red-vial/bloqueos/carga").file(archivo("mis-bloqueos.txt", CONTENIDO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("bloqueo.AAMM.txt")));
    }

    @Test
    void indicaLaLineaInvalidaYNoGuardaNada() throws Exception {
        String conError = "01d02h22m-01d04h42m:25,45,45,45\n01d02h42m-01d06h15m:25,25,30\n";
        mvc.perform(multipart("/api/red-vial/bloqueos/carga").file(archivo("bloqueo.2601.txt", conError)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("linea 2")));

        mvc.perform(get("/api/red-vial/bloqueos"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void respondeNotFoundParaBloqueoInexistente() throws Exception {
        mvc.perform(get("/api/red-vial/bloqueos/999999"))
                .andExpect(status().isNotFound());
    }
}
