import type {
  PedidoAlgoritmo,
  VehiculoAlgoritmo,
} from "@/features/algoritmos/algoritmo.types";

export interface PedidoTemporal
  extends PedidoAlgoritmo {
  /**
   * Minuto de simulación en el que
   * ingresó el pedido.
   */
  creadoEnMin: number;
}

export interface VehiculoTemporal
  extends VehiculoAlgoritmo {
  /**
   * Minuto a partir del cual
   * el vehículo puede volver a utilizarse.
   */
  disponibleDesdeMin: number;
}

export interface AsignacionTemporal {
  pedidoId: string;

  vehiculoId: string;
  tipoVehiculo: VehiculoAlgoritmo["tipo"];

  inicioMin: number;
  llegadaPedidoMin: number;

  finAcondicionamientoMin: number;
  retornoCentralMin: number;

  distanciaIdaKm: number;
  distanciaTotalKm: number;

  costoTotal: number;
}

export interface ResultadoAsignacionTemporal {
  asignacion: AsignacionTemporal | null;

  vehiculosActualizados: VehiculoTemporal[];

  motivo?: string;
}