import { afterEach, describe, expect, it, vi } from "vitest";
import { claveUmbralesActuales, claveUmbralesEjecucion, guardarUmbrales, leerUmbrales, nivelSemaforo } from "./semaforo";

describe("semaforización de llenado y ocupación", () => {
  it.each([
    [0, "VACIO", "VACIO"], [33, "VERDE", "VERDE"], [40, "AMBAR", "VERDE"],
    [60, "AMBAR", "VERDE"], [66, "AMBAR", "AMBAR"], [70, "ROJO", "AMBAR"],
    [85, "ROJO", "AMBAR"], [100, "ROJO", "ROJO"],
  ])("clasifica %i%% con límites de almacén y de vehículo", (valor, almacen, vehiculo) => {
    expect(nivelSemaforo(valor, 33, 66)).toBe(almacen);
    expect(nivelSemaforo(valor, 60, 85)).toBe(vehiculo);
  });
  it("respeta nuevos límites y da prioridad a vacío", () => {
    expect(nivelSemaforo(0, 0, 70)).toBe("VACIO");
    expect(nivelSemaforo(40, 40, 70)).toBe("VERDE");
    expect(nivelSemaforo(70, 40, 70)).toBe("AMBAR");
    expect(nivelSemaforo(85, 40, 70)).toBe("ROJO");
  });
});

describe("umbrales por ejecución en el navegador", () => {
  afterEach(() => vi.unstubAllGlobals());
  it("conserva instantáneas independientes frente a cambios posteriores", () => {
    const datos = new Map<string, string>();
    vi.stubGlobal("localStorage", { getItem: (k: string) => datos.get(k) ?? null,
      setItem: (k: string, v: string) => datos.set(k, v) });
    const original = { almacenVerdeHasta: 33, almacenAmbarHasta: 66, vehiculoVerdeHasta: 60, vehiculoAmbarHasta: 85 };
    const nuevo = { ...original, almacenVerdeHasta: 40 };
    expect(guardarUmbrales(claveUmbralesEjecucion(1), original)).toBe(true);
    expect(guardarUmbrales(claveUmbralesActuales, nuevo)).toBe(true);
    expect(guardarUmbrales(claveUmbralesEjecucion(2), nuevo)).toBe(true);
    expect(leerUmbrales(claveUmbralesEjecucion(1))).toEqual(original);
    expect(leerUmbrales(claveUmbralesEjecucion(2))).toEqual(nuevo);
    expect(leerUmbrales(claveUmbralesEjecucion(3))).toBeNull();
  });
  it("informa si el almacenamiento está bloqueado", () => {
    vi.stubGlobal("localStorage", { getItem: () => { throw Error("bloqueado"); }, setItem: () => { throw Error("bloqueado"); } });
    expect(leerUmbrales(claveUmbralesEjecucion(1))).toBeNull();
    expect(guardarUmbrales(claveUmbralesEjecucion(1), { almacenVerdeHasta: 33, almacenAmbarHasta: 66, vehiculoVerdeHasta: 60, vehiculoAmbarHasta: 85 })).toBe(false);
  });
});
