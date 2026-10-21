package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.planificacion.dto.ModoOperacion;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.ReplanificacionResponse;
import com.pucp.paqrap.modulos.planificacion.dto.SolicitudPlanificacion;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.persistence.RoadBlockEntity;
import com.pucp.paqrap.modulos.redvial.repository.RoadBlockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conecta el motor de escenarios con SA a través de {@link ReplanificacionService}: el primer ciclo crea el plan
 * inicial y los siguientes replanifican sin perder lo ya ejecutado (las rutas en marcha se conservan y sus
 * vehículos siguen ocupados hasta volver al central). En cada ciclo toma de la BD los pedidos llegados que la
 * ejecución aún no conoce y los bloqueos del horizonte, y guarda el último plan de cada ejecución (en memoria).
 */
@Component
public class PlanificadorSaAdapter implements PlanificadorPort, PlanVigente {

    private static final Logger log = LoggerFactory.getLogger(PlanificadorSaAdapter.class);
    /** El plazo más largo de un pedido: los bloqueos posteriores no afectan al plan. */
    private static final Duration HORIZONTE = Duration.ofHours(36);
    private static final int TAMANO_PAGINA = 500;

    private final ReplanificacionService replanificacion;
    private final OrderRepository pedidoRepository;
    private final RoadBlockRepository bloqueoRepository;
    private final ScenarioExecutionRepository ejecucionRepository;
    private final int maxPedidos;
    private final long presupuestoMs;
    private final Map<Long, ReplanificacionResponse> ultimaReplanificacion = new ConcurrentHashMap<>();

    /** @param maxPedidos máximo de pedidos nuevos que se incorporan por ciclo; el resto entra en los siguientes */
    public PlanificadorSaAdapter(ReplanificacionService replanificacion, OrderRepository pedidoRepository,
                                 RoadBlockRepository bloqueoRepository, ScenarioExecutionRepository ejecucionRepository,
                                 @Value("${paqrap.planificacion.max-pedidos:100}") int maxPedidos,
                                 @Value("${paqrap.planificacion.presupuesto-ms:1500}") long presupuestoMs) {
        this.replanificacion = replanificacion;
        this.pedidoRepository = pedidoRepository;
        this.bloqueoRepository = bloqueoRepository;
        this.ejecucionRepository = ejecucionRepository;
        this.maxPedidos = maxPedidos;
        this.presupuestoMs = presupuestoMs;
    }

    @Override
    public ResultadoCiclo planificar(SolicitudCiclo solicitud) {
        long ejecucionId = solicitud.ejecucionId();
        Instant instante = solicitud.instante();
        ScenarioExecutionEntity ejecucion = ejecucionRepository.findById(ejecucionId).orElse(null);
        // Cada escenario solo atiende los pedidos que llegan desde su inicio simulado.
        Instant inicio = ejecucion != null ? ejecucion.getSimulationStartedAt() : Instant.EPOCH;
        List<RoadBlock> horizonte = bloqueoRepository.buscarQueSeCruzan(instante, instante.plus(HORIZONTE)).stream()
                .map(RoadBlockEntity::toDomain).toList();

        ReplanificacionResponse respuesta;
        if (solicitud.motivo() == Motivo.INICIAL || !replanificacion.estaIniciada(ejecucionId)) {
            replanificacion.finalizar(ejecucionId);
            List<Order> pedidos = pedidosNuevos(ejecucionId, inicio, instante);
            respuesta = replanificacion.iniciar(ejecucionId, new SolicitudPlanificacion(modo(ejecucion), instante,
                    pedidos, horizonte, null, presupuestoMs));
        } else {
            List<RoadBlock> bloqueos = new ArrayList<>(horizonte);
            bloqueos.addAll(solicitud.bloqueosIniciados());
            respuesta = replanificacion.replanificar(ejecucionId, new ReplanificacionService.Eventos(
                    pedidosNuevos(ejecucionId, inicio, instante), bloqueos, solicitud.averias()), instante);
        }
        ultimaReplanificacion.put(ejecucionId, respuesta);
        PlanResponse plan = respuesta.plan();
        log.info("Ejecución {} ({} -> {} n.º {} en {}): {} rutas ({} conservadas, {} nuevas), {} no atendidos, {} ms",
                ejecucionId, solicitud.motivo(), respuesta.motivo(), respuesta.numeroReplanificacion(), instante,
                plan.rutas().size(), respuesta.rutasConservadas().size(), respuesta.rutasNuevas().size(),
                plan.noAtendidos().size(), plan.duracionMs());
        return new ResultadoCiclo(plan.colapso());
    }

    /** El estado de la replanificación se libera; el último plan se conserva para consultarlo. */
    @Override
    public void finalizar(long ejecucionId) {
        replanificacion.finalizar(ejecucionId);
    }

    @Override
    public Optional<PlanResponse> ultimoPlan(long ejecucionId) {
        return ultimaReplanificacion(ejecucionId).map(ReplanificacionResponse::plan);
    }

    /** Último plan con el detalle de la replanificación: rutas conservadas y nuevas, reasignados y no disponibles. */
    public Optional<ReplanificacionResponse> ultimaReplanificacion(long ejecucionId) {
        return Optional.ofNullable(ultimaReplanificacion.get(ejecucionId));
    }

    /** Pedidos llegados que la ejecución aún no conoce, en orden de llegada y hasta {@code maxPedidos}. */
    private List<Order> pedidosNuevos(long ejecucionId, Instant inicio, Instant instante) {
        List<Order> nuevos = new ArrayList<>();
        for (int pagina = 0; nuevos.size() < maxPedidos; pagina++) {
            List<OrderEntity> registrados = pedidoRepository.registradosEnDesde(inicio, instante,
                    PageRequest.of(pagina, TAMANO_PAGINA));
            for (OrderEntity pedido : registrados) {
                if (nuevos.size() < maxPedidos && !replanificacion.conocePedido(ejecucionId, pedido.getOrderId())) {
                    nuevos.add(pedido.toDomain());
                }
            }
            if (registrados.size() < TAMANO_PAGINA) {
                break;
            }
        }
        return nuevos;
    }

    private ModoOperacion modo(ScenarioExecutionEntity ejecucion) {
        if (ejecucion == null) {
            return ModoOperacion.SIMULACION_5D;
        }
        return switch (ejecucion.getScenarioType()) {
            case DAY_TO_DAY -> ModoOperacion.DIA_A_DIA;
            case FIVE_DAY -> ModoOperacion.SIMULACION_5D;
            case COLLAPSE -> ModoOperacion.COLAPSO;
        };
    }
}
