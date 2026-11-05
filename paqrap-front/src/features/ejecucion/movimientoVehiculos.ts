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
