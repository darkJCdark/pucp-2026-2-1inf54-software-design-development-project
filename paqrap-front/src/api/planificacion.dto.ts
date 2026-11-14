export type ModoOperacionBackend =
  | "DIA_A_DIA"
  | "SIMULACION_5D"
  | "COLAPSO";

export type MotivoReplanificacionBackend =
  | "INICIO"
  | "NUEVO_PEDIDO"
  | "BLOQUEO"
  | "AVERIA"
  | "PERIODICO";

export type TipoParadaBackend =
  | "ENTREGA"
  | "ALMACEN"
  | "TRASVASE";

export interface ParadaResponseDto {
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

  /** Hora física de llegada a la parada antes de iniciar el refrigerio. */
  inicioRefrigerio?: string | null;

  /** Nodos del camino real hacia esta parada (rodea los bloqueos), desde la parada anterior hasta esta. */
  camino?: PuntoCaminoDto[] | null;
}

export interface PuntoCaminoDto {
  x: number;
  y: number;
}

/** Calle real y temporizada; las esperas se conservan como huecos entre salida/llegada. */
export interface TramoRutaResponseDto {
  ordenParada: number;
  desdeX: number;
  desdeY: number;
  hastaX: number;
  hastaY: number;
  salida: string;
  llegada: string;
}

export interface RutaResponseDto {
  id: string;
  vehiculoId: string;

  tipoVehiculo:
    | "CAR"
    | "MOTORCYCLE"
    | "BICYCLE";

  salida: string;
  llegadaFinal: string | null;

  distanciaKm: number;
  costo: number;

  paradas: ParadaResponseDto[];
  /** Opcional únicamente para detectar respuestas de un backend anterior. */
  tramos?: TramoRutaResponseDto[] | null;

  almacenInicialId?: string | null;
  planPersistedId?: number | null;
  routePersistedId?: number | null;
}

export interface ViolacionResponseDto {
  tipo: string;
  rutaId: string | null;
  detalle: string;
}

export interface NoAtendidoResponseDto {
  pedidoId: string;
  paquetes: number;
  plazo: string;
}

export interface PlanResponseDto {
  modo: ModoOperacionBackend;
  planificadoEn: string;

  factible: boolean;
  colapso: boolean;

  costoTotal: number;
  distanciaTotalKm: number;

  semilla: number;
  duracionMs: number;
  presupuestoMs: number;
  presupuestoAgotado: boolean;

  rutas: RutaResponseDto[];
  violaciones: ViolacionResponseDto[];
  noAtendidos: NoAtendidoResponseDto[];
}

export interface ReplanificacionResponseDto {
  ejecucionId: number;
  numeroReplanificacion: number;
  motivo: MotivoReplanificacionBackend;
  hora: string;

  plan: PlanResponseDto;

  rutasConservadas: string[];
  rutasNuevas: string[];
  pedidosReasignados: string[];
  vehiculosNoDisponibles: string[];
}
