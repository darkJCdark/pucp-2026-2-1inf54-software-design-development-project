package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.config.EscenariosProperties;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioStatus;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.flota.entity.ParametrosFlotaActualizados;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.incidencias.entity.AveriaRegistrada;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.pedidos.entity.PedidoRegistrado;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.planificacion.service.PlanificadorPort;
import com.pucp.paqrap.modulos.planificacion.service.ReplanificacionService;
import com.pucp.paqrap.modulos.planificacion.service.PlanificadorSinOperacion;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Ejecuta los escenarios: mantiene el reloj simulado de cada ejecución activa, pide un plan al
 * {@link PlanificadorPort} al iniciar, cada {@code intervaloPlanificacion} simulado y en el tick siguiente a un
 * pedido nuevo, una avería o el inicio de un bloqueo, y cierra la ejecución
 * cuando termina (5D por tiempo, colapso cuando {@link DetectorColapso} lo declara) o la detiene el operario.
 *
 * <p>Los relojes viven en memoria: si el backend se reinicia, las ejecuciones que estaban activas se marcan como
 * FAILED al arrancar. Solo puede haber una ejecución activa a la vez, porque todas comparten la flota.
 */
@Service
public class MotorEscenarios {

    private static final Logger log = LoggerFactory.getLogger(MotorEscenarios.class);
    private static final Set<ScenarioStatus> ACTIVOS = Set.of(ScenarioStatus.RUNNING, ScenarioStatus.PAUSED);

    private final ScenarioExecutionRepository ejecucionRepository;
    private final TransactionTemplate transaccion;
    private final Clock relojReal;
    private final EscenariosProperties propiedades;
    private final ObjectProvider<PlanificadorPort> planificadores;
    private final EventosSimulacion eventos;
    private final EjecutorPlanService ejecutorPlan;
    private final DetectorColapso detectorColapso;
    private final OrderRepository pedidoRepository;
    private final ScenarioResultService resultadoService;
    /** Escenarios activos por id de ejecución; protegido por el monitor de esta instancia. */
    private final Map<Long, EscenarioActivo> activos = new HashMap<>();
    private ScheduledExecutorService ejecutor;

    private static final class EscenarioActivo {
        private final ScenarioType tipo;
        private final SimulationClock reloj;
        private Instant proximaPlanificacion;
        /** Los eventos de la simulación ya se procesaron hasta este instante simulado. */
        private Instant procesadoHasta;
        /** Bloqueos que empezaron y averías ocurridas desde el último plan: exigen replanificar en el próximo tick. */
        private final List<RoadBlock> bloqueosPendientes = new ArrayList<>();
        private final List<RoadBlock> bloqueosTerminadosPendientes = new ArrayList<>();
        private final List<BreakdownEvent> averiasPendientes = new ArrayList<>();
        /** Averías cuya ventana de indisponibilidad terminó desde el último plan. */
        private final List<BreakdownEvent> averiasTerminadasPendientes = new ArrayList<>();
        /** Llegaron pedidos desde el último plan: se replanifica en el próximo tick. */
        private boolean pedidosPendientes;
        /** Cambiaron los parámetros de un tipo de vehículo desde el último plan: se replanifica en el próximo tick. */
        private final Set<VehicleType> tiposFlotaCambiados = new HashSet<>();
        /** IDs de pedidos que pertenecen al intervalo simulado ya procesado. */
        private final Set<String> pedidosProcesados = new HashSet<>();
        /** Bloqueos cuyo inicio fue detectado en un intervalo ya procesado. */
        private final Set<Long> bloqueosProcesados = new HashSet<>();
        private final Set<Long> bloqueosTerminadosProcesados = new HashSet<>();
        /** Averías de esta ejecución detectadas en un intervalo ya procesado. */
        private final Set<Long> averiasProcesadas = new HashSet<>();
        /** Finalizaciones de avería ya procesadas, para no replanificar dos veces por el mismo evento. */
        private final Set<Long> averiasTerminadasProcesadas = new HashSet<>();

        private EscenarioActivo(ScenarioType tipo, SimulationClock reloj) {
            this.tipo = tipo;
            this.reloj = reloj;
            this.procesadoHasta = reloj.inicioSimulado();
        }
    }

    public MotorEscenarios(ScenarioExecutionRepository ejecucionRepository, PlatformTransactionManager transacciones,
                           Clock relojReal, EscenariosProperties propiedades,
                           ObjectProvider<PlanificadorPort> planificadores, EventosSimulacion eventos,
                           EjecutorPlanService ejecutorPlan, DetectorColapso detectorColapso,
                           OrderRepository pedidoRepository, ScenarioResultService resultadoService) {
        this.ejecucionRepository = ejecucionRepository;
        this.transaccion = new TransactionTemplate(transacciones);
        this.relojReal = relojReal;
        this.propiedades = propiedades;
        this.planificadores = planificadores;
        this.eventos = eventos;
        this.ejecutorPlan = ejecutorPlan;
        this.detectorColapso = detectorColapso;
        this.pedidoRepository = pedidoRepository;
        this.resultadoService = resultadoService;
    }

    /**
     * Una avería registrada en una ejecución activa la hace replanificar (CU-07) cuando el reloj llega a su
     * instante; esos eventos los entrega {@link EventosSimulacion}. Si se registra con un instante que el reloj ya
     * pasó, se replanifica en el próximo tick.
     */
    @TransactionalEventListener(fallbackExecution = true)
    public synchronized void alRegistrarAveria(AveriaRegistrada averia) {
        EscenarioActivo activo = activos.get(averia.ejecucionId());
        if (activo != null && !averia.ocurridaEn().isAfter(activo.procesadoHasta)) {
            activo.averiasPendientes.add(averia.averia());
        }
    }

    /**
     * Cambiar velocidad, capacidad o costo de un tipo de vehículo con un escenario en marcha lo hace replanificar en
     * el próximo tick. Las rutas en marcha del tipo cambiado se vuelven a programar con el perfil vigente; las de los demás
     * tipos se conservan.
     */
    @TransactionalEventListener(fallbackExecution = true)
    public synchronized void alActualizarParametrosFlota(ParametrosFlotaActualizados cambio) {
        for (EscenarioActivo activo : activos.values()) {
            activo.tiposFlotaCambiados.add(cambio.tipo());
        }
    }

    /**
     * Un pedido registrado a mano replanifica en el próximo tick. Si su hora de registro aún no llega, lo hará
     * {@link EventosSimulacion} cuando el reloj la alcance.
     */
    @TransactionalEventListener(fallbackExecution = true)
    public synchronized void alRegistrarPedido(PedidoRegistrado pedido) {
        for (EscenarioActivo activo : activos.values()) {
            if (!pedido.registradoEn().isBefore(activo.reloj.inicioSimulado())
                    && !pedido.registradoEn().isAfter(activo.procesadoHasta)) {
                activo.pedidosProcesados.add(pedido.pedidoId());
                activo.pedidosPendientes = true;
            }
        }
    }

    /** Un tick de duración cero desactiva el avance automático (los tests llaman a {@link #tick()} a mano). */
    @PostConstruct
    void arrancar() {
        long periodoMs = propiedades.tick().toMillis();
        if (periodoMs > 0) {
            ejecutor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "motor-escenarios"));
            ejecutor.scheduleAtFixedRate(this::tickSeguro, periodoMs, periodoMs, TimeUnit.MILLISECONDS);
        }
    }

    @PreDestroy
    void apagar() {
        if (ejecutor != null) {
            ejecutor.shutdownNow();
        }
    }

    /** Los relojes no sobreviven a un reinicio: las ejecuciones que quedaron activas ya no pueden continuar. */
    @EventListener(ApplicationReadyEvent.class)
    public void cerrarEjecucionesHuerfanas() {
        transaccion.executeWithoutResult(estado -> {
            for (ScenarioExecutionEntity huerfana : ejecucionRepository.findByStatusIn(ACTIVOS)) {
                log.warn("La ejecución {} quedó activa antes de un reinicio; se marca como FAILED",
                        huerfana.getExecutionId());
                huerfana.terminar(ScenarioStatus.FAILED, relojReal.instant(), huerfana.getSimulationStartedAt());
            }
        });
    }

    public synchronized void iniciar(long ejecucionId) {
        if (!ejecucionRepository.existsById(ejecucionId)) {
            throw new RecursoNoEncontradoException("Ejecución", ejecucionId);
        }
        if (ejecucionRepository.existsByStatusIn(ACTIVOS)) {
            throw new ReglaNegocioException("Ya hay un escenario en ejecución o en pausa; deténgalo antes de iniciar otro");
        }
        ScenarioExecutionEntity ejecucion = actualizar(ejecucionId, e -> e.iniciar(relojReal.instant()));

        SimulationClock reloj = new SimulationClock(relojReal, ejecucion.getSimulationStartedAt(),
                propiedades.factorPara(ejecucion.getScenarioType()));
        reloj.reanudar();
        EscenarioActivo activo = new EscenarioActivo(ejecucion.getScenarioType(), reloj);
        activos.put(ejecucionId, activo);
        ejecutorPlan.prepararInicio(ejecucionId, ejecucion.getSimulationStartedAt());
        activo.pedidosProcesados.addAll(pedidoRepository.idsRegistradosEn(ejecucion.getSimulationStartedAt()));
        detectorColapso.reiniciar(ejecucionId);
        planificar(ejecucionId, activo, PlanificadorPort.Motivo.INICIAL);
        revisarColapso(ejecucionId, activo);
    }

    public synchronized void pausar(long ejecucionId) {
        actualizar(ejecucionId, ScenarioExecutionEntity::pausar);
        activo(ejecucionId).reloj.pausar();
    }

    public synchronized void reanudar(long ejecucionId) {
        actualizar(ejecucionId, ScenarioExecutionEntity::reanudar);
        activo(ejecucionId).reloj.reanudar();
    }

    public synchronized void detener(long ejecucionId) {
        EscenarioActivo activo = activos.get(ejecucionId);
        Instant instanteSimulado = activo != null ? activo.reloj.ahora() : null;
        actualizar(ejecucionId, e -> e.terminar(ScenarioStatus.STOPPED, relojReal.instant(),
                instanteSimulado != null ? instanteSimulado : e.getSimulationStartedAt()));
        persistirResultado(ejecucionId, activo);
        activos.remove(ejecucionId);
        planificador().finalizar(ejecucionId);
    }

    /** Ids de las ejecuciones activas (en marcha o pausadas), en orden. */
    public synchronized List<Long> ejecucionesActivas() {
        return activos.keySet().stream().sorted().toList();
    }

    /** Instante simulado actual de una ejecución activa. */
    public synchronized Optional<Instant> instanteSimulado(long ejecucionId) {
        return Optional.ofNullable(activos.get(ejecucionId)).map(activo -> activo.reloj.ahora());
    }

    /** Instante simulado hasta el que ya se procesaron eventos y se ejecutó el plan de una ejecución activa. */
    public synchronized Optional<Instant> ejecutadoHasta(long ejecucionId) {
        return Optional.ofNullable(activos.get(ejecucionId)).map(activo -> activo.procesadoHasta);
    }

    /** Avanza todos los escenarios en marcha: cierra los que terminaron y pide los planes que tocan. */
    public synchronized void tick() {
        for (Map.Entry<Long, EscenarioActivo> entrada : List.copyOf(activos.entrySet())) {
            long ejecucionId = entrada.getKey();
            EscenarioActivo activo = entrada.getValue();
            if (!activo.reloj.estaEnMarcha()) {
                continue;
            }
            try {
                avanzar(ejecucionId, activo);
            } catch (RuntimeException excepcion) {
                log.error("Falló el escenario {}; se marca como FAILED", ejecucionId, excepcion);
                finalizar(ejecucionId, ScenarioStatus.FAILED, activo.reloj.ahora());
            }
        }
    }

    private void avanzar(long ejecucionId, EscenarioActivo activo) {
        Instant ahora = activo.reloj.ahora();
        Optional<Instant> fin = activo.tipo.duracionSimulada().map(activo.reloj.inicioSimulado()::plus);
        Instant hasta = fin.filter(ahora::isAfter).orElse(ahora);

        if (hasta.isAfter(activo.procesadoHasta)) {
            EventosSimulacion.Resumen resumen = eventos.procesar(ejecucionId, activo.procesadoHasta, hasta);
            // El plan vigente se ejecuta hasta ahora antes de que un plan nuevo lo reemplace.
            ejecutorPlan.avanzar(ejecucionId, activo.procesadoHasta, hasta);
            activo.procesadoHasta = hasta;
            activo.pedidosProcesados.addAll(resumen.pedidosLlegadosIds());
            activo.bloqueosProcesados.addAll(resumen.bloqueosIniciadosIds());
            for (int indice = 0; indice < resumen.bloqueosTerminados().size(); indice++) {
                if (activo.bloqueosTerminadosProcesados.add(resumen.bloqueosTerminadosIds().get(indice))) {
                    activo.bloqueosTerminadosPendientes.add(resumen.bloqueosTerminados().get(indice));
                }
            }
            activo.averiasProcesadas.addAll(resumen.averiasIds());
            for (int indice = 0; indice < resumen.averiasTerminadas().size(); indice++) {
                if (activo.averiasTerminadasProcesadas.add(resumen.averiasTerminadasIds().get(indice))) {
                    activo.averiasTerminadasPendientes.add(resumen.averiasTerminadas().get(indice));
                }
            }
            activo.bloqueosPendientes.addAll(resumen.bloqueosIniciados());
            activo.averiasPendientes.addAll(resumen.averias());
            if (!resumen.pedidosLlegadosIds().isEmpty()) {
                activo.pedidosPendientes = true;
            }
        }
        if (revisarColapso(ejecucionId, activo)) {
            return;
        }
        if (fin.isPresent() && !ahora.isBefore(fin.get())) {
            finalizar(ejecucionId, ScenarioStatus.COMPLETED, fin.get());
            return;
        }
        if (!activo.bloqueosPendientes.isEmpty() || !activo.bloqueosTerminadosPendientes.isEmpty()
                || !activo.averiasPendientes.isEmpty() || !activo.averiasTerminadasPendientes.isEmpty()) {
            planificar(ejecucionId, activo, PlanificadorPort.Motivo.INCIDENCIA);
        } else if (activo.pedidosPendientes) {
            planificar(ejecucionId, activo, PlanificadorPort.Motivo.NUEVO_PEDIDO);
        } else if (!activo.tiposFlotaCambiados.isEmpty() || !ahora.isBefore(activo.proximaPlanificacion)) {
            planificar(ejecucionId, activo, PlanificadorPort.Motivo.PERIODICO);
        }
    }

    /**
     * El plan se pide para el instante hasta el que se ejecutó el plan vigente: así el corte de la replanificación
     * coincide con lo que el ejecutor ya aplicó.
     */
    private void planificar(long ejecucionId, EscenarioActivo activo, PlanificadorPort.Motivo motivo) {
        Instant ahora = activo.procesadoHasta;
        // Que SA deje pedidos sin asignar en un ciclo no es colapso: lo decide DetectorColapso.
        planificador().planificar(new PlanificadorPort.SolicitudCiclo(
                ejecucionId, ahora, motivo, activo.bloqueosPendientes, activo.bloqueosTerminadosPendientes,
                activo.averiasPendientes, activo.averiasTerminadasPendientes,
                List.copyOf(activo.tiposFlotaCambiados)));
        activo.proximaPlanificacion = ahora.plus(propiedades.intervaloPlanificacion());
        activo.bloqueosPendientes.clear();
        activo.bloqueosTerminadosPendientes.clear();
        activo.averiasPendientes.clear();
        activo.averiasTerminadasPendientes.clear();
        activo.pedidosPendientes = false;
        activo.tiposFlotaCambiados.clear();
    }

    /**
     * Registra el primer colapso de la ejecución (en 5D y Día a Día solo se informa en el monitoreo); el escenario
     * de colapso termina en el instante del colapso. Devuelve true si la ejecución terminó.
     */
    private boolean revisarColapso(long ejecucionId, EscenarioActivo activo) {
        Optional<DetectorColapso.Colapso> colapso = detectorColapso.revisar(ejecucionId,
                activo.reloj.inicioSimulado(), activo.procesadoHasta);
        if (colapso.isPresent() && activo.tipo == ScenarioType.COLLAPSE) {
            finalizar(ejecucionId, ScenarioStatus.COLLAPSED, colapso.get().instante());
            return true;
        }
        return false;
    }

    private void finalizar(long ejecucionId, ScenarioStatus estadoFinal, Instant instanteSimulado) {
        actualizar(ejecucionId, e -> e.terminar(estadoFinal, relojReal.instant(), instanteSimulado));
        EscenarioActivo activo = activos.get(ejecucionId);
        persistirResultado(ejecucionId, activo);
        activos.remove(ejecucionId);
        planificador().finalizar(ejecucionId);
    }

    /** Construye el resultado mientras todavía existen los acumuladores del motor y del planificador. */
    private void persistirResultado(long ejecucionId, EscenarioActivo activo) {
        if (activo == null) {
            throw new IllegalStateException("No hay estado activo para construir el resultado de " + ejecucionId);
        }
        EjecutorPlanService.Metricas metricas = ejecutorPlan.metricas(ejecucionId)
                .orElseThrow(() -> new IllegalStateException("Faltan métricas ejecutadas para " + ejecucionId));
        ReplanificacionService.MetricasPlanificacion planificacion = planificador().metricas(ejecucionId)
                .orElseThrow(() -> new IllegalStateException("Faltan métricas de planificación para " + ejecucionId));
        resultadoService.persistir(ejecucionId, activo.pedidosProcesados.size(), metricas,
                planificacion.totalComputationMs(), planificacion.replanningCount(),
                activo.averiasProcesadas.size(), activo.bloqueosProcesados.size());
    }

    private PlanificadorPort planificador() {
        return planificadores.getIfAvailable(PlanificadorSinOperacion::new);
    }

    private void tickSeguro() {
        try {
            tick();
        } catch (RuntimeException excepcion) {
            log.error("Error inesperado en el motor de escenarios", excepcion);
        }
    }

    private EscenarioActivo activo(long ejecucionId) {
        EscenarioActivo activo = activos.get(ejecucionId);
        if (activo == null) {
            throw new IllegalStateException("La ejecución " + ejecucionId + " no tiene reloj activo");
        }
        return activo;
    }

    /** Aplica un cambio de estado a la ejecución en su propia transacción y devuelve la entidad actualizada. */
    private ScenarioExecutionEntity actualizar(long ejecucionId, Consumer<ScenarioExecutionEntity> cambio) {
        return transaccion.execute(estado -> {
            ScenarioExecutionEntity ejecucion = ejecucionRepository.findById(ejecucionId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Ejecución", ejecucionId));
            cambio.accept(ejecucion);
            return ejecucion;
        });
    }
}
