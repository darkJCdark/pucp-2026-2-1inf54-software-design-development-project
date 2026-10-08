package com.pucp.paqrap.modulos.flota.controller;

import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.MaintenanceDayEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.persistence.VehicleTypeParametersEntity;
import com.pucp.paqrap.modulos.flota.repository.MaintenanceDayRepository;
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
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FlotaApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private VehicleTypeParametersRepository tipoRepository;
    @Autowired
    private VehicleRepository vehiculoRepository;
    @Autowired
    private MaintenanceDayRepository mantenimientoRepository;

    @BeforeEach
    void cargarDatos() {
        tipoRepository.save(new VehicleTypeParametersEntity(VehicleType.CAR, 24, new BigDecimal("40.00"), new BigDecimal("8.00")));
        tipoRepository.save(new VehicleTypeParametersEntity(VehicleType.MOTORCYCLE, 8, new BigDecimal("25.00"), new BigDecimal("6.00")));
        tipoRepository.save(new VehicleTypeParametersEntity(VehicleType.BICYCLE, 4, new BigDecimal("12.00"), new BigDecimal("3.00")));
        vehiculoRepository.save(new VehicleEntity("TA01", VehicleType.CAR, VehicleOperationalStatus.AVAILABLE));
        vehiculoRepository.save(new VehicleEntity("TA02", VehicleType.CAR, VehicleOperationalStatus.UNAVAILABLE));
        vehiculoRepository.save(new VehicleEntity("TA03", VehicleType.CAR, VehicleOperationalStatus.IN_ROUTE));
        vehiculoRepository.save(new VehicleEntity("TM01", VehicleType.MOTORCYCLE, VehicleOperationalStatus.AVAILABLE));
        mantenimientoRepository.save(new MaintenanceDayEntity("TA01", LocalDate.of(2026, 1, 10)));
    }

    @Test
    void listaLosTiposDeVehiculo() throws Exception {
        mvc.perform(get("/api/flota/tipos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.tipo=='CAR')].codigoFlota").value("TA"));
    }

    @Test
    void actualizaLosParametrosDeUnTipo() throws Exception {
        mvc.perform(put("/api/flota/tipos/CAR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"capacidadPaquetes": 30, "velocidadKmh": 45.5, "costoPorKm": 9}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacidadPaquetes").value(30))
                .andExpect(jsonPath("$.velocidadKmh").value(45.5));
    }

    @Test
    void rechazaParametrosInvalidos() throws Exception {
        mvc.perform(put("/api/flota/tipos/CAR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"capacidadPaquetes": 0, "velocidadKmh": -1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasSize(3)));
    }

    @Test
    void explicaPorQueElJsonEsInvalido() throws Exception {
        mvc.perform(patch("/api/flota/vehiculos/TA01/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"tal vez\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("tal vez")));
    }

    @Test
    void rechazaTipoInexistente() throws Exception {
        mvc.perform(get("/api/flota/tipos/AVION"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filtraVehiculosPorTipoYEstado() throws Exception {
        mvc.perform(get("/api/flota/vehiculos").param("tipo", "CAR").param("estado", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("TA01"))
                .andExpect(jsonPath("$[0].x").value(27))
                .andExpect(jsonPath("$[0].cargaActual").value(0));
    }

    @Test
    void cambiaElEstadoDeUnVehiculo() throws Exception {
        mvc.perform(patch("/api/flota/vehiculos/TA02/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"AVAILABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("AVAILABLE"));
    }

    @Test
    void noPermiteAsignarEnRutaManualmente() throws Exception {
        mvc.perform(patch("/api/flota/vehiculos/TA01/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"IN_ROUTE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void noPermiteCambiarUnVehiculoEnRuta() throws Exception {
        mvc.perform(patch("/api/flota/vehiculos/TA03/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"UNAVAILABLE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void respondeNotFoundParaVehiculoInexistente() throws Exception {
        mvc.perform(get("/api/flota/vehiculos/TA99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Vehículo no encontrado: TA99"));
    }

    @Test
    void programaYCancelaUnMantenimiento() throws Exception {
        mvc.perform(post("/api/flota/mantenimientos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehiculoId\": \"TM01\", \"fecha\": \"2026-01-15\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehiculoId").value("TM01"));

        mvc.perform(get("/api/flota/mantenimientos").param("desde", "2026-01-11"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fecha").value("2026-01-15"));

        mvc.perform(delete("/api/flota/mantenimientos/TM01/2026-01-15"))
                .andExpect(status().isNoContent());
    }

    @Test
    void rechazaMantenimientoDuplicado() throws Exception {
        mvc.perform(post("/api/flota/mantenimientos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehiculoId\": \"TA01\", \"fecha\": \"2026-01-10\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rechazaMantenimientoDeVehiculoInexistente() throws Exception {
        mvc.perform(post("/api/flota/mantenimientos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehiculoId\": \"TB99\", \"fecha\": \"2026-01-10\"}"))
                .andExpect(status().isNotFound());
    }
}
