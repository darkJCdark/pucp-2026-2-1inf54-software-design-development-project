import type {
  BloqueoResponseDto,
  CargaBloqueosResponseDto,
  CargaBloqueosMultipleResponseDto,
} from "@/api/bloqueos.dto";

import {
  apiRequest,
} from "@/services/api";

export const bloqueosService = {
  /**
   * Con {@code ejecucionId} (Día a Día en marcha) los meses cargados se habilitan además para esa ejecución; sin él,
   * se habilitan al crear la ejecución mediante {@code periodosBloqueos}.
   */
  cargarArchivos(archivos: File[], ejecucionId?: number): Promise<CargaBloqueosMultipleResponseDto> {
    if (archivos.length < 1 || archivos.length > 2) {
      return Promise.reject(new Error("Selecciona uno o dos archivos mensuales de bloqueos."));
    }
    const formData = new FormData();
    archivos.forEach((archivo) => formData.append("archivos", archivo));
    const sufijo = ejecucionId === undefined ? "" : `?ejecucionId=${encodeURIComponent(String(ejecucionId))}`;
    return apiRequest<CargaBloqueosMultipleResponseDto>(`/api/red-vial/bloqueos/carga-multiple${sufijo}`, {
      method: "POST",
      body: formData,
    });
  },

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
