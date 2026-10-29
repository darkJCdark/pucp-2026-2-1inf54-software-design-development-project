import type { ParadaMapa, RutaMapa, VehiculoMapa } from "./ejecucion.types";
import {
  arriboFisicoMs,
  calcularPosicionVehiculoAnimada,
  fechaMs,
} from "./movimientoVehiculos";

export type EstadoParadaVisual =
  | "COMPLETADA"
  | "REFRIGERIO"
  | "EN_SERVICIO"
  | "SIGUIENTE"
  | "FUTURA";

export interface ParadaSeguimiento {
  parada: ParadaMapa;
  estado: EstadoParadaVisual;
}

export interface SeguimientoRuta {
  posicionVisual: { x: number; y: number };
  enMovimiento: boolean;
  enRefrigerio: boolean;
  refrigerioHasta: string | null;
  progresoTramo: number;
  progresoRuta: number;
  siguienteParada: ParadaMapa | null;
  paradaEnServicio: ParadaMapa | null;
  paradaEnRefrigerio: ParadaMapa | null;
  eta: string | null;
  paradas: ParadaSeguimiento[];
}

function clamp01(valor: number) {
  return Math.min(1, Math.max(0, valor));
}

export function obtenerSeguimientoRuta(
  ruta: RutaMapa,
  vehiculo: VehiculoMapa,
  instanteMs: number,
): SeguimientoRuta {
  const posicion = calcularPosicionVehiculoAnimada(vehiculo, ruta, instanteMs);
  const paradasOrdenadas = [...ruta.paradas].sort((a, b) => a.orden - b.orden);

  const paradaEnRefrigerio =
    paradasOrdenadas.find((parada) => {
      const inicio = fechaMs(parada.inicioRefrigerio);
      const finRefrigerio = fechaMs(parada.llegada);
      return (
        Number.isFinite(inicio) &&
        Number.isFinite(finRefrigerio) &&
        instanteMs >= inicio &&
        instanteMs < finRefrigerio
      );
    }) ?? null;

  const paradaEnServicio =
    paradasOrdenadas.find((parada) => {
      const llegada = fechaMs(parada.llegada);
      const fin = fechaMs(parada.fin);
      return (
        Number.isFinite(llegada) &&
        Number.isFinite(fin) &&
        instanteMs >= llegada &&
        instanteMs < fin
      );
    }) ?? null;

  const siguienteParada =
    paradaEnRefrigerio ??
    paradaEnServicio ??
    paradasOrdenadas.find((parada) => {
      const arribo = arriboFisicoMs(parada);
      return Number.isFinite(arribo) && arribo > instanteMs;
    }) ??
    null;

  const paradas: ParadaSeguimiento[] = paradasOrdenadas.map((parada) => {
    const fin = fechaMs(parada.fin);

    if (Number.isFinite(fin) && instanteMs >= fin) {
      return { parada, estado: "COMPLETADA" };
    }
    if (paradaEnRefrigerio?.orden === parada.orden) {
      return { parada, estado: "REFRIGERIO" };
    }
    if (paradaEnServicio?.orden === parada.orden) {
      return { parada, estado: "EN_SERVICIO" };
    }
    if (siguienteParada?.orden === parada.orden) {
      return { parada, estado: "SIGUIENTE" };
    }
    return { parada, estado: "FUTURA" };
  });

  const salida = fechaMs(ruta.salida);
  const final = fechaMs(
    ruta.llegadaFinal ?? paradasOrdenadas.at(-1)?.fin ?? paradasOrdenadas.at(-1)?.llegada,
  );
  const progresoRuta =
    Number.isFinite(salida) && Number.isFinite(final) && final > salida
      ? clamp01((instanteMs - salida) / (final - salida))
      : 0;

  return {
    posicionVisual: { x: posicion.x, y: posicion.y },
    enMovimiento: posicion.enMovimiento,
    enRefrigerio: paradaEnRefrigerio !== null || vehiculo.motivoDetencion === "REFRIGERIO",
    refrigerioHasta:
      vehiculo.motivoDetencion === "REFRIGERIO"
        ? vehiculo.detenidoHasta
        : paradaEnRefrigerio?.llegada ?? null,
    progresoTramo: posicion.progresoTramo,
    progresoRuta,
    siguienteParada,
    paradaEnServicio,
    paradaEnRefrigerio,
    eta: siguienteParada?.inicioRefrigerio ?? siguienteParada?.llegada ?? null,
    paradas,
  };
}

export function nombreParada(parada: ParadaMapa) {
  return parada.tipo === "ALMACEN"
    ? `Almacén ${parada.almacenId ?? ""}`.trim()
    : `Pedido ${parada.pedidoId ?? ""}`.trim();
}
