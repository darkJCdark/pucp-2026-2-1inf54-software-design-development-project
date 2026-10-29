import type { ResultadoEscenarioResponseDto } from "@/api/resultados.dto";

import { apiBlobRequest, apiRequest } from "@/services/api";

export const resultadosService = {
  obtener(ejecucionId: number): Promise<ResultadoEscenarioResponseDto> {
    return apiRequest<ResultadoEscenarioResponseDto>(
      `/api/escenarios/${ejecucionId}/resultado`,
    );
  },

  async descargarCsv(ejecucionId: number) {
    const { blob, nombreArchivo } = await apiBlobRequest(
      `/api/escenarios/${ejecucionId}/resultado/csv`,
    );

    const url = URL.createObjectURL(blob);
    const enlace = document.createElement("a");
    enlace.href = url;
    enlace.download = nombreArchivo ?? `resultado-escenario-${ejecucionId}.csv`;
    document.body.appendChild(enlace);
    enlace.click();
    enlace.remove();
    URL.revokeObjectURL(url);
  },
};
