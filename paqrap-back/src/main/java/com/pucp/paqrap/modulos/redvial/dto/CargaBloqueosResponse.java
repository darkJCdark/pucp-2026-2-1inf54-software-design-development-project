package com.pucp.paqrap.modulos.redvial.dto;

import java.time.YearMonth;

/** Resultado de cargar un archivo mensual: los bloqueos previos de ese mes se reemplazan. */
public record CargaBloqueosResponse(String archivo, YearMonth periodo, int reemplazados, int registrados) {
}
