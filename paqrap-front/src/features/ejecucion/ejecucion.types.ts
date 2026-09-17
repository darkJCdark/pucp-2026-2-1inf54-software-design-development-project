export type TipoVehiculo =
  | "AUTO"
  | "MOTO"
  | "BICICLETA";

export type EstadoVehiculo =
  | "DISPONIBLE"
  | "EN_RUTA"
  | "AVERIADO";

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
  tipo: "CENTRAL" | "INTERMEDIO";
  x: number;
  y: number;
  stock?: number;
}

export interface PedidoMapa {
  id: string;
  x: number;
  y: number;
  estado: "PENDIENTE" | "EN_TRANSITO";
}

export interface IndicadoresOperacion {
  costoAcumulado: number;
  distanciaRecorrida: number;
  entregasCompletadas: number;
  pedidosRiesgo: number;
}