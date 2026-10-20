package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.planificacion.dto.ModoOperacion;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.SolicitudPlanificacion;
import com.pucp.paqrap.modulos.redvial.persistence.RoadBlockEntity;
import com.pucp.paqrap.modulos.redvial.repository.RoadBlockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conecta el motor de escenarios con SA: en cada ciclo toma de la BD los pedidos pendientes y los bloqueos del
 * horizonte, llama a {@link PlanificadorService} y guarda el último plan de cada ejecución (en memoria).
 */
@Component
public class PlanificadorSaAdapter implements PlanificadorPort, PlanVigente {

    private static final Logger log = LoggerFactory.getLogger(PlanificadorSaAdapter.class);
    /** El plazo más largo de un pedido: los bloqueos posteriores no afectan al plan. */
    private static final Duration HORIZONTE = Duration.ofHours(36);

    private final PlanificadorService planificador;
    private final OrderRepository pedidoRepository;
    private final RoadBlockRepository bloqueoRepository;
    private final ScenarioExecutionRepository ejecucionRepository;
    private final int maxPedidos;
    private final long presupuestoMs;
    private final Map<Long, PlanResponse> ultimoPlan = new ConcurrentHashMap<>();

    public PlanificadorSaAdapter(PlanificadorService planificador, OrderRepository pedidoRepository,
                                 RoadBlockRepository bloqueoRepository, ScenarioExecutionRepository ejecucionRepository,
                                 @Value("${paqrap.planificacion.max-pedidos:100}") int maxPedidos,
                                 @Value("${paqrap.planificacion.presupuesto-ms:1500}") long presupuestoMs) {
        this.planificador = planificador;
        this.pedidoRepository = pedidoRepository;
        this.bloqueoRepository = bloqueoRepository;
        this.ejecucionRepository = ejecucionRepository;
        this.maxPedidos = maxPedidos;
        this.presupuestoMs = presupuestoMs;
    }

    @Override
    public ResultadoCiclo planificar(SolicitudCiclo solicitud) {
        Instant instante = solicitud.instante();
        ScenarioExecutionEntity ejecucion = ejecucionRepository.findById(solicitud.ejecucionId()).orElse(null);
        // Cada escenario solo atiende los pedidos que llegan desde su inicio simulado.
        Instant inicio = ejecucion != null ? ejecucion.getSimulationStartedAt() : Instant.EPOCH;
        var pedidos = pedidoRepository.pendientesEnDesde(inicio, instante, PageRequest.of(0, maxPedidos)).stream()
                .map(OrderEntity::toDomain).toList();
        var bloqueos = bloqueoRepository.buscarQueSeCruzan(instante, instante.plus(HORIZONTE)).stream()
                .map(RoadBlockEntity::toDomain).toList();

        PlanResponse plan = planificador.planificar(new SolicitudPlanificacion(modo(ejecucion), instante, pedidos,
                bloqueos, null, presupuestoMs));
        ultimoPlan.put(solicitud.ejecucionId(), plan);
        log.info("Ejecución {} ({} en {}): {} pedidos, {} bloqueos -> {} rutas, {} no atendidos, {} ms",
                solicitud.ejecucionId(), solicitud.motivo(), instante, pedidos.size(), bloqueos.size(),
                plan.rutas().size(), plan.noAtendidos().size(), plan.duracionMs());
        return new ResultadoCiclo(plan.colapso());
    }

    @Override
    public Optional<PlanResponse> ultimoPlan(long ejecucionId) {
        return Optional.ofNullable(ultimoPlan.get(ejecucionId));
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
