import type { ParadaMapa, RutaMapa, VehiculoMapa } from "./ejecucion.types";

export interface PosicionMapa {
  x: number;
  y: number;
}

export interface PosicionVehiculoAnimada extends PosicionMapa {
  enMovimiento: boolean;
  progresoTramo: number;
  destino: PosicionMapa | null;
}

export function fechaMs(valor: string | null | undefined) {
  return valor ? new Date(valor).getTime() : Number.NaN;
}

export function arriboFisicoMs(parada: ParadaMapa) {
  return fechaMs(parada.inicioRefrigerio ?? parada.llegada);
}

function clamp01(valor: number) {
  return Math.min(1, Math.max(0, valor));
}

export function horizontalPrimeroParaVehiculo(vehiculoId: string) {
  let hash = 0;
  for (let index = 0; index < vehiculoId.length; index += 1) {
    hash = (hash * 31 + vehiculoId.charCodeAt(index)) | 0;
  }
  return Math.abs(hash) % 2 === 0;
}

export function interpolarOrtogonal(
  origen: PosicionMapa,
  destino: PosicionMapa,
  progreso: number,
  horizontalPrimero: boolean,
): PosicionMapa {
  const p = clamp01(progreso);
  const dx = destino.x - origen.x;
  const dy = destino.y - origen.y;
  const largoX = Math.abs(dx);
  const largoY = Math.abs(dy);
  const total = largoX + largoY;

  if (total === 0) return { ...destino };

  const recorrido = total * p;
  const signoX = Math.sign(dx);
  const signoY = Math.sign(dy);

  if (horizontalPrimero) {
    if (recorrido <= largoX) {
      return { x: origen.x + signoX * recorrido, y: origen.y };
    }
    return { x: destino.x, y: origen.y + signoY * (recorrido - largoX) };
  }

  if (recorrido <= largoY) {
    return { x: origen.x, y: origen.y + signoY * recorrido };
  }
  return { x: origen.x + signoX * (recorrido - largoY), y: destino.y };
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
 * Posición únicamente visual. Replica el criterio del backend:
 * - usa inicioRefrigerio como arribo físico si existe;
 * - mantiene el vehículo quieto durante refrigerio/servicio;
 * - avanza por el camino real de la parada (`camino`) o, si no viene, en L;
 * - no reemplaza el estado operacional persistido.
 */
export function calcularPosicionVehiculoAnimada(
  vehiculo: VehiculoMapa,
  ruta: RutaMapa | undefined,
  instanteSimuladoMs: number,
): PosicionVehiculoAnimada {
  const posicionBackend = { x: vehiculo.x, y: vehiculo.y };

  if (
    vehiculo.estado !== "EN_RUTA" ||
    !ruta ||
    ruta.paradas.length === 0 ||
    !Number.isFinite(instanteSimuladoMs)
  ) {
    return { ...posicionBackend, enMovimiento: false, progresoTramo: 0, destino: null };
  }

  const paradas = [...ruta.paradas].sort((a, b) => a.orden - b.orden);

  for (const parada of paradas) {
    const enElLugar = arriboFisicoMs(parada);
    const fin = fechaMs(parada.fin);
    if (
      Number.isFinite(enElLugar) &&
      Number.isFinite(fin) &&
      instanteSimuladoMs >= enElLugar &&
      instanteSimuladoMs < fin
    ) {
      return {
        x: parada.x,
        y: parada.y,
        enMovimiento: false,
        progresoTramo: 1,
        destino: { x: parada.x, y: parada.y },
      };
    }
  }

  const indiceSiguiente = paradas.findIndex(
    (parada) => Number.isFinite(arriboFisicoMs(parada)) && arriboFisicoMs(parada) > instanteSimuladoMs,
  );

  if (indiceSiguiente < 0) {
    return { ...posicionBackend, enMovimiento: false, progresoTramo: 1, destino: null };
  }

  const siguiente = paradas[indiceSiguiente];
  const anterior = indiceSiguiente > 0 ? paradas[indiceSiguiente - 1] : null;
  const inicioTramo = anterior
    ? fechaMs(anterior.fin ?? anterior.llegada)
    : fechaMs(ruta.salida);
  const finTramo = arriboFisicoMs(siguiente);
  const origen = anterior ? { x: anterior.x, y: anterior.y } : posicionBackend;
  const destino = { x: siguiente.x, y: siguiente.y };

  if (!Number.isFinite(inicioTramo) || !Number.isFinite(finTramo) || finTramo <= inicioTramo) {
    return { ...posicionBackend, enMovimiento: false, progresoTramo: 0, destino };
  }

  if (instanteSimuladoMs <= inicioTramo) {
    return { ...origen, enMovimiento: false, progresoTramo: 0, destino };
  }

  const progreso = clamp01((instanteSimuladoMs - inicioTramo) / (finTramo - inicioTramo));
  // Con el camino real del plan rodea los bloqueos; sin él (planes antiguos), camino en L.
  const posicion = caminoValido(siguiente.camino)
    ? interpolarEnCamino(siguiente.camino, progreso)
    : interpolarOrtogonal(
        origen,
        destino,
        progreso,
        horizontalPrimeroParaVehiculo(vehiculo.id),
      );

  return {
    ...posicion,
    enMovimiento: progreso > 0 && progreso < 1,
    progresoTramo: progreso,
    destino,
  };
}
