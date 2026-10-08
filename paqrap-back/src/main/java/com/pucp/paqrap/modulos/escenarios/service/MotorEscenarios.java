package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.config.EscenariosProperties;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioStatus;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.planificacion.service.PlanificadorPort;
import com.pucp.paqrap.modulos.planificacion.service.PlanificadorSinOperacion;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
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
 * {@link PlanificadorPort} al iniciar y cada {@code intervaloPlanificacion} simulado, y cierra la ejecución
 * cuando termina (5D por tiempo, colapso cuando el planificador lo reporta) o la detiene el operario.
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
    /** Escenarios activos por id de ejecución; protegido por el monitor de esta instancia. */
    private final Map<Long, EscenarioActivo> activos = new HashMap<>();
    private ScheduledExecutorService ejecutor;

    private static final class EscenarioActivo {
        private final ScenarioType tipo;
        private final SimulationClock reloj;
        private Instant proximaPlanificacion;

        private EscenarioActivo(ScenarioType tipo, SimulationClock reloj) {
            this.tipo = tipo;
            this.reloj = reloj;
        }
    }

    public MotorEscenarios(ScenarioExecutionRepository ejecucionRepository, PlatformTransactionManager transacciones,
                           Clock relojReal, EscenariosProperties propiedades,
                           ObjectProvider<PlanificadorPort> planificadores) {
        this.ejecucionRepository = ejecucionRepository;
        this.transaccion = new TransactionTemplate(transacciones);
        this.relojReal = relojReal;
        this.propiedades = propiedades;
        this.planificadores = planificadores;
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
        planificar(ejecucionId, activo, PlanificadorPort.Motivo.INICIAL);
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
        activos.remove(ejecucionId);
    }

    /** Instante simulado actual de una ejecución activa. */
    public synchronized Optional<Instant> instanteSimulado(long ejecucionId) {
        return Optional.ofNullable(activos.get(ejecucionId)).map(activo -> activo.reloj.ahora());
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
        if (fin.isPresent() && !ahora.isBefore(fin.get())) {
            finalizar(ejecucionId, ScenarioStatus.COMPLETED, fin.get());
            return;
        }
        if (!ahora.isBefore(activo.proximaPlanificacion)) {
            planificar(ejecucionId, activo, PlanificadorPort.Motivo.PERIODICO);
        }
    }

    private void planificar(long ejecucionId, EscenarioActivo activo, PlanificadorPort.Motivo motivo) {
        Instant ahora = activo.reloj.ahora();
        PlanificadorPort.ResultadoCiclo resultado = planificadores.getIfAvailable(PlanificadorSinOperacion::new)
                .planificar(new PlanificadorPort.SolicitudCiclo(ejecucionId, ahora, motivo));
        activo.proximaPlanificacion = ahora.plus(propiedades.intervaloPlanificacion());
        if (resultado.colapso() && activo.tipo == ScenarioType.COLLAPSE) {
            finalizar(ejecucionId, ScenarioStatus.COLLAPSED, ahora);
        }
    }

    private void finalizar(long ejecucionId, ScenarioStatus estadoFinal, Instant instanteSimulado) {
        actualizar(ejecucionId, e -> e.terminar(estadoFinal, relojReal.instant(), instanteSimulado));
        activos.remove(ejecucionId);
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
