import type {
  AveriaResponseDto,
  RegistrarAveriaRequestDto,
} from "@/api/incidencias.dto";

import {
  apiRequest,
} from "@/services/api";

export const incidenciasService = {
  obtenerConfiguracionMenor(): Promise<{ duracionMinutos: number }> {
    return apiRequest("/api/incidencias/averias/configuracion/MINOR");
  },
  actualizarConfiguracionMenor(duracionMinutos: number): Promise<{ duracionMinutos: number }> {
    return apiRequest("/api/incidencias/averias/configuracion/MINOR", {
      method: "PUT", body: JSON.stringify({ duracionMinutos }),
    });
  },
  registrarAveria(
    request: RegistrarAveriaRequestDto,
  ): Promise<AveriaResponseDto> {
    return apiRequest<AveriaResponseDto>(
      "/api/incidencias/averias",
      {
        method: "POST",
        body: JSON.stringify(request),
      },
    );
  },
};
