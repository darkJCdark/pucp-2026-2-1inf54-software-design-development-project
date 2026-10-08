export type TipoAlmacenBackend =
  | "CENTRAL"
  | "INTERMEDIATE";

export interface AlmacenResponseDto {
  id: string;
  tipo: TipoAlmacenBackend;

  x: number;
  y: number;

  stockInicial: number | null;
  capacidad: number | null;
}