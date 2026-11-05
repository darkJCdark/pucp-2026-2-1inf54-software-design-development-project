import type { ParadaMapa, RutaMapa, VehiculoMapa } from "./ejecucion.types";
import {
  buscarTramoPendiente,
  obtenerTramosTemporizados,
  puntoEnTramo,
} from "./geometriaRutas";

export interface PosicionMapa {
  x: number;
  y: number;
}

export interface PosicionVehiculoAnimada extends PosicionMapa {
  enMovimiento: boolean;
  progresoTramo: number;
  destino: PosicionMapa | null;
  direccionGrados: number | null;
}

export function fechaMs(valor: string | null | undefined) {
  return valor ? Date.parse(valor) : Number.NaN;
}

export function arriboFisicoMs(parada: ParadaMapa) {
  return fechaMs(parada.inicioRefrigerio ?? parada.llegada);
}

function detenido(
  posicion: PosicionMapa,
  destino: PosicionMapa | null = null,
  progresoTramo = 0,
): PosicionVehiculoAnimada {
  return { ...posicion, enMovimiento: false, progresoTramo, destino, direccionGrados: null };
}

/** Camino real informado por el backend, si tiene al menos un tramo. */
export function caminoValido(
  camino: PosicionMapa[] | null | undefined,
): camino is PosicionMapa[] {
  return Array.isArray(camino) && camino.length >= 2;
}

/**
 * Recorre `progreso` (0 a 1) del camino real (calles de 1 km a velocidad pareja).
 * Mismo cálculo que EjecutorPlanService.interpolarEnCamino del backend, sin redondear.
 */
export function interpolarEnCamino(
  camino: PosicionMapa[],
  progreso: number,
): PosicionMapa {
  const calles = camino.length - 1;
  const recorrido = clamp01(progreso) * calles;
  const indice = Math.min(Math.floor(recorrido), calles - 1);
  const fraccion = recorrido - indice;
  const desde = camino[indice];
  const hasta = camino[indice + 1];
  return {
    x: desde.x + (hasta.x - desde.x) * fraccion,
    y: desde.y + (hasta.y - desde.y) * fraccion,
  };
}

/** Posición continua de `punto` a lo largo del camino (índice de calle + fracción). */
function avanceEnCamino(camino: PosicionMapa[], punto: PosicionMapa): number {
  let mejor = 0;
  let menorDistancia = Number.POSITIVE_INFINITY;
  for (let indice = 0; indice < camino.length - 1; indice += 1) {
    const a = camino[indice];
    const b = camino[indice + 1];
    const largo = Math.abs(b.x - a.x) + Math.abs(b.y - a.y);
    const t = largo === 0
      ? 0
      : clamp01(((punto.x - a.x) * (b.x - a.x) + (punto.y - a.y) * (b.y - a.y)) / (largo * largo));
    const x = a.x + (b.x - a.x) * t;
    const y = a.y + (b.y - a.y) * t;
    const distancia = Math.hypot(punto.x - x, punto.y - y);
    if (distancia < menorDistancia - 1e-9) {
      menorDistancia = distancia;
      mejor = indice + t;
    }
  }
  return mejor;
}

/**
 * Nodos del camino que hay que recorrer para ir de `desde` a `hasta`, ambos sobre el camino
 * (p. ej. la posición actual del vehículo y la siguiente parada). Incluye los extremos.
 */
export function tramoDeCamino(
  camino: PosicionMapa[],
  desde: PosicionMapa,
  hasta: PosicionMapa,
): PosicionMapa[] {
  const inicio = avanceEnCamino(camino, desde);
  const fin = avanceEnCamino(camino, hasta);
  if (fin < inicio) {
    return [desde, hasta];
  }
  const intermedios = camino.filter(
    (_, indice) => indice > inicio + 1e-9 && indice < fin - 1e-9,
  );
  return [desde, ...intermedios, hasta];
}

/**
 * Sólo suaviza la posición usando los tramos reales del plan vigente.
 * Los huecos entre RoadLeg son esperas; nunca se reparte ese tiempo como movimiento.
 * Sin geometría válida se conserva la posición de monitoreo, no se inventa una ruta.
 */
export function calcularPosicionVehiculoAnimada(
  vehiculo: VehiculoMapa,
  ruta: RutaMapa | undefined,
  instanteSimuladoMs: number,
): PosicionVehiculoAnimada {
  const posicionBackend = { x: vehiculo.x, y: vehiculo.y };
  if (vehiculo.estado !== "EN_RUTA" || !ruta || !Number.isFinite(instanteSimuladoMs)) {
    return detenido(posicionBackend);
  }
  const salida = fechaMs(ruta.salida);
  if (!Number.isFinite(salida) || instanteSimuladoMs < salida) return detenido(posicionBackend);

  for (const parada of ruta.paradas) {
    const arribo = arriboFisicoMs(parada);
    const fin = fechaMs(parada.fin);
    if (Number.isFinite(arribo) && Number.isFinite(fin) &&
        instanteSimuladoMs >= arribo && instanteSimuladoMs < fin) {
      const lugar = { x: parada.x, y: parada.y };
      return detenido(lugar, lugar, 1);
    }
  }

  const tramos = obtenerTramosTemporizados(ruta);
  if (tramos.length === 0) return detenido(posicionBackend);

  const indice = buscarTramoPendiente(tramos, instanteSimuladoMs);
  const tramo = tramos[indice];
  if (!tramo) {
    const ultimo = tramos[tramos.length - 1];
    return detenido({ x: ultimo.hastaX, y: ultimo.hastaY }, null, 1);
  }
  const destino = { x: tramo.hastaX, y: tramo.hastaY };
  if (instanteSimuladoMs < tramo.salidaMs) {
    return detenido({ x: tramo.desdeX, y: tramo.desdeY }, destino);
  }

  return {
    ...puntoEnTramo(tramo, instanteSimuladoMs),
    enMovimiento: true,
    progresoTramo: (instanteSimuladoMs - tramo.salidaMs) / (tramo.llegadaMs - tramo.salidaMs),
    destino,
    // Dirección de la calle actual, no del punto del siguiente frame (podría estar después de una curva).
    direccionGrados: Math.atan2(-(tramo.hastaY - tramo.desdeY), tramo.hastaX - tramo.desdeX) * 180 / Math.PI,
  };
}
