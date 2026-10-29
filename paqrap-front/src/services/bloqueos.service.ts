import type {
  BloqueoResponseDto,
  CargaBloqueosResponseDto,
} from "@/api/bloqueos.dto";

import {
  apiRequest,
} from "@/services/api";

export const bloqueosService = {
  cargarArchivo(
    archivo: File,
    periodo?: string,
  ): Promise<CargaBloqueosResponseDto> {
    const formData = new FormData();

    formData.append(
      "archivo",
      archivo,
    );

    return apiRequest<CargaBloqueosResponseDto>(
      `/api/red-vial/bloqueos/carga${periodo ? `?periodo=${encodeURIComponent(periodo)}` : ""}`,
      {
        method: "POST",
        body: formData,
      },
    );
  },

  listar(params: { desde?: string; hasta?: string; vigenteEn?: string } = {}): Promise<BloqueoResponseDto[]> {
    const query = new URLSearchParams();
    if (params.desde) query.set("desde", params.desde);
    if (params.hasta) query.set("hasta", params.hasta);
    if (params.vigenteEn) query.set("vigenteEn", params.vigenteEn);
    const sufijo = query.size > 0 ? `?${query.toString()}` : "";
    return apiRequest<BloqueoResponseDto[]>(`/api/red-vial/bloqueos${sufijo}`);
  },
};
