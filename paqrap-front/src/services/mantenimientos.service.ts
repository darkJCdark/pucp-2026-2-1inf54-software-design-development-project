import type {
  MantenimientoRequestDto,
  MantenimientoResponseDto,
} from "@/api/mantenimientos.dto";

import {
  apiRequest,
} from "@/services/api";

interface ListarMantenimientosParams {
  vehiculoId?: string;
  desde?: string;
  hasta?: string;
}

export const mantenimientosService = {
  listar(
    params: ListarMantenimientosParams = {},
  ): Promise<MantenimientoResponseDto[]> {
    const query = new URLSearchParams();

    if (params.vehiculoId) {
      query.set(
        "vehiculoId",
        params.vehiculoId,
      );
    }

    if (params.desde) {
      query.set("desde", params.desde);
    }

    if (params.hasta) {
      query.set("hasta", params.hasta);
    }

    const suffix = query.toString();

    return apiRequest<
      MantenimientoResponseDto[]
    >(
      `/api/flota/mantenimientos${
        suffix ? `?${suffix}` : ""
      }`,
    );
  },

  programar(
    request: MantenimientoRequestDto,
  ): Promise<MantenimientoResponseDto> {
    return apiRequest<MantenimientoResponseDto>(
      "/api/flota/mantenimientos",
      {
        method: "POST",
        body: JSON.stringify(request),
      },
    );
  },

  cancelar(
    vehiculoId: string,
    fecha: string,
  ): Promise<void> {
    return apiRequest<void>(
      `/api/flota/mantenimientos/${encodeURIComponent(
        vehiculoId,
      )}/${fecha}`,
      {
        method: "DELETE",
      },
    );
  },
};
