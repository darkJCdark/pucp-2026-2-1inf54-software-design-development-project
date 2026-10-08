import type {
  PaginaResponseDto,
  PedidoResponseDto,
  RegistrarPedidoRequestDto,
} from "@/api/pedidos.dto";

import {
  apiRequest,
} from "@/services/api";

export const pedidosService = {
  listar(): Promise<
    PaginaResponseDto<PedidoResponseDto>
  > {
    return apiRequest<
      PaginaResponseDto<PedidoResponseDto>
    >(
      "/api/pedidos?size=500&sort=registradoEn,asc",
    );
  },

  obtener(
    id: string,
  ): Promise<PedidoResponseDto> {
    return apiRequest<PedidoResponseDto>(
      `/api/pedidos/${encodeURIComponent(
        id,
      )}`,
    );
  },

  registrar(
    request: RegistrarPedidoRequestDto,
  ): Promise<PedidoResponseDto> {
    return apiRequest<PedidoResponseDto>(
      "/api/pedidos",
      {
        method: "POST",

        body: JSON.stringify(
          request,
        ),
      },
    );
  },
};