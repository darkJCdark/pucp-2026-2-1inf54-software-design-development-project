import type { RutaMapa, TramoRutaMapa } from "./ejecucion.types";

export interface PuntoRuta {
  x: number;
  y: number;
}

export interface TramoTemporizado extends TramoRutaMapa {
  salidaMs: number;
  llegadaMs: number;
}

/** Una polilínea contiene exclusivamente calles contiguas que llegaron en el plan. */
export type TrazoRuta = PuntoRuta[];

export interface TrazosRuta {
  recorridos: TrazoRuta[];
  actuales: TrazoRuta[];
  futuros: TrazoRuta[];
  pendientes: TrazoRuta[];
}

// Las rutas del adaptador son inmutables. Evita parsear cientos de fechas en cada frame.
const cacheTramos = new WeakMap<RutaMapa, TramoTemporizado[]>();

export function obtenerTramosTemporizados(ruta: RutaMapa): TramoTemporizado[] {
  const guardados = cacheTramos.get(ruta);
  if (guardados) return guardados;

  const tramos = (ruta.tramos ?? []).map((tramo) => ({
    ...tramo,
    salidaMs: Date.parse(tramo.salida),
    llegadaMs: Date.parse(tramo.llegada),
  }));
  const validos = tramos.every((tramo, indice) => {
    const anterior = tramos[indice - 1];
    return (
      [tramo.desdeX, tramo.desdeY, tramo.hastaX, tramo.hastaY].every(Number.isInteger) &&
      Number.isInteger(tramo.ordenParada) && tramo.ordenParada > 0 &&
      Math.abs(tramo.hastaX - tramo.desdeX) + Math.abs(tramo.hastaY - tramo.desdeY) === 1 &&
      Number.isFinite(tramo.salidaMs) && Number.isFinite(tramo.llegadaMs) &&
      tramo.llegadaMs > tramo.salidaMs &&
      (!anterior || (
        tramo.salidaMs >= anterior.llegadaMs &&
        tramo.desdeX === anterior.hastaX && tramo.desdeY === anterior.hastaY &&
        tramo.ordenParada >= anterior.ordenParada
      ))
    );
  });
  // Ante geometría ausente o inválida no fabricar una L ni una diagonal.
  const resultado = validos ? tramos : [];
  cacheTramos.set(ruta, resultado);
  return resultado;
}

/** Primer tramo que aún no ha terminado. La llegada pertenece al tramo siguiente/espera. */
export function buscarTramoPendiente(tramos: TramoTemporizado[], instanteMs: number): number {
  let izquierda = 0;
  let derecha = tramos.length;
  while (izquierda < derecha) {
    const medio = Math.floor((izquierda + derecha) / 2);
    if (tramos[medio].llegadaMs <= instanteMs) izquierda = medio + 1;
    else derecha = medio;
  }
  return izquierda;
}

export function puntoEnTramo(tramo: TramoTemporizado, instanteMs: number): PuntoRuta {
  const progreso = Math.min(1, Math.max(0,
    (instanteMs - tramo.salidaMs) / (tramo.llegadaMs - tramo.salidaMs),
  ));
  return {
    x: tramo.desdeX + (tramo.hastaX - tramo.desdeX) * progreso,
    y: tramo.desdeY + (tramo.hastaY - tramo.desdeY) * progreso,
  };
}

function mismoPunto(a: PuntoRuta, b: PuntoRuta) {
  return Math.abs(a.x - b.x) < 1e-9 && Math.abs(a.y - b.y) < 1e-9;
}

/** No unir extremos distintos: eso introduciría calles que el backend nunca calculó. */
function agregarSegmento(trazos: TrazoRuta[], desde: PuntoRuta, hasta: PuntoRuta) {
  if (mismoPunto(desde, hasta)) return;
  const ultimo = trazos.at(-1);
  if (ultimo && mismoPunto(ultimo[ultimo.length - 1], desde)) ultimo.push(hasta);
  else trazos.push([desde, hasta]);
}

/** Dibujo y animación parten de los mismos RoadLeg. No hay un enrutador en React. */
export function obtenerTrazosRuta(
  ruta: RutaMapa,
  instanteMs: number,
  ordenSiguienteParada?: number | null,
): TrazosRuta {
  const resultado: TrazosRuta = { recorridos: [], actuales: [], futuros: [], pendientes: [] };
  if (!Number.isFinite(instanteMs)) return resultado;

  for (const tramo of obtenerTramosTemporizados(ruta)) {
    const origen = { x: tramo.desdeX, y: tramo.desdeY };
    const destino = { x: tramo.hastaX, y: tramo.hastaY };
    if (instanteMs >= tramo.llegadaMs) {
      agregarSegmento(resultado.recorridos, origen, destino);
      continue;
    }
    const posicion = puntoEnTramo(tramo, instanteMs);
    if (instanteMs > tramo.salidaMs) agregarSegmento(resultado.recorridos, origen, posicion);
    agregarSegmento(resultado.pendientes, posicion, destino);
    agregarSegmento(
      tramo.ordenParada === ordenSiguienteParada ? resultado.actuales : resultado.futuros,
      posicion,
      destino,
    );
  }
  return resultado;
}

export function convertirTrazoAPixeles(
  trazo: TrazoRuta,
  convertirX: (valor: number) => number,
  convertirY: (valor: number) => number,
): number[] {
  return trazo.flatMap((punto) => [convertirX(punto.x), convertirY(punto.y)]);
}
