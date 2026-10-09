import type {
  AlmacenResponseDto,
} from "@/api/almacenes.dto";

import {
  apiRequest,
} from "@/services/api";

export const almacenesService = {
  listar(): Promise<
    AlmacenResponseDto[]
  > {
    return apiRequest<
      AlmacenResponseDto[]
    >("/api/almacenes");
  },

  obtener(
    id: string,
  ): Promise<AlmacenResponseDto> {
    return apiRequest<AlmacenResponseDto>(
      `/api/almacenes/${encodeURIComponent(
        id,
      )}`,
    );
  },
};