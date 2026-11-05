package com.pucp.paqrap.modulos.planificacion.service;

import org.junit.jupiter.api.Test;

class TramosRutaTest {
    @Test void desvioSeConserva() { VerificacionTramos.desvioSeConserva(); }
    @Test void mapperCopiaTodosLosCampos() { VerificacionTramos.mapperCopiaTodosLosCampos(); }
    @Test void identidadesNoPierdenGeometria() { VerificacionTramos.identidadesNoPierdenGeometria(); }
    @Test void posicionSigueElDesvio() { VerificacionTramos.posicionSigueElDesvio(); }
    @Test void esperaEntreCalles() { VerificacionTramos.esperaEntreCalles(); }
    @Test void esperaAntesDePrimerTramo() { VerificacionTramos.esperaAntesDePrimerTramo(); }
    @Test void refrigerioYServicio() { VerificacionTramos.refrigerioYServicio(); }
    @Test void sinGeometriaNoInventa() { VerificacionTramos.sinGeometriaNoInventa(); }
    @Test void limitesTemporales() { VerificacionTramos.limitesTemporales(); }
    @Test void rutaSinProgramar() { VerificacionTramos.rutaSinProgramar(); }
}
