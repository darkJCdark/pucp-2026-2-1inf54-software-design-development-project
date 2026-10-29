import type {
  EstadoEscenarioBackend,
  TipoEscenarioBackend,
} from "@/api/escenarios.dto";

export interface ResultadoEscenarioResponseDto {
  executionId: number;
  tipo: TipoEscenarioBackend;
  estado: EstadoEscenarioBackend;
  inicioSimulado: string;
  finSimulado: string | null;
  inicioReal: string | null;
  finReal: string | null;
  colapsoEn: string | null;
  totalOrders: number;
  deliveredOrders: number;
  onTimeOrders: number;
  undeliveredOrders: number;
  totalDistanceKm: number;
  totalCost: number;
  computationTimeMs: number;
  breakdownCount: number;
  roadBlockCount: number;
  replanningCount: number;
}
