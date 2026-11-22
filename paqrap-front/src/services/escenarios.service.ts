import type {
  CrearEscenarioRequestDto,
  EscenarioResponseDto,
  PaginaEscenariosResponseDto,
} from "@/api/escenarios.dto";

import {
  apiRequest,
} from "@/services/api";
import { esEjecucionActiva } from "@/lib/ejecuciones";

export const escenariosService = {
  async listarActivas(): Promise<EscenarioResponseDto[]> {
    const activas: EscenarioResponseDto[] = [];
    // Día a Día puede seguir activa detrás de muchas páginas de simulaciones.
    let page = 0;
    let totalPaginas: number;
    do {
      const pagina = await escenariosService.listar(page, 100);
      if (!Array.isArray(pagina.contenido) || !Number.isFinite(pagina.totalPaginas)) {
        throw new Error("No se pudo verificar el estado de las ejecuciones.");
      }
      activas.push(...pagina.contenido.filter(esEjecucionActiva));
      totalPaginas = pagina.totalPaginas;
      page += 1;
    } while (page < totalPaginas);
    return [...new Map(activas.map((ejecucion) => [ejecucion.id, ejecucion])).values()];
  },

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
