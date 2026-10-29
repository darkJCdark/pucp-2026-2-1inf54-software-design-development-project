import type {
  ActualizarTipoVehiculoRequestDto,
  TipoVehiculoBackend,
  TipoVehiculoResponseDto,
  VehiculoResponseDto,
} from "@/api/flota.dto";

import {
  apiRequest,
} from "@/services/api";

export const flotaService = {
  listarVehiculos(): Promise<
    VehiculoResponseDto[]
  > {
    return apiRequest<
      VehiculoResponseDto[]
    >("/api/flota/vehiculos");
  },

  obtenerVehiculo(
    id: string,
  ): Promise<VehiculoResponseDto> {
    return apiRequest<VehiculoResponseDto>(
      `/api/flota/vehiculos/${encodeURIComponent(
        id,
      )}`,
    );
  },


  altaVehiculo(
    tipo: TipoVehiculoBackend,
  ): Promise<VehiculoResponseDto> {
    return apiRequest<VehiculoResponseDto>(
      "/api/flota/vehiculos",
      {
        method: "POST",
        body: JSON.stringify({ tipo }),
      },
    );
  },

  bajaVehiculo(
    id: string,
  ): Promise<void> {
    return apiRequest<void>(
      `/api/flota/vehiculos/${encodeURIComponent(id)}`,
      { method: "DELETE" },
    );
  },

  listarTipos(): Promise<
    TipoVehiculoResponseDto[]
  > {
    return apiRequest<
      TipoVehiculoResponseDto[]
    >("/api/flota/tipos");
  },

  obtenerTipo(
    tipo: string,
  ): Promise<TipoVehiculoResponseDto> {
    return apiRequest<TipoVehiculoResponseDto>(
      `/api/flota/tipos/${encodeURIComponent(
        tipo,
      )}`,
    );
  },

  actualizarTipo(
    tipo: TipoVehiculoBackend,
    request: ActualizarTipoVehiculoRequestDto,
  ): Promise<TipoVehiculoResponseDto> {
    return apiRequest<TipoVehiculoResponseDto>(
      `/api/flota/tipos/${tipo}`,
      {
        method: "PUT",
        body: JSON.stringify(request),
      },
    );
  },
};