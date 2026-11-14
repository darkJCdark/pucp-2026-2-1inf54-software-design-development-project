package com.pucp.paqrap.modulos.redvial.dto;

import java.util.List;

public record CargaBloqueosMultipleResponse(List<CargaBloqueosResponse> archivos, int reemplazados, int registrados) {
    public CargaBloqueosMultipleResponse { archivos = List.copyOf(archivos); }

    public static CargaBloqueosMultipleResponse de(List<CargaBloqueosResponse> archivos) {
        return new CargaBloqueosMultipleResponse(archivos,
                archivos.stream().mapToInt(CargaBloqueosResponse::reemplazados).sum(),
                archivos.stream().mapToInt(CargaBloqueosResponse::registrados).sum());
    }
}
