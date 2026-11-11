export type NivelDetalleMapa =
  | "BAJO"
  | "MEDIO"
  | "ALTO";

export interface ViewportPixeles {
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
}