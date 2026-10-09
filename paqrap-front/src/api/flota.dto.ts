export type TipoVehiculoBackend =
  | "CAR"
  | "MOTORCYCLE"
  | "BICYCLE";

export type EstadoVehiculoBackend =
  | "AVAILABLE"
  | "IN_ROUTE"
  | "UNAVAILABLE";

export interface VehiculoResponseDto {
  id: string;
  tipo: TipoVehiculoBackend;
  estado: EstadoVehiculoBackend;

  x: number;
  y: number;

  cargaActual: number;
  disponibleDesde: string | null;
}

export interface TipoVehiculoResponseDto {
  tipo: TipoVehiculoBackend;
  codigoFlota: string;

  capacidadPaquetes: number;
  velocidadKmh: number;
  costoPorKm: number;

  actualizadoEn: string;
}