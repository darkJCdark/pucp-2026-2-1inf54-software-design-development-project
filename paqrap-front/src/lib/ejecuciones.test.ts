import { describe, expect, it } from "vitest";
import type { EscenarioResponseDto, EstadoEscenarioBackend, TipoEscenarioBackend } from "@/api/escenarios.dto";
import { activaDelGrupo } from "./ejecuciones";

function ejecucion(id: number, tipo: TipoEscenarioBackend, estado: EstadoEscenarioBackend): EscenarioResponseDto {
  return {
    id, tipo, estado, factorAceleracion: tipo === "DAY_TO_DAY" ? 1 : 160,
    inicioSimulado: "2026-11-21T05:00:00Z", instanteSimulado: "2026-11-21T06:00:00Z",
    finSimulado: null, colapsoEn: null, inicioReal: "2026-11-21T05:00:00Z", finReal: null,
  };
}

describe("plazas independientes para operación y simulación", () => {
  it("permite abrir 5D o Colapso mientras Día a Día ya está activo", () => {
    const operacion = ejecucion(1, "DAY_TO_DAY", "RUNNING");
    expect(activaDelGrupo([operacion], "FIVE_DAY")).toBeNull();
    expect(activaDelGrupo([operacion], "COLLAPSE")).toBeNull();
    expect(activaDelGrupo([operacion], "DAY_TO_DAY")).toEqual(operacion);
  });

  it("permite abrir Día a Día después de cualquiera de las simulaciones", () => {
    for (const tipo of ["FIVE_DAY", "COLLAPSE"] as const) {
      expect(activaDelGrupo([ejecucion(2, tipo, "RUNNING")], "DAY_TO_DAY")).toBeNull();
    }
  });

  it("encuentra ambas plazas sin depender del orden de llegada", () => {
    const operacion = ejecucion(1, "DAY_TO_DAY", "RUNNING");
    const simulacion = ejecucion(2, "FIVE_DAY", "RUNNING");
    for (const lista of [[operacion, simulacion], [simulacion, operacion]]) {
      expect(activaDelGrupo(lista, "DAY_TO_DAY")?.id).toBe(1);
      expect(activaDelGrupo(lista, "FIVE_DAY")?.id).toBe(2);
      expect(activaDelGrupo(lista, "COLLAPSE")?.id).toBe(2);
    }
  });

  it("reserva la misma plaza en curso o en pausa, incluyendo 5D frente a Colapso", () => {
    for (const estado of ["RUNNING", "PAUSED"] as const) {
      const operacion = ejecucion(1, "DAY_TO_DAY", estado);
      expect(activaDelGrupo([operacion], "DAY_TO_DAY")).toEqual(operacion);
      for (const tipo of ["FIVE_DAY", "COLLAPSE"] as const) {
        const simulacion = ejecucion(2, tipo, estado);
        expect(activaDelGrupo([simulacion], "FIVE_DAY")).toEqual(simulacion);
        expect(activaDelGrupo([simulacion], "COLLAPSE")).toEqual(simulacion);
        expect(activaDelGrupo([simulacion], "DAY_TO_DAY")).toBeNull();
      }
    }
  });

  it("libera únicamente la plaza que termina y no reserva una ejecución creada sin iniciar", () => {
    for (const estado of ["CREATED", "STOPPED", "COMPLETED", "COLLAPSED", "FAILED"] as const) {
      const operacion = ejecucion(1, "DAY_TO_DAY", "RUNNING");
      const lista = [operacion, ejecucion(2, "FIVE_DAY", estado)];
      expect(activaDelGrupo(lista, "FIVE_DAY")).toBeNull();
      expect(activaDelGrupo(lista, "DAY_TO_DAY")).toEqual(operacion);
      expect(activaDelGrupo([ejecucion(3, "DAY_TO_DAY", estado)], "DAY_TO_DAY")).toBeNull();
    }
  });
});
