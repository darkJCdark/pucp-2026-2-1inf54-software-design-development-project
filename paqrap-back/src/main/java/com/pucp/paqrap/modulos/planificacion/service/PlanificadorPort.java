package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Punto de conexión entre el motor de escenarios y el planificador (SA). El motor lo invoca en cada ciclo de
 * planificación; la implementación arma la entrada desde la BD, ejecuta SA y persiste el plan.
 *
 * <p>Para conectar SA basta con una clase {@code @Component} que implemente esta interfaz. Mientras no exista,
 * el motor usa {@link PlanificadorSinOperacion}.
 */
public interface PlanificadorPort {

    ResultadoCiclo planificar(SolicitudCiclo solicitud);

    /** La ejecución terminó (por tiempo, colapso, falla o detención): se libera su estado en memoria. */
    default void finalizar(long ejecucionId) {
    }

    /** Métricas de planificación disponibles mientras la ejecución sigue viva. */
    default Optional<ReplanificacionService.MetricasPlanificacion> metricas(long ejecucionId) {
        return Optional.empty();
    }

    /** Por qué se pide planificar en este momento. */
    enum Motivo {
        /** Primer plan al iniciar el escenario. */
        INICIAL,
        /** Ciclo periódico del escenario. */
        PERIODICO,
        /** Llegaron pedidos nuevos (de archivo al pasar su hora de registro, o registrados a mano). */
        NUEVO_PEDIDO,
        /** Una avería o un bloqueo afectó al plan vigente. */
        INCIDENCIA
    }

    /**
     * @param ejecucionId       ejecución de {@code scenario_executions} a la que pertenece el plan
     * @param instante          instante simulado para el que se planifica
     * @param bloqueosIniciados bloqueos que empezaron desde el ciclo anterior
     * @param averias           averías de la ejecución ocurridas desde el ciclo anterior
     */
    record SolicitudCiclo(long ejecucionId, Instant instante, Motivo motivo, List<RoadBlock> bloqueosIniciados,
                          List<RoadBlock> bloqueosTerminados, List<BreakdownEvent> averias,
                          List<BreakdownEvent> averiasTerminadas, List<VehicleType> tiposFlotaActualizados) {
        public SolicitudCiclo {
            bloqueosIniciados = bloqueosIniciados == null ? List.of() : List.copyOf(bloqueosIniciados);
            bloqueosTerminados = bloqueosTerminados == null ? List.of() : List.copyOf(bloqueosTerminados);
            averias = averias == null ? List.of() : List.copyOf(averias);
            averiasTerminadas = averiasTerminadas == null ? List.of() : List.copyOf(averiasTerminadas);
            tiposFlotaActualizados = tiposFlotaActualizados == null ? List.of() : List.copyOf(tiposFlotaActualizados);
        }

        /** Conserva el contrato anterior: sin averías terminadas explícitas. */
        public SolicitudCiclo(long ejecucionId, Instant instante, Motivo motivo, List<RoadBlock> bloqueosIniciados,
                              List<RoadBlock> bloqueosTerminados, List<BreakdownEvent> averias,
                              List<VehicleType> tiposFlotaActualizados) {
            this(ejecucionId, instante, motivo, bloqueosIniciados, bloqueosTerminados, averias,
                    List.of(), tiposFlotaActualizados);
        }

        public SolicitudCiclo(long ejecucionId, Instant instante, Motivo motivo, List<RoadBlock> bloqueosIniciados,
                              List<RoadBlock> bloqueosTerminados, List<BreakdownEvent> averias) {
            this(ejecucionId, instante, motivo, bloqueosIniciados, bloqueosTerminados, averias,
                    List.of(), List.of());
        }

        public SolicitudCiclo(long ejecucionId, Instant instante, Motivo motivo, List<RoadBlock> bloqueosIniciados,
                              List<BreakdownEvent> averias) {
            this(ejecucionId, instante, motivo, bloqueosIniciados, List.of(), averias,
                    List.of(), List.of());
        }

        public SolicitudCiclo(long ejecucionId, Instant instante, Motivo motivo) {
            this(ejecucionId, instante, motivo, List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    /**
     * @param colapso true si SA dejó pedidos sin asignar en este ciclo. Es informativo: el motor no termina la
     *                ejecución por esto, porque SA con presupuesto de tiempo puede no hallar una solución que sí
     *                existe; el colapso lo declara {@code DetectorColapso} del módulo de escenarios
     */
    record ResultadoCiclo(boolean colapso) {
        public static final ResultadoCiclo SIN_COLAPSO = new ResultadoCiclo(false);
    }
}
