export type TipoEscenarioBackend =
  | "DAY_TO_DAY"
  | "FIVE_DAY"
  | "COLLAPSE";

export type EstadoEscenarioBackend =
  | "CREATED"
  | "RUNNING"
  | "PAUSED"
  | "STOPPED"
  | "COMPLETED"
  | "COLLAPSED"
  | "FAILED";

export interface CrearEscenarioRequestDto {
  tipo: TipoEscenarioBackend;

  /**
   * DAY_TO_DAY:
   * no debe enviarse.
   *
   * FIVE_DAY / COLLAPSE:
   * obligatorio.
   */
  inicioSimulado?: string;
}

export interface EscenarioResponseDto {
  id: number;

  tipo: TipoEscenarioBackend;
  estado: EstadoEscenarioBackend;

  factorAceleracion: number;

  inicioSimulado: string;
  instanteSimulado: string | null;
  finSimulado: string | null;

  colapsoEn: string | null;

  inicioReal: string | null;
  finReal: string | null;
}

export interface PaginaEscenariosResponseDto {
  contenido: EscenarioResponseDto[];

  pagina: number;
  tamanio: number;

  totalElementos: number;
  totalPaginas: number;
}