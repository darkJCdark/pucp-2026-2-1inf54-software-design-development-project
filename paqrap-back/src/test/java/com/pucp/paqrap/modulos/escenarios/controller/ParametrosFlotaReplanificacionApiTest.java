package com.pucp.paqrap.modulos.escenarios.controller;

import com.jayway.jsonpath.JsonPath;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
import com.pucp.paqrap.modulos.almacenes.repository.InventoryMovementRepository;
import com.pucp.paqrap.modulos.almacenes.repository.WarehouseRepository;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioStatus;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioResultRepository;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioResultEntity;
import com.pucp.paqrap.modulos.escenarios.service.EjecutorPlanService;
import com.pucp.paqrap.modulos.escenarios.service.MotorEscenarios;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.persistence.VehicleTypeParametersEntity;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.flota.repository.VehicleTypeParametersRepository;
import com.pucp.paqrap.modulos.incidencias.repository.BreakdownEventRepository;
import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.entity.OrderStatus;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderStatusHistoryEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.pedidos.repository.OrderStatusHistoryRepository;
import com.pucp.paqrap.modulos.planificacion.persistence.PlanEntity;
import com.pucp.paqrap.modulos.planificacion.persistence.RouteEntity;
import com.pucp.paqrap.modulos.planificacion.persistence.RouteStopEntity;
import com.pucp.paqrap.modulos.planificacion.repository.PlanRepository;
import com.pucp.paqrap.modulos.planificacion.repository.RouteRepository;
import com.pucp.paqrap.modulos.planificacion.repository.RouteStopRepository;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.persistence.RoadBlockEntity;
import com.pucp.paqrap.modulos.redvial.repository.RoadBlockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cambiar los parámetros de un tipo de vehículo (por ejemplo la velocidad) con un escenario en marcha lo hace
 * replanificar en el próximo tick; si no cambia ningún valor, o no hay escenario activo, no pasa nada.
 * Escenario 5D (x160) desde el 01-ene 00:00 en Lima = 05:00Z; 1 s real = 2 min 40 s simulados.
 */
@SpringBootTest(properties = "paqrap.planificacion.presupuesto-ms=200")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ParametrosFlotaReplanificacionApiTest {

    private static final Instant INICIO = Instant.parse("2026-01-01T05:00:00Z");

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        RelojManual relojManual() {
            return new RelojManual();
        }
    }

    static final class RelojManual extends Clock {
        private volatile Instant ahora = Instant.parse("2026-10-08T12:00:00Z");

        void avanzar(Duration duracion) { ahora = ahora.plus(duracion); }

        @Override public Instant instant() { return ahora; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    @Autowired private MockMvc mvc;
    @Autowired private RelojManual reloj;
    @Autowired private MotorEscenarios motor;
    @Autowired private EjecutorPlanService ejecutor;
    @Autowired private ScenarioExecutionRepository ejecucionRepository;
    @Autowired private ScenarioResultRepository resultadoRepository;
    @Autowired private WarehouseRepository almacenRepository;
    @Autowired private InventoryMovementRepository movimientoRepository;
    @Autowired private VehicleRepository vehiculoRepository;
    @Autowired private VehicleTypeParametersRepository tipoRepository;
    @Autowired private BreakdownEventRepository averiaRepository;
    @Autowired private RoadBlockRepository bloqueoRepository;
    @Autowired private OrderRepository pedidoRepository;
    @Autowired private OrderStatusHistoryRepository historialRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private RouteStopRepository routeStopRepository;

    @BeforeEach
    void cargarDatos() {
        tipoRepository.save(new VehicleTypeParametersEntity(VehicleType.CAR, 24, new BigDecimal("40.00"), new BigDecimal("8.00")));
        tipoRepository.save(new VehicleTypeParametersEntity(VehicleType.MOTORCYCLE, 8, new BigDecimal("25.00"), new BigDecimal("6.00")));
        tipoRepository.save(new VehicleTypeParametersEntity(VehicleType.BICYCLE, 4, new BigDecimal("12.00"), new BigDecimal("3.00")));
        almacenRepository.save(new WarehouseEntity("CENTRAL", WarehouseKind.CENTRAL, 27, 14, null));
        for (String id : List.of("TA01", "TA02", "TA03")) {
            vehiculoRepository.save(new VehicleEntity(id, VehicleType.CAR, VehicleOperationalStatus.AVAILABLE));
        }
    }

    @AfterEach
    void limpiar() {
        ejecucionRepository.findByStatusIn(List.of(ScenarioStatus.RUNNING, ScenarioStatus.PAUSED))
                .forEach(e -> motor.detener(e.getExecutionId()));
        historialRepository.deleteAll();
        routeStopRepository.deleteAll();
        routeRepository.deleteAll();
        planRepository.deleteAll();
        pedidoRepository.deleteAll();
        movimientoRepository.deleteAll();
        averiaRepository.deleteAll();
        bloqueoRepository.deleteAll();
        resultadoRepository.deleteAll();
        ejecucionRepository.deleteAll();
        vehiculoRepository.deleteAll();
        tipoRepository.deleteAll();
        almacenRepository.deleteAll();
    }

    private void pedido(String id, int x, int y, int paquetes, Instant registro) {
        OrderEntity pedido = new OrderEntity(id, "c-" + id, new Location(x, y), paquetes, registro, DeliveryType.REGULAR, 36);
        pedidoRepository.save(pedido);
        historialRepository.save(OrderStatusHistoryEntity.registro(pedido));
    }

    private long iniciarCincoDias() throws Exception {
        String respuesta = mvc.perform(post("/api/escenarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\": \"FIVE_DAY\", \"inicioSimulado\": \"" + INICIO + "\"}"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
        mvc.perform(post("/api/escenarios/" + id + "/iniciar")).andExpect(status().isOk());
        return id;
    }

    private void avanzar(Duration real) {
        reloj.avanzar(real);
        motor.tick();
    }

    private String replanificacion(long id) throws Exception {
        return mvc.perform(get("/api/planificacion/" + id + "/replanificacion"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static <T> T leer(String json, String ruta) {
        return JsonPath.read(json, ruta);
    }

    private void actualizarCar(String capacidad, String velocidad, String costo) throws Exception {
        mvc.perform(put("/api/flota/tipos/CAR").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacidadPaquetes\": " + capacidad + ", \"velocidadKmh\": " + velocidad
                                + ", \"costoPorKm\": " + costo + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void cambiarLaVelocidadDeMotocicletaRecalculaLaRutaConservada() throws Exception {
        vehiculoRepository.findAll().forEach(vehiculo -> vehiculo.setOperationalStatus(VehicleOperationalStatus.UNAVAILABLE));
        vehiculoRepository.save(new VehicleEntity("TM01", VehicleType.MOTORCYCLE, VehicleOperationalStatus.AVAILABLE));
        pedido("P1", 60, 14, 3, INICIO);
        long id = iniciarCincoDias();

        String antes = replanificacion(id);
        assertEquals("TM01", leer(antes, "$.plan.rutas[0].vehiculoId"));
        Instant llegadaAntes = Instant.parse(leer(antes, "$.plan.rutas[0].paradas[0].llegada"));
        int numeroAntes = leer(antes, "$.numeroReplanificacion");

        mvc.perform(put("/api/flota/tipos/MOTORCYCLE").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacidadPaquetes\": 8, \"velocidadKmh\": 50, \"costoPorKm\": 6}"))
                .andExpect(status().isOk());

        avanzar(Duration.ofSeconds(1));
        String despues = replanificacion(id);
        Instant llegadaDespues = Instant.parse(leer(despues, "$.plan.rutas[0].paradas[0].llegada"));
        assertEquals("PERIODICO", leer(despues, "$.motivo"));
        assertEquals(numeroAntes + 1, (int) leer(despues, "$.numeroReplanificacion"));
        assertEquals("TM01", leer(despues, "$.plan.rutas[0].vehiculoId"));
        assertTrue(llegadaDespues.isBefore(llegadaAntes));
    }
    @Test
    void cambiarLaVelocidadConUnEscenarioEnMarchaReplanifica() throws Exception {
        pedido("P1", 30, 14, 5, INICIO);
        long id = iniciarCincoDias();
        int antes = leer(replanificacion(id), "$.numeroReplanificacion");

        actualizarCar("24", "80", "8");

        // 2 min 40 s simulados: todavía no toca el ciclo periódico (cada hora), pero la flota cambió.
        avanzar(Duration.ofSeconds(1));
        String despues = replanificacion(id);
        assertEquals("PERIODICO", leer(despues, "$.motivo"));
        assertEquals(antes + 1, (int) leer(despues, "$.numeroReplanificacion"));
    }

    @Test
    void cambiarCapacidadOCostoTambienReplanifica() throws Exception {
        pedido("P1", 30, 14, 5, INICIO);
        long id = iniciarCincoDias();
        int antes = leer(replanificacion(id), "$.numeroReplanificacion");

        actualizarCar("30", "40", "9");

        avanzar(Duration.ofSeconds(1));
        assertEquals(antes + 1, (int) leer(replanificacion(id), "$.numeroReplanificacion"));
    }

    @Test
    void guardarLosMismosValoresNoReplanifica() throws Exception {
        pedido("P1", 30, 14, 5, INICIO);
        long id = iniciarCincoDias();
        int antes = leer(replanificacion(id), "$.numeroReplanificacion");

        actualizarCar("24", "40", "8");

        avanzar(Duration.ofSeconds(1));
        assertEquals(antes, (int) leer(replanificacion(id), "$.numeroReplanificacion"));
    }

    @Test
    void sinEscenarioActivoElCambioSeGuardaYNoDejaMarcasPendientes() throws Exception {
        pedido("P1", 30, 14, 5, INICIO);
        actualizarCar("24", "80", "8");
        assertEquals(0, new BigDecimal("80").compareTo(
                tipoRepository.findById(VehicleType.CAR).orElseThrow().getSpeedKmh()));

        // Un escenario que empieza después no hereda el cambio anterior como si fuera nuevo.
        long id = iniciarCincoDias();
        int antes = leer(replanificacion(id), "$.numeroReplanificacion");
        avanzar(Duration.ofSeconds(1));
        assertEquals(antes, (int) leer(replanificacion(id), "$.numeroReplanificacion"));
    }

    @Test
    void unaVelocidadInvalidaSeRechazaYNoReplanifica() throws Exception {
        pedido("P1", 30, 14, 5, INICIO);
        long id = iniciarCincoDias();
        int antes = leer(replanificacion(id), "$.numeroReplanificacion");

        mvc.perform(put("/api/flota/tipos/CAR").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacidadPaquetes\": 24, \"velocidadKmh\": 0, \"costoPorKm\": 8}"))
                .andExpect(status().isBadRequest());


        mvc.perform(put("/api/flota/tipos/MOTORCYCLE").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacidadPaquetes\": 8, \"velocidadKmh\": -1, \"costoPorKm\": 6}"))
                .andExpect(status().isBadRequest());

        avanzar(Duration.ofSeconds(1));
        assertEquals(antes, (int) leer(replanificacion(id), "$.numeroReplanificacion"));
    }
}
