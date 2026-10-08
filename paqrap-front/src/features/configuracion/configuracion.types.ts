export type Escenario =
  | "DIA_A_DIA"
  | "SIMULACION_5D"
  | "COLAPSO";

export interface ConfiguracionPlanificador {
  escenario: Escenario;

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