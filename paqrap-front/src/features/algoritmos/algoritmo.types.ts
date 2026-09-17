export interface PedidoAlgoritmo {
  id: string;
  x: number;
  y: number;
  cantidad: number;

  /**
   * Para esta versión preliminar:
   * el reloj comienza en t = 0 y el plazo se mide en horas.
   */
  plazoHoras: number;
}

export interface VehiculoAlgoritmo {
  id: string;
  tipo: "AUTO" | "MOTO" | "BICICLETA";

  capacidad: number;
  velocidad: number;
  costoKm: number;
}

export interface ParadaAlgoritmo {
  pedidoId: string;
  x: number;
  y: number;
  cantidad: number;

  /**
   * Horas transcurridas desde el inicio
   * hasta llegar al pedido.
   */
  horaLlegada: number;
}

export interface RutaAlgoritmo {
  vehiculoId: string;
  tipoVehiculo: VehiculoAlgoritmo["tipo"];

  paradas: ParadaAlgoritmo[];

  cargaTotal: number;
  distanciaTotal: number;
  costoTotal: number;
}

export interface SolucionAlgoritmo {
  rutas: RutaAlgoritmo[];

  pedidosNoAsignados: PedidoAlgoritmo[];

  distanciaTotal: number;
  costoTotal: number;

  factible: boolean;
}

export interface ConfiguracionGrasp {
  alpha: number;
  maxIteraciones: number;
  semilla: number;
}