export type TipoAveriaBackend =
  | "MINOR"
  | "INTERMEDIATE"
  | "MAJOR";

export interface RegistrarAveriaRequestDto {
  vehiculoId: string;
  tipo: TipoAveriaBackend;

  ocurridaEn?: string;
  x?: number;
  y?: number;
  ejecucionId?: number;
}

export interface AveriaResponseDto {
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
