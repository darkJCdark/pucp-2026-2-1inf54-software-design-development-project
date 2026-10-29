import type {
  CargaPedidosResponseDto,
  EstadoPedidoBackend,
  PaginaResponseDto,
  PedidoDetalleResponseDto,
  PedidoResponseDto,
  RegistrarPedidoRequestDto,
  TipoEntregaBackend,
} from "@/api/pedidos.dto";

import {
  apiRequest,
} from "@/services/api";

export interface ListarPedidosParams {
  clienteId?: string;
  estado?: EstadoPedidoBackend;
  tipoEntrega?: TipoEntregaBackend;

  registradoDesde?: string;
  registradoHasta?: string;
  venceHasta?: string;

  page?: number;
  size?: number;
  sort?: string;
}

function crearQuery(
  params: ListarPedidosParams,
) {
  const query =
    new URLSearchParams();

  query.set(
    "page",
    String(params.page ?? 0),
  );

  query.set(
    "size",
    String(params.size ?? 50),
  );

  query.append(
    "sort",
    params.sort ??
      "registradoEn,asc",
  );

  if (params.clienteId) {
    query.set(
      "clienteId",
      params.clienteId,
    );
  }

  if (params.estado) {
    query.set(
      "estado",
      params.estado,
    );
  }

  if (params.tipoEntrega) {
    query.set(
      "tipoEntrega",
      params.tipoEntrega,
    );
  }

  if (params.registradoDesde) {
    query.set(
      "registradoDesde",
      params.registradoDesde,
    );
  }

  if (params.registradoHasta) {
    query.set(
      "registradoHasta",
      params.registradoHasta,
    );
  }

  if (params.venceHasta) {
    query.set(
      "venceHasta",
      params.venceHasta,
    );
  }

  return query;
}

export const pedidosService = {
  listar(
    params: ListarPedidosParams = {},
  ): Promise<
    PaginaResponseDto<PedidoResponseDto>
  > {
    const query = crearQuery(params);

    return apiRequest<
      PaginaResponseDto<PedidoResponseDto>
    >(
      `/api/pedidos?${query.toString()}`,
    );
  },

  async listarTodos(
    params: Omit<
      ListarPedidosParams,
      "page" | "size"
    > = {},
  ): Promise<PedidoResponseDto[]> {
    const acumulados: PedidoResponseDto[] = [];
    let pagina = 0;
    let totalPaginas = 1;

    while (pagina < totalPaginas) {
      const respuesta =
        await pedidosService.listar({
          ...params,
          page: pagina,
          size: 500,
        });

      acumulados.push(
        ...respuesta.contenido,
      );

      totalPaginas =
        respuesta.totalPaginas;

      pagina += 1;
    }

    return acumulados;
  },

  obtener(
    id: string,
    instante?: string,
  ): Promise<PedidoDetalleResponseDto> {
    const query = instante
      ? `?instante=${encodeURIComponent(instante)}`
      : "";

    return apiRequest<PedidoDetalleResponseDto>(
      `/api/pedidos/${encodeURIComponent(id)}${query}`,
    );
  },

  registrar(
    request: RegistrarPedidoRequestDto,
  ): Promise<PedidoResponseDto> {
    return apiRequest<PedidoResponseDto>(
      "/api/pedidos",
      {
        method: "POST",
        body: JSON.stringify(request),
      },
    );
  },

  cargarArchivo(
    archivo: File,
  ): Promise<CargaPedidosResponseDto> {
    const formData = new FormData();

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
