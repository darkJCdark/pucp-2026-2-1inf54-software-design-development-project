import type {
  EscenarioResponseDto,
} from "@/api/escenarios.dto";

export type TipoVehiculoMonitoreoDto =
  | "CAR"
  | "MOTORCYCLE"
  | "BICYCLE";

export type EstadoVehiculoMonitoreoDto =
  | "AVAILABLE"
  | "IN_ROUTE"
  | "UNAVAILABLE";

export type TipoAlmacenMonitoreoDto =
  | "CENTRAL"
  | "INTERMEDIATE";

export type TipoAveriaBackend =
  | "MINOR"
  | "INTERMEDIATE"
  | "MAJOR";

export interface ResumenPedidosMonitoreoDto {
  /**
   * Pedidos registrados desde el inicio
   * de esta ejecución hasta el instante actual.
   */
  llegados: number;

  /**
   * Pedidos todavía atendibles y no entregados.
   */
  pendientes: number;

  vencidosSinEntregar: number;
}

export interface VehiculoMonitoreoDto {
  id: string;

  tipo: TipoVehiculoMonitoreoDto;
  estado: EstadoVehiculoMonitoreoDto;

  x: number;
  y: number;

  cargaActual: number;

  averiadoHasta: string | null;
}

export interface AlmacenMonitoreoDto {
  id: string;

  tipo: TipoAlmacenMonitoreoDto;

  x: number;
  y: number;

  /**
   * null para almacén central.
   */
  stock: number | null;

  /**
   * null para almacén central.
   */
  capacidad: number | null;
}

export interface NodoBloqueoDto {
  x: number;
  y: number;
}

export interface BloqueoMonitoreoDto {
  id: number;

  inicio: string;
  fin: string;

  nodos: NodoBloqueoDto[];
}

export interface AveriaMonitoreoDto {
  id: number;

  ejecucionId: number | null;

  vehiculoId: string;

  tipo: TipoAveriaBackend;

  ocurridaEn: string;

  x: number;
  y: number;

  indisponibleHasta: string;

  regresaAlCentralEn: string | null;
}

export interface MonitoreoResponseDto {
  escenario: EscenarioResponseDto;

  instante: string;

  pedidos: ResumenPedidosMonitoreoDto;

  vehiculos: VehiculoMonitoreoDto[];

  almacenes: AlmacenMonitoreoDto[];

  bloqueosVigentes: BloqueoMonitoreoDto[];

  averiasActivas: AveriaMonitoreoDto[];
}