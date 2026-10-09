import type {
  MonitoreoResponseDto,
} from "@/api/monitoreo.dto";

import {
  apiRequest,
} from "@/services/api";

export const monitoreoService = {
  obtener(
    ejecucionId: number,
  ): Promise<MonitoreoResponseDto> {
    return apiRequest<MonitoreoResponseDto>(
      `/api/monitoreo/${ejecucionId}`,
    );
  },
};