import type {
  AlmacenDetalleResponseDto,
  VehiculoDetalleResponseDto,
} from "@/api/detalles.dto";
import type { PedidoDetalleResponseDto } from "@/api/pedidos.dto";

import { apiRequest } from "@/services/api";

export const detallesService = {
  pedido(id: string, instante?: string): Promise<PedidoDetalleResponseDto> {
    const query = instante ? `?instante=${encodeURIComponent(instante)}` : "";
    return apiRequest<PedidoDetalleResponseDto>(
      `/api/pedidos/${encodeURIComponent(id)}${query}`,
    );
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
