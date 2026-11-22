import type {
  CargaPedidosResponseDto,
  CargaPedidosMultipleResponseDto,
  EstadoPedidoBackend,
  PaginaResponseDto,
  PedidoDetalleResponseDto,
  PedidoResponseDto,
  PeriodoCargadoResponseDto,
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

  /**
   * Ejecución cuyos pedidos se consultan. Las simulaciones son aisladas: sus estados (asignado, entregado...) viven
   * en memoria y no en la BD, así que sin este id una simulación ya terminada mostraría los estados de la BD.
   */
  ejecucionId?: number;

  /** Pedidos tal como están en la BD, sin el estado de ninguna ejecución (solo identidad, destino y plazo). */
  catalogo?: boolean;
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

  if (params.catalogo) {
    query.set(
      "catalogo",
      "true",
    );
  }

  if (params.ejecucionId !== undefined) {
    query.set(
      "ejecucionId",
      String(params.ejecucionId),
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

  periodosCargados(): Promise<PeriodoCargadoResponseDto[]> {
    return apiRequest<PeriodoCargadoResponseDto[]>(
      "/api/pedidos/periodos-cargados",
    );
  },

  obtener(
    id: string,
    instante?: string,
    ejecucionId?: number,
  ): Promise<PedidoDetalleResponseDto> {
    const query = new URLSearchParams();
    if (instante) query.set("instante", instante);
    if (ejecucionId !== undefined) query.set("ejecucionId", String(ejecucionId));
    const sufijo = query.size > 0 ? `?${query.toString()}` : "";

    return apiRequest<PedidoDetalleResponseDto>(
      `/api/pedidos/${encodeURIComponent(id)}${sufijo}`,
    );
  },

  registrar(
    request: RegistrarPedidoRequestDto,
    ejecucionId?: number,
  ): Promise<PedidoResponseDto> {
    const query = ejecucionId === undefined ? "" : `?ejecucionId=${ejecucionId}`;
    return apiRequest<PedidoResponseDto>(
      `/api/pedidos${query}`,
      {
        method: "POST",
        body: JSON.stringify(request),
      },
    );
  },

  cargarArchivos(archivos: File[]): Promise<CargaPedidosMultipleResponseDto> {
    if (archivos.length < 1 || archivos.length > 2) {
      return Promise.reject(new Error("Selecciona uno o dos archivos mensuales de pedidos."));
    }
    const formData = new FormData();
    archivos.forEach((archivo) => formData.append("archivos", archivo));
    return apiRequest<CargaPedidosMultipleResponseDto>("/api/pedidos/carga-multiple", {
      method: "POST",
      body: formData,
    });
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
