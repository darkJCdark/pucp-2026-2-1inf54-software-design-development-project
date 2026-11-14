package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ObjetivoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.AnnealingConfig;
import com.pucp.paqrap.modulos.planificacion.dto.ModoOperacion;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.ReplanificacionResponse;
import com.pucp.paqrap.modulos.planificacion.dto.SolicitudPlanificacion;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.persistence.RoadBlockEntity;
import com.pucp.paqrap.modulos.redvial.repository.RoadBlockRepository;

/**
 * Adaptador entre el motor de escenarios y la planificación.
 *
 * Conserva el nombre histórico PlanificadorSaAdapter para no
 * romper la arquitectura existente, pero el algoritmo real
 * puede ser:
 *
 *     SA
 *
 * o:
 *
 *     ALNS
 *
 * según:
 *
 *     paqrap.planificacion.algoritmo
 *
 * Cuando se usa ALNS, también muestra el objetivo:
 *
 *     ALNS/SERVICE_MAX
 *
 * o:
 *
 *     ALNS/SERVICE_THEN_COST
 */
@Component
public class PlanificadorSaAdapter
        implements PlanificadorPort,
        PlanVigente {

    private static final Logger log =
            LoggerFactory.getLogger(
                    PlanificadorSaAdapter.class
            );

    private static final int TAMANO_PAGINA =
            500;

    /*
     * =====================================================
     * DEPENDENCIAS
     * =====================================================
     */

    private final ReplanificacionService replanificacion;

    private final OrderRepository pedidoRepository;

    private final RoadBlockRepository bloqueoRepository;

    private final ScenarioExecutionRepository ejecucionRepository;

    /*
     * =====================================================
     * CONFIGURACIÓN
     * =====================================================
     */

    private final int maxPedidosSa;

    private final int maxPedidosAlns;

    private final long presupuestoMs;

    private final String algoritmo;

    private final ObjetivoPlanificacion objetivo;

    /** Horizonte móvil usado para consultar restricciones futuras relevantes. */
    private final Duration horizonteDecision;

    /*
     * =====================================================
     * ESTADO
     * =====================================================
     */

    private final Map<Long, ReplanificacionResponse>
            ultimaReplanificacion =
            new ConcurrentHashMap<>();

    /*
     * =====================================================
     * CONSTRUCTOR SPRING
     * =====================================================
     */

    @Autowired
    public PlanificadorSaAdapter(
            ReplanificacionService replanificacion,
            OrderRepository pedidoRepository,
            RoadBlockRepository bloqueoRepository,
            ScenarioExecutionRepository ejecucionRepository,

            @Value(
                    "${paqrap.planificacion.max-pedidos:100}"
            )
            int maxPedidosSa,

            @Value(
                    "${paqrap.planificacion.alns.max-pedidos:20000}"
            )
            int maxPedidosAlns,

            @Value(
                    "${paqrap.planificacion.presupuesto-ms:1500}"
            )
            long presupuestoMs,

            @Value(
                    "${paqrap.planificacion.algoritmo:SA}"
            )
            String algoritmo,

            @Value(
                    "${paqrap.planificacion.objetivo:SERVICE_THEN_COST}"
            )
            String objetivo,

            @Value(
                    "${paqrap.escenarios.horizonte-decision:36h}"
            )
            Duration horizonteDecision
    ) {

        this.replanificacion =
                replanificacion;

        this.pedidoRepository =
                pedidoRepository;

        this.bloqueoRepository =
                bloqueoRepository;

        this.ejecucionRepository =
                ejecucionRepository;

        if (
                maxPedidosSa <= 0
        ) {

            throw new IllegalArgumentException(
                    "maxPedidosSa debe ser positivo"
            );
        }

        if (
                maxPedidosAlns <= 0
        ) {

            throw new IllegalArgumentException(
                    "maxPedidosAlns debe ser positivo"
            );
        }

        if (
                presupuestoMs <= 0
        ) {

            throw new IllegalArgumentException(
                    "presupuestoMs debe ser positivo"
            );
        }

        this.maxPedidosSa =
                maxPedidosSa;

        this.maxPedidosAlns =
                maxPedidosAlns;

        this.presupuestoMs =
                presupuestoMs;

        this.algoritmo =
                normalizarAlgoritmo(
                        algoritmo
                );

        this.objetivo =
                ObjetivoPlanificacion.parse(
                        objetivo
                );

        this.horizonteDecision =
                horizonteDecision == null
                        || horizonteDecision.isZero()
                        || horizonteDecision.isNegative()
                        ? Duration.ofHours(36)
                        : horizonteDecision;
    }

    /*
     * =====================================================
     * CONSTRUCTOR COMPATIBLE CON TESTS ANTIGUOS
     * =====================================================
     *
     * Cualquier test que todavía construya:
     *
     * new PlanificadorSaAdapter(
     *     replanificacion,
     *     orderRepository,
     *     roadBlockRepository,
     *     executionRepository,
     *     100,
     *     1500
     * )
     *
     * sigue compilando.
     */

    public PlanificadorSaAdapter(
            ReplanificacionService replanificacion,
            OrderRepository pedidoRepository,
            RoadBlockRepository bloqueoRepository,
            ScenarioExecutionRepository ejecucionRepository,
            int maxPedidos,
            long presupuestoMs
    ) {

        this(
                replanificacion,
                pedidoRepository,
                bloqueoRepository,
                ejecucionRepository,
                maxPedidos,
                20_000,
                presupuestoMs,
                "SA",
                ObjetivoPlanificacion
                        .SERVICE_THEN_COST
                        .name(),
                Duration.ofHours(36)
        );
    }

    /*
     * =====================================================
     * CICLO
     * =====================================================
     */

    @Override
    public ResultadoCiclo planificar(
            SolicitudCiclo solicitud
    ) {

        long ejecucionId =
                solicitud.ejecucionId();

        Instant instante =
                solicitud.instante();

        ScenarioExecutionEntity ejecucion =
                ejecucionRepository
                        .findById(
                                ejecucionId
                        )
                        .orElse(
                                null
                        );

        /*
         * Cada escenario solo puede conocer pedidos que
         * llegaron desde su inicio simulado.
         */
        Instant inicio =
                ejecucion != null

                        ? ejecucion.getSimulationStartedAt()

                        : Instant.EPOCH;

        List<RoadBlock> horizonte =
                bloqueoRepository
                        .buscarQueSeCruzan(
                                instante,
                                instante.plus(
                                        horizonteDecision
                                )
                        )
                        .stream()

                        .map(
                                RoadBlockEntity::toDomain
                        )

                        .toList();

        ReplanificacionResponse respuesta;

        if (
                solicitud.motivo()
                        ==
                        Motivo.INICIAL

                ||
                !replanificacion.estaIniciada(
                        ejecucionId
                )
        ) {

            /*
             * =================================================
             * PLAN INICIAL
             * =================================================
             */

            replanificacion.finalizar(
                    ejecucionId
            );

            List<Order> pedidos =
                    pedidosNuevos(
                            ejecucionId,
                            inicio,
                            instante
                    );

            respuesta =
                    replanificacion.iniciar(
                            ejecucionId,

                            new SolicitudPlanificacion(
                                    modo(
                                            ejecucion
                                    ),

                                    instante,

                                    pedidos,

                                    horizonte,

                                    ejecucion == null
                                            ? null
                                            : ejecucion.getSaSeed(),

                                    presupuestoMs,

                                    configuracionSa(
                                            ejecucion
                                    )
                            )
                    );

        } else {

            /*
             * =================================================
             * REPLANIFICACIÓN
             * =================================================
             */

            List<RoadBlock> bloqueos =
                    new ArrayList<>(
                            horizonte
                    );

            bloqueos.addAll(
                    solicitud.bloqueosIniciados()
            );

            respuesta =
                    replanificacion.replanificar(
                            ejecucionId,

                            new ReplanificacionService.Eventos(
                                    pedidosNuevos(
                                            ejecucionId,
                                            inicio,
                                            instante
                                    ),

                                    bloqueos,

                                    solicitud.bloqueosTerminados(),

                                    solicitud.averias(),

                                    solicitud.averiasTerminadas(),

                                    solicitud.tiposFlotaActualizados(),

                                    motivoReplanificacionDe(solicitud.motivo()),

                                    solicitud.fallosTransbordo()
                            ),

                            instante
                    );
        }

        ultimaReplanificacion.put(
                ejecucionId,
                respuesta
        );

        PlanResponse plan =
                respuesta.plan();

        log.info(
                "Ejecución {} [{}] ({} -> {} n.º {} en {}): "
                        +
                        "{} rutas ({} conservadas, {} nuevas), "
                        +
                        "{} no atendidos, {} ms",

                ejecucionId,

                etiquetaAlgoritmo(),

                solicitud.motivo(),

                respuesta.motivo(),

                respuesta.numeroReplanificacion(),

                instante,

                plan.rutas().size(),

                respuesta
                        .rutasConservadas()
                        .size(),

                respuesta
                        .rutasNuevas()
                        .size(),

                plan
                        .noAtendidos()
                        .size(),

                plan.duracionMs()
        );

        return new ResultadoCiclo(
                plan.colapso()
        );
    }

    private static com.pucp.paqrap.modulos.planificacion.dto.MotivoReplanificacion
    motivoReplanificacionDe(Motivo motivo) {
        return switch (motivo) {
            case ROLLING_HORIZON ->
                    com.pucp.paqrap.modulos.planificacion.dto.MotivoReplanificacion.ROLLING_HORIZON;
            case RIESGO_ETA ->
                    com.pucp.paqrap.modulos.planificacion.dto.MotivoReplanificacion.RIESGO_ETA;
            case FIN_RUTA ->
                    com.pucp.paqrap.modulos.planificacion.dto.MotivoReplanificacion.FIN_RUTA;
            case TRANSBORDO_FALLIDO ->
                    com.pucp.paqrap.modulos.planificacion.dto.MotivoReplanificacion.TRANSBORDO_FALLIDO;
            default -> null;
        };
    }

    /*
     * =====================================================
     * FINALIZACIÓN
     * =====================================================
     */

    @Override
    public void finalizar(
            long ejecucionId
    ) {

        replanificacion.finalizar(
                ejecucionId
        );
    }

    /*
     * =====================================================
     * MÉTRICAS
     * =====================================================
     */

    @Override
    public Optional<
            ReplanificacionService.MetricasPlanificacion
            > metricas(
            long ejecucionId
    ) {

        return replanificacion.metricas(
                ejecucionId
        );
    }

    /*
     * =====================================================
     * PLAN VIGENTE
     * =====================================================
     */

    @Override
    public Optional<PlanResponse> ultimoPlan(
            long ejecucionId
    ) {

        return ultimaReplanificacion(
                ejecucionId
        )
                .map(
                        ReplanificacionResponse::plan
                );
    }

    /**
     * Último resultado de replanificación con:
     *
     * - rutas conservadas;
     * - rutas nuevas;
     * - reasignaciones;
     * - unidades no disponibles.
     */
    public Optional<ReplanificacionResponse>
    ultimaReplanificacion(
            long ejecucionId
    ) {

        return Optional.ofNullable(
                ultimaReplanificacion.get(
                        ejecucionId
                )
        );
    }

    /*
     * =====================================================
     * PEDIDOS
     * =====================================================
     */

    /**
     * Devuelve pedidos que:
     *
     * - ya llegaron;
     * - pertenecen al periodo actual;
     * - todavía no son conocidos por la ejecución.
     *
     * Nunca conoce pedidos futuros.
     */
    private List<Order> pedidosNuevos(
            long ejecucionId,
            Instant inicio,
            Instant instante
    ) {

        int limite =
                maxPedidosActual();

        List<Order> nuevos =
                new ArrayList<>();

        for (
                int pagina = 0;
                nuevos.size() < limite;
                pagina++
        ) {

            List<OrderEntity> registrados =
                    pedidoRepository.registradosEnDesde(
                            inicio,
                            instante,

                            PageRequest.of(
                                    pagina,
                                    TAMANO_PAGINA
                            )
                    );

            for (
                    OrderEntity pedido :
                    registrados
            ) {

                if (
                        nuevos.size() < limite

                        &&
                        !replanificacion.conocePedido(
                                ejecucionId,
                                pedido.getOrderId()
                        )
                ) {

                    nuevos.add(
                            pedido.toDomain()
                    );
                }
            }

            if (
                    registrados.size()
                            <
                            TAMANO_PAGINA
            ) {

                break;
            }
        }

        return nuevos;
    }

    /*
     * =====================================================
     * CONFIGURACIÓN SA DEL ESCENARIO
     * =====================================================
     */

    private AnnealingConfig configuracionSa(
            ScenarioExecutionEntity ejecucion
    ) {

        if (
                ejecucion == null
        ) {

            return PlanificadorSaService
                    .configuracionPorDefecto();
        }

        return PlanificadorSaService
                .configuracionConParametros(
                        ejecucion.getSaInitialTemperature(),
                        ejecucion.getSaMinimumTemperature(),
                        ejecucion.getSaCoolingFactor(),
                        ejecucion.getSaIterationsPerTemperature()
                );
    }

    /*
     * =====================================================
     * MODO
     * =====================================================
     */

    private ModoOperacion modo(
            ScenarioExecutionEntity ejecucion
    ) {

        if (
                ejecucion == null
        ) {

            return ModoOperacion.SIMULACION_5D;
        }

        return switch (
                ejecucion.getScenarioType()
        ) {

            case DAY_TO_DAY ->
                    ModoOperacion.DIA_A_DIA;

            case FIVE_DAY ->
                    ModoOperacion.SIMULACION_5D;

            case COLLAPSE ->
                    ModoOperacion.COLAPSO;
        };
    }

    /*
     * =====================================================
     * ALGORITMO
     * =====================================================
     */

    private int maxPedidosActual() {

        return "ALNS".equals(
                algoritmo
        )
                ? maxPedidosAlns
                : maxPedidosSa;
    }

    private String etiquetaAlgoritmo() {

        if (
                "ALNS".equals(
                        algoritmo
                )
        ) {

            return "ALNS/"
                    +
                    objetivo.name();
        }

        return "SA";
    }

    private static String normalizarAlgoritmo(
            String algoritmo
    ) {

        if (
                algoritmo == null
                ||
                algoritmo.isBlank()
        ) {

            return "SA";
        }

        String normalized =
                algoritmo
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        return switch (
                normalized
        ) {

            case "SA",
                 "SIMULATED_ANNEALING" ->

                    "SA";

            case "ALNS" ->

                    "ALNS";

            default ->

                    throw new IllegalArgumentException(
                            "Algoritmo desconocido: "
                                    +
                                    algoritmo
                    );
        };
    }
}