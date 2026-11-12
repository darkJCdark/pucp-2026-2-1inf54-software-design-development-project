package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
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
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.dto.MotivoReplanificacion;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.ReplanificacionResponse;
import com.pucp.paqrap.modulos.planificacion.dto.SolicitudPlanificacion;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.entity.TransferStop;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.planificacion.persistence.PlanEntity;
import com.pucp.paqrap.modulos.redvial.entity.Location;
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
    /** Una avería deja de importar al planificador cuando pasó su indisponibilidad más larga (MAJOR: 2 días + 4 h). */
    private static final Duration VIGENCIA_AVERIA = Duration.ofDays(3);
    /** Ruta de una unidad que, de regreso al central, se desvía para hacer un trasvase. */
    static final String SUFIJO_DESVIO = "-TRASV";
    /** Inserción en rutas en marcha; {@code -Dpaqrap.insercion=false} la desactiva (para medir su efecto). */
    static final boolean INSERTAR_EN_MARCHA =
            Boolean.parseBoolean(System.getProperty("paqrap.insercion", "true"));
    /**
     * Holgura bajo la cual un pedido a bordo de una unidad averiada se trasvasa (Semana 13). Propiedad
     * {@code paqrap.trasvase.margenMin}: minutos (60 por defecto), negativo = trasvasar siempre, "off" = sin trasvase.
     */
    static final Duration MARGEN_TRASVASE = margenTrasvase(System.getProperty("paqrap.trasvase.margenMin", "60"));

    private static Duration margenTrasvase(String valor) {
        return "off".equals(valor) ? null : Duration.ofMinutes(Long.parseLong(valor));
    }

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
                solicitud.horaPlanificacion(), List.of(), List.of(), List.of(), List.of(), false, Set.of());
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
                bloqueos, eventos.averias(), eventos.averiasTerminadas(), bloqueoIniciado || bloqueoTerminado,
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
                                                 List<BreakdownEvent> nuevasAverias, List<BreakdownEvent> averiasTerminadas,
                                                 boolean cambioBloqueo,
                                                 Set<VehicleType> tiposFlotaActualizados) {
        // 1. Datos con los eventos aplicados (el estado se confirma recién al final).
        Map<String, Order> pedidos = new LinkedHashMap<>(estado.pedidos);
        nuevosPedidos.forEach(pedido -> pedidos.put(pedido.id(), pedido));
        // Lo vencido ya no afecta a ningún plan: sin podarlo, las listas crecen durante meses y cada camino mínimo
        // y cada consulta de disponibilidad las recorre completas.
        List<RoadBlock> bloqueos = new ArrayList<>(estado.bloqueos);
        bloqueos.addAll(nuevosBloqueos);
        bloqueos.removeIf(bloqueo -> !bloqueo.endsAt().isAfter(ahora));
        List<BreakdownEvent> averias = new ArrayList<>(estado.averias);
        averias.addAll(nuevasAverias);
        averias.removeIf(averia -> averia.occurredAt().plus(VIGENCIA_AVERIA).isBefore(ahora));
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
        // Los almacenes intermedios se recargan cada día a las 23:59:59: lo retirado antes ya no descuenta stock.
        Map<String, Integer> retiros = huboRecarga(estado.ultimaHora, ahora) ? new HashMap<>()
                : new HashMap<>(estado.retiros);
        CortePlan.sumar(retiros, corte.retirosSalientes());
        for (RutaVigente interrumpida : corte.interrumpidas()) {
            forzados.put(interrumpida.vehiculoId(), estadoTrasAveria(interrumpida.ruta().vehicle(),
                    averiaPorVehiculo.get(interrumpida.vehiculoId()), central));
        }

        // 3. Conserved routes are rescheduled when their road path or fleet parameters change.
        List<RutaVigente> conservadas = new ArrayList<>(corte.conservadas().stream()
                .map(ruta -> cambioBloqueo || tiposFlotaActualizados.contains(ruta.ruta().vehicle().type())
                        ? reprogramar(ruta, bloqueos, !tiposFlotaActualizados.isEmpty()) : ruta)
                .map(ruta -> liberarViajesSiIncumple(ruta, ahora, bloqueos))
                .toList());
        agregarContinuaciones(corte.interrumpidas(), averiaPorVehiculo, ahora, central, almacenes, conservadas, forzados,
                averias, retiros, bloqueos);
        agregarTrasvases(corte.interrumpidas(), averiaPorVehiculo, ahora, central, almacenes, conservadas, forzados,
                averias, retiros, bloqueos, entregados);
        agregarRegresosAlCentral(averiasTerminadas, ahora, central, almacenes, conservadas, forzados, averias, retiros,
                bloqueos);

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
        Map<String, Integer> conservadaPorVehiculo = new HashMap<>();
        for (int i = 0; i < vigentes.size(); i++) {
            conservadaPorVehiculo.put(vigentes.get(i).vehiculoId(), i);
        }
        Map<String, Order> sinEncadenar = new LinkedHashMap<>();
        for (DeliveryRoute ruta : nuevas) {
            Integer indice = conservadaPorVehiculo.get(ruta.vehicle().id());
            if (indice != null) {
                // Despacho diferido: el SA usó un vehículo que aún está en ruta desde su regreso al central.
                RutaVigente base = vigentes.get(indice);
                RutaVigente unida = encadenar(base, ruta, bloqueos);
                if (unida == null) {
                    pedidosConPaquetes(ruta).forEach(pedido -> sinEncadenar.merge(pedido.id(), pedido,
                            (a, b) -> conPaquetes(a, a.packages() + b.packages())));
                    continue;
                }
                vigentes.set(indice, unida);
                plan = plan.withRoute(unida.ruta());
                programadas.put(unida.ruta().id(), unida.programada());
                costo += unida.programada().totalCost()
                        - (base.programada() == null ? 0 : base.programada().totalCost());
                continue;
            }
            plan = plan.withRoute(ruta);
            ScheduledDeliveryRoute programada = sa.evaluacion().schedulesByRouteId().get(ruta.id());
            if (programada != null) {
                programadas.put(ruta.id(), programada);
            }
            vigentes.add(new RutaVigente(ruta, snapshot, programada, List.of()));
        }
        violaciones.addAll(sa.evaluacion().violations());
        costo += sa.costoTotal();
        List<Order> noAtendidos = new ArrayList<>(sa.noAtendidos());
        for (Order pedido : sinEncadenar.values()) {
            int previo = noAtendidos.stream().filter(o -> o.id().equals(pedido.id())).mapToInt(Order::packages).sum();
            noAtendidos.removeIf(o -> o.id().equals(pedido.id()));
            noAtendidos.add(conPaquetes(pedido, previo + pedido.packages()));
        }
        // Lo que el SA no pudo atender se intenta meter en rutas que ya están en la calle (Semana 13).
        for (RutaVigente[] cambio : insertarEnRutasEnMarcha(noAtendidos, vigentes, conservadas.size(), snapshot, ahora,
                bloqueos)) {
            plan = plan.withRoute(cambio[1].ruta());
            programadas.remove(cambio[0].ruta().id());
            programadas.put(cambio[1].ruta().id(), cambio[1].programada());
            costo += cambio[1].programada().totalCost()
                    - (cambio[0].programada() == null ? 0 : cambio[0].programada().totalCost());
        }
        ResultadoPlanificacion combinado = new ResultadoPlanificacion(plan,
                new PlanEvaluation(programadas, violaciones, sa.evaluacion().remainingInventory(), costo), noAtendidos);
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

    /**
     * Indicación del profesor: una avería menor se supera pronto y la unidad no tiene por qué volver al central. Pasadas
     * las 2 horas continúa desde donde quedó con su carga y las paradas que le faltaban. Si ya no llegaría a tiempo a
     * algún pedido, sus pedidos se reasignan a otras unidades.
     */
    private void agregarContinuaciones(List<RutaVigente> interrumpidas, Map<String, BreakdownEvent> averiaPorVehiculo,
                                       Instant ahora, Warehouse central, Collection<Warehouse> almacenes,
                                       List<RutaVigente> conservadas, Map<String, VehicleOperationalState> forzados,
                                       List<BreakdownEvent> averias, Map<String, Integer> retiros,
                                       List<RoadBlock> bloqueos) {
        for (RutaVigente interrumpida : interrumpidas) {
            BreakdownEvent averia = averiaPorVehiculo.get(interrumpida.vehiculoId());
            if (averia.type() != BreakdownType.MINOR || interrumpida.programada() == null) {
                continue;
            }
            List<ScheduledRouteStop> faltantes = interrumpida.programada().scheduledStops().stream()
                    .filter(parada -> !parada.arrivedAt().isBefore(averia.occurredAt()))
                    .toList();
            if (faltantes.stream().noneMatch(parada -> parada.stop() instanceof DeliveryStop)) {
                continue;
            }
            Instant reanuda = averia.occurredAt().plus(BreakdownType.MINOR.minimumUnavailable());
            DeliveryRoute continuacion = DeliveryRoute.replanFromCurrentLocation(
                    interrumpida.ruta().id() + CortePlan.SUFIJO_CONTINUACION, interrumpida.ruta().vehicle(),
                    averia.location(), faltantes.getFirst().loadBefore(), reanuda.isAfter(ahora) ? reanuda : ahora)
                    .withReplacedStops(faltantes.stream().map(ScheduledRouteStop::stop).toList());
            // La continuación parte donde quedó la unidad, con su carga, cuando termina la avería.
            Map<String, VehicleOperationalState> sinDetencion = new HashMap<>(forzados);
            sinDetencion.put(interrumpida.vehiculoId(), new VehicleOperationalState(interrumpida.ruta().vehicle(),
                    VehicleStatus.AVAILABLE, averia.location(), faltantes.getFirst().loadBefore(), continuacion.departureAt()));
            OperationalSnapshot snapshot = construirSnapshot(ahora, central, almacenes, conservadas, sinDetencion,
                    averias, retiros);
            RutaVigente vigente = reprogramar(new RutaVigente(continuacion, snapshot, null, List.of()), bloqueos, false);
            if (vigente.programada() != null && llegaATiempo(vigente)) {
                forzados.remove(interrumpida.vehiculoId());
                conservadas.add(vigente);
            }
        }
    }

    /**
     * Inserción en rutas en marcha (Semana 13): un pedido que el SA dejó sin atender se suma como parada futura de una
     * unidad que ya está en la calle, sin que vuelva al central. Como la unidad no lleva sus paquetes, la inserción es
     * "recargar en un almacén intermedio con stock y entregar": {@code ... → INTERMEDIO (recarga) → PEDIDO → ...}.
     *
     * <p>Reglas: solo entre paradas cuyo tramo aún no empezó (no se toca lo hecho ni lo que está en curso); ningún
     * pedido de la ruta puede quedar tarde; capacidad, refrigerio y bloqueos los valida el evaluador; se reserva el
     * stock del intermedio. Un pedido grande puede repartirse en varias rutas. Entre todas las opciones se elige la que
     * menos alarga el recorrido. Se recarga solo en intermedios: recargar en el central es volver y empezar otro
     * viaje, y eso ya lo cubre el despacho diferido.
     *
     * @return pares [anterior, nueva] de las rutas modificadas (también se reemplazan en {@code vigentes})
     */
    private List<RutaVigente[]> insertarEnRutasEnMarcha(List<Order> noAtendidos, List<RutaVigente> vigentes,
                                                         int enMarcha, OperationalSnapshot snapshot, Instant ahora,
                                                         List<RoadBlock> bloqueos) {
        List<RutaVigente[]> cambios = new ArrayList<>();
        if (!INSERTAR_EN_MARCHA || noAtendidos.isEmpty() || enMarcha == 0) {
            return cambios;
        }
        Map<String, Integer> reservado = new HashMap<>();
        Map<String, RutaVigente> originales = new HashMap<>();
        List<Order> ordenados = new ArrayList<>(noAtendidos);
        ordenados.sort(Comparator.comparing(Order::deadline).thenComparing(Order::id));
        for (Order pedido : ordenados) {
            if (!pedido.deadline().isAfter(ahora)) {
                continue;
            }
            int falta = pedido.packages();
            while (falta > 0) {
                Insercion mejor = null;
                for (int i = 0; i < enMarcha; i++) {
                    Insercion candidata = mejorInsercion(vigentes.get(i), i, pedido, falta, snapshot, reservado,
                            ahora, bloqueos);
                    if (candidata != null && (mejor == null || candidata.extraKm() < mejor.extraKm())) {
                        mejor = candidata;
                    }
                }
                if (mejor == null) {
                    break;
                }
                originales.putIfAbsent(mejor.ruta().ruta().id(), vigentes.get(mejor.indice()));
                vigentes.set(mejor.indice(), mejor.ruta());
                reservado.merge(mejor.almacen().id(), mejor.paquetes(), Integer::sum);
                falta -= mejor.paquetes();
            }
            int cubiertos = pedido.packages() - falta;
            if (cubiertos > 0) {
                noAtendidos.removeIf(o -> o.id().equals(pedido.id()));
                if (falta > 0) {
                    noAtendidos.add(conPaquetes(pedido, falta));
                }
            }
        }
        for (Map.Entry<String, RutaVigente> original : originales.entrySet()) {
            vigentes.stream().filter(r -> r.ruta().id().equals(original.getKey())).findFirst()
                    .ifPresent(nueva -> cambios.add(new RutaVigente[]{original.getValue(), nueva}));
        }
        return cambios;
    }

    /** La mejor forma de sumar hasta {@code falta} paquetes del pedido a una ruta en marcha, o null. */
    private Insercion mejorInsercion(RutaVigente vigente, int indice, Order pedido, int falta,
                                     OperationalSnapshot snapshot, Map<String, Integer> reservado, Instant ahora,
                                     List<RoadBlock> bloqueos) {
        DeliveryRoute ruta = vigente.ruta();
        ScheduledDeliveryRoute programada = vigente.programada();
        if (programada == null || !vigente.violaciones().isEmpty()
                || ruta.stops().stream().anyMatch(TransferStop.class::isInstance)) {
            return null;
        }
        var parametros = snapshot.fleetProfile().parametersFor(ruta.vehicle().type());
        List<ScheduledRouteStop> paradas = programada.scheduledStops();
        List<Insercion> opciones = new ArrayList<>();
        for (int pos = 0; pos < paradas.size(); pos++) {
            Instant saleHacia = pos == 0 ? ruta.departureAt() : paradas.get(pos - 1).completedAt();
            if (saleHacia.isBefore(ahora)) {
                continue; // ese tramo ya empezó
            }
            Location desde = pos == 0 ? ruta.startLocation() : paradas.get(pos - 1).stop().location();
            int cargaAhi = pos == 0 ? ruta.initialLoad() : paradas.get(pos - 1).loadAfter();
            int paquetes = Math.min(falta, parametros.capacity() - cargaAhi);
            if (paquetes <= 0) {
                continue;
            }
            for (Warehouse almacen : snapshot.inventory().warehouses()) {
                if (almacen.isCentral() || snapshot.inventory().availableStock(almacen.id())
                        - reservado.getOrDefault(almacen.id(), 0) < paquetes) {
                    continue;
                }
                int ida = manhattan(desde, almacen.location()) + manhattan(almacen.location(), pedido.destination());
                // Cota optimista (sale apenas termina el servicio, sin refrigerio, en línea recta): si ni así llega
                // a tiempo, no vale la pena programarla.
                Instant cota = salidaMasTemprana(paradas, pos, ruta).plusSeconds(
                        Math.round(ida * 3600.0 / parametros.speedKmPerHour()));
                if (cota.isAfter(pedido.deadline())) {
                    continue;
                }
                Location hacia = paradas.get(pos).stop().location();
                int extra = ida + manhattan(pedido.destination(), hacia) - manhattan(desde, hacia);
                opciones.add(new Insercion(null, indice, almacen, paquetes, extra, pos));
            }
        }
        opciones.sort(Comparator.comparingInt(Insercion::extraKm));
        for (Insercion opcion : opciones) {
            List<RouteStop> stops = new ArrayList<>(ruta.stops());
            stops.add(opcion.posicion(), new DeliveryStop(pedido, opcion.paquetes()));
            stops.add(opcion.posicion(), new WarehouseVisit(opcion.almacen(), opcion.paquetes()));
            RutaVigente nueva = reprogramar(new RutaVigente(ruta.withReplacedStops(stops), vigente.snapshotOrigen(),
                    null, List.of()), bloqueos, false);
            if (nueva.programada() != null && nueva.violaciones().isEmpty() && llegaATiempo(nueva)) {
                return new Insercion(nueva, indice, opcion.almacen(), opcion.paquetes(), opcion.extraKm(),
                        opcion.posicion());
            }
        }
        return null;
    }

    /** Hora más temprana en que la unidad puede salir hacia la parada {@code pos}: sin contar el refrigerio. */
    private static Instant salidaMasTemprana(List<ScheduledRouteStop> paradas, int pos, DeliveryRoute ruta) {
        if (pos == 0) {
            return ruta.departureAt();
        }
        ScheduledRouteStop previa = paradas.get(pos - 1);
        return switch (previa.stop()) {
            case DeliveryStop entrega -> previa.arrivedAt().plus(DeliveryStop.SERVICE_TIME);
            case TransferStop trasvase -> previa.arrivedAt().plus(TransferStop.TRANSFER_TIME);
            case WarehouseVisit visita -> previa.arrivedAt();
        };
    }

    private static int manhattan(Location a, Location b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
    }

    private record Insercion(RutaVigente ruta, int indice, Warehouse almacen, int paquetes, int extraKm,
                             int posicion) {
    }

    /**
     * Une a una ruta en marcha el viaje que el SA le asignó para cuando vuelva al central: la ruta conserva su id y
     * su salida (el ejecutor la sigue como la misma ruta) y el retiro del central pasa a ser la carga del viaje nuevo.
     * Devuelve null si la unión ya no llega a tiempo o no es válida; entonces esos pedidos quedan sin atender.
     */
    private RutaVigente encadenar(RutaVigente base, DeliveryRoute viaje, List<RoadBlock> bloqueos) {
        List<RouteStop> paradas = new ArrayList<>(base.ruta().stops());
        if (base.programada() == null || paradas.isEmpty()
                || !(paradas.getLast() instanceof WarehouseVisit regreso)
                || !viaje.startLocation().equals(regreso.location())) {
            return null;
        }
        paradas.set(paradas.size() - 1, new WarehouseVisit(regreso.warehouse(), viaje.initialLoad()));
        paradas.addAll(viaje.stops());
        RutaVigente unida = reprogramar(new RutaVigente(base.ruta().withReplacedStops(paradas), base.snapshotOrigen(),
                null, List.of()), bloqueos, false);
        return unida.programada() != null && unida.violaciones().isEmpty() && llegaATiempo(unida) ? unida : null;
    }

    private static List<Order> pedidosConPaquetes(DeliveryRoute ruta) {
        Map<String, Order> pedidos = new LinkedHashMap<>();
        ruta.stops().forEach(parada -> {
            if (parada instanceof DeliveryStop entrega) {
                Order parcial = conPaquetes(entrega.order(), entrega.deliveredPackages());
                pedidos.merge(parcial.id(), parcial, (a, b) -> conPaquetes(a, a.packages() + b.packages()));
            }
        });
        return List.copyOf(pedidos.values());
    }

    private static Order conPaquetes(Order pedido, int paquetes) {
        return new Order(pedido.id(), pedido.destination(), paquetes, pedido.registeredAt(), pedido.deadline());
    }

    /** True si entre {@code desde} (excluido) y {@code hasta} (incluido) hubo una recarga diaria de 23:59:59. */
    static boolean huboRecarga(Instant desde, Instant hasta) {
        LocalDate dia = desde.atZone(ShiftSchedule.DEFAULT_ZONE).toLocalDate();
        Instant recarga = dia.atTime(23, 59, 59).atZone(ShiftSchedule.DEFAULT_ZONE).toInstant();
        if (!recarga.isAfter(desde)) {
            recarga = dia.plusDays(1).atTime(23, 59, 59).atZone(ShiftSchedule.DEFAULT_ZONE).toInstant();
        }
        return !recarga.isAfter(hasta);
    }

    /**
     * Trasvase (indicación del profesor): cuando una unidad se avería con pedidos a bordo, otra unidad va al lugar de la
     * avería, recibe los paquetes (30 min) y los entrega; lo que no se trasvasa vuelve al central y se replanifica.
     * Rescata una unidad que ya está en la calle: la que atiende su última entrega (en vez de volver), la que ya
     * vuelve vacía (se desvía) o una en mitad de su ruta (el trasvase se intercala y luego sigue con lo suyo).
     * Solo se trasvasa si para algún pedido a bordo conviene ({@link #conviene}): la unidad de rescate llega antes que un
     * despacho nuevo desde el almacén más cercano, o ese despacho quedaría sin holgura.
     */
    private void agregarTrasvases(List<RutaVigente> interrumpidas, Map<String, BreakdownEvent> averiaPorVehiculo,
                                  Instant ahora, Warehouse central, Collection<Warehouse> almacenes,
                                  List<RutaVigente> conservadas, Map<String, VehicleOperationalState> forzados,
                                  List<BreakdownEvent> averias, Map<String, Integer> retiros, List<RoadBlock> bloqueos,
                                  Map<String, Integer> entregados) {
        if (MARGEN_TRASVASE == null) {
            return;
        }
        for (RutaVigente interrumpida : interrumpidas) {
            String averiado = interrumpida.vehiculoId();
            BreakdownEvent averia = averiaPorVehiculo.get(averiado);
            if (averia == null || interrumpida.programada() == null
                    || conservadas.stream().anyMatch(ruta -> ruta.vehiculoId().equals(averiado))) {
                continue; // la unidad siguió su ruta por sí misma (avería menor)
            }
            List<DeliveryStop> aBordo = new ArrayList<>();
            for (ScheduledRouteStop parada : interrumpida.programada().scheduledStops()) {
                if (parada.arrivedAt().isBefore(averia.occurredAt())) {
                    continue;
                }
                if (!(parada.stop() instanceof DeliveryStop entrega)) {
                    break; // lo que sigue a un almacén aún no estaba cargado
                }
                aBordo.add(entrega);
            }
            if (aBordo.isEmpty()) {
                continue;
            }
            OperationalSnapshot snapshot = construirSnapshot(ahora, central, almacenes, conservadas, forzados, averias,
                    retiros);
            Map<DeliveryStop, Instant> directa = new HashMap<>();
            aBordo.forEach(entrega -> directa.put(entrega, llegadaDirecta(entrega, ahora, snapshot, averiado)));
            // Con stock ilimitado en el central, una unidad que sale del central llega antes yendo directo al cliente
            // que pasando por la avería: el trasvase solo conviene con unidades que ya están en la calle.
            Rescate mejor = null;
            for (int i = 0; i < conservadas.size(); i++) {
                RutaVigente enMarcha = conservadas.get(i);
                List<ScheduledRouteStop> paradas = enMarcha.programada() == null ? List.of()
                        : enMarcha.programada().scheduledStops();
                int ultima = paradas.size() - 2;
                if (ultima < 0 || !(paradas.get(ultima).stop() instanceof DeliveryStop)
                        || !(paradas.getLast().stop() instanceof WarehouseVisit regreso) || !regreso.warehouse().isCentral()
                        || paradas.getLast().loadBefore() != 0
                        || enMarcha.ruta().stops().stream().anyMatch(TransferStop.class::isInstance)
                        || !paradas.getLast().arrivedAt().isAfter(ahora)) {
                    continue;
                }
                if (!paradas.get(ultima).completedAt().isBefore(ahora)) {
                    // Atiende su última entrega: al terminarla va a la avería en vez de volver al central.
                    mejor = mejorRescate(mejor, rescate(enMarcha.ruta(), enMarcha.ruta().stops().subList(0, ultima + 1),
                            List.of(new WarehouseVisit(central, 0)), 0, i, averia, aBordo, enMarcha.snapshotOrigen(),
                            bloqueos, directa));
                } else {
                    // Ya vuelve vacía al central: se desvía desde el nodo por el que va.
                    Location donde = nodoEn(paradas.getLast(), ahora);
                    DeliveryRoute desvio = DeliveryRoute.replanFromCurrentLocation(
                            enMarcha.ruta().id() + SUFIJO_DESVIO, enMarcha.ruta().vehicle(), donde, 0, ahora);
                    mejor = mejorRescate(mejor, rescate(desvio, List.of(), List.of(new WarehouseVisit(central, 0)), 0, i,
                            averia, aBordo, snapshotDesvio(enMarcha.snapshotOrigen(), enMarcha.ruta().vehicle(), donde,
                                    ahora), bloqueos, directa));
                }
            }
            // Unidades en mitad de su ruta: el trasvase se inserta entre dos paradas cuyo tramo aún no empezó y la
            // unidad sigue después con sus propias entregas (todas deben seguir llegando a tiempo).
            for (int i = 0; i < conservadas.size(); i++) {
                RutaVigente enMarcha = conservadas.get(i);
                if (enMarcha.programada() == null || !enMarcha.violaciones().isEmpty()
                        || enMarcha.vehiculoId().equals(averiado)
                        || enMarcha.ruta().stops().stream().anyMatch(TransferStop.class::isInstance)) {
                    continue;
                }
                DeliveryRoute ruta = enMarcha.ruta();
                List<ScheduledRouteStop> paradas = enMarcha.programada().scheduledStops();
                double velocidad = snapshot.fleetProfile().parametersFor(ruta.vehicle().type()).speedKmPerHour();
                for (int pos = 0; pos < paradas.size() - 1; pos++) {
                    Instant saleHacia = pos == 0 ? ruta.departureAt() : paradas.get(pos - 1).completedAt();
                    if (saleHacia.isBefore(ahora)) {
                        continue; // ese tramo ya empezó
                    }
                    Location desde = pos == 0 ? ruta.startLocation() : paradas.get(pos - 1).stop().location();
                    if (!algunoAlcanzable(salidaMasTemprana(paradas, pos, ruta), desde, averia.location(), aBordo,
                            velocidad)) {
                        continue;
                    }
                    int carga = pos == 0 ? ruta.initialLoad() : paradas.get(pos - 1).loadAfter();
                    mejor = mejorRescate(mejor, rescate(ruta, ruta.stops().subList(0, pos),
                            ruta.stops().subList(pos, ruta.stops().size()), carga, i, averia, aBordo,
                            enMarcha.snapshotOrigen(), bloqueos, directa));
                }
            }
            if (mejor == null) {
                continue;
            }
            if (mejor.reemplaza() >= 0) {
                RutaVigente anterior = conservadas.get(mejor.reemplaza());
                if (mejor.ruta().ruta().id().endsWith(SUFIJO_DESVIO)) {
                    // La ruta de la que se desvía ya hizo todas sus entregas: pasan a lo entregado.
                    CortePlan.sumar(entregados, CortePlan.entregas(anterior, null));
                    CortePlan.sumar(retiros, CortePlan.retirosIntermedios(anterior, null));
                }
                conservadas.set(mejor.reemplaza(), mejor.ruta());
            } else {
                conservadas.add(mejor.ruta());
            }
        }
    }

    /** Ruta de rescate a partir de {@code base}: sus paradas previas, el trasvase, las entregas que llegan a tiempo. */
    private Rescate rescate(DeliveryRoute base, List<RouteStop> previas, List<RouteStop> posteriores, int cargaAntes,
                            int reemplaza, BreakdownEvent averia, List<DeliveryStop> aBordo,
                            OperationalSnapshot snapshot, List<RoadBlock> bloqueos,
                            Map<DeliveryStop, Instant> directa) {
        int capacidad = snapshot.fleetProfile().parametersFor(base.vehicle().type()).capacity() - cargaAntes;
        List<DeliveryStop> elegidas = new ArrayList<>();
        int carga = 0;
        for (DeliveryStop entrega : aBordo) {
            if (carga + entrega.deliveredPackages() <= capacidad) {
                elegidas.add(entrega);
                carga += entrega.deliveredPackages();
            }
        }
        while (!elegidas.isEmpty()) {
            List<RouteStop> paradas = new ArrayList<>(previas);
            paradas.add(new TransferStop(averia.vehicleId(), averia.location(),
                    elegidas.stream().mapToInt(DeliveryStop::deliveredPackages).sum()));
            paradas.addAll(elegidas);
            paradas.addAll(posteriores);
            RutaVigente ruta = reprogramar(new RutaVigente(base.withReplacedStops(paradas), snapshot, null, List.of()),
                    bloqueos, false);
            if (ruta.programada() == null || !ruta.violaciones().stream()
                    .allMatch(v -> v.type() == PlanViolationType.SLA_MISSED)) {
                return null;
            }
            List<DeliveryStop> tarde = ruta.programada().scheduledStops().stream()
                    .filter(p -> p.stop() instanceof DeliveryStop e && elegidas.contains(e)
                            && p.arrivedAt().isAfter(e.order().deadline()))
                    .map(p -> (DeliveryStop) p.stop())
                    .toList();
            if (tarde.isEmpty() && ruta.violaciones().isEmpty()) {
                int sirven = 0;
                for (ScheduledRouteStop parada : ruta.programada().scheduledStops()) {
                    if (parada.stop() instanceof DeliveryStop e && directa.containsKey(e)
                            && conviene(e, parada.arrivedAt(), directa.get(e))) {
                        sirven++;
                    }
                }
                return new Rescate(ruta, reemplaza, sirven, elegidas.size(), ruta.programada().completedAt());
            }
            if (tarde.isEmpty()) {
                return null;
            }
            elegidas.removeAll(tarde);
        }
        return null;
    }

    /**
     * Cota optimista: la unidad sale en {@code sale} desde {@code desde}, llega en línea recta a la avería, hace el
     * transbordo y alcanza a tiempo al menos uno de los pedidos a bordo.
     */
    private static boolean algunoAlcanzable(Instant sale, Location desde, Location averia, List<DeliveryStop> aBordo,
                                            double velocidad) {
        Instant enAveria = sale.plusSeconds(Math.round(manhattan(desde, averia) * 3600.0 / velocidad))
                .plus(TransferStop.TRANSFER_TIME);
        return aBordo.stream().anyMatch(entrega -> !enAveria
                .plusSeconds(Math.round(manhattan(averia, entrega.location()) * 3600.0 / velocidad))
                .isAfter(entrega.order().deadline()));
    }

    /** Último nodo por el que pasó el vehículo en {@code ahora} durante el tramo hacia {@code parada}. */
    private static Location nodoEn(ScheduledRouteStop parada, Instant ahora) {
        Location nodo = parada.approach().origin();
        for (var calle : parada.approach().legs()) {
            if (calle.departsAt().isAfter(ahora)) {
                break;
            }
            nodo = calle.from();
            if (!calle.arrivesAt().isAfter(ahora)) {
                nodo = calle.to();
            }
        }
        return nodo;
    }

    /** Snapshot de origen con el vehículo desviado disponible en su posición actual. */
    private static OperationalSnapshot snapshotDesvio(OperationalSnapshot origen, Vehicle vehiculo, Location donde,
                                                      Instant ahora) {
        Map<String, VehicleOperationalState> estados = new LinkedHashMap<>(origen.vehiclesById());
        estados.put(vehiculo.id(), new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, donde, 0, ahora));
        return new OperationalSnapshot(ahora, origen.fleetProfile(), origen.inventory(), estados,
                origen.maintenanceCalendar(), origen.shiftSchedule(), origen.breakdowns());
    }

    private static Rescate mejorRescate(Rescate actual, Rescate candidato) {
        if (candidato == null || candidato.urgentes() == 0) return actual;
        if (actual == null) return candidato;
        if (candidato.urgentes() != actual.urgentes()) return candidato.urgentes() > actual.urgentes() ? candidato : actual;
        if (candidato.entregas() != actual.entregas()) return candidato.entregas() > actual.entregas() ? candidato : actual;
        return candidato.termina().isBefore(actual.termina()) ? candidato : actual;
    }

    /**
     * El trasvase conviene para un pedido si la unidad de rescate llega antes que un despacho nuevo, o si el despacho
     * nuevo no llegaría con al menos {@link #MARGEN_TRASVASE} de holgura (puede no haber unidad libre a tiempo). Con
     * margen negativo todo pedido a bordo se trasvasa.
     */
    private static boolean conviene(DeliveryStop entrega, Instant llegadaRescate, Instant llegadaDirecta) {
        return MARGEN_TRASVASE.isNegative() || llegadaRescate.isBefore(llegadaDirecta)
                || llegadaDirecta.equals(Instant.MAX)
                || llegadaDirecta.plus(MARGEN_TRASVASE).isAfter(entrega.order().deadline());
    }

    /**
     * Cota optimista de un despacho nuevo con las unidades que de verdad hay: cada una sale de donde estará (un
     * almacén) cuando quede libre, pasa por el almacén con stock que menos la desvía y va en línea recta al cliente.
     * Si ninguna puede, no hay despacho nuevo posible.
     */
    private static Instant llegadaDirecta(DeliveryStop entrega, Instant ahora, OperationalSnapshot snapshot,
                                          String averiado) {
        Instant mejor = Instant.MAX;
        for (VehicleOperationalState estado : snapshot.vehiclesById().values()) {
            String id = estado.vehicle().id();
            Instant sale = estado.availableAt().isAfter(ahora) ? estado.availableAt() : ahora;
            if (id.equals(averiado) || estado.carriedPackages() != 0 || !snapshot.isVehiclePlannableAt(id, sale)
                    || snapshot.fleetProfile().parametersFor(estado.vehicle().type()).capacity() < 1) {
                continue;
            }
            int km = Integer.MAX_VALUE;
            for (Warehouse almacen : snapshot.inventory().warehouses()) {
                if (snapshot.inventory().availableStock(almacen.id()) >= entrega.deliveredPackages()) {
                    km = Math.min(km, manhattan(estado.location(), almacen.location())
                            + manhattan(almacen.location(), entrega.location()));
                }
            }
            if (km == Integer.MAX_VALUE) {
                continue;
            }
            double velocidad = snapshot.fleetProfile().parametersFor(estado.vehicle().type()).speedKmPerHour();
            Instant llega = sale.plusSeconds(Math.round(km * 3600.0 / velocidad));
            if (llega.isBefore(mejor)) {
                mejor = llega;
            }
        }
        return mejor;
    }

    private record Rescate(RutaVigente ruta, int reemplaza, int urgentes, int entregas, Instant termina) {
    }

    private static boolean llegaATiempo(RutaVigente ruta) {
        return ruta.programada().scheduledStops().stream().allMatch(parada ->
                !(parada.stop() instanceof DeliveryStop entrega)
                        || !parada.arrivedAt().isAfter(entrega.order().deadline()));
    }

    /**
     * Unidad que no pudo continuar (sin entregas pendientes o sin llegar a tiempo): al terminar la avería menor regresa
     * vacía al central, porque el planificador solo asigna unidades que están allí.
     */
    private void agregarRegresosAlCentral(List<BreakdownEvent> averiasTerminadas, Instant ahora, Warehouse central,
                                          Collection<Warehouse> almacenes, List<RutaVigente> conservadas,
                                          Map<String, VehicleOperationalState> forzados, List<BreakdownEvent> averias,
                                          Map<String, Integer> retiros, List<RoadBlock> bloqueos) {
        for (BreakdownEvent averia : averiasTerminadas) {
            String vehiculoId = averia.vehicleId();
            VehicleOperationalState detenido = forzados.get(vehiculoId);
            if (averia.type() != BreakdownType.MINOR || detenido == null
                    || conservadas.stream().anyMatch(ruta -> ruta.vehiculoId().equals(vehiculoId))) {
                continue;
            }
            forzados.remove(vehiculoId);
            if (averia.location().equals(central.location())) {
                continue;
            }
            DeliveryRoute regreso = DeliveryRoute.replanFromCurrentLocation("REGRESO-" + vehiculoId, detenido.vehicle(),
                    averia.location(), 0, ahora).returningTo(central);
            OperationalSnapshot snapshot = construirSnapshot(ahora, central, almacenes, conservadas, forzados, averias,
                    retiros);
            conservadas.add(reprogramar(new RutaVigente(regreso, snapshot, null, List.of()), bloqueos, false));
        }
    }

    /**
     * Si una ruta en marcha quedó incumpliendo un plazo (tras un bloqueo o un cambio de flota), se liberan también los
     * viajes que aún no salieron de un almacén intermedio, para que el planificador los reasigne. Solo se hace cuando
     * hace falta: liberarlos en cada ciclo multiplicaba el tiempo de cómputo sin mejorar los planes (Semana 13).
     */
    private RutaVigente liberarViajesSiIncumple(RutaVigente ruta, Instant ahora, List<RoadBlock> bloqueos) {
        if (ruta.programada() == null
                || ruta.violaciones().stream().noneMatch(v -> v.type() == PlanViolationType.SLA_MISSED)) {
            return ruta;
        }
        RutaVigente recortada = CortePlan.sinViajesPendientes(ruta, ahora, true);
        return recortada == ruta ? ruta : new RutaVigente(recortada.ruta(), recortada.snapshotOrigen(),
                recortada.programada(), violacionesDeRuta(recortada, bloqueos, recortada.snapshotOrigen()));
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
        Map<String, Location> terminaEn = new HashMap<>();
        conservadas.forEach(ruta -> {
            ocupadosHasta.put(ruta.vehiculoId(), ruta.programada() == null ? ahora : ruta.programada().completedAt());
            // Donde quedará el vehículo al terminar: el SA puede darle un viaje desde esa hora (despacho diferido).
            terminaEn.put(ruta.vehiculoId(), ruta.ruta().stops().isEmpty() ? central.location()
                    : ruta.ruta().stops().getLast().location());
        });
        Map<String, VehicleOperationalState> estados = new LinkedHashMap<>();
        for (Vehicle vehiculo : fuente.flota()) {
            VehicleOperationalState estado;
            if (ocupadosHasta.containsKey(vehiculo.id())) {
                estado = new VehicleOperationalState(vehiculo, VehicleStatus.IN_ROUTE, terminaEn.get(vehiculo.id()), 0,
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
