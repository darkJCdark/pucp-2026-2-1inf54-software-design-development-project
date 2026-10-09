import type {
  CargaPedidosResponseDto,
  PaginaResponseDto,
  PedidoResponseDto,
  RegistrarPedidoRequestDto,
} from "@/api/pedidos.dto";

import {
  apiRequest,
} from "@/services/api";

interface ListarPedidosParams {
  registradoHasta?: string;
}

export const pedidosService = {
  listar(
    params: ListarPedidosParams = {},
  ): Promise<
    PaginaResponseDto<PedidoResponseDto>
  > {
    const query =
      new URLSearchParams();

    query.set(
      "size",
      "500",
    );

    query.append(
      "sort",
      "registradoEn,asc",
    );

    if (
      params.registradoHasta
    ) {
      query.set(
        "registradoHasta",
        params.registradoHasta,
      );
    }

    return apiRequest<
      PaginaResponseDto<PedidoResponseDto>
    >(
      `/api/pedidos?${query.toString()}`,
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

  cargarArchivo(
    archivo: File,
  ): Promise<CargaPedidosResponseDto> {
    const formData =
      new FormData();

    formData.append(
      "archivo",
      archivo,
    );

    return apiRequest<CargaPedidosResponseDto>(
      "/api/pedidos/carga",
      {
        method: "POST",
        body: formData,
      },
    );
  },
};