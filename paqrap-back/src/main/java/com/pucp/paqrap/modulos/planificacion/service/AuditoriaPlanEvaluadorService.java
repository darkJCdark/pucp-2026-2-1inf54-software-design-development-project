package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolationType;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.dto.AuditoriaPlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.PedidoAtrasadoResponse;
import com.pucp.paqrap.modulos.planificacion.dto.ViolacionDetalleResponse;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Auditoría con el {@link OperationalPlanEvaluator} original. Además de las violaciones, calcula desde los
 * horarios del plan qué pedidos están vencidos sin entregar a la hora auditada, cuáles llegarán tarde y
 * cuáles no tienen cobertura.
 */
@Service
public class AuditoriaPlanEvaluadorService implements AuditoriaPlanService {

    private final ReplanificacionSaService replanificacion;
    private final OperationalPlanEvaluator evaluador = new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()));

    @Autowired
    public AuditoriaPlanEvaluadorService(ReplanificacionSaService replanificacion) {
        this.replanificacion = Objects.requireNonNull(replanificacion, "replanificacion es requerida");
    }

    @Override
    public AuditoriaPlanResponse auditarPlanVigente(long ejecucionId, Instant ahora) {
        Objects.requireNonNull(ahora, "ahora es requerido");
        ReplanificacionSaService.VistaEjecucion vista = replanificacion.vista(ejecucionId);
        if (ahora.isBefore(vista.ultimaHora())) {
            throw new ReglaNegocioException("La hora " + ahora + " es anterior a la última replanificación ("
                    + vista.ultimaHora() + ")");
        }
        List<PlanViolation> violaciones = new ArrayList<>();
        Map<String, String> vehiculoPorRuta = new HashMap<>();
        List<ScheduledDeliveryRoute> programadas = new ArrayList<>();
        for (RutaVigente ruta : vista.rutas()) {
            vehiculoPorRuta.put(ruta.ruta().id(), ruta.vehiculoId());
            violaciones.addAll(violacionesDeRuta(ruta, vista.bloqueos()));
            if (ruta.programada() != null) {
                programadas.add(ruta.programada());
            }
        }
        return construir(ejecucionId, ahora, violaciones, vehiculoPorRuta, vista.pedidos().values(),
                vista.entregados(), programadas);
    }

    @Override
    public AuditoriaPlanResponse auditar(OperationalPlan plan, OperationalSnapshot snapshot, Collection<Order> pedidos,
                                         List<RoadBlock> bloqueos, Instant ahora) {
        Objects.requireNonNull(ahora, "ahora es requerido");
        PlanEvaluation evaluacion = evaluador.evaluate(plan, snapshot, pedidos, bloqueos);
        Map<String, String> vehiculoPorRuta = new HashMap<>();
        plan.routes().forEach(ruta -> vehiculoPorRuta.put(ruta.id(), ruta.vehicle().id()));
        return construir(null, ahora, evaluacion.violations(), vehiculoPorRuta, pedidos, Map.of(),
                List.copyOf(evaluacion.schedulesByRouteId().values()));
    }

    /**
     * Cada ruta del plan vigente se evalúa sola con su snapshot de origen y los bloqueos actuales. Se descarta
     * PARTIAL_DELIVERY_MISMATCH porque una ruta puede llevar solo una parte de un pedido; la cobertura total
     * de cada pedido se informa en pedidosNoAtendidos.
     */
    private List<PlanViolation> violacionesDeRuta(RutaVigente ruta, List<RoadBlock> bloqueos) {
        Map<String, Order> pedidos = new LinkedHashMap<>();
        ruta.ruta().stops().forEach(parada -> {
            if (parada instanceof DeliveryStop entrega) {
                pedidos.putIfAbsent(entrega.order().id(), entrega.order());
            }
        });
        PlanEvaluation evaluacion = evaluador.evaluate(OperationalPlan.empty().withRoute(ruta.ruta()),
                ruta.snapshotOrigen(), pedidos.values(), bloqueos);
        return evaluacion.violations().stream()
                .filter(violacion -> violacion.type() != PlanViolationType.PARTIAL_DELIVERY_MISMATCH)
                .toList();
    }

    private AuditoriaPlanResponse construir(Long ejecucionId, Instant ahora, List<PlanViolation> violaciones,
                                            Map<String, String> vehiculoPorRuta, Collection<Order> pedidos,
                                            Map<String, Integer> entregadosPrevios,
                                            List<ScheduledDeliveryRoute> programadas) {
        Map<String, Integer> entregadoHastaAhora = new HashMap<>(entregadosPrevios);
        Map<String, Integer> planificado = new HashMap<>(entregadosPrevios);
        Map<String, Instant> ultimaLlegada = new HashMap<>();
        for (ScheduledDeliveryRoute programada : programadas) {
            for (ScheduledRouteStop parada : programada.scheduledStops()) {
                if (parada.stop() instanceof DeliveryStop entrega) {
                    String id = entrega.order().id();
                    planificado.merge(id, entrega.deliveredPackages(), Integer::sum);
                    if (!parada.completedAt().isAfter(ahora)) {
                        entregadoHastaAhora.merge(id, entrega.deliveredPackages(), Integer::sum);
                    }
                    ultimaLlegada.merge(id, parada.arrivedAt(), (a, b) -> a.isAfter(b) ? a : b);
                }
            }
        }

        List<PedidoAtrasadoResponse> vencidos = new ArrayList<>();
        List<PedidoAtrasadoResponse> atrasados = new ArrayList<>();
        List<String> noAtendidos = new ArrayList<>();
        List<Order> ordenados = pedidos.stream().sorted(Comparator.comparing(Order::id)).toList();
        for (Order pedido : ordenados) {
            int pendientes = Math.max(0, pedido.packages() - entregadoHastaAhora.getOrDefault(pedido.id(), 0));
            Instant llegada = ultimaLlegada.get(pedido.id());
            if (pendientes > 0 && ahora.isAfter(pedido.deadline())) {
                vencidos.add(new PedidoAtrasadoResponse(pedido.id(), pedido.deadline(), llegada,
                        Duration.between(pedido.deadline(), ahora).toMinutes(), pendientes));
            }
            if (llegada != null && llegada.isAfter(pedido.deadline())) {
                atrasados.add(new PedidoAtrasadoResponse(pedido.id(), pedido.deadline(), llegada,
                        Duration.between(pedido.deadline(), llegada).toMinutes(), pendientes));
            }
            if (planificado.getOrDefault(pedido.id(), 0) < pedido.packages()) {
                noAtendidos.add(pedido.id());
            }
        }

        Map<String, Integer> porTipo = new TreeMap<>();
        List<ViolacionDetalleResponse> detalles = new ArrayList<>();
        for (PlanViolation violacion : violaciones) {
            porTipo.merge(violacion.type().name(), 1, Integer::sum);
            detalles.add(new ViolacionDetalleResponse(violacion.type().name(), DescripcionViolacion.de(violacion.type()),
                    violacion.routeId(), violacion.routeId() == null ? null : vehiculoPorRuta.get(violacion.routeId()),
                    violacion.detail()));
        }
        return new AuditoriaPlanResponse(ejecucionId, ahora, violaciones.isEmpty(),
                !vencidos.isEmpty() || !noAtendidos.isEmpty(), violaciones.size(),
                Collections.unmodifiableMap(new LinkedHashMap<>(porTipo)), detalles, vencidos, atrasados,
                noAtendidos);
    }
}
