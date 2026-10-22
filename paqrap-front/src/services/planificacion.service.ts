import type {
  PlanResponseDto,
  ReplanificacionResponseDto,
} from "@/api/planificacion.dto";

import {
  apiRequest,
} from "@/services/api";

export const planificacionService = {
  obtenerPlan(
    ejecucionId: number,
  ): Promise<PlanResponseDto> {
    return apiRequest<PlanResponseDto>(
      `/api/planificacion/${ejecucionId}/plan`,
    );
  },

  obtenerReplanificacion(
    ejecucionId: number,
  ): Promise<ReplanificacionResponseDto> {
    return apiRequest<ReplanificacionResponseDto>(
      `/api/planificacion/${ejecucionId}/replanificacion`,
    );
  },
};
