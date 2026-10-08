package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Instant;

/**
 * Punto de conexión entre el motor de escenarios y el planificador (SA). El motor lo invoca en cada ciclo de
 * planificación; la implementación arma la entrada desde la BD, ejecuta SA y persiste el plan.
 *
 * <p>Para conectar SA basta con una clase {@code @Component} que implemente esta interfaz. Mientras no exista,
 * el motor usa {@link PlanificadorSinOperacion}.
 */
public interface PlanificadorPort {

    ResultadoCiclo planificar(SolicitudCiclo solicitud);

    /** Por qué se pide planificar en este momento. */
    enum Motivo {
        /** Primer plan al iniciar el escenario. */
        INICIAL,
        /** Ciclo periódico del escenario. */
        PERIODICO,
        /** Una avería o un bloqueo afectó al plan vigente. */
        INCIDENCIA
    }

    /**
     * @param ejecucionId   ejecución de {@code scenario_executions} a la que pertenece el plan
     * @param instante      instante simulado para el que se planifica
     */
    record SolicitudCiclo(long ejecucionId, Instant instante, Motivo motivo) {
    }

    /**
     * @param colapso true si el planificador ya no puede cumplir los plazos de los pedidos pendientes; en el
     *                escenario de colapso, termina la ejecución
     */
    record ResultadoCiclo(boolean colapso) {
        public static final ResultadoCiclo SIN_COLAPSO = new ResultadoCiclo(false);
    }
}
