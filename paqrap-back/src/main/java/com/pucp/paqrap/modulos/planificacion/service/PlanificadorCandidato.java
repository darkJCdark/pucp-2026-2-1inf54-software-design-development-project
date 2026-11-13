package com.pucp.paqrap.modulos.planificacion.service;

/**
 * Extensión opcional del planificador para separar el cálculo costoso de su publicación.
 * Los candidatos no deben modificar rutas, inventario ni snapshots antes de confirmarse.
 */
public interface PlanificadorCandidato extends PlanificadorPort {

    interface Candidato {
    }

    Candidato preparar(SolicitudCiclo solicitud);

    ResultadoCiclo confirmar(Candidato candidato);

    void descartar(Candidato candidato);
}
