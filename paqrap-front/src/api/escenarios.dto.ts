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

export interface ConfiguracionSaRequestDto {
  semilla?: number;
  temperaturaInicial?: number;
  temperaturaMinima?: number;
  factorEnfriamiento?: number;
  iteracionesPorTemperatura?: number;
}

export interface ConfiguracionSaResponseDto {
  semilla: number;
  temperaturaInicial: number;
  temperaturaMinima: number;
  factorEnfriamiento: number;
  iteracionesPorTemperatura: number;
}

export interface CrearEscenarioRequestDto {
  tipo: TipoEscenarioBackend;

  /**
   * DAY_TO_DAY: no debe enviarse.
   * FIVE_DAY / COLLAPSE: obligatorio.
   */
  inicioSimulado?: string;

  /** Configuración reproducible del planificador. Si se omite, backend usa sus valores por defecto. */
  configuracionSa?: ConfiguracionSaRequestDto;

  /**
   * Meses (AAAA-MM) de los archivos de bloqueos cargados para esta ejecución. Solo esos bloqueos se muestran y se
   * planifican; una lista vacía significa "sin bloqueos" aunque la base de datos tenga bloqueos guardados.
   */
  periodosBloqueos?: string[];
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

  /** Disponible en el backend que persiste la configuración de SA. */
  configuracionSa?: ConfiguracionSaResponseDto | null;
}

export interface PaginaEscenariosResponseDto {
  contenido: EscenarioResponseDto[];
  pagina: number;
  tamanio: number;
  totalElementos: number;
  totalPaginas: number;
}
