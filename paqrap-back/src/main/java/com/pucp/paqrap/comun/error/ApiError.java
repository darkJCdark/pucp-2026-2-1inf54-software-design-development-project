package com.pucp.paqrap.comun.error;

import java.time.Instant;
import java.util.List;

/** Cuerpo uniforme de las respuestas de error de la API. */
public record ApiError(Instant timestamp, int status, String error, String mensaje, String ruta,
                       List<CampoInvalido> detalles) {

    public record CampoInvalido(String campo, String mensaje) {
    }
}
