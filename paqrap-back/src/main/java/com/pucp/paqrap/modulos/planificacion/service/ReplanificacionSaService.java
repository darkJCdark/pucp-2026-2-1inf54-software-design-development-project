package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownResolution;
import com.pucp.paqrap.modulos.incidencias.service.BreakdownAvailabilityCalculator;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolationType;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.dto.MotivoReplanificacion;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.ReplanificacionResponse;
import com.pucp.paqrap.modulos.planificacion.dto.SolicitudPlanificacion;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Replanificación con SA. En cada evento: corta el plan vigente en {@code ahora} ({@link CortePlan}),
 * conserva las rutas en marcha (con un bloqueo, las vuelve a programar con el RouteScheduler original),
 * calcula lo pendiente, ejecuta el SA solo con los vehículos libres y une ambos conjuntos de rutas.
 * El estado de cada ejecución vive en memoria y solo se actualiza si la replanificación termina bien.
 */
@Service
public class ReplanificacionSaService implements ReplanificacionService {

    private final FuenteDatosOperativos fuente;
    private final PlanificadorSaService planificador;
    private final RouteScheduler programador = new RouteScheduler(new RoadNetwork());
    private final OperationalPlanEvaluator evaluador = new OperationalPlanEvaluator(programador);
    private final PlanResponseMapper mapper = new PlanResponseMapper();
    private final Map<Long, EstadoEjecucion> ejecuciones = new ConcurrentHashMap<>();

    @Autowired
    public ReplanificacionSaService(FuenteDatosOperativos fuente, PlanificadorSaService planificador) {
        this.fuente = Objects.requireNonNull(fuente, "fuente es requerida");
        this.planificador = Objects.requireNonNull(planificador, "planificador es requerido");
    }

    // ------------------------------------------------------------------ disparadores

    @Override
    public synchronized ReplanificacionResponse iniciar(long ejecucionId, SolicitudPlanificacion solicitud) {
        Objects.requireNonNull(solicitud, "solicitud es requerida");
        if (ejecuciones.containsKey(ejecucionId)) {
            throw new ReglaNegocioException("La ejecución " + ejecucionId + " ya fue iniciada");
        }
        EstadoEjecucion estado = new EstadoEjecucion(ejecucionId, solicitud.modo(),
                PlanificadorSaService.semillaDe(solicitud), PlanificadorSaService.presupuestoDe(solicitud),
                solicitud.horaPlanificacion());
        Map<String, Order> pedidos = new LinkedHashMap<>();
        for (Order pedido : solicitud.pedidosPendientes()) {
            if (pedidos.put(pedido.id(), pedido) != null) {
                throw new ReglaNegocioException("Pedido repetido en la solicitud: " + pedido.id());
            }
        }
        estado.pedidos = pedidos;
        estado.bloqueos = new ArrayList<>(solicitud.bloqueosActivos());
        ReplanificacionResponse respuesta = replanificar(estado, MotivoReplanificacion.INICIO,
                solicitud.horaPlanificacion(), null, null, null);
        ejecuciones.put(ejecucionId, estado);
        return respuesta;
    }

    @Override
    public synchronized ReplanificacionResponse registrarPedido(long ejecucionId, Order pedido, Instant ahora) {
        Objects.requireNonNull(pedido, "pedido es requerido");
        EstadoEjecucion estado = obtener(ejecucionId);
        validarHora(estado, ahora);
        if (estado.pedidos.containsKey(pedido.id())) {
            throw new ReglaNegocioException("El pedido " + pedido.id() + " ya existe en la ejecución " + ejecucionId);
        }
        return replanificar(estado, MotivoReplanificacion.NUEVO_PEDIDO, ahora, pedido, null, null);
    }

    @Override
    public synchronized ReplanificacionResponse activarBloqueo(long ejecucionId, RoadBlock bloqueo, Instant ahora) {
        Objects.requireNonNull(bloqueo, "bloqueo es requerido");
        EstadoEjecucion estado = obtener(ejecucionId);
        validarHora(estado, ahora);
        Instant inicio = bloqueo.startsAt().isAfter(ahora) ? bloqueo.startsAt() : ahora;
        RoadBlock efectivo = bloqueo.endsAt().isAfter(inicio)
                ? new RoadBlock(inicio, bloqueo.endsAt(), bloqueo.nodes()) : null;
        return replanificar(estado, MotivoReplanificacion.BLOQUEO, ahora, null, efectivo, null);
    }

    @Override
    public synchronized ReplanificacionResponse registrarAveria(long ejecucionId, BreakdownEvent averia, Instant ahora) {
        Objects.requireNonNull(averia, "averia es requerida");
        EstadoEjecucion estado = obtener(ejecucionId);
        validarHora(estado, ahora);
        boolean existe = fuente.flota().stream().anyMatch(vehiculo -> vehiculo.id().equals(averia.vehicleId()));
        if (!existe) {
            throw new ReglaNegocioException("Vehículo desconocido: " + averia.vehicleId());
        }
        return replanificar(estado, MotivoReplanificacion.AVERIA, ahora, null, null, averia);
    }

    @Override
    public Optional<ReplanificacionResponse> planVigente(long ejecucionId) {
        EstadoEjecucion estado = ejecuciones.get(ejecucionId);
        return estado == null ? Optional.empty() : Optional.ofNullable(estado.ultimaRespuesta);
    }

    @Override
    public synchronized void finalizar(long ejecucionId) {
        ejecuciones.remove(ejecucionId);
    }

    /** Solo para pruebas: rutas del plan vigente con sus snapshots de origen. */
    synchronized List<RutaVigente> rutasVigentes(long ejecucionId) {
        return List.copyOf(obtener(ejecucionId).rutas);
    }

    // ------------------------------------------------------------------ replanificación

    private ReplanificacionResponse replanificar(EstadoEjecucion estado, MotivoReplanificacion motivo, Instant ahora,
                                                 Order nuevoPedido, RoadBlock nuevoBloqueo, BreakdownEvent nuevaAveria) {
        // 1. Datos con el evento aplicado (el estado se confirma recién al final).
        Map<String, Order> pedidos = new LinkedHashMap<>(estado.pedidos);
        if (nuevoPedido != null) {
            pedidos.put(nuevoPedido.id(), nuevoPedido);
        }
        List<RoadBlock> bloqueos = new ArrayList<>(estado.bloqueos);
        if (nuevoBloqueo != null) {
            bloqueos.add(nuevoBloqueo);
        }
        List<BreakdownEvent> averias = new ArrayList<>(estado.averias);
        if (nuevaAveria != null) {
            averias.add(nuevaAveria);
        }
        Map<String, VehicleOperationalState> forzados = new HashMap<>(estado.estadosForzados);
        Collection<Warehouse> almacenes = fuente.almacenes();
        Warehouse central = almacenes.stream().filter(Warehouse::isCentral).findFirst()
                .orElseThrow(() -> new IllegalStateException("La fuente de datos no define un almacén central"));

        // 2. Corte del plan vigente en 'ahora'.
        Set<String> interrumpidos = nuevaAveria == null ? Set.of() : Set.of(nuevaAveria.vehicleId());
        CortePlan.Resultado corte = CortePlan.cortar(estado.rutas, ahora, interrumpidos);
        Map<String, Integer> entregados = new HashMap<>(estado.entregados);
        CortePlan.sumar(entregados, corte.entregasSalientes());
        Map<String, Integer> retiros = new HashMap<>(estado.retiros);
        CortePlan.sumar(retiros, corte.retirosSalientes());
        for (RutaVigente interrumpida : corte.interrumpidas()) {
            forzados.put(interrumpida.vehiculoId(), estadoTrasAveria(interrumpida.ruta().vehicle(), nuevaAveria, central));
        }

        // 3. Rutas conservadas; ante un bloqueo se vuelven a programar con el RouteScheduler original.
        List<RutaVigente> conservadas = motivo == MotivoReplanificacion.BLOQUEO
                ? corte.conservadas().stream().map(ruta -> reprogramar(ruta, bloqueos)).toList()
                : corte.conservadas();

        // 4. Pendiente = pedido menos lo entregado y lo que llevan las rutas conservadas.
        Map<String, Integer> cubiertos = new HashMap<>(entregados);
        Map<String, Integer> retirosTotales = new HashMap<>(retiros);
        for (RutaVigente ruta : conservadas) {
            CortePlan.sumar(cubiertos, CortePlan.entregas(ruta, null));
            CortePlan.sumar(retirosTotales, CortePlan.retirosIntermedios(ruta, null));
        }
        List<Order> pendientes = new ArrayList<>();
        for (Order pedido : pedidos.values()) {
            int falta = pedido.packages() - cubiertos.getOrDefault(pedido.id(), 0);
            if (falta > 0) {
                pendientes.add(falta == pedido.packages() ? pedido
                        : new Order(pedido.id(), pedido.destination(), falta, pedido.registeredAt(), pedido.deadline()));
            }
        }

        // 5. SA solo con lo pendiente y los vehículos libres.
        OperationalSnapshot snapshot = construirSnapshot(ahora, central, almacenes, conservadas, forzados, averias,
                retirosTotales);
        int numero = estado.numeroReplanificacion + 1;
        long semilla = estado.semillaBase + numero;
        PlanificadorSaService.EjecucionSa ejecucion =
                planificador.ejecutarSa(snapshot, pendientes, bloqueos, semilla, estado.presupuestoMs);
        ResultadoPlanificacion sa = ejecucion.resultado();

        // 6. Plan vigente = conservadas + nuevas.
        OperationalPlan plan = OperationalPlan.empty();
        Map<String, ScheduledDeliveryRoute> programadas = new LinkedHashMap<>();
        List<PlanViolation> violaciones = new ArrayList<>();
        double costo = 0;
        List<RutaVigente> vigentes = new ArrayList<>();
        for (RutaVigente ruta : conservadas) {
            plan = plan.withRoute(ruta.ruta());
            if (ruta.programada() != null) {
                programadas.put(ruta.ruta().id(), ruta.programada());
                costo += ruta.programada().totalCost();
            }
            violaciones.addAll(ruta.violaciones());
            vigentes.add(ruta);
        }
        List<DeliveryRoute> nuevas = sa.plan().routes().stream()
                .sorted(Comparator.comparing(ruta -> ruta.vehicle().id())).toList();
        for (DeliveryRoute ruta : nuevas) {
            plan = plan.withRoute(ruta);
            ScheduledDeliveryRoute programada = sa.evaluacion().schedulesByRouteId().get(ruta.id());
            if (programada != null) {
                programadas.put(ruta.id(), programada);
            }
            vigentes.add(new RutaVigente(ruta, snapshot, programada, List.of()));
        }
        violaciones.addAll(sa.evaluacion().violations());
        costo += sa.costoTotal();
        ResultadoPlanificacion combinado = new ResultadoPlanificacion(plan,
                new PlanEvaluation(programadas, violaciones, sa.evaluacion().remainingInventory(), costo), sa.noAtendidos());
        PlanResponse planResponse = mapper.aResponse(estado.modo, ahora, combinado, semilla, ejecucion.duracionMs(),
                estado.presupuestoMs);

        Set<String> asignadosAntes = pedidosDe(estado.rutas.stream().map(RutaVigente::ruta).toList());
        List<String> reasignados = pedidosDe(nuevas).stream().filter(asignadosAntes::contains).sorted().toList();
        Set<String> ocupados = new HashSet<>();
        conservadas.forEach(ruta -> ocupados.add(ruta.vehiculoId()));
        List<String> noDisponibles = fuente.flota().stream().map(Vehicle::id)
                .filter(id -> !ocupados.contains(id) && !snapshot.isVehiclePlannableAt(id, ahora))
                .sorted().toList();
        ReplanificacionResponse respuesta = new ReplanificacionResponse(estado.ejecucionId, numero, motivo, ahora,
                planResponse,
                conservadas.stream().map(ruta -> ruta.ruta().id()).sorted().toList(),
                nuevas.stream().map(DeliveryRoute::id).sorted().toList(),
                reasignados, noDisponibles);

        // 7. Confirmación del estado.
        estado.pedidos = pedidos;
        estado.bloqueos = bloqueos;
        estado.averias = averias;
        estado.estadosForzados = forzados;
        estado.entregados = entregados;
        estado.retiros = retiros;
        estado.rutas = List.copyOf(vigentes);
        estado.numeroReplanificacion = numero;
        estado.ultimaHora = ahora;
        estado.ultimaRespuesta = respuesta;
        return respuesta;
    }

    private RutaVigente reprogramar(RutaVigente ruta, List<RoadBlock> bloqueos) {
        try {
            ScheduledDeliveryRoute programada = programador.schedule(ruta.ruta(), ruta.snapshotOrigen(), bloqueos);
            return new RutaVigente(ruta.ruta(), ruta.snapshotOrigen(), programada, violacionesDeRuta(ruta, bloqueos));
        } catch (IllegalStateException sinCamino) {
            return new RutaVigente(ruta.ruta(), ruta.snapshotOrigen(), ruta.programada(),
                    List.of(new PlanViolation(PlanViolationType.NO_ROAD_PATH, ruta.ruta().id(), sinCamino.getMessage())));
        }
    }

    /**
     * Evalúa la ruta sola con el evaluador original y su snapshot de origen. Se descarta
     * PARTIAL_DELIVERY_MISMATCH porque una ruta puede llevar solo una parte de un pedido.
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

    /**
     * Estado del vehículo cuya ruta se interrumpió, según el cálculo original de la avería: si la avería
     * define regreso al central, queda disponible allí desde el mayor entre el fin de la indisponibilidad y
     * ese regreso; si no lo define (MINOR), queda fuera de servicio en el lugar de la avería.
     */
    private VehicleOperationalState estadoTrasAveria(Vehicle vehiculo, BreakdownEvent averia, Warehouse central) {
        BreakdownResolution resolucion =
                new BreakdownAvailabilityCalculator(new ShiftSchedule(ShiftSchedule.DEFAULT_ZONE)).resolve(averia);
        if (resolucion.returnsToCentralAt() == null) {
            return new VehicleOperationalState(vehiculo, VehicleStatus.OUT_OF_SERVICE, averia.location(),
                    averia.occurredAt());
        }
        Instant disponible = resolucion.unavailableUntil().isAfter(resolucion.returnsToCentralAt())
                ? resolucion.unavailableUntil() : resolucion.returnsToCentralAt();
        return new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, central.location(), disponible);
    }

    private OperationalSnapshot construirSnapshot(Instant ahora, Warehouse central, Collection<Warehouse> almacenes,
                                                  List<RutaVigente> conservadas,
                                                  Map<String, VehicleOperationalState> forzados,
                                                  List<BreakdownEvent> averias, Map<String, Integer> retiros) {
        InventorySnapshot inventario = InventorySnapshot.from(almacenes);
        for (Map.Entry<String, Integer> retiro : retiros.entrySet()) {
            int cantidad = Math.min(retiro.getValue(), inventario.availableStock(retiro.getKey()));
            if (cantidad > 0) {
                inventario = inventario.withdraw(retiro.getKey(), cantidad);
            }
        }
        Map<String, Instant> ocupadosHasta = new HashMap<>();
        conservadas.forEach(ruta -> ocupadosHasta.put(ruta.vehiculoId(),
                ruta.programada() == null ? ahora : ruta.programada().completedAt()));
        Map<String, VehicleOperationalState> estados = new LinkedHashMap<>();
        for (Vehicle vehiculo : fuente.flota()) {
            VehicleOperationalState estado;
            if (ocupadosHasta.containsKey(vehiculo.id())) {
                estado = new VehicleOperationalState(vehiculo, VehicleStatus.IN_ROUTE, central.location(), 0,
                        ocupadosHasta.get(vehiculo.id()));
            } else if (forzados.containsKey(vehiculo.id())) {
                estado = forzados.get(vehiculo.id());
            } else {
                estado = new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, central.location(), ahora);
            }
            estados.put(vehiculo.id(), estado);
        }
        return new OperationalSnapshot(ahora, fuente.perfilFlota(), inventario, estados,
                fuente.calendarioMantenimiento(), new ShiftSchedule(ShiftSchedule.DEFAULT_ZONE), averias);
    }

    private static Set<String> pedidosDe(List<DeliveryRoute> rutas) {
        Set<String> ids = new HashSet<>();
        for (DeliveryRoute ruta : rutas) {
            ruta.stops().forEach(parada -> {
                if (parada instanceof DeliveryStop entrega) {
                    ids.add(entrega.order().id());
                }
            });
        }
        return ids;
    }

    private EstadoEjecucion obtener(long ejecucionId) {
        EstadoEjecucion estado = ejecuciones.get(ejecucionId);
        if (estado == null) {
            throw new RecursoNoEncontradoException("Ejecución", ejecucionId);
        }
        return estado;
    }

    private static void validarHora(EstadoEjecucion estado, Instant ahora) {
        Objects.requireNonNull(ahora, "ahora es requerido");
        if (ahora.isBefore(estado.ultimaHora)) {
            throw new ReglaNegocioException("La hora " + ahora + " es anterior a la última replanificación ("
                    + estado.ultimaHora + ")");
        }
    }
}
