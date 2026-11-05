import type { Escenario } from "./configuracion.types";

const MESES = [
  "enero",
  "febrero",
  "marzo",
  "abril",
  "mayo",
  "junio",
  "julio",
  "agosto",
  "septiembre",
  "octubre",
  "noviembre",
  "diciembre",
] as const;

export function periodoDesdeArchivoVentas(nombre: string) {
  const match = /^ventas\.(\d{4})(\d{2})\.txt$/i.exec(nombre);
  if (!match) return null;

  const mes = Number(match[2]);
  if (mes < 1 || mes > 12) return null;

  return `${match[1]}-${match[2]}`;
}

export function periodoDesdeArchivoBloqueos(nombre: string) {
  const match = /^bloqueo\.(\d{2})(\d{2})\.txt$/i.exec(nombre);
  if (!match) return null;

  const mes = Number(match[2]);
  if (mes < 1 || mes > 12) return null;

  return `20${match[1]}-${match[2]}`;
}

export function periodosRequeridos(
  escenario: Escenario,
  inicioSimulado: string,
) {
  if (escenario === "DIA_A_DIA" || !inicioSimulado) return [];

  const inicio = fechaLocalUtc(inicioSimulado);
  if (!inicio) return [];

  const fin = new Date(inicio.getTime());
  if (escenario === "SIMULACION_5D") {
    fin.setUTCDate(fin.getUTCDate() + 5);
  }

  const resultado: string[] = [];
  let anio = inicio.getUTCFullYear();
  let mes = inicio.getUTCMonth() + 1;
  const anioFin = fin.getUTCFullYear();
  const mesFin = fin.getUTCMonth() + 1;

  while (anio < anioFin || (anio === anioFin && mes <= mesFin)) {
    resultado.push(`${anio}-${String(mes).padStart(2, "0")}`);
    mes += 1;
    if (mes === 13) {
      mes = 1;
      anio += 1;
    }
  }

  return resultado;
}

export function archivoVentasEsperado(periodo: string) {
  return `ventas.${periodo.replace("-", "")}.txt`;
}

export function periodoLegible(periodo: string) {
  const match = /^(\d{4})-(\d{2})$/.exec(periodo);
  if (!match) return periodo;

  const mes = Number(match[2]);
  return `${MESES[mes - 1] ?? match[2]} ${match[1]}`;
}

function fechaLocalUtc(valor: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(valor);
  if (!match) return null;

  const fecha = new Date(
    Date.UTC(
      Number(match[1]),
      Number(match[2]) - 1,
      Number(match[3]),
      Number(match[4]),
      Number(match[5]),
      Number(match[6] ?? 0),
    ),
  );

  return Number.isNaN(fecha.getTime()) ? null : fecha;
}
