import type {
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
};