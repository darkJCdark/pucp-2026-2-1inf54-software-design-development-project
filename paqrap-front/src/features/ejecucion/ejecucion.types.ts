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
}

export interface PedidoOperacion {
  id: string;
  x: number;
  y: number;

  cantidad: number;
  plazoHoras: number;

  estado: EstadoPedido;
}

export interface IndicadoresOperacion {
  costoAcumulado: number;
  distanciaRecorrida: number;
  entregasCompletadas: number;
  pedidosRiesgo: number;
}