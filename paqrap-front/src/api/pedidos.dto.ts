export type TipoEntregaBackend =
  | "REGULAR"
  | "PRIORITY";

export type EstadoPedidoBackend =
  | "REGISTERED"
  | "ASSIGNED"
  | "IN_TRANSIT"
  | "DELIVERED";

export interface RegistrarPedidoRequestDto {
  clienteId: string;

  x: number;
  y: number;

  paquetes: number;

  tipoEntrega: TipoEntregaBackend;

  /**
   * REGULAR:
   * puede omitirse; backend fija 36 h.
   *
   * PRIORITY:
   * obligatorio: 4, 8, 12 o 18.
   */
  horasPrometidas?: number;

  /**
   * Opcional.
   * Si no se envía, backend utiliza Instant.now().
   */
  registradoEn?: string;
}

export interface PedidoResponseDto {
  id: string;
  clienteId: string;

  x: number;
  y: number;

  paquetes: number;

  tipoEntrega: TipoEntregaBackend;
  horasPrometidas: number;

  registradoEn: string;
  vence: string;

  estado: EstadoPedidoBackend;

  entregadoEn: string | null;
}

export interface PaginaResponseDto<T> {
  contenido: T[];

  pagina: number;
  tamanio: number;

  totalElementos: number;
  totalPaginas: number;
}

export interface CargaPedidosResponseDto {
  archivo: string;

  periodo: string;

  leidos: number;
  registrados: number;
  omitidos: number;
}