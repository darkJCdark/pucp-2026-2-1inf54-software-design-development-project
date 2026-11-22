import type { EscenarioResponseDto, EstadoEscenarioBackend, TipoEscenarioBackend } from "@/api/escenarios.dto";

export function esEjecucionActiva(ejecucion: EscenarioResponseDto) {
  return ejecucion.estado === "RUNNING" || ejecucion.estado === "PAUSED";
}

/** Día a Día ocupa una plaza; 5D y Colapso comparten la otra. */
export function mismoGrupo(a: TipoEscenarioBackend, b: TipoEscenarioBackend) {
  return (a === "DAY_TO_DAY") === (b === "DAY_TO_DAY");
}

export function activaDelGrupo(ejecuciones: EscenarioResponseDto[], tipo: TipoEscenarioBackend) {
  return ejecuciones.find((ejecucion) => esEjecucionActiva(ejecucion) && mismoGrupo(ejecucion.tipo, tipo)) ?? null;
}

export function nombreTipoEjecucion(tipo: TipoEscenarioBackend) {
  return tipo === "DAY_TO_DAY" ? "Operación Día a Día" : tipo === "FIVE_DAY" ? "Simulación 5D" : "Simulación de colapso";
}

export function nombreEstadoEjecucion(estado: EstadoEscenarioBackend) {
  const nombres: Record<EstadoEscenarioBackend, string> = {
    CREATED: "Por iniciar", RUNNING: "En curso", PAUSED: "En pausa", STOPPED: "Detenida",
    COMPLETED: "Completada", COLLAPSED: "Colapsada", FAILED: "Con error",
  };
  return nombres[estado];
}
