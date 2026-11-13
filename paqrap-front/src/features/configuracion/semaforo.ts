import type { ConfiguracionPlanificador } from "./configuracion.types";

export type NivelSemaforo = "VACIO" | "VERDE" | "AMBAR" | "ROJO";
export type UmbralesSemaforo = Pick<ConfiguracionPlanificador,
  "almacenVerdeHasta" | "almacenAmbarHasta" | "vehiculoVerdeHasta" | "vehiculoAmbarHasta">;

/** Rangos de llenado/ocupación: vacío prevalece y los límites superiores son inclusivos. */
export function nivelSemaforo(porcentaje: number, verdeHasta: number, ambarHasta: number): NivelSemaforo {
  if (porcentaje <= 0) return "VACIO";
  if (porcentaje <= verdeHasta) return "VERDE";
  if (porcentaje <= ambarHasta) return "AMBAR";
  return "ROJO";
}

export function extraerUmbrales(configuracion: UmbralesSemaforo): UmbralesSemaforo {
  const { almacenVerdeHasta, almacenAmbarHasta, vehiculoVerdeHasta, vehiculoAmbarHasta } = configuracion;
  return { almacenVerdeHasta, almacenAmbarHasta, vehiculoVerdeHasta, vehiculoAmbarHasta };
}

const CLAVE = "paqrap:umbrales:v1";

export function leerUmbrales(clave: string): UmbralesSemaforo | null {
  try {
    const valor = JSON.parse(localStorage.getItem(clave) ?? "null") as UmbralesSemaforo | null;
    if (!valor) return null;
    const numeros = [valor.almacenVerdeHasta, valor.almacenAmbarHasta, valor.vehiculoVerdeHasta, valor.vehiculoAmbarHasta];
    if (!numeros.every((n) => typeof n === "number" && Number.isFinite(n) && n >= 0 && n <= 100)
        || valor.almacenVerdeHasta >= valor.almacenAmbarHasta
        || valor.vehiculoVerdeHasta >= valor.vehiculoAmbarHasta) return null;
    return extraerUmbrales(valor);
  } catch {
    return null;
  }
}

export function guardarUmbrales(clave: string, umbrales: UmbralesSemaforo): boolean {
  try {
    localStorage.setItem(clave, JSON.stringify(extraerUmbrales(umbrales)));
    return true;
  } catch {
    return false;
  }
}

export const claveUmbralesActuales = CLAVE;
export const claveUmbralesEjecucion = (id: number) => `${CLAVE}:ejecucion:${id}`;
