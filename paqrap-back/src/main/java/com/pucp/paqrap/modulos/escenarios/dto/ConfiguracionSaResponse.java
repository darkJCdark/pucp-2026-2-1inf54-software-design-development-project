package com.pucp.paqrap.modulos.escenarios.dto;

/** Configuraci?n efectiva e inmutable de SA que pertenece a una ejecuci?n de escenario. */
public record ConfiguracionSaResponse(long semilla, double temperaturaInicial, double temperaturaMinima,
                                      double factorEnfriamiento, int iteracionesPorTemperatura) {
}
