package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.AnnealingConfig;
import com.pucp.paqrap.modulos.planificacion.dto.ModoOperacion;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.ReplanificacionResponse;
import com.pucp.paqrap.modulos.planificacion.dto.SolicitudPlanificacion;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.persistence.RoadBlockEntity;
import com.pucp.paqrap.modulos.redvial.repository.RoadBlockRepository;

/**
 * Conecta MotorEscenarios con el planificador.
 *
 * El nombre histórico PlanificadorSaAdapter se
 * conserva por compatibilidad.
 *
 * El algoritmo activo puede ser SA o ALNS.
 */
@Component
public class PlanificadorSaAdapter
        implements PlanificadorPort, PlanVigente {

    private static final Logger log =
            LoggerFactory.getLogger(
                    PlanificadorSaAdapter.class
            );

    private static final Duration HORIZONTE =
            Duration.ofHours(
                    36
            );

    private static final int TAMANO_PAGINA =
            500;

    private final ReplanificacionService replanificacion;

    private final OrderRepository pedidoRepository;

    private final RoadBlockRepository bloqueoRepository;

    private final ScenarioExecutionRepository ejecucionRepository;

    private final AlgoritmoPlanificacion algoritmo;

    /*
     * SA mantiene el tope histórico.
     */
    private final int maxPedidosSa;

    /*
     * ALNS puede recibir una cantidad mucho mayor.
     */
    private final int maxPedidosAlns;

    private final long presupuestoMs;

    private final Map<
            Long,
            ReplanificacionResponse
            > ultimaReplanificacion =
            new ConcurrentHashMap<>();

    public PlanificadorSaAdapter(
            ReplanificacionService replanificacion,
            OrderRepository pedidoRepository,
            RoadBlockRepository bloqueoRepository,
            ScenarioExecutionRepository ejecucionRepository,

            @Value(
                    "${paqrap.planificacion.algoritmo:SA}"
            )
            String algoritmo,

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
            long presupuestoMs
    ) {

        this.replanificacion =
                replanificacion;

        this.pedidoRepository =
                pedidoRepository;

        this.bloqueoRepository =
                bloqueoRepository;

        this.ejecucionRepository =
                ejecucionRepository;

        this.algoritmo =
                AlgoritmoPlanificacion.desde(
                        algoritmo
                );

        if (
                maxPedidosSa <= 0
        ) {

            throw new IllegalArgumentException(
                    "paqrap.planificacion.max-pedidos debe ser positivo"
            );
        }

        if (
                maxPedidosAlns <= 0
        ) {

            throw new IllegalArgumentException(
                    "paqrap.planificacion.alns.max-pedidos debe ser positivo"
            );
        }

        this.maxPedidosSa =
                maxPedidosSa;

        this.maxPedidosAlns =
                maxPedidosAlns;

        this.presupuestoMs =
                presupuestoMs;
    }

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

        Instant inicio =
                ejecucion != null
                        ? ejecucion
                                .getSimulationStartedAt()
                        : Instant.EPOCH;

        List<RoadBlock> horizonte =
                bloqueoRepository
                        .buscarQueSeCruzan(
                                instante,
                                instante.plus(
                                        HORIZONTE
                                )
                        )
                        .stream()

                        .map(
                                RoadBlockEntity::toDomain
                        )

                        .toList();

        ReplanificacionResponse respuesta;

        /*
         * =================================================
         * PLAN INICIAL
         * =================================================
         */

        if (
                solicitud.motivo() ==
                        Motivo.INICIAL ||

                !replanificacion.estaIniciada(
                        ejecucionId
                )
        ) {

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
                                            : ejecucion
                                                    .getSaSeed(),

                                    presupuestoMs,

                                    configuracionSa(
                                            ejecucion
                                    )
                            )
                    );

        } else {

            /*
             * =============================================
             * REPLANIFICACIÓN
             * =============================================
             */

            List<RoadBlock> bloqueos =
                    new ArrayList<>(
                            horizonte
                    );

            bloqueos.addAll(
                    solicitud
                            .bloqueosIniciados()
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

                                    solicitud
                                            .bloqueosTerminados(),

                                    solicitud
                                            .averias(),

                                    solicitud
                                            .averiasTerminadas(),

                                    solicitud
                                            .tiposFlotaActualizados()
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

        /*
         * Ahora el log indica qué algoritmo
         * produjo el plan.
         */
        log.info(
                "Ejecución {} [{}] ({} -> {} n.º {} en {}): {} rutas ({} conservadas, {} nuevas), {} no atendidos, {} ms",
                ejecucionId,
                algoritmo,
                solicitud.motivo(),
                respuesta.motivo(),
                respuesta.numeroReplanificacion(),
                instante,
                plan.rutas().size(),
                respuesta.rutasConservadas().size(),
                respuesta.rutasNuevas().size(),
                plan.noAtendidos().size(),
                plan.duracionMs()
        );

        return new ResultadoCiclo(
                plan.colapso()
        );
    }

    @Override
    public void finalizar(
            long ejecucionId
    ) {

        replanificacion.finalizar(
                ejecucionId
        );
    }

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

    @Override
    public Optional<PlanResponse> ultimoPlan(
            long ejecucionId
    ) {

        return ultimaReplanificacion(
                ejecucionId
        ).map(
                ReplanificacionResponse::plan
        );
    }

    public Optional<ReplanificacionResponse> ultimaReplanificacion(
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
     * PEDIDOS NUEVOS
     * =====================================================
     */

    private List<Order> pedidosNuevos(
            long ejecucionId,
            Instant inicio,
            Instant instante
    ) {

        /*
         * Aquí eliminamos finalmente el cuello de
         * botella artificial de 100 pedidos cuando
         * estamos usando ALNS.
         */
        int limite =
                algoritmo ==
                        AlgoritmoPlanificacion.ALNS

                        ? maxPedidosAlns
                        : maxPedidosSa;

        List<Order> nuevos =
                new ArrayList<>();

        for (
                int pagina = 0;
                nuevos.size() < limite;
                pagina++
        ) {

            List<OrderEntity> registrados =
                    pedidoRepository
                            .registradosEnDesde(
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
                        nuevos.size() <
                                limite &&

                        !replanificacion
                                .conocePedido(
                                        ejecucionId,
                                        pedido
                                                .getOrderId()
                                )
                ) {

                    nuevos.add(
                            pedido.toDomain()
                    );
                }
            }

            if (
                    registrados.size() <
                            TAMANO_PAGINA
            ) {

                break;
            }
        }

        return nuevos;
    }

    /*
     * =====================================================
     * CONFIGURACIÓN SA
     * =====================================================
     *
     * Se mantiene porque ScenarioExecutionEntity todavía
     * almacena los parámetros históricos de SA.
     *
     * Si ALNS está seleccionado, PlanificadorSaService
     * simplemente los ignora.
     */

    private AnnealingConfig configuracionSa(
            ScenarioExecutionEntity ejecucion
    ) {

        return ejecucion == null
                ? PlanificadorSaService
                        .configuracionPorDefecto()

                : PlanificadorSaService
                        .configuracionConParametros(
                                ejecucion
                                        .getSaInitialTemperature(),

                                ejecucion
                                        .getSaMinimumTemperature(),

                                ejecucion
                                        .getSaCoolingFactor(),

                                ejecucion
                                        .getSaIterationsPerTemperature()
                        );
    }

    private ModoOperacion modo(
            ScenarioExecutionEntity ejecucion
    ) {

        if (
                ejecucion == null
        ) {

            return ModoOperacion
                    .SIMULACION_5D;
        }

        return switch (
                ejecucion.getScenarioType()
        ) {

            case DAY_TO_DAY ->
                    ModoOperacion
                            .DIA_A_DIA;

            case FIVE_DAY ->
                    ModoOperacion
                            .SIMULACION_5D;

            case COLLAPSE ->
                    ModoOperacion
                            .COLAPSO;
        };
    }
}