export type Escenario =
  | "DIA_A_DIA"
  | "SIMULACION_5D"
  | "COLAPSO";

export type Algoritmo =
  | "GRASP"
  | "SIMULATED_ANNEALING";

export interface ConfiguracionPlanificador {
  escenario: Escenario;

  compararAlgoritmos: boolean;
  algoritmo: Algoritmo;
  semilla: number;

  // GRASP
  graspAlpha: number;
  graspMaxIter: number;

  // Simulated Annealing
  saTemperaturaInicial: number;
  saFactorEnfriamiento: number;
  saIteracionesNivel: number;
  saTemperaturaMinima: number;

  // Flota
  autosCantidad: number;
  autosVelocidad: number;

  motosCantidad: number;
  motosVelocidad: number;

  bicicletasCantidad: number;
  bicicletasVelocidad: number;

  // Semaforización
  almacenVerdeHasta: number;
  almacenAmbarHasta: number;

  pedidoVerdeHasta: number;
  pedidoAmbarHasta: number;
}