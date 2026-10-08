package com.pucp.paqrap.modulos.almacenes.controller;

import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
import com.pucp.paqrap.modulos.almacenes.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AlmacenApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private WarehouseRepository almacenRepository;

    @BeforeEach
    void cargarDatos() {
        almacenRepository.save(new WarehouseEntity("CENTRAL", WarehouseKind.CENTRAL, 27, 14, null));
        almacenRepository.save(new WarehouseEntity("NORTHWEST", WarehouseKind.INTERMEDIATE, 12, 38, 1000));
        almacenRepository.save(new WarehouseEntity("EAST", WarehouseKind.INTERMEDIATE, 57, 27, 1000));
    }

    @Test
    void listaLosAlmacenesConElCentralPrimero() throws Exception {
        mvc.perform(get("/api/almacenes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value("CENTRAL"))
                .andExpect(jsonPath("$[0].capacidad").doesNotExist());
    }

    @Test
    void obtieneUnAlmacenIntermedio() throws Exception {
        mvc.perform(get("/api/almacenes/EAST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.x").value(57))
                .andExpect(jsonPath("$.stockInicial").value(1000))
                .andExpect(jsonPath("$.capacidad").value(1000));
    }

    @Test
    void respondeMethodNotAllowedEnVezDeErrorInterno() throws Exception {
        mvc.perform(post("/api/almacenes"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void respondeNotFoundParaAlmacenInexistente() throws Exception {
        mvc.perform(get("/api/almacenes/SUR"))
                .andExpect(status().isNotFound());
    }
}
