import type {
  AveriaResponseDto,
  RegistrarAveriaRequestDto,
} from "@/api/incidencias.dto";

import {
  apiRequest,
} from "@/services/api";

export const incidenciasService = {
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
