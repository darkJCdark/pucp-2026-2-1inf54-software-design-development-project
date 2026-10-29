export interface CargaBloqueosResponseDto {
  archivo: string;
  periodo: string;
  reemplazados: number;
  registrados: number;
}

export interface NodoBloqueoResponseDto {
  x: number;
  y: number;
}

export interface BloqueoResponseDto {
  id: number;
  inicio: string;
  fin: string;
  nodos: NodoBloqueoResponseDto[];
}
