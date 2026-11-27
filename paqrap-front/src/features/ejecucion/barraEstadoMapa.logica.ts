/*
 * Reglas puras de la barra superior del mapa (sin React) para poder
 * probarlas con vitest.
 */

export type TonoAlertas = "ok" | "aviso" | "colapso";

export interface ResumenAlertas {
  tono: TonoAlertas;
  texto: string;
}

/** Texto y color del aviso de alertas visible con el panel plegado. */
export function resumenAlertas(
  cantidad: number,
  colapso: boolean,
): ResumenAlertas {
  if (colapso) {
    return { tono: "colapso", texto: "Colapso registrado" };
  }

  if (cantidad > 0) {
    return {
      tono: "aviso",
      texto: `${cantidad} ${cantidad === 1 ? "alerta" : "alertas"}`,
    };
  }

  return { tono: "ok", texto: "Sin alertas" };
}

/** Esc cierra el panel solo si está abierto. */
export function teclaCierraPanel(
  tecla: string,
  abierto: boolean,
): boolean {
  return abierto && tecla === "Escape";
}
