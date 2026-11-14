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
import com.pucp.paqrap.config.RescatePreventivoProperties;
import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownResolution;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
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
import com.pucp.paqrap.modulos.planificacion.entity.TransferStop;
import com.pucp.paqrap.modulos.planificacion.persistence.PlanEntity;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

/**
 * Servicio de replanificación operacional.
 *
 * La lógica histórica de corte, bloqueos, averías,
 * inventario y persistencia se conserva.
 *
 * Cuando el motor configurado es ALNS, antes de mandar
 * pedidos pendientes únicamente a vehículos libres se
 * intenta insertarlos en la parte todavía modificable
 * de rutas que ya están en marcha.
 */
@Service
public class ReplanificacionSaService
        implements ReplanificacionService {

    private final FuenteDatosOperativos fuente;

    private final PlanificadorSaService planificador;

    private final RouteScheduler programador =
            new RouteScheduler(
                    new RoadNetwork()
            );

    /**
     * Mismo evaluador del motor de planificación.
     */
    private final OperationalPlanEvaluator evaluador;

    private final RescatePreventivoPedidosService rescatePreventivo;

    private final RecuperacionTransbordoFallidoService recuperacionTransbordo;

    private final RescatePreventivoProperties configuracionRescate;

    private final EstadoVehiculoRutaService estadosRutas =
            new EstadoVehiculoRutaService();

    private final PlanResponseMapper mapper =
            new PlanResponseMapper();

    private final PlanPersistenceService persistenciaPlanes;

    private final Map<
            Long,
            EstadoEjecucion
            > ejecuciones =
            new ConcurrentHashMap<>();

    @Autowired
    public ReplanificacionSaService(
            FuenteDatosOperativos fuente,
            PlanificadorSaService planificador,
            PlanPersistenceService persistenciaPlanes,
            RescatePreventivoProperties configuracionRescate
    ) {

        this.fuente =
                Objects.requireNonNull(
                        fuente,
                        "fuente es requerida"
                );

        this.planificador =
                Objects.requireNonNull(
                        planificador,
                        "planificador es requerido"
                );

        this.evaluador =
                planificador.evaluador();

        this.configuracionRescate =
                Objects.requireNonNull(
                        configuracionRescate,
                        "configuracionRescate es requerida"
                );

        this.rescatePreventivo =
                new RescatePreventivoPedidosService(
                        this.evaluador,
                        this.programador,
                        this.configuracionRescate
                );

        this.recuperacionTransbordo =
                new RecuperacionTransbordoFallidoService(this.programador);

        this.persistenciaPlanes =
                Objects.requireNonNull(
                        persistenciaPlanes,
                        "persistenciaPlanes es requerida"
                );
    }

    /** Conserva compatibilidad con pruebas que construyen el servicio sin Spring. */
    public ReplanificacionSaService(
            FuenteDatosOperativos fuente,
            PlanificadorSaService planificador,
            PlanPersistenceService persistenciaPlanes
    ) {
        this(fuente, planificador, persistenciaPlanes, RescatePreventivoProperties.defaults());
    }

    // ------------------------------------------------------------------
    // disparadores
    // ------------------------------------------------------------------

    @Override
    public synchronized ReplanificacionResponse iniciar(
            long ejecucionId,
            SolicitudPlanificacion solicitud
    ) {

        Objects.requireNonNull(
                solicitud,
                "solicitud es requerida"
        );

        if (
                ejecuciones.containsKey(
                        ejecucionId
                )
        ) {

            throw new ReglaNegocioException(
                    "La ejecución "
                            +
                            ejecucionId
                            +
                            " ya fue iniciada"
            );
        }

        EstadoEjecucion estado =
                new EstadoEjecucion(
                        ejecucionId,

                        solicitud.modo(),

                        PlanificadorSaService
                                .semillaDe(
                                        solicitud
                                ),

                        PlanificadorSaService
                                .presupuestoDe(
                                        solicitud
                                ),

                        solicitud.configuracionSa() != null
                                ? solicitud.configuracionSa()
                                : PlanificadorSaService
                                        .configuracionPorDefecto(),

                        solicitud.horaPlanificacion()
                );

        Map<String, Order> pedidos =
                new LinkedHashMap<>();

        for (
                Order pedido :
                solicitud.pedidosPendientes()
        ) {

            if (
                    pedidos.put(
                            pedido.id(),
                            pedido
                    ) != null
            ) {

                throw new ReglaNegocioException(
                        "Pedido repetido en la solicitud: "
                                +
                                pedido.id()
                );
            }
        }

        estado.pedidos =
                pedidos;

        estado.bloqueos =
                new ArrayList<>(
                        solicitud.bloqueosActivos()
                );

        estado.bloqueosConocidos =
                new HashSet<>(
                        solicitud.bloqueosActivos()
                );

        estado.averias =
                new ArrayList<>(
                        fuente.averias(
                                ejecucionId
                        )
                );

        ReplanificacionResponse respuesta =
                replanificar(
                        estado,

                        MotivoReplanificacion.INICIO,

                        solicitud.horaPlanificacion(),

                        List.of(),

                        List.of(),

                        List.of(),

                        List.of(),

                        false,

                        Set.of(),

                        List.of()
                );

        ejecuciones.put(
                ejecucionId,
                estado
        );

        return respuesta;
    }

    @Override
    public synchronized ReplanificacionResponse replanificar(
            long ejecucionId,
            Eventos eventos,
            Instant ahora
    ) {

        Objects.requireNonNull(
                eventos,
                "eventos es requerido"
        );

        EstadoEjecucion estado =
                obtener(
                        ejecucionId
                );

        validarHora(
                estado,
                ahora
        );

        Map<String, Order> nuevos =
                new LinkedHashMap<>();

        for (
                Order pedido :
                eventos.pedidos()
        ) {

            Objects.requireNonNull(
                    pedido,
                    "pedido es requerido"
            );

            if (
                    estado
                            .pedidos
                            .containsKey(
                                    pedido.id()
                            )
                    ||
                    nuevos.put(
                            pedido.id(),
                            pedido
                    ) != null
            ) {

                throw new ReglaNegocioException(
                        "El pedido "
                                +
                                pedido.id()
                                +
                                " ya existe en la ejecución "
                                +
                                ejecucionId
                );
            }
        }

        Set<RoadBlock> recibidos =
                new LinkedHashSet<>();

        List<RoadBlock> bloqueos =
                new ArrayList<>();

        for (
                RoadBlock bloqueo :
                eventos.bloqueos()
        ) {

            Objects.requireNonNull(
                    bloqueo,
                    "bloqueo es requerido"
            );

            if (
                    estado
                            .bloqueosConocidos
                            .contains(
                                    bloqueo
                            )
                    ||
                    !recibidos.add(
                            bloqueo
                    )
            ) {
                continue;
            }

            Instant inicio =
                    bloqueo
                            .startsAt()
                            .isAfter(
                                    ahora
                            )
                            ? bloqueo.startsAt()
                            : ahora;

            if (
                    bloqueo
                            .endsAt()
                            .isAfter(
                                    inicio
                            )
            ) {

                bloqueos.add(
                        new RoadBlock(
                                inicio,
                                bloqueo.endsAt(),
                                bloqueo.nodes()
                        )
                );
            }
        }

        Set<String> flota =
                new HashSet<>(
                        fuente
                                .flota()
                                .stream()
                                .map(
                                        Vehicle::id
                                )
                                .toList()
                );

        for (
                BreakdownEvent averia :
                eventos.averias()
        ) {

            Objects.requireNonNull(
                    averia,
                    "averia es requerida"
            );

            if (
                    !flota.contains(
                            averia.vehicleId()
                    )
            ) {

                throw new ReglaNegocioException(
                        "Vehículo desconocido: "
                                +
                                averia.vehicleId()
                );
            }
        }

        boolean bloqueoIniciado =
                eventos
                        .bloqueos()
                        .stream()

                        .anyMatch(
                                bloqueo ->
                                        !bloqueo
                                                .startsAt()
                                                .isAfter(
                                                        ahora
                                                )
                                        &&
                                        (
                                                bloqueo
                                                        .startsAt()
                                                        .isAfter(
                                                                estado.ultimaHora
                                                        )
                                                ||
                                                recibidos.contains(
                                                        bloqueo
                                                )
                                        )
                        );

        boolean bloqueoTerminado =
                eventos
                        .bloqueosTerminados()
                        .stream()

                        .anyMatch(
                                bloqueo ->
                                        !bloqueo
                                                .endsAt()
                                                .isAfter(
                                                        ahora
                                                )
                                        &&
                                        bloqueo
                                                .endsAt()
                                                .isAfter(
                                                        estado.ultimaHora
                                                )
                        );

        MotivoReplanificacion motivo =
                !eventos.averias().isEmpty()
                        ||
                        !eventos
                                .averiasTerminadas()
                                .isEmpty()

                        ? MotivoReplanificacion.AVERIA

                        : bloqueoIniciado
                        ||
                        bloqueoTerminado

                        ? MotivoReplanificacion.BLOQUEO

                        : !eventos.pedidos().isEmpty()

                        ? MotivoReplanificacion.NUEVO_PEDIDO

                        : eventos.motivoSolicitado() != null

                        ? eventos.motivoSolicitado()

                        : MotivoReplanificacion.PERIODICO;

        ReplanificacionResponse respuesta =
                replanificar(
                        estado,
                        motivo,
                        ahora,
                        List.copyOf(
                                nuevos.values()
                        ),
                        bloqueos,
                        eventos.averias(),
                        eventos.averiasTerminadas(),
                        bloqueoIniciado
                                ||
                                bloqueoTerminado,
                        Set.copyOf(
                                eventos
                                        .tiposFlotaActualizados()
                        ),
                        eventos.fallosTransbordo()
                );

        estado
                .bloqueosConocidos
                .addAll(
                        recibidos
                );

        return respuesta;
    }

    @Override
    public boolean conocePedido(
            long ejecucionId,
            String pedidoId
    ) {

        EstadoEjecucion estado =
                ejecuciones.get(
                        ejecucionId
                );

        return
                estado != null
                &&
                estado
                        .pedidos
                        .containsKey(
                                pedidoId
                        );
    }

    @Override
    public boolean estaIniciada(
            long ejecucionId
    ) {

        return ejecuciones.containsKey(
                ejecucionId
        );
    }

    @Override
    public Optional<ReplanificacionResponse> planVigente(
            long ejecucionId
    ) {

        EstadoEjecucion estado =
                ejecuciones.get(
                        ejecucionId
                );

        return estado == null
                ? Optional.empty()
                : Optional.ofNullable(
                        estado.ultimaRespuesta
                );
    }

    @Override
    public Optional<MetricasPlanificacion> metricas(
            long ejecucionId
    ) {

        EstadoEjecucion estado =
                ejecuciones.get(
                        ejecucionId
                );

        return estado == null
                ? Optional.empty()

                : Optional.of(
                        new MetricasPlanificacion(
                                estado.totalComputationMs,
                                estado.numeroReplanificacion
                        )
                );
    }

    @Override
    public synchronized void finalizar(
            long ejecucionId
    ) {

        ejecuciones.remove(
                ejecucionId
        );
    }

    synchronized List<RutaVigente> rutasVigentes(
            long ejecucionId
    ) {

        return List.copyOf(
                obtener(
                        ejecucionId
                ).rutas
        );
    }

    synchronized List<RoadBlock> bloqueosVigentes(
            long ejecucionId
    ) {

        return List.copyOf(
                obtener(
                        ejecucionId
                ).bloqueos
        );
    }

    // ------------------------------------------------------------------
    // replanificación
    // ------------------------------------------------------------------

    private ReplanificacionResponse replanificar(
            EstadoEjecucion estado,
            MotivoReplanificacion motivo,
            Instant ahora,
            List<Order> nuevosPedidos,
            List<RoadBlock> nuevosBloqueos,
            List<BreakdownEvent> nuevasAverias,
            List<BreakdownEvent> averiasTerminadas,
            boolean cambioBloqueo,
            Set<VehicleType> tiposFlotaActualizados,
            List<FalloTransbordoOperativo> nuevosFallosTransbordo
    ) {

        long inicioComputoCicloNanos = System.nanoTime();

        /*
         * =====================================================
         * 1. APLICAR EVENTOS SOBRE UNA COPIA
         * =====================================================
         */

        Map<String, Order> pedidos =
                new LinkedHashMap<>(
                        estado.pedidos
                );

        nuevosPedidos.forEach(
                pedido ->
                        pedidos.put(
                                pedido.id(),
                                pedido
                        )
        );

        List<RoadBlock> bloqueos =
                new ArrayList<>(
                        estado.bloqueos
                );

        bloqueos.addAll(
                nuevosBloqueos
        );

        List<BreakdownEvent> averias =
                new ArrayList<>(
                        estado.averias
                );

        averias.addAll(
                nuevasAverias
        );

        Map<
                String,
                VehicleOperationalState
                > forzados =
                new HashMap<>(
                        estado.estadosForzados
                );

        Map<String, Integer> reasignacionesPreventivas =
                new HashMap<>(estado.reasignacionesPreventivas);

        Map<String, Instant> cooldownRescateHasta =
                new HashMap<>(estado.cooldownRescateHasta);

        Map<String, RecuperacionTransbordoFallidoService.Pendiente>
                recuperacionesTransbordoPendientes =
                new LinkedHashMap<>(estado.fallosTransbordoPendientes);

        Set<String> pedidosRescatadosPreventivamente =
                new LinkedHashSet<>();

        int rescatesPreventivosCiclo = 0;
        int transbordosPlanificadosCiclo = 0;

        Collection<Warehouse> almacenes =
                fuente.almacenes();

        Warehouse central =
                almacenes
                        .stream()
                        .filter(
                                Warehouse::isCentral
                        )
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "La fuente de datos no define un almacén central"
                                        )
                        );

        /*
         * =====================================================
         * 2. CORTAR PLAN VIGENTE
         * =====================================================
         */

        Map<String, BreakdownEvent> averiaPorVehiculo =
                new HashMap<>();

        nuevasAverias.forEach(
                averia ->
                        averiaPorVehiculo.merge(
                                averia.vehicleId(),
                                averia,
                                (
                                        a,
                                        b
                                ) ->
                                        a
                                                .occurredAt()
                                                .isAfter(
                                                        b.occurredAt()
                                                )
                                                ? b
                                                : a
                        )
        );

        Map<String, Instant> interrumpidos =
                new HashMap<>();

        averiaPorVehiculo.forEach(
                (
                        vehiculo,
                        averia
                ) ->
                        interrumpidos.put(
                                vehiculo,
                                averia.occurredAt()
                        )
        );

        Map<String, FalloTransbordoOperativo> falloPorVehiculo =
                new HashMap<>();

        for (FalloTransbordoOperativo fallo : nuevosFallosTransbordo) {
            falloPorVehiculo.put(fallo.donorVehicleId(), fallo);
            falloPorVehiculo.put(fallo.receiverVehicleId(), fallo);
            interrumpidos.merge(
                    fallo.donorVehicleId(),
                    fallo.occurredAt(),
                    (a, b) -> a.isBefore(b) ? a : b);
            interrumpidos.merge(
                    fallo.receiverVehicleId(),
                    fallo.occurredAt(),
                    (a, b) -> a.isBefore(b) ? a : b);
        }

        Map<String, RutaVigente> rutasAntesDelCorte = new LinkedHashMap<>();
        estado.rutas.forEach(ruta -> rutasAntesDelCorte.put(ruta.vehiculoId(), ruta));

        for (FalloTransbordoOperativo fallo : nuevosFallosTransbordo) {
            RutaVigente donante = rutasAntesDelCorte.get(fallo.donorVehicleId());
            RutaVigente receptor = rutasAntesDelCorte.get(fallo.receiverVehicleId());
            if (donante != null) {
                recuperacionesTransbordoPendientes.put(
                        fallo.transferId(),
                        new RecuperacionTransbordoFallidoService.Pendiente(
                                fallo, donante, receptor));
            }
        }

        CortePlan.Resultado corte =
                CortePlan.cortar(
                        estado.rutas,
                        ahora,
                        interrumpidos
                );

        Map<String, Integer> entregados =
                new HashMap<>(
                        estado.entregados
                );

        CortePlan.sumar(
                entregados,
                corte.entregasSalientes()
        );

        Map<String, Integer> retiros =
                new HashMap<>(
                        estado.retiros
                );

        CortePlan.sumar(
                retiros,
                corte.retirosSalientes()
        );

        Map<String, VehicleOperationalState> estadosReales =
                fuente.estadosFlota(ahora);

        for (
                RutaVigente interrumpida :
                corte.interrumpidas()
        ) {
            String vehiculoId = interrumpida.vehiculoId();
            FalloTransbordoOperativo fallo = falloPorVehiculo.get(vehiculoId);
            if (fallo != null) {
                VehicleOperationalState real = estadosReales.get(vehiculoId);
                if (real != null) {
                    forzados.put(vehiculoId, real);
                }
                continue;
            }

            BreakdownEvent averia = averiaPorVehiculo.get(vehiculoId);
            if (averia != null) {
                forzados.put(
                        vehiculoId,
                        estadoTrasAveria(
                                interrumpida.ruta().vehicle(),
                                averia,
                                central));
            }
        }

        /*
         * =====================================================
         * 3. RUTAS ACTIVAS
         * =====================================================
         */

        List<RutaVigente> conservadas =
                new ArrayList<>(
                        corte
                                .conservadas()
                                .stream()

                                .map(
                                        ruta ->
                                                cambioBloqueo
                                                ||
                                                tiposFlotaActualizados
                                                        .contains(
                                                                ruta
                                                                        .ruta()
                                                                        .vehicle()
                                                                        .type()
                                                        )

                                                        ? reprogramar(
                                                                ruta,
                                                                bloqueos,
                                                                !tiposFlotaActualizados
                                                                        .isEmpty()
                                                        )

                                                        : ruta
                                )

                                .toList()
                );

        Map<String, Integer> cantidadesProtegidasTransbordo = new LinkedHashMap<>();

        if (!recuperacionesTransbordoPendientes.isEmpty()) {
            /*
             * La fuente persistida contiene la carga que realmente quedó en
             * cada unidad. Para intentar la recuperación se reemplaza cualquier
             * bloqueo temporal de un ciclo anterior por ese estado real.
             */
            for (RecuperacionTransbordoFallidoService.Pendiente pendiente :
                    recuperacionesTransbordoPendientes.values()) {
                FalloTransbordoOperativo fallo = pendiente.fallo();
                VehicleOperationalState donanteReal =
                        estadosReales.get(fallo.donorVehicleId());
                VehicleOperationalState receptorReal =
                        estadosReales.get(fallo.receiverVehicleId());
                if (donanteReal != null) {
                    forzados.put(fallo.donorVehicleId(), donanteReal);
                }
                if (receptorReal != null) {
                    forzados.put(fallo.receiverVehicleId(), receptorReal);
                }
            }

            OperationalSnapshot snapshotRecuperacion = construirSnapshot(
                    ahora, central, almacenes, conservadas, forzados, averias, retiros);

            RecuperacionTransbordoFallidoService.Resultado recuperacion =
                    recuperacionTransbordo.recuperar(
                            ahora,
                            recuperacionesTransbordoPendientes.values(),
                            almacenes,
                            estadosReales,
                            snapshotRecuperacion,
                            bloqueos);

            conservadas.addAll(recuperacion.rutasRecuperadas());
            recuperacion.vehiculosRecuperados().forEach(forzados::remove);
            cantidadesProtegidasTransbordo.putAll(recuperacion.cantidadesProtegidas());

            recuperacionesTransbordoPendientes = new LinkedHashMap<>();
            for (RecuperacionTransbordoFallidoService.Pendiente pendiente :
                    recuperacion.pendientes()) {
                FalloTransbordoOperativo fallo = pendiente.fallo();
                recuperacionesTransbordoPendientes.put(fallo.transferId(), pendiente);

                /*
                 * Mientras no exista una reconstrucción válida, ambos
                 * participantes quedan fuera de nuevas asignaciones. El
                 * donante conserva la carga transferible y el receptor puede
                 * conservar otros pedidos de su manifiesto anterior; liberar
                 * cualquiera de los dos permitiría duplicar compromisos.
                 */
                bloquearVehiculoPorRecuperacion(
                        fallo.donorVehicleId(), estadosReales, forzados);
                bloquearVehiculoPorRecuperacion(
                        fallo.receiverVehicleId(), estadosReales, forzados);
            }
        }

        agregarRegresosAlCentral(
                averiasTerminadas,
                ahora,
                central,
                almacenes,
                conservadas,
                forzados,
                averias,
                retiros,
                bloqueos
        );

        long presupuestoPlannerMs =
                estado.presupuestoMs;

        /*
         * =====================================================
         * 4. RESCATE PREVENTIVO Y PEDIDOS PENDIENTES
         * =====================================================
         */

        Map<String, Integer> cubiertos =
                new HashMap<>(
                        entregados
                );

        Map<String, Integer> retirosTotales =
                new HashMap<>(
                        retiros
                );

        for (
                RutaVigente ruta :
                conservadas
        ) {

            CortePlan.sumar(
                    cubiertos,
                    CortePlan.entregas(
                            ruta,
                            null
                    )
            );

            CortePlan.sumar(
                    retirosTotales,
                    CortePlan
                            .retirosIntermedios(
                                    ruta,
                                    null
                            )
            );
        }

        /*
         * Una transferencia fallida no mueve la carga: la cantidad sigue a
         * bordo del donante. Se protege temporalmente para que ALNS no la
         * vuelva a asignar a otro vehículo mientras se recupera la ruta.
         */
        CortePlan.sumar(cubiertos, cantidadesProtegidasTransbordo);

        /*
         * El rescate actúa antes de calcular pendientes: mueve una entrega
         * ya comprometida hacia otra ruta, o programa un transbordo físico si
         * la carga ya está a bordo. Consume como máximo 10 % del presupuesto
         * (150 ms) y nunca sustituye la optimización global de ALNS.
         */
        if (
                configuracionRescate.enabled()
                && planificador.algoritmo() == AlgoritmoPlanificacion.ALNS
                && !conservadas.isEmpty()
        ) {

            long inicioRescateNanos = System.nanoTime();
            long presupuestoRescateMs =
                    configuracionRescate.presupuestoPara(estado.presupuestoMs);
            long deadlineRescate = inicioRescateNanos
                    + presupuestoRescateMs * 1_000_000L;

            InventorySnapshot inventarioRescate =
                    inventarioDisponible(almacenes, retirosTotales);

            /*
             * Snapshot ACTUAL de toda la flota. Es imprescindible para que el
             * rescate preventivo vea también vehículos AVAILABLE que no tienen
             * una ruta vigente.
             */
            OperationalSnapshot snapshotRescate =
                    construirSnapshot(
                            ahora,
                            central,
                            almacenes,
                            conservadas,
                            forzados,
                            averias,
                            retirosTotales
                    );

            RescatePreventivoPedidosService.Resultado rescate =
                    rescatePreventivo.rescatar(
                            ahora,
                            conservadas,
                            central,
                            inventarioRescate,
                            snapshotRescate,
                            bloqueos,
                            reasignacionesPreventivas,
                            cooldownRescateHasta,
                            deadlineRescate
                    );

            conservadas = new ArrayList<>(rescate.rutas());
            rescatesPreventivosCiclo = rescate.rescates();
            transbordosPlanificadosCiclo = rescate.transbordos();
            pedidosRescatadosPreventivamente.addAll(rescate.pedidosRescatados());

            long gastadoRescateMs = Math.max(
                    0L,
                    (System.nanoTime() - inicioRescateNanos) / 1_000_000L
            );
            presupuestoPlannerMs = Math.max(
                    1L,
                    presupuestoPlannerMs - gastadoRescateMs
            );

            if (rescatesPreventivosCiclo > 0) {
                cubiertos = new HashMap<>(entregados);
                retirosTotales = new HashMap<>(retiros);
                for (RutaVigente ruta : conservadas) {
                    CortePlan.sumar(cubiertos, CortePlan.entregas(ruta, null));
                    CortePlan.sumar(retirosTotales, CortePlan.retirosIntermedios(ruta, null));
                }
                CortePlan.sumar(cubiertos, cantidadesProtegidasTransbordo);
            }
        }

        List<Order> pendientes =
                new ArrayList<>();

        for (
                Order pedido :
                pedidos.values()
        ) {

            int falta =
                    pedido.packages()
                    -
                    cubiertos.getOrDefault(
                            pedido.id(),
                            0
                    );

            if (
                    falta > 0
            ) {

                pendientes.add(
                        falta ==
                                pedido.packages()

                                ? pedido

                                : new Order(
                                        pedido.id(),
                                        pedido.destination(),
                                        falta,
                                        pedido.registeredAt(),
                                        pedido.deadline()
                                )
                );
            }
        }

        boolean soloRescateLocal =
                motivo == MotivoReplanificacion.RIESGO_ETA
                && rescatesPreventivosCiclo > 0;

        /*
         * =====================================================
         * 5. INSERCIÓN EN RUTAS ACTIVAS - SOLO ALNS
         * =====================================================
         *
         * Reservamos como máximo:
         *
         * - 20 % del presupuesto;
         * - con techo de 300 ms.
         *
         * El resto siempre queda disponible para ALNS.
         */

        if (
                !soloRescateLocal
                && planificador.algoritmo() ==
                        AlgoritmoPlanificacion.ALNS
                &&
                !pendientes.isEmpty()
                &&
                !conservadas.isEmpty()
        ) {

            long inicioInsercionNanos =
                    System.nanoTime();

            long presupuestoInsercionMs =
                    Math.min(
                            300L,

                            Math.max(
                                    25L,
                                    estado.presupuestoMs
                                            /
                                            5L
                            )
                    );

            long deadlineInsercion =
                    inicioInsercionNanos
                    +
                    presupuestoInsercionMs
                    *
                    1_000_000L;

            InventorySnapshot inventarioInsercion =
                    inventarioDisponible(
                            almacenes,
                            retirosTotales
                    );

            InsercionRutasActivasAlns.Resultado insercion =
                    new InsercionRutasActivasAlns(
                            evaluador
                    )
                            .insertar(
                                    ahora,
                                    conservadas,
                                    pendientes,
                                    central,
                                    inventarioInsercion,
                                    bloqueos,
                                    deadlineInsercion
                            );

            conservadas =
                    new ArrayList<>(
                            insercion.rutas()
                    );

            pendientes =
                    new ArrayList<>(
                            insercion.pendientes()
                    );

            /*
             * La inserción dinámica puede haber añadido retiros
             * en almacenes intermedios. Recalculamos las reservas
             * antes de abrir rutas nuevas con ALNS.
             */
            retirosTotales =
                    new HashMap<>(
                            retiros
                    );

            for (
                    RutaVigente ruta :
                    conservadas
            ) {

                CortePlan.sumar(
                        retirosTotales,
                        CortePlan.retirosIntermedios(
                                ruta,
                                null
                        )
                );
            }

            long gastadoInsercionMs =
                    Math.max(
                            0L,

                            (
                                    System.nanoTime()
                                    -
                                    inicioInsercionNanos
                            )
                            /
                            1_000_000L
                    );

            presupuestoPlannerMs =
                    Math.max(
                            1L,
                            presupuestoPlannerMs - gastadoInsercionMs
                    );
        }

        /*
         * =====================================================
         * 6. ALNS / SA CON LO QUE TODAVÍA QUEDA PENDIENTE
         * =====================================================
         */

        OperationalSnapshot snapshot =
                construirSnapshot(
                        ahora,
                        central,
                        almacenes,
                        conservadas,
                        forzados,
                        averias,
                        retirosTotales
                );

        int numero =
                estado.numeroReplanificacion
                +
                1;

        long semilla =
                estado.semillaBase
                +
                numero;

        PlanificadorSaService.EjecucionSa ejecucion;

        if (soloRescateLocal) {
            InventorySnapshot restante =
                    inventarioDisponible(almacenes, retirosTotales);

            ResultadoPlanificacion resultadoLocal =
                    new ResultadoPlanificacion(
                            OperationalPlan.empty(),
                            new PlanEvaluation(Map.of(), List.of(), restante, 0.0),
                            List.copyOf(pendientes)
                    );

            ejecucion = new PlanificadorSaService.EjecucionSa(resultadoLocal, 0L);
        } else {
            ejecucion = planificador.ejecutarSa(
                    snapshot,
                    pendientes,
                    bloqueos,
                    estado.configuracionSa,
                    semilla,
                    presupuestoPlannerMs
            );
        }

        ResultadoPlanificacion resultadoHeuristico =
                ejecucion.resultado();

        /*
         * =====================================================
         * 7. COMBINAR RUTAS CONSERVADAS + NUEVAS
         * =====================================================
         */

        OperationalPlan plan =
                OperationalPlan.empty();

        Map<
                String,
                ScheduledDeliveryRoute
                > programadas =
                new LinkedHashMap<>();

        List<PlanViolation> violaciones =
                new ArrayList<>();

        double costo =
                0;

        List<RutaVigente> vigentes =
                new ArrayList<>();

        for (
                RutaVigente ruta :
                conservadas
        ) {

            plan =
                    plan.withRoute(
                            ruta.ruta()
                    );

            if (
                    ruta.programada() != null
            ) {

                programadas.put(
                        ruta
                                .ruta()
                                .id(),

                        ruta.programada()
                );

                costo +=
                        ruta
                                .programada()
                                .totalCost();
            }

            violaciones.addAll(
                    ruta.violaciones()
            );

            vigentes.add(
                    ruta
            );
        }

        List<DeliveryRoute> nuevas =
                resultadoHeuristico
                        .plan()
                        .routes()
                        .stream()

                        .sorted(
                                Comparator.comparing(
                                        ruta ->
                                                ruta
                                                        .vehicle()
                                                        .id()
                                )
                        )

                        .toList();

        for (
                DeliveryRoute ruta :
                nuevas
        ) {

            plan =
                    plan.withRoute(
                            ruta
                    );

            ScheduledDeliveryRoute programada =
                    resultadoHeuristico
                            .evaluacion()
                            .schedulesByRouteId()
                            .get(
                                    ruta.id()
                            );

            if (
                    programada != null
            ) {

                programadas.put(
                        ruta.id(),
                        programada
                );
            }

            vigentes.add(
                    new RutaVigente(
                            ruta,
                            snapshot,
                            programada,
                            List.of()
                    )
            );
        }

        violaciones.addAll(
                resultadoHeuristico
                        .evaluacion()
                        .violations()
        );

        costo +=
                resultadoHeuristico
                        .costoTotal();

        ResultadoPlanificacion combinado =
                new ResultadoPlanificacion(
                        plan,

                        new PlanEvaluation(
                                programadas,
                                violaciones,

                                resultadoHeuristico
                                        .evaluacion()
                                        .remainingInventory(),

                                costo
                        ),

                        resultadoHeuristico
                                .noAtendidos()
                );

        long duracionComputoCicloMs = Math.max(
                0L,
                (System.nanoTime() - inicioComputoCicloNanos) / 1_000_000L
        );

        estado.totalComputationMs += duracionComputoCicloMs;

        PlanResponse planResponse =
                mapper.aResponse(
                        estado.modo,
                        ahora,
                        combinado,
                        semilla,
                        duracionComputoCicloMs,
                        estado.presupuestoMs
                );

        /*
         * =====================================================
         * METADATOS DE REPLANIFICACIÓN
         * =====================================================
         */

        Set<String> asignadosAntes =
                pedidosDe(
                        estado
                                .rutas
                                .stream()
                                .map(
                                        RutaVigente::ruta
                                )
                                .toList()
                );

        Set<String> reasignadosSet =
                new LinkedHashSet<>(pedidosRescatadosPreventivamente);

        pedidosDe(nuevas)
                .stream()
                .filter(asignadosAntes::contains)
                .forEach(reasignadosSet::add);

        List<String> reasignados =
                reasignadosSet.stream().sorted().toList();

        Set<String> ocupados =
                new HashSet<>();

        conservadas.forEach(
                ruta ->
                        ocupados.add(
                                ruta.vehiculoId()
                        )
        );

        List<String> noDisponibles =
                fuente
                        .flota()
                        .stream()

                        .map(
                                Vehicle::id
                        )

                        .filter(
                                id ->
                                        !ocupados.contains(
                                                id
                                        )
                                        &&
                                        !snapshot
                                                .isVehiclePlannableAt(
                                                        id,
                                                        ahora
                                                )
                        )

                        .sorted()

                        .toList();

        /*
         * =====================================================
         * PERSISTENCIA
         * =====================================================
         */

        PlanEntity snapshotPersistido =
                persistenciaPlanes.persistir(
                        estado.ejecucionId,
                        motivo,
                        plan,
                        programadas,
                        planResponse
                );

        if (
                snapshotPersistido != null
        ) {

            planResponse =
                    mapper.conIdentidadesPersistidas(
                            planResponse,

                            persistenciaPlanes
                                    .rutasPersistidasPorOperacion(
                                            snapshotPersistido
                                                    .getPlanId()
                                    ),

                            snapshotPersistido
                                    .getPlanId()
                    );
        }

        ReplanificacionResponse respuesta =
                new ReplanificacionResponse(
                        estado.ejecucionId,
                        numero,
                        motivo,
                        ahora,

                        planResponse,

                        conservadas
                                .stream()
                                .map(
                                        ruta ->
                                                ruta
                                                        .ruta()
                                                        .id()
                                )
                                .sorted()
                                .toList(),

                        nuevas
                                .stream()
                                .map(
                                        DeliveryRoute::id
                                )
                                .sorted()
                                .toList(),

                        reasignados,

                        noDisponibles
                );

        /*
         * =====================================================
         * 8. CONFIRMAR ESTADO
         * =====================================================
         */

        estado.pedidos =
                pedidos;

        estado.bloqueos =
                bloqueos;

        estado.averias =
                averias;

        estado.estadosForzados =
                forzados;

        estado.reasignacionesPreventivas =
                reasignacionesPreventivas;

        estado.cooldownRescateHasta =
                cooldownRescateHasta;

        estado.fallosTransbordoPendientes =
                recuperacionesTransbordoPendientes;

        estado.rescatesPreventivos +=
                rescatesPreventivosCiclo;

        estado.transbordosPlanificados +=
                transbordosPlanificadosCiclo;

        estado.entregados =
                entregados;

        estado.retiros =
                retiros;

        estado.rutas =
                List.copyOf(
                        vigentes
                );

        estado.numeroReplanificacion =
                numero;

        estado.ultimaHora =
                ahora;

        estado.ultimaRespuesta =
                respuesta;

        return respuesta;
    }

    /*
     * =========================================================
     * REGRESOS TRAS AVERÍA
     * =========================================================
     */

    private static void bloquearVehiculoPorRecuperacion(
            String vehiculoId,
            Map<String, VehicleOperationalState> estadosReales,
            Map<String, VehicleOperationalState> forzados
    ) {
        VehicleOperationalState real = estadosReales.get(vehiculoId);
        if (real == null) {
            return;
        }
        forzados.put(
                vehiculoId,
                new VehicleOperationalState(
                        real.vehicle(),
                        VehicleStatus.IN_ROUTE,
                        real.location(),
                        real.carriedPackages(),
                        Instant.MAX));
    }

    private void agregarRegresosAlCentral(
            List<BreakdownEvent> averiasTerminadas,
            Instant ahora,
            Warehouse central,
            Collection<Warehouse> almacenes,
            List<RutaVigente> conservadas,
            Map<String, VehicleOperationalState> forzados,
            List<BreakdownEvent> averias,
            Map<String, Integer> retiros,
            List<RoadBlock> bloqueos
    ) {

        for (
                BreakdownEvent averia :
                averiasTerminadas
        ) {

            String vehiculoId =
                    averia.vehicleId();

            VehicleOperationalState detenido =
                    forzados.get(
                            vehiculoId
                    );

            if (
                    averia.type() !=
                            BreakdownType.MINOR
                    ||
                    detenido == null
                    ||
                    conservadas
                            .stream()
                            .anyMatch(
                                    ruta ->
                                            ruta
                                                    .vehiculoId()
                                                    .equals(
                                                            vehiculoId
                                                    )
                            )
            ) {
                continue;
            }

            forzados.remove(
                    vehiculoId
            );

            if (
                    averia
                            .location()
                            .equals(
                                    central.location()
                            )
            ) {
                continue;
            }

            DeliveryRoute regreso =
                    DeliveryRoute
                            .replanFromCurrentLocation(
                                    "REGRESO-"
                                            +
                                            vehiculoId,

                                    detenido.vehicle(),

                                    averia.location(),

                                    0,

                                    ahora
                            )
                            .returningTo(
                                    central
                            );

            OperationalSnapshot snapshot =
                    construirSnapshot(
                            ahora,
                            central,
                            almacenes,
                            conservadas,
                            forzados,
                            averias,
                            retiros
                    );

            conservadas.add(
                    reprogramar(
                            new RutaVigente(
                                    regreso,
                                    snapshot,
                                    null,
                                    List.of()
                            ),

                            bloqueos,

                            false
                    )
            );
        }
    }

    /*
     * =========================================================
     * REPROGRAMACIÓN DE RUTAS CONSERVADAS
     * =========================================================
     */

    private RutaVigente reprogramar(
            RutaVigente ruta,
            List<RoadBlock> bloqueos,
            boolean perfilFlotaActualizado
    ) {

        OperationalSnapshot snapshot =
                perfilFlotaActualizado

                        ? conPerfilFlotaActual(
                                ruta.snapshotOrigen()
                        )

                        : ruta.snapshotOrigen();

        try {

            ScheduledDeliveryRoute programada =
                    programador.schedule(
                            ruta.ruta(),
                            snapshot,
                            bloqueos
                    );

            return new RutaVigente(
                    ruta.ruta(),
                    snapshot,
                    programada,

                    violacionesDeRuta(
                            ruta,
                            bloqueos,
                            snapshot
                    )
            );

        } catch (
                IllegalStateException sinCamino
        ) {

            return new RutaVigente(
                    ruta.ruta(),
                    snapshot,
                    ruta.programada(),

                    List.of(
                            new PlanViolation(
                                    PlanViolationType.NO_ROAD_PATH,
                                    ruta
                                            .ruta()
                                            .id(),
                                    sinCamino.getMessage()
                            )
                    )
            );
        }
    }

    private OperationalSnapshot conPerfilFlotaActual(
            OperationalSnapshot origen
    ) {

        return new OperationalSnapshot(
                origen.planningTime(),

                fuente.perfilFlota(),

                origen.inventory(),

                origen.vehiclesById(),

                origen.maintenanceCalendar(),

                origen.shiftSchedule(),

                origen.breakdowns()
        );
    }

    /*
     * =========================================================
     * VALIDACIÓN INDIVIDUAL DE RUTA
     * =========================================================
     */

    private List<PlanViolation> violacionesDeRuta(
            RutaVigente ruta,
            List<RoadBlock> bloqueos,
            OperationalSnapshot snapshot
    ) {

        Map<String, Order> pedidos =
                new LinkedHashMap<>();

        ruta
                .ruta()
                .stops()
                .forEach(
                        parada -> {

                            if (
                                    parada instanceof
                                            DeliveryStop entrega
                            ) {

                                pedidos.putIfAbsent(
                                        entrega.order().id(),
                                        entrega.order()
                                );
                            } else if (
                                    parada instanceof TransferStop transferencia
                            ) {
                                pedidos.putIfAbsent(
                                        transferencia.order().id(),
                                        transferencia.order()
                                );
                            }
                        }
                );

        PlanEvaluation evaluacion =
                evaluador.evaluate(
                        OperationalPlan
                                .empty()
                                .withRoute(
                                        ruta.ruta()
                                ),

                        snapshot,

                        pedidos.values(),

                        bloqueos
                );

        return evaluacion
                .violations()
                .stream()

                .filter(
                        violacion ->
                                violacion.type() != PlanViolationType.PARTIAL_DELIVERY_MISMATCH
                                && violacion.type() != PlanViolationType.INVALID_TRANSFER_PAIR
                                && violacion.type() != PlanViolationType.TRANSFER_NOT_SYNCHRONIZED
                )

                .toList();
    }

    /*
     * =========================================================
     * AVERÍAS
     * =========================================================
     */

    private VehicleOperationalState estadoTrasAveria(
            Vehicle vehiculo,
            BreakdownEvent averia,
            Warehouse central
    ) {

        BreakdownResolution resolucion =
                new BreakdownAvailabilityCalculator(
                        new ShiftSchedule(
                                ShiftSchedule.DEFAULT_ZONE
                        )
                )
                        .resolve(
                                averia
                        );

        return new VehicleOperationalState(
                vehiculo,

                VehicleStatus.AVAILABLE,

                resolucion
                                .returnsToCentralAt()
                        ==
                        null

                        ? averia.location()

                        : central.location(),

                resolucion.availableFrom()
        );
    }

    private InventorySnapshot inventarioDisponible(
            Collection<Warehouse> almacenes,
            Map<String, Integer> retiros
    ) {

        InventorySnapshot inventario =
                InventorySnapshot.from(
                        almacenes
                );

        for (
                Map.Entry<String, Integer> retiro :
                retiros.entrySet()
        ) {

            Warehouse almacen;

            try {

                almacen =
                        inventario.warehouse(
                                retiro.getKey()
                        );

            } catch (
                    IllegalArgumentException ignored
            ) {

                continue;
            }

            if (
                    almacen.isCentral()
            ) {
                continue;
            }

            int cantidad =
                    Math.min(
                            retiro.getValue(),
                            inventario.availableStock(
                                    retiro.getKey()
                            )
                    );

            if (
                    cantidad > 0
            ) {

                inventario =
                        inventario.withdraw(
                                retiro.getKey(),
                                cantidad
                        );
            }
        }

        return inventario;
    }

    /*
     * =========================================================
     * SNAPSHOT
     * =========================================================
     */

    private OperationalSnapshot construirSnapshot(
            Instant ahora,
            Warehouse central,
            Collection<Warehouse> almacenes,
            List<RutaVigente> conservadas,
            Map<String, VehicleOperationalState> forzados,
            List<BreakdownEvent> averias,
            Map<String, Integer> retiros
    ) {

        InventorySnapshot inventario =
                inventarioDisponible(
                        almacenes,
                        retiros
                );

        Map<String, RutaVigente> activasPorVehiculo =
                new LinkedHashMap<>();

        for (
                RutaVigente ruta :
                conservadas
        ) {

            activasPorVehiculo.put(
                    ruta.vehiculoId(),
                    ruta
            );
        }

        Map<String, VehicleOperationalState> persistidos =
                fuente.estadosFlota(
                        ahora
                );

        Map<
                String,
                VehicleOperationalState
                > estados =
                new LinkedHashMap<>();

        for (
                Vehicle vehiculo :
                fuente.flota()
        ) {

            VehicleOperationalState estado;

            /*
             * Los estados forzados corresponden a averías o
             * regresos explícitos y tienen máxima prioridad.
             */
            if (
                    forzados.containsKey(
                            vehiculo.id()
                    )
            ) {

                estado =
                        forzados.get(
                                vehiculo.id()
                        );

            } else if (
                    activasPorVehiculo.containsKey(
                            vehiculo.id()
                    )
            ) {

                RutaVigente activa =
                        activasPorVehiculo.get(
                                vehiculo.id()
                        );

                if (
                        activa.programada() != null
                ) {

                    estado =
                            estadosRutas.estadoEn(
                                    activa,
                                    ahora
                            );

                } else {

                    /*
                     * Una ruta sin programación no debe abrir
                     * simultáneamente otra ruta para el mismo
                     * vehículo.
                     */
                    estado =
                            new VehicleOperationalState(
                                    vehiculo,
                                    VehicleStatus.IN_ROUTE,
                                    activa
                                            .ruta()
                                            .startLocation(),
                                    activa
                                            .ruta()
                                            .initialLoad(),
                                    Instant.MAX
                            );
                }

            } else {

                /*
                 * Para vehículos libres se conserva la última
                 * posición y carga confirmadas por el ejecutor.
                 *
                 * Esto permite que una unidad que terminó en un
                 * almacén intermedio vuelva a planificarse desde
                 * ese almacén, en vez de reaparecer en Central.
                 */
                estado =
                        persistidos.getOrDefault(
                                vehiculo.id(),

                                new VehicleOperationalState(
                                        vehiculo,
                                        VehicleStatus.AVAILABLE,
                                        central.location(),
                                        ahora
                                )
                        );
            }

            estados.put(
                    vehiculo.id(),
                    estado
            );
        }

        return new OperationalSnapshot(
                ahora,

                fuente.perfilFlota(),

                inventario,

                estados,

                fuente.calendarioMantenimiento(),

                new ShiftSchedule(
                        ShiftSchedule.DEFAULT_ZONE
                ),

                averias
        );
    }

    /*
     * =========================================================
     * HELPERS
     * =========================================================
     */

    private static Set<String> pedidosDe(
            List<DeliveryRoute> rutas
    ) {

        Set<String> ids =
                new HashSet<>();

        for (
                DeliveryRoute ruta :
                rutas
        ) {

            ruta
                    .stops()
                    .forEach(
                            parada -> {

                                if (
                                        parada instanceof
                                                DeliveryStop entrega
                                ) {

                                    ids.add(
                                            entrega
                                                    .order()
                                                    .id()
                                    );
                                }
                            }
                    );
        }

        return ids;
    }

    private EstadoEjecucion obtener(
            long ejecucionId
    ) {

        EstadoEjecucion estado =
                ejecuciones.get(
                        ejecucionId
                );

        if (
                estado == null
        ) {

            throw new RecursoNoEncontradoException(
                    "Ejecución",
                    ejecucionId
            );
        }

        return estado;
    }

    private static void validarHora(
            EstadoEjecucion estado,
            Instant ahora
    ) {

        Objects.requireNonNull(
                ahora,
                "ahora es requerido"
        );

        if (
                ahora.isBefore(
                        estado.ultimaHora
                )
        ) {

            throw new ReglaNegocioException(
                    "La hora "
                            +
                            ahora
                            +
                            " es anterior a la última replanificación ("
                            +
                            estado.ultimaHora
                            +
                            ")"
            );
        }
    }
}