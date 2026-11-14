package com.pucp.paqrap.modulos.pedidos.dto;

import java.util.List;

/** Resultado de una sola transacción que carga hasta dos archivos mensuales. */
public record CargaPedidosMultipleResponse(List<CargaPedidosResponse> archivos, int leidos,
                                           int registrados, int omitidos) {
    public CargaPedidosMultipleResponse {
        archivos = List.copyOf(archivos);
    }

    public static CargaPedidosMultipleResponse de(List<CargaPedidosResponse> archivos) {
        return new CargaPedidosMultipleResponse(archivos,
                archivos.stream().mapToInt(CargaPedidosResponse::leidos).sum(),
                archivos.stream().mapToInt(CargaPedidosResponse::registrados).sum(),
                archivos.stream().mapToInt(CargaPedidosResponse::omitidos).sum());
    }
}
