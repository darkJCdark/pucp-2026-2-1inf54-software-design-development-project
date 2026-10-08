package com.pucp.paqrap.modulos.planificacion.service;

/** Implementación vacía mientras SA no esté integrado: no planifica y nunca reporta colapso. */
public class PlanificadorSinOperacion implements PlanificadorPort {

    @Override
    public ResultadoCiclo planificar(SolicitudCiclo solicitud) {
        return ResultadoCiclo.SIN_COLAPSO;
    }
}
