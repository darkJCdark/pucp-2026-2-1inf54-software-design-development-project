package com.pucp.paqrap.modulos.pedidos.dto;

/** Mes con pedidos cargados desde un archivo {@code ventas.AAAAMM.txt}; {@code periodo} tiene la forma AAAA-MM. */
public record PeriodoCargadoResponse(String periodo, long pedidos) {
}
