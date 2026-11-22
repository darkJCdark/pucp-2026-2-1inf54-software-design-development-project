import type {
  AlmacenDetalleResponseDto,
  VehiculoDetalleResponseDto,
} from "@/api/detalles.dto";
import type { PedidoDetalleResponseDto } from "@/api/pedidos.dto";

import { apiRequest } from "@/services/api";
import { pedidosService } from "@/services/pedidos.service";

export const detallesService = {
  pedido(id: string, instante?: string, ejecucionId?: number): Promise<PedidoDetalleResponseDto> {
    return pedidosService.obtener(id, instante, ejecucionId);
  },

  vehiculo(id: string, ejecucionId: number): Promise<VehiculoDetalleResponseDto> {
    const query = new URLSearchParams({ ejecucionId: String(ejecucionId) });
    return apiRequest<VehiculoDetalleResponseDto>(
      `/api/flota/vehiculos/${encodeURIComponent(id)}/detalle?${query.toString()}`,
    );
  },

  almacen(
    id: string,
    ejecucionId: number,
    horas = 24,
  ): Promise<AlmacenDetalleResponseDto> {
    const query = new URLSearchParams({
      ejecucionId: String(ejecucionId),
      horas: String(horas),
    });

    return apiRequest<AlmacenDetalleResponseDto>(
      `/api/almacenes/${encodeURIComponent(id)}/detalle?${query.toString()}`,
    );
  },
};
