import type {
  EscenarioResponseDto,
} from "@/api/escenarios.dto";

import type {
  AveriaResponseDto,
} from "@/api/incidencias.dto";

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

export interface ResumenPedidosMonitoreoDto {
  llegados: number;
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

  stock: number | null;
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

export interface EjecucionMonitoreoDto {
  pedidosEntregados: number;
  entregadosATiempo: number;
  paquetesEntregados: number;
  distanciaKm: number;
  costo: number;

  vehiculosEnRuta: Partial<
    Record<
      TipoVehiculoMonitoreoDto,
      number
    >
  >;
}

export type CausaColapsoBackend =
  | "PLAZO_VENCIDO"
  | "PLAZO_INALCANZABLE";

export interface ColapsoMonitoreoDto {
  instante: string;
  causa: CausaColapsoBackend;
  pedidoId: string;
  plazo: string;
  llegadaMinima: string | null;
  detectadoEn: string;
  pedidosPendientes: number;
  vencidosSinEntregar: number;
  vehiculosHabilitados: number;
}

export interface MonitoreoResponseDto {
  escenario: EscenarioResponseDto;
  instante: string;

  pedidos: ResumenPedidosMonitoreoDto;
  vehiculos: VehiculoMonitoreoDto[];
  almacenes: AlmacenMonitoreoDto[];
  bloqueosVigentes: BloqueoMonitoreoDto[];
  averiasActivas: AveriaResponseDto[];

  ejecucion: EjecucionMonitoreoDto;
  colapso: ColapsoMonitoreoDto | null;
}
