import type {
  CrearEscenarioRequestDto,
  EscenarioResponseDto,
  PaginaEscenariosResponseDto,
} from "@/api/escenarios.dto";

import {
  apiRequest,
} from "@/services/api";

export const escenariosService = {
  crear(
    request: CrearEscenarioRequestDto,
  ): Promise<EscenarioResponseDto> {
    return apiRequest<EscenarioResponseDto>(
      "/api/escenarios",
      {
        method: "POST",
        body: JSON.stringify(request),
      },
    );
  },

  listar(
    page = 0,
    size = 20,
  ): Promise<PaginaEscenariosResponseDto> {
    return apiRequest<PaginaEscenariosResponseDto>(
      `/api/escenarios?page=${page}&size=${size}`,
    );
  },

  obtener(
    id: number,
  ): Promise<EscenarioResponseDto> {
    return apiRequest<EscenarioResponseDto>(
      `/api/escenarios/${id}`,
    );
  },

  iniciar(
    id: number,
  ): Promise<EscenarioResponseDto> {
    return apiRequest<EscenarioResponseDto>(
      `/api/escenarios/${id}/iniciar`,
      {
        method: "POST",
      },
    );
  },

  pausar(
    id: number,
  ): Promise<EscenarioResponseDto> {
    return apiRequest<EscenarioResponseDto>(
      `/api/escenarios/${id}/pausar`,
      {
        method: "POST",
      },
    );
  },

  reanudar(
    id: number,
  ): Promise<EscenarioResponseDto> {
    return apiRequest<EscenarioResponseDto>(
      `/api/escenarios/${id}/reanudar`,
      {
        method: "POST",
      },
    );
  },

  detener(
    id: number,
  ): Promise<EscenarioResponseDto> {
    return apiRequest<EscenarioResponseDto>(
      `/api/escenarios/${id}/detener`,
      {
        method: "POST",
      },
    );
  },
};