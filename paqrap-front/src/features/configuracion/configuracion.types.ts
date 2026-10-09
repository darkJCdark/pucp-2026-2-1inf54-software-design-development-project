export type Escenario =
  | "DIA_A_DIA"
  | "SIMULACION_5D"
  | "COLAPSO";

export interface ConfiguracionPlanificador {
  escenario: Escenario;

  /**
   * datetime-local del navegador.
   *
   * Solo se usa para:
   * - SIMULACION_5D
   * - COLAPSO
   */
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
}