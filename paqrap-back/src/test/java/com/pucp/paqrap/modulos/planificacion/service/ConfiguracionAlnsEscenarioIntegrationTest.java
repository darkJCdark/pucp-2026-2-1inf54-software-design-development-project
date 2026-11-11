package com.pucp.paqrap.modulos.planificacion.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
import com.pucp.paqrap.modulos.almacenes.repository.InventoryMovementRepository;
import com.pucp.paqrap.modulos.almacenes.repository.WarehouseRepository;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioStatus;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioResultRepository;
import com.pucp.paqrap.modulos.escenarios.service.MotorEscenarios;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.persistence.VehicleTypeParametersEntity;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.flota.repository.VehicleTypeParametersRepository;
import com.pucp.paqrap.modulos.incidencias.repository.BreakdownEventRepository;
import com.pucp.paqrap.modulos.pedidos.CoberturaDePrueba;
import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderStatusHistoryEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.pedidos.repository.OrderStatusHistoryRepository;
import com.pucp.paqrap.modulos.planificacion.persistence.PlanEntity;
import com.pucp.paqrap.modulos.planificacion.persistence.RouteEntity;
import com.pucp.paqrap.modulos.planificacion.repository.PlanRepository;
import com.pucp.paqrap.modulos.planificacion.repository.RouteRepository;
import com.pucp.paqrap.modulos.planificacion.repository.RouteStopRepository;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.repository.RoadBlockRepository;

/**
 * Verifica la integración completa de ALNS:
 *
 * HTTP
 * -> MotorEscenarios
 * -> PlanificadorPort
 * -> PlanificadorSaAdapter
 * -> ReplanificacionSaService
 * -> PlanificadorSaService
 * -> OperationalAlnsPlanner
 * -> persistencia.
 *
 * También verifica una replanificación real por la
 * llegada de un pedido nuevo.
 */
@SpringBootTest(
        properties = {
                /*
                 * Seleccionamos explícitamente ALNS.
                 */
                "paqrap.planificacion.algoritmo=ALNS",

                /*
                 * Presupuesto suficiente para evitar
                 * flakiness en CI, pero pequeño.
                 */
                "paqrap.planificacion.presupuesto-ms=1000",

                /*
                 * Configuración reducida para el test.
                 */
                "paqrap.planificacion.alns.initial-batch-size=8",
                "paqrap.planificacion.alns.candidate-vehicles=3",
                "paqrap.planificacion.alns.min-routes-to-remove=1",
                "paqrap.planificacion.alns.max-routes-to-remove=2",
                "paqrap.planificacion.alns.max-iterations=30",
                "paqrap.planificacion.alns.max-iterations-without-improvement=15",

                /*
                 * ALNS no utiliza el límite histórico de
                 * 100 pedidos de SA.
                 */
                "paqrap.planificacion.alns.max-pedidos=20000"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfiguracionAlnsEscenarioIntegrationTest {

    private static final Instant INICIO =
            Instant.parse(
                    "2026-01-01T05:00:00Z"
            );

    /*
     * =====================================================
     * CONFIGURACIÓN DEL RELOJ
     * =====================================================
     */

    @TestConfiguration
    static class Config {

        @Bean
        @Primary
        RelojManual relojManual() {
            return new RelojManual();
        }
    }

    /**
     * Reloj real controlable.
     *
     * FIVE_DAY utiliza factor 160:
     *
     * 5 segundos reales
     * =
     * 13 minutos 20 segundos simulados.
     */
    static final class RelojManual
            extends Clock {

        private Instant ahora =
                Instant.parse(
                        "2026-10-08T12:00:00Z"
                );

        void avanzar(
                Duration duracion
        ) {
            ahora =
                    ahora.plus(
                            duracion
                    );
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(
                ZoneId zone
        ) {
            return this;
        }
    }

    /*
     * =====================================================
     * DEPENDENCIAS
     * =====================================================
     */

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RelojManual reloj;

    @Autowired
    private MotorEscenarios motor;

    /*
     * Servicio real.
     *
     * No se reemplaza por un mock.
     */
    @Autowired
    private PlanificadorSaService planificador;

    @Autowired
    private ScenarioExecutionRepository ejecucionRepository;

    @Autowired
    private ScenarioResultRepository resultadoRepository;

    @Autowired
    private WarehouseRepository almacenRepository;

    @Autowired
    private InventoryMovementRepository movimientoRepository;

    @Autowired
    private VehicleRepository vehiculoRepository;

    @Autowired
    private VehicleTypeParametersRepository tipoRepository;

    @Autowired
    private BreakdownEventRepository averiaRepository;

    @Autowired
    private RoadBlockRepository bloqueoRepository;

    @Autowired
    private OrderRepository pedidoRepository;

    @Autowired
    private OrderStatusHistoryRepository historialRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private RouteStopRepository routeStopRepository;

    /*
     * =====================================================
     * SETUP
     * =====================================================
     */

    @BeforeEach
    void cargarDatosOperativos() {

        /*
         * Parámetros de flota.
         */
        tipoRepository.save(
                new VehicleTypeParametersEntity(
                        VehicleType.CAR,
                        24,
                        new BigDecimal(
                                "40.00"
                        ),
                        new BigDecimal(
                                "8.00"
                        )
                )
        );

        tipoRepository.save(
                new VehicleTypeParametersEntity(
                        VehicleType.MOTORCYCLE,
                        8,
                        new BigDecimal(
                                "25.00"
                        ),
                        new BigDecimal(
                                "6.00"
                        )
                )
        );

        tipoRepository.save(
                new VehicleTypeParametersEntity(
                        VehicleType.BICYCLE,
                        4,
                        new BigDecimal(
                                "12.00"
                        ),
                        new BigDecimal(
                                "3.00"
                        )
                )
        );

        /*
         * Almacén central.
         */
        almacenRepository.save(
                new WarehouseEntity(
                        "CENTRAL",
                        WarehouseKind.CENTRAL,
                        27,
                        14,
                        null
                )
        );

        /*
         * Tres unidades para permitir que una ruta
         * se conserve mientras ALNS utiliza otra
         * unidad para pedidos nuevos.
         */
        for (
                String id :
                List.of(
                        "TA01",
                        "TA02",
                        "TA03"
                )
        ) {

            vehiculoRepository.save(
                    new VehicleEntity(
                            id,
                            VehicleType.CAR,
                            VehicleOperationalStatus.AVAILABLE
                    )
            );
        }
    }

    @AfterEach
    void limpiar() {

        ejecucionRepository
                .findByStatusIn(
                        List.of(
                                ScenarioStatus.RUNNING,
                                ScenarioStatus.PAUSED
                        )
                )
                .forEach(
                        ejecucion ->
                                motor.detener(
                                        ejecucion
                                                .getExecutionId()
                                )
                );

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

    /*
     * =====================================================
     * TEST PRINCIPAL
     * =====================================================
     */

    @Test
void cincoDiasUsaAlnsEnPlanInicialYReplanificacionPorNuevoPedido()
        throws Exception {

    /*
     * =================================================
     * 1. CONFIRMAR CONFIGURACIÓN
     * =================================================
     */

    assertEquals(
            AlgoritmoPlanificacion.ALNS,
            planificador.algoritmo(),
            "Spring debe haber creado el planner en modo ALNS"
    );

    /*
     * =================================================
     * 2. PEDIDOS
     * =================================================
     *
     * P1 existe desde el inicio.
     *
     * P2 aparece 20 minutos simulados después.
     */

    pedido(
            "P1",
            30,
            14,
            5,
            INICIO
    );

    pedido(
            "P2",
            24,
            14,
            3,
            INICIO.plusSeconds(
                    20 * 60
            )
    );

    /*
     * =================================================
     * 3. CREAR ESCENARIO 5D
     * =================================================
     */

    long ejecucionId =
            crearCincoDias();

    /*
     * =================================================
     * 4. INICIAR
     * =================================================
     */

    mvc.perform(
                    post(
                            "/api/escenarios/" +
                                    ejecucionId +
                                    "/iniciar"
                    )
            )
            .andExpect(
                    status().isOk()
            );

    /*
     * =================================================
     * 5. PLAN INICIAL
     * =================================================
     */

    String inicial =
            ultimaReplanificacion(
                    ejecucionId
            );

    assertEquals(
            "INICIO",
            leer(
                    inicial,
                    "$.motivo"
            )
    );

    assertEquals(
            0,
            ((Number) leer(
                    inicial,
                    "$.numeroReplanificacion"
            )).intValue()
    );

    assertEquals(
            Boolean.TRUE,
            leer(
                    inicial,
                    "$.plan.factible"
            )
    );

    assertEquals(
            0,
            ((List<?>) leer(
                    inicial,
                    "$.plan.noAtendidos"
            )).size()
    );

    /*
     * P1 debe estar dentro de una ruta generada
     * inicialmente por ALNS.
     */

    String rutaP1 =
            rutaQueEntrega(
                    inicial,
                    "P1"
            );

    assertTrue(
            rutaP1.startsWith(
                    "ALNS-"
            ),
            () ->
                    "La ruta inicial debería ser ALNS: "
                            +
                            rutaP1
    );

    /*
     * P2 todavía no ha sido registrado.
     *
     * Regla causal:
     *
     * registeredAt(P2) > hora actual
     *
     * por lo que el planificador no puede conocerlo.
     */

    assertFalse(
            contienePedido(
                    inicial,
                    "P2"
            ),
            "P2 todavía no debe aparecer en el plan inicial"
    );

    /*
     * =================================================
     * 6. PERSISTENCIA DEL PLAN INICIAL
     * =================================================
     */

    assertPersistenciaAlns(
            ejecucionId,
            1
    );

    /*
     * =================================================
     * 7. AVANZAR 13m20s SIMULADOS
     * =================================================
     *
     * FIVE_DAY:
     *
     * 5 segundos reales × 160
     * =
     * 13 minutos 20 segundos simulados.
     *
     * P2 todavía no ha llegado.
     */

    reloj.avanzar(
            Duration.ofSeconds(
                    5
            )
    );

    motor.tick();

    /*
     * =================================================
     * 8. AVANZAR OTROS 13m20s
     * =================================================
     *
     * Tiempo acumulado:
     *
     * 26m40s.
     *
     * P2 estaba registrado en +20 minutos.
     *
     * Ahora sí debe entrar al sistema.
     */

    reloj.avanzar(
            Duration.ofSeconds(
                    5
            )
    );

    motor.tick();

    /*
     * =================================================
     * 9. REPLANIFICACIÓN
     * =================================================
     */

    String replanificado =
            ultimaReplanificacion(
                    ejecucionId
            );

    assertEquals(
            "NUEVO_PEDIDO",
            leer(
                    replanificado,
                    "$.motivo"
            )
    );

    assertEquals(
            1,
            ((Number) leer(
                    replanificado,
                    "$.numeroReplanificacion"
            )).intValue()
    );

    assertEquals(
            Boolean.TRUE,
            leer(
                    replanificado,
                    "$.plan.factible"
            )
    );

    assertEquals(
            0,
            ((List<?>) leer(
                    replanificado,
                    "$.plan.noAtendidos"
            )).size()
    );

    /*
     * =================================================
     * 10. LA RUTA ACTIVA DE P1 DEBE CONSERVARSE
     * =================================================
     *
     * Este es ahora un comportamiento fundamental.
     *
     * No destruimos la ejecución física existente.
     */

    List<String> conservadas =
            leer(
                    replanificado,
                    "$.rutasConservadas"
            );

    assertTrue(
            conservadas.contains(
                    rutaP1
            ),
            () ->
                    "La ruta activa de P1 debería conservarse: "
                            +
                            replanificado
    );

    /*
     * =================================================
     * 11. P2 DEBE SER INCORPORADO
     * =================================================
     */

    String rutaP2 =
            rutaQueEntrega(
                    replanificado,
                    "P2"
            );

    assertTrue(
            rutaP2.startsWith(
                    "ALNS-"
            ),
            () ->
                    "P2 debería pertenecer a una ruta ALNS: "
                            +
                            rutaP2
    );

    /*
     * =================================================
     * 12. NUEVA SEMÁNTICA:
     *     REUTILIZAR LA RUTA ACTIVA
     * =================================================
     *
     * Antes esperábamos:
     *
     * P1 -> TA01
     * P2 -> otro vehículo
     *
     * Ahora queremos permitir:
     *
     * P1
     * ↓
     * recarga
     * ↓
     * P2
     *
     * sobre la misma ruta activa, siempre que sea
     * factible.
     *
     * El resultado observado:
     *
     * ALNS-TA01
     *
     * P1
     * CENTRAL
     * P2
     * CENTRAL
     *
     * es precisamente el comportamiento esperado.
     */

    assertEquals(
            rutaP1,
            rutaP2,
            "P2 debe poder insertarse en el futuro modificable de la ruta activa"
    );

    /*
     * =================================================
     * 13. NO DEBE CREARSE UNA RUTA NUEVA
     * =================================================
     *
     * Como P2 fue absorbido por ALNS-TA01, no hay
     * razón para utilizar otro vehículo.
     */

    List<String> nuevas =
            leer(
                    replanificado,
                    "$.rutasNuevas"
            );

    assertTrue(
            nuevas.isEmpty(),
            () ->
                    "No debería crearse otra ruta si la ruta activa puede absorber P2: "
                            +
                            nuevas
    );

    /*
     * La ruta modificada continúa apareciendo como
     * conservada.
     */

    assertTrue(
            conservadas.contains(
                    rutaP2
            )
    );

    /*
     * =================================================
     * 14. VERIFICAR ORDEN DE PARADAS
     * =================================================
     *
     * Queremos comprobar algo más fuerte que simplemente
     * encontrar P2.
     *
     * La parada ya comprometida P1 debe permanecer antes
     * que P2.
     */

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rutas =
            JsonPath.read(
                    replanificado,
                    "$.plan.rutas"
            );

    Map<String, Object> rutaActiva =
            rutas
                    .stream()

                    .filter(
                            ruta ->
                                    rutaP1.equals(
                                            ruta.get(
                                                    "id"
                                            )
                                    )
                    )

                    .findFirst()

                    .orElseThrow();

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> paradas =
            (
                    List<
                            Map<String, Object>
                            >
            ) rutaActiva.get(
                    "paradas"
            );

    int posicionP1 =
            posicionPedido(
                    paradas,
                    "P1"
            );

    int posicionP2 =
            posicionPedido(
                    paradas,
                    "P2"
            );

    assertTrue(
            posicionP1 >= 0,
            "P1 debe continuar en la ruta"
    );

    assertTrue(
            posicionP2 >= 0,
            "P2 debe haberse insertado en la ruta"
    );

    assertTrue(
            posicionP1 < posicionP2,
            "La parada ya comprometida P1 debe mantenerse antes del nuevo pedido P2"
    );

    /*
     * =================================================
     * 15. COMPROBAR QUE EXISTE RECARGA ANTES DE P2
     * =================================================
     *
     * P1 consume la carga inicial.
     *
     * Para llevar los 3 paquetes de P2, la solución
     * introduce:
     *
     * CENTRAL (+3)
     * ↓
     * P2 (-3)
     *
     * Esto comprueba que la modificación mantiene
     * consistencia de carga.
     */

    boolean hayRecargaAntesDeP2 =
            false;

    for (
            int i = posicionP1 + 1;
            i < posicionP2;
            i++
    ) {

        Map<String, Object> parada =
                paradas.get(i);

        if (
                "ALMACEN".equals(
                        parada.get(
                                "tipo"
                        )
                )
                &&
                "CENTRAL".equals(
                        parada.get(
                                "almacenId"
                        )
                )
        ) {

            Number paquetes =
                    (
                            Number
                    ) parada.get(
                            "paquetes"
                    );

            if (
                    paquetes != null
                    &&
                    paquetes.intValue() >=
                            3
            ) {

                hayRecargaAntesDeP2 =
                        true;

                break;
            }
        }
    }

    assertTrue(
            hayRecargaAntesDeP2,
            "La ruta debe recargar paquetes antes de atender P2"
    );

    /*
     * =================================================
     * 16. SEGUNDO SNAPSHOT PERSISTIDO
     * =================================================
     */

    assertPersistenciaAlns(
            ejecucionId,
            2
    );
}

    /*
     * =====================================================
     * HELPERS
     * =====================================================
     */

    private void pedido(
            String id,
            int x,
            int y,
            int paquetes,
            Instant registro
    ) {

        OrderEntity pedido =
                new OrderEntity(
                        id,
                        "cliente-" + id,
                        new Location(
                                x,
                                y
                        ),
                        paquetes,
                        registro,
                        DeliveryType.REGULAR,
                        36
                );

        pedidoRepository.save(
                pedido
        );

        historialRepository.save(
                OrderStatusHistoryEntity.registro(
                        pedido
                )
        );
    }

    private static int posicionPedido(
        List<Map<String, Object>> paradas,
        String pedidoId
) {

    for (
            int i = 0;
            i < paradas.size();
            i++
    ) {

        if (
                pedidoId.equals(
                        paradas
                                .get(i)
                                .get(
                                        "pedidoId"
                                )
                )
        ) {
            return i;
        }
    }

    return -1;
}

    /**
     * Crea el escenario 5D, pero todavía no lo inicia.
     */
    private long crearCincoDias()
            throws Exception {

        /*
         * 5D exige cobertura del mes correspondiente.
         */
        CoberturaDePrueba.cubrirMes(
                pedidoRepository,
                YearMonth.of(
                        2026,
                        1
                )
        );

        String respuesta =
                mvc.perform(
                                post(
                                        "/api/escenarios"
                                )
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                """
                                                {
                                                  "tipo": "FIVE_DAY",
                                                  "inicioSimulado": "%s",
                                                  "configuracionSa": {
                                                    "semilla": 42,
                                                    "temperaturaInicial": 200.0,
                                                    "temperaturaMinima": 2.0,
                                                    "factorEnfriamiento": 0.90,
                                                    "iteracionesPorTemperatura": 7
                                                  }
                                                }
                                                """.formatted(
                                                        INICIO
                                                )
                                        )
                        )
                        .andExpect(
                                status().isCreated()
                        )
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        /*
         * El marcador solo es necesario durante
         * la validación de creación del escenario.
         */
        CoberturaDePrueba.quitarMarcador(
                pedidoRepository,
                YearMonth.of(
                        2026,
                        1
                )
        );

        return (
                (
                        Number
                ) JsonPath.read(
                        respuesta,
                        "$.id"
                )
        ).longValue();
    }

    private String ultimaReplanificacion(
            long ejecucionId
    )
            throws Exception {

        return mvc.perform(
                        get(
                                "/api/planificacion/" +
                                        ejecucionId +
                                        "/replanificacion"
                        )
                )
                .andExpect(
                        status().isOk()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /**
     * Helper genérico para leer JSON.
     *
     * Cuando después necesitemos llamar métodos
     * específicos como intValue() o size(), hacemos
     * primero un cast explícito:
     *
     * ((Number) leer(...)).intValue()
     * ((List<?>) leer(...)).size()
     */
    @SuppressWarnings(
            "unchecked"
    )
    private static <T> T leer(
            String json,
            String ruta
    ) {

        return (
                T
        ) JsonPath.read(
                json,
                ruta
        );
    }

    /**
     * Encuentra la ruta que contiene una entrega
     * para el pedido indicado.
     */
    @SuppressWarnings(
            "unchecked"
    )
    private static String rutaQueEntrega(
            String json,
            String pedidoId
    ) {

        List<
                Map<String, Object>
                > rutas =
                JsonPath.read(
                        json,
                        "$.plan.rutas"
                );

        List<String> ids =
                rutas
                        .stream()

                        .filter(
                                ruta -> {

                                    List<
                                            Map<String, Object>
                                            > paradas =
                                            (
                                                    List<
                                                            Map<String, Object>
                                                            >
                                            ) ruta.get(
                                                    "paradas"
                                            );

                                    return paradas
                                            .stream()
                                            .anyMatch(
                                                    parada ->
                                                            pedidoId.equals(
                                                                    parada.get(
                                                                            "pedidoId"
                                                                    )
                                                            )
                                            );
                                }
                        )

                        .map(
                                ruta ->
                                        (
                                                String
                                        ) ruta.get(
                                                "id"
                                        )
                        )

                        .toList();

        assertEquals(
                1,
                ids.size(),
                () ->
                        "El pedido "
                                +
                                pedidoId
                                +
                                " debe aparecer en exactamente una ruta: "
                                +
                                json
        );

        return ids.getFirst();
    }

    /**
     * Indica si un pedido aparece en cualquier
     * entrega del plan.
     */
    @SuppressWarnings(
            "unchecked"
    )
    private static boolean contienePedido(
            String json,
            String pedidoId
    ) {

        List<
                Map<String, Object>
                > rutas =
                JsonPath.read(
                        json,
                        "$.plan.rutas"
                );

        for (
                Map<String, Object> ruta :
                rutas
        ) {

            List<
                    Map<String, Object>
                    > paradas =
                    (
                            List<
                                    Map<String, Object>
                                    >
                    ) ruta.get(
                            "paradas"
                    );

            boolean encontrado =
                    paradas
                            .stream()
                            .anyMatch(
                                    parada ->
                                            pedidoId.equals(
                                                    parada.get(
                                                            "pedidoId"
                                                    )
                                            )
                            );

            if (
                    encontrado
            ) {
                return true;
            }
        }

        return false;
    }

    /**
     * Verifica que el snapshot realmente llegó
     * hasta la base de datos y que sus rutas
     * conservan el identificador generado por ALNS.
     */
    private void assertPersistenciaAlns(
            long ejecucionId,
            int cantidadPlanesEsperada
    ) {

        List<PlanEntity> planes =
                planRepository
                        .findByExecutionExecutionIdOrderByPlanIdAsc(
                                ejecucionId
                        );

        assertEquals(
                cantidadPlanesEsperada,
                planes.size(),
                "Cantidad inesperada de snapshots persistidos"
        );

        PlanEntity ultimo =
                planes.getLast();

        assertTrue(
                ultimo.isFeasible(),
                "El plan persistido debe ser factible"
        );

        List<RouteEntity> rutas =
                routeRepository
                        .findByPlanPlanIdOrderByRouteIdAsc(
                                ultimo.getPlanId()
                        );

        assertFalse(
                rutas.isEmpty(),
                "El plan persistido debe contener rutas"
        );

        assertTrue(
                rutas
                        .stream()
                        .allMatch(
                                ruta ->
                                        ruta
                                                .getOperationalRouteId()
                                                .startsWith(
                                                        "ALNS-"
                                                )
                        ),
                () ->
                        "Todas las rutas del test deben haber sido creadas por ALNS: "
                                +
                                rutas
                                        .stream()
                                        .map(
                                                RouteEntity::
                                                        getOperationalRouteId
                                        )
                                        .toList()
        );
    }
}