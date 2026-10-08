package com.pucp.paqrap.modulos.pedidos.dto;

import java.time.YearMonth;

/** Resultado de cargar un archivo de ventas: los pedidos que ya existían se omiten, no se duplican. */
public record CargaPedidosResponse(String archivo, YearMonth periodo, int leidos, int registrados, int omitidos) {
}
