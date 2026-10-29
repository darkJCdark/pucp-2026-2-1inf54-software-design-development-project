package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
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
import com.pucp.paqrap.modulos.planificacion.persistence.PlanEntity;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

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
    /** El mismo evaluador del SA, para validar las rutas conservadas con las mismas reglas (incluido el límite por tramo). */
    private final OperationalPlanEvaluator evaluador;
    private final PlanResponseMapper mapper = new PlanResponseMapper();
    private final PlanPersistenceService persistenciaPlanes;
    private final Map<Long, EstadoEjecucion> ejecuciones = new ConcurrentHashMap<>();

    @Autowired
    public ReplanificacionSaService(FuenteDatosOperativos fuente, PlanificadorSaService planificador, PlanPersistenceService persistenciaPlanes) {
        this.fuente = Objects.requireNonNull(fuente, "fuente es requerida");
        this.planificador = Objects.requireNonNull(planificador, "planificador es requerido");
        this.evaluador = planificador.evaluador();
        this.persistenciaPlanes = Objects.requireNonNull(persistenciaPlanes, "persistenciaPlanes es requerida");
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
                solicitud.configuracionSa() != null ? solicitud.configuracionSa() : PlanificadorSaService.configuracionPorDefecto(),
                solicitud.horaPlanificacion());
        Map<String, Order> pedidos = new LinkedHashMap<>();
        for (Order pedido : solicitud.pedidosPendientes()) {
            if (pedidos.put(pedido.id(), pedido) != null) {
                throw new ReglaNegocioException("Pedido repetido en la solicitud: " + pedido.id());
            }
        }
        estado.pedidos = pedidos;
        estado.bloqueos = new ArrayList<>(solicitud.bloqueosActivos());
        estado.bloqueosConocidos = new HashSet<>(solicitud.bloqueosActivos());
        // Igual que el plan sin replanificación: las averías ya registradas cuentan desde el inicio.
        estado.averias = new ArrayList<>(fuente.averias(ejecucionId));
        ReplanificacionResponse respuesta = replanificar(estado, MotivoReplanificacion.INICIO,
                solicitud.horaPlanificacion(), List.of(), List.of(), List.of(), false, Set.of());
        ejecuciones.put(ejecucionId, estado);
        return respuesta;
    }

    @Override
    public synchronized ReplanificacionResponse replanificar(long ejecucionId, Eventos eventos, Instant ahora) {
        Objects.requireNonNull(eventos, "eventos es requerido");
        EstadoEjecucion estado = obtener(ejecucionId);
        validarHora(estado, ahora);
        Map<String, Order> nuevos = new LinkedHashMap<>();
        for (Order pedido : eventos.pedidos()) {
            Objects.requireNonNull(pedido, "pedido es requerido");
            if (estado.pedidos.containsKey(pedido.id()) || nuevos.put(pedido.id(), pedido) != null) {
                throw new ReglaNegocioException("El pedido " + pedido.id() + " ya existe en la ejecución " + ejecucionId);
            }
        }
        // Cada bloqueo nuevo rige desde max(inicio, ahora); uno ya vencido o ya conocido no altera las rutas.
        Set<RoadBlock> recibidos = new LinkedHashSet<>();
        List<RoadBlock> bloqueos = new ArrayList<>();
        for (RoadBlock bloqueo : eventos.bloqueos()) {
            Objects.requireNonNull(bloqueo, "bloqueo es requerido");
            if (estado.bloqueosConocidos.contains(bloqueo) || !recibidos.add(bloqueo)) {
                continue;
            }
            Instant inicio = bloqueo.startsAt().isAfter(ahora) ? bloqueo.startsAt() : ahora;
            if (bloqueo.endsAt().isAfter(inicio)) {
                bloqueos.add(new RoadBlock(inicio, bloqueo.endsAt(), bloqueo.nodes()));
            }
        }
        Set<String> flota = new HashSet<>(fuente.flota().stream().map(Vehicle::id).toList());
        for (BreakdownEvent averia : eventos.averias()) {
            Objects.requireNonNull(averia, "averia es requerida");
            if (!flota.contains(averia.vehicleId())) {
                throw new ReglaNegocioException("Vehículo desconocido: " + averia.vehicleId());
            }
        }
        // Los bloqueos futuros del horizonte no son un evento: solo cuenta uno que empezó desde la replanificación
        // anterior o uno nuevo que ya empezó.
        boolean bloqueoIniciado = eventos.bloqueos().stream().anyMatch(bloqueo -> !bloqueo.startsAt().isAfter(ahora)
                && (bloqueo.startsAt().isAfter(estado.ultimaHora) || recibidos.contains(bloqueo)));
        boolean bloqueoTerminado = eventos.bloqueosTerminados().stream().anyMatch(bloqueo ->
                !bloqueo.endsAt().isAfter(ahora) && bloqueo.endsAt().isAfter(estado.ultimaHora));
        MotivoReplanificacion motivo = !eventos.averias().isEmpty() || !eventos.averiasTerminadas().isEmpty()
                ? MotivoReplanificacion.AVERIA
                : bloqueoIniciado || bloqueoTerminado ? MotivoReplanificacion.BLOQUEO
                : !eventos.pedidos().isEmpty() ? MotivoReplanificacion.NUEVO_PEDIDO
                : MotivoReplanificacion.PERIODICO;
        ReplanificacionResponse respuesta = replanificar(estado, motivo, ahora, List.copyOf(nuevos.values()),
                bloqueos, eventos.averias(), bloqueoIniciado || bloqueoTerminado,
                Set.copyOf(eventos.tiposFlotaActualizados()));
        estado.bloqueosConocidos.addAll(recibidos);
        return respuesta;
    }

    @Override
    public boolean conocePedido(long ejecucionId, String pedidoId) {
        EstadoEjecucion estado = ejecuciones.get(ejecucionId);
        return estado != null && estado.pedidos.containsKey(pedidoId);
    }

    @Override
    public boolean estaIniciada(long ejecucionId) {
        return ejecuciones.containsKey(ejecucionId);
    }

    @Override
    public Optional<ReplanificacionResponse> planVigente(long ejecucionId) {
        EstadoEjecucion estado = ejecuciones.get(ejecucionId);
        return estado == null ? Optional.empty() : Optional.ofNullable(estado.ultimaRespuesta);
    }

    @Override
    public Optional<MetricasPlanificacion> metricas(long ejecucionId) {
        EstadoEjecucion estado = ejecuciones.get(ejecucionId);
        return estado == null ? Optional.empty()
                : Optional.of(new MetricasPlanificacion(estado.totalComputationMs, estado.numeroReplanificacion));
    }

    @Override
    public synchronized void finalizar(long ejecucionId) {
        ejecuciones.remove(ejecucionId);
    }

    /** Solo para pruebas: rutas del plan vigente con sus snapshots de origen. */
    synchronized List<RutaVigente> rutasVigentes(long ejecucionId) {
        return List.copyOf(obtener(ejecucionId).rutas);
    }

    /** Solo para pruebas: bloqueos con los que planifica la ejecución. */
    synchronized List<RoadBlock> bloqueosVigentes(long ejecucionId) {
        return List.copyOf(obtener(ejecucionId).bloqueos);
    }

    // ------------------------------------------------------------------ replanificación

    private ReplanificacionResponse replanificar(EstadoEjecucion estado, MotivoReplanificacion motivo, Instant ahora,
                                                 List<Order> nuevosPedidos, List<RoadBlock> nuevosBloqueos,
                                                 List<BreakdownEvent> nuevasAverias, boolean cambioBloqueo,
                                                 Set<VehicleType> tiposFlotaActualizados) {
        // 1. Datos con los eventos aplicados (el estado se confirma recién al final).
        Map<String, Order> pedidos = new LinkedHashMap<>(estado.pedidos);
        nuevosPedidos.forEach(pedido -> pedidos.put(pedido.id(), pedido));
        List<RoadBlock> bloqueos = new ArrayList<>(estado.bloqueos);
        bloqueos.addAll(nuevosBloqueos);
        List<BreakdownEvent> averias = new ArrayList<>(estado.averias);
        averias.addAll(nuevasAverias);
        Map<String, VehicleOperationalState> forzados = new HashMap<>(estado.estadosForzados);
        Collection<Warehouse> almacenes = fuente.almacenes();
        Warehouse central = almacenes.stream().filter(Warehouse::isCentral).findFirst()
                .orElseThrow(() -> new IllegalStateException("La fuente de datos no define un almacén central"));

        // 2. Corte del plan vigente en 'ahora'; la ruta de un vehículo averiado se corta en el instante de la avería.
        Map<String, BreakdownEvent> averiaPorVehiculo = new HashMap<>();
        nuevasAverias.forEach(averia -> averiaPorVehiculo.merge(averia.vehicleId(), averia,
                (a, b) -> a.occurredAt().isAfter(b.occurredAt()) ? b : a));
        Map<String, Instant> interrumpidos = new HashMap<>();
        averiaPorVehiculo.forEach((vehiculo, averia) -> interrumpidos.put(vehiculo, averia.occurredAt()));
        CortePlan.Resultado corte = CortePlan.cortar(estado.rutas, ahora, interrumpidos);
        Map<String, Integer> entregados = new HashMap<>(estado.entregados);
        CortePlan.sumar(entregados, corte.entregasSalientes());
        Map<String, Integer> retiros = new HashMap<>(estado.retiros);
        CortePlan.sumar(retiros, corte.retirosSalientes());
        for (RutaVigente interrumpida : corte.interrumpidas()) {
            forzados.put(interrumpida.vehiculoId(), estadoTrasAveria(interrumpida.ruta().vehicle(),
                    averiaPorVehiculo.get(interrumpida.vehiculoId()), central));
        }

        // 3. Conserved routes are rescheduled when their road path or fleet parameters change.
        List<RutaVigente> conservadas = corte.conservadas().stream()
                .map(ruta -> cambioBloqueo || tiposFlotaActualizados.contains(ruta.ruta().vehicle().type())
                        ? reprogramar(ruta, bloqueos, !tiposFlotaActualizados.isEmpty()) : ruta)
                .toList();

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
                planificador.ejecutarSa(snapshot, pendientes, bloqueos, estado.configuracionSa, semilla, estado.presupuestoMs);
        estado.totalComputationMs += ejecucion.duracionMs();
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
        PlanEntity snapshotPersistido = persistenciaPlanes.persistir(estado.ejecucionId, motivo, plan, programadas,
                planResponse);
        if (snapshotPersistido != null) { // Los dobles de prueba históricos no persisten snapshots.
            planResponse = mapper.conIdentidadesPersistidas(planResponse,
                    persistenciaPlanes.rutasPersistidasPorOperacion(snapshotPersistido.getPlanId()),
                    snapshotPersistido.getPlanId());
        }
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

    private RutaVigente reprogramar(RutaVigente ruta, List<RoadBlock> bloqueos, boolean perfilFlotaActualizado) {
        OperationalSnapshot snapshot = perfilFlotaActualizado ? conPerfilFlotaActual(ruta.snapshotOrigen())
                : ruta.snapshotOrigen();
        try {
            ScheduledDeliveryRoute programada = programador.schedule(ruta.ruta(), snapshot, bloqueos);
            return new RutaVigente(ruta.ruta(), snapshot, programada, violacionesDeRuta(ruta, bloqueos, snapshot));
        } catch (IllegalStateException sinCamino) {
            return new RutaVigente(ruta.ruta(), snapshot, ruta.programada(),
                    List.of(new PlanViolation(PlanViolationType.NO_ROAD_PATH, ruta.ruta().id(), sinCamino.getMessage())));
        }
    }

    private OperationalSnapshot conPerfilFlotaActual(OperationalSnapshot origen) {
        return new OperationalSnapshot(origen.planningTime(), fuente.perfilFlota(), origen.inventory(),
                origen.vehiclesById(), origen.maintenanceCalendar(), origen.shiftSchedule(), origen.breakdowns());
    }

    /**
     * Evalúa la ruta sola con el evaluador original y su snapshot de origen. Se descarta
     * PARTIAL_DELIVERY_MISMATCH porque una ruta puede llevar solo una parte de un pedido.
     */
    private List<PlanViolation> violacionesDeRuta(RutaVigente ruta, List<RoadBlock> bloqueos,
                                                   OperationalSnapshot snapshot) {
        Map<String, Order> pedidos = new LinkedHashMap<>();
        ruta.ruta().stops().forEach(parada -> {
            if (parada instanceof DeliveryStop entrega) {
                pedidos.putIfAbsent(entrega.order().id(), entrega.order());
            }
        });
        PlanEvaluation evaluacion = evaluador.evaluate(OperationalPlan.empty().withRoute(ruta.ruta()),
                snapshot, pedidos.values(), bloqueos);
        return evaluacion.violations().stream()
                .filter(violacion -> violacion.type() != PlanViolationType.PARTIAL_DELIVERY_MISMATCH)
                .toList();
    }

    /**
     * Estado del vehículo después de interrumpir una ruta por avería.
     *
     * <p>Una MINOR permanece en el lugar de la avería y vuelve a ser planificable al finalizar sus dos horas.
     * Las INTERMEDIATE y MAJOR quedan en el central desde el instante en que terminan tanto la indisponibilidad
     * como el retorno automático.
     */
    private VehicleOperationalState estadoTrasAveria(Vehicle vehiculo, BreakdownEvent averia, Warehouse central) {
        BreakdownResolution resolucion =
                new BreakdownAvailabilityCalculator(new ShiftSchedule(ShiftSchedule.DEFAULT_ZONE)).resolve(averia);
        return new VehicleOperationalState(
                vehiculo,
                VehicleStatus.AVAILABLE,
                resolucion.returnsToCentralAt() == null ? averia.location() : central.location(),
                resolucion.availableFrom());
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
