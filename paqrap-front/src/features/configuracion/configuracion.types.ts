export type Escenario =
  | "DIA_A_DIA"
  | "SIMULACION_5D"
  | "COLAPSO";

export interface ConfiguracionPlanificador {
  escenario: Escenario;

  /** datetime-local del navegador para 5D y Colapso. */
  inicioSimulado: string;

  autosCantidad: number;
  autosVelocidad: number;
  motosCantidad: number;
  motosVelocidad: number;
  bicicletasCantidad: number;
  bicicletasVelocidad: number;

  almacenVerdeHasta: number;
  almacenAmbarHasta: number;
  pedidoVerdeHasta: number;
  pedidoAmbarHasta: number;

  usarConfiguracionSa: boolean;
  saSemilla: string;
  saTemperaturaInicial: number;
  saTemperaturaMinima: number;
  saFactorEnfriamiento: number;
  saIteracionesPorTemperatura: number;
}
