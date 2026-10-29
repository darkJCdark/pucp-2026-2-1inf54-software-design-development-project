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

/**
 * Posición únicamente visual. Replica el criterio del backend:
 * - usa inicioRefrigerio como arribo físico si existe;
 * - mantiene el vehículo quieto durante refrigerio/servicio;
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
  const posicion = interpolarOrtogonal(
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
