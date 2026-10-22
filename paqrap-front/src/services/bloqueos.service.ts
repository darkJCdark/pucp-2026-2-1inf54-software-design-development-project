import type {
  CargaBloqueosResponseDto,
} from "@/api/bloqueos.dto";

import {
  apiRequest,
} from "@/services/api";

export const bloqueosService = {
  cargarArchivo(
    archivo: File,
  ): Promise<CargaBloqueosResponseDto> {
    const formData = new FormData();

    formData.append(
      "archivo",
      archivo,
    );

    return apiRequest<CargaBloqueosResponseDto>(
      "/api/red-vial/bloqueos/carga",
      {
        method: "POST",
        body: formData,
      },
    );
  },
};
