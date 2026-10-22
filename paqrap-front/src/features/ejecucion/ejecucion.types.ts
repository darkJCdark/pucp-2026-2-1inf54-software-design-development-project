import type {
  TipoEntregaBackend,
} from "@/api/pedidos.dto";

import type {
  TipoParadaBackend,
} from "@/api/planificacion.dto";

export type TipoVehiculo =
  | "AUTO"
  | "MOTO"
  | "BICICLETA";

export type EstadoVehiculo =
  | "DISPONIBLE"
  | "EN_RUTA"
  | "NO_DISPONIBLE";

export type EstadoPedido =
  | "PENDIENTE"
  | "ASIGNADO"
  | "EN_TRANSITO"
  | "ENTREGADO";

export interface VehiculoMapa {
  id: string;
  tipo: TipoVehiculo;
  estado: EstadoVehiculo;

  x: number;
  y: number;

  cargaActual: number;
  averiadoHasta: string | null;
}

export interface AlmacenMapa {
  id: string;
  nombre: string;

  tipo:
    | "CENTRAL"
    | "INTERMEDIO";

  x: number;
  y: number;

  stock?: number;
  capacidad?: number;
}

export interface PedidoOperacion {
  id: string;
  clienteId: string;

  x: number;
  y: number;

  cantidad: number;
  plazoHoras: number;

  tipoEntrega: TipoEntregaBackend;
  estado: EstadoPedido;

  registradoEn: string;
  vence: string;
  entregadoEn: string | null;
}

export interface ParadaMapa {
  orden: number;
  tipo: TipoParadaBackend;

  pedidoId: string | null;
  almacenId: string | null;

  x: number;
  y: number;

  paquetes: number;

  llegada: string | null;
  fin: string | null;

  cargaAntes: number | null;
  cargaDespues: number | null;
}

export interface RutaMapa {
  id: string;
  vehiculoId: string;
  tipoVehiculo: TipoVehiculo;

  salida: string;
  llegadaFinal: string | null;

  distanciaKm: number;
  costo: number;

  paradas: ParadaMapa[];
}

export interface IndicadoresOperacion {
  costoAcumulado: number;
  distanciaRecorrida: number;
  entregasCompletadas: number;
  entregasATiempo: number;
  paquetesEntregados: number;
  pedidosRiesgo: number;
}
