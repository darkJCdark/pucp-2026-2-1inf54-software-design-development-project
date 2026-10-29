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
  horasPrometidas?: number;
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

export interface CambioEstadoPedidoDto {
  estado: EstadoPedidoBackend;
  ocurridoEn: string;
  vehiculoId: string | null;
  almacenId: string | null;
}

export interface PedidoDetalleResponseDto {
  pedido: PedidoResponseDto;
  historial: CambioEstadoPedidoDto[];
  unidadAsignadaId: string | null;
  incumplido: boolean;
  minutosRestantes: number | null;
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
