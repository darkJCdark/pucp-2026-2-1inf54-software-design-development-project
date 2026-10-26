package com.pucp.paqrap.modulos.flota.controller;

import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.persistence.VehicleTypeParametersEntity;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.flota.repository.VehicleTypeParametersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cantidad de flota: alta y baja de unidades (C7). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FlotaCantidadApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private VehicleTypeParametersRepository tipoRepository;
    @Autowired private VehicleRepository vehiculoRepository;
    @Autowired private ScenarioExecutionRepository ejecucionRepository;

    @BeforeEach
    void cargarDatos() {
        tipoRepository.save(new VehicleTypeParametersEntity(VehicleType.CAR, 24, new BigDecimal("40.00"), new BigDecimal("8.00")));
        tipoRepository.save(new VehicleTypeParametersEntity(VehicleType.MOTORCYCLE, 8, new BigDecimal("25.00"), new BigDecimal("6.00")));
        vehiculoRepository.save(new VehicleEntity("TA01", VehicleType.CAR, VehicleOperationalStatus.AVAILABLE));
        vehiculoRepository.save(new VehicleEntity("TA02", VehicleType.CAR, VehicleOperationalStatus.UNAVAILABLE));
        vehiculoRepository.save(new VehicleEntity("TA03", VehicleType.CAR, VehicleOperationalStatus.IN_ROUTE));
        vehiculoRepository.save(new VehicleEntity("TM01", VehicleType.MOTORCYCLE, VehicleOperationalStatus.AVAILABLE));
    }

    private org.springframework.test.web.servlet.ResultActions alta(String json) throws Exception {
        return mvc.perform(post("/api/flota/vehiculos").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void daDeAltaLaSiguienteUnidadDelTipoEnElCentralYDisponible() throws Exception {
        alta("{\"tipo\": \"CAR\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("TA04"))
                .andExpect(jsonPath("$.tipo").value("CAR"))
                .andExpect(jsonPath("$.estado").value("AVAILABLE"))
                .andExpect(jsonPath("$.x").value(27))
                .andExpect(jsonPath("$.y").value(14))
                .andExpect(jsonPath("$.cargaActual").value(0));
        mvc.perform(get("/api/flota/vehiculos/TA04")).andExpect(status().isOk());
        alta("{\"tipo\": \"CAR\"}").andExpect(jsonPath("$.id").value("TA05"));
        alta("{\"tipo\": \"MOTORCYCLE\"}").andExpect(jsonPath("$.id").value("TM02"));
    }

    @Test
    void rechazaElAltaDeUnTipoSinParametrosOInvalido() throws Exception {
        alta("{\"tipo\": \"BICYCLE\"}").andExpect(status().isNotFound());
        alta("{\"tipo\": \"AVION\"}").andExpect(status().isBadRequest());
        alta("{}").andExpect(status().isBadRequest());
    }

    @Test
    void daDeBajaUnaUnidadLibre() throws Exception {
        mvc.perform(delete("/api/flota/vehiculos/TA02")).andExpect(status().isNoContent());
        mvc.perform(get("/api/flota/vehiculos/TA02")).andExpect(status().isNotFound());
    }

    @Test
    void noDaDeBajaUnaUnidadEnRuta() throws Exception {
        mvc.perform(delete("/api/flota/vehiculos/TA03"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("en ruta")));
        mvc.perform(get("/api/flota/vehiculos/TA03")).andExpect(status().isOk());
    }

    @Test
    void noDaDeBajaUnaUnidadInexistente() throws Exception {
        mvc.perform(delete("/api/flota/vehiculos/NOEXISTE")).andExpect(status().isNotFound());
    }

    @Test
    void noDaDeBajaConUnEscenarioActivo() throws Exception {
        ScenarioExecutionEntity ejecucion = new ScenarioExecutionEntity(ScenarioType.FIVE_DAY,
                Instant.parse("2026-01-01T05:00:00Z"));
        ejecucion.iniciar(Instant.parse("2026-10-08T12:00:00Z"));
        ejecucionRepository.save(ejecucion);

        mvc.perform(delete("/api/flota/vehiculos/TA02"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("escenario activo")));
        mvc.perform(get("/api/flota/vehiculos/TA02")).andExpect(status().isOk());
        // El alta sí es posible con un escenario activo: la nueva unidad entra en el siguiente plan.
        alta("{\"tipo\": \"CAR\"}").andExpect(status().isCreated());
    }
}
