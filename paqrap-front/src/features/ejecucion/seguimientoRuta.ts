import type {
  ParadaMapa,
  RutaMapa,
  VehiculoMapa,
} from "./ejecucion.types";

import {
  calcularPosicionVehiculoAnimada,
} from "./movimientoVehiculos";

export type EstadoParadaVisual =
  | "COMPLETADA"
  | "EN_SERVICIO"
  | "SIGUIENTE"
  | "FUTURA";

export interface ParadaSeguimiento {
  parada: ParadaMapa;
  estado: EstadoParadaVisual;
}

export interface SeguimientoRuta {
  posicionVisual: {
    x: number;
    y: number;
  };
  enMovimiento: boolean;
  progresoTramo: number;
  progresoRuta: number;
  siguienteParada: ParadaMapa | null;
  paradaEnServicio: ParadaMapa | null;
  eta: string | null;
  paradas: ParadaSeguimiento[];
}

function fechaMs(
  valor: string | null | undefined,
) {
  if (!valor) {
    return Number.NaN;
  }

  return new Date(valor).getTime();
}

function clamp01(valor: number) {
  return Math.min(1, Math.max(0, valor));
}

export function obtenerSeguimientoRuta(
  ruta: RutaMapa,
  vehiculo: VehiculoMapa,
  instanteMs: number,
): SeguimientoRuta {
  const posicion =
    calcularPosicionVehiculoAnimada(
      vehiculo,
      ruta,
      instanteMs,
    );

  const paradasOrdenadas = [
    ...ruta.paradas,
  ].sort((a, b) => a.orden - b.orden);

  const paradaEnServicio =
    paradasOrdenadas.find((parada) => {
      const llegada = fechaMs(
        parada.llegada,
      );
      const fin = fechaMs(parada.fin);

      return (
        Number.isFinite(llegada) &&
        Number.isFinite(fin) &&
        instanteMs >= llegada &&
        instanteMs < fin
      );
    }) ?? null;

  const siguienteParada =
    paradaEnServicio ??
    paradasOrdenadas.find((parada) => {
      const llegada = fechaMs(
        parada.llegada,
      );

      return (
        Number.isFinite(llegada) &&
        llegada > instanteMs
      );
    }) ??
    null;

  const paradas: ParadaSeguimiento[] =
    paradasOrdenadas.map((parada) => {
      const llegada = fechaMs(
        parada.llegada,
      );
      const fin = fechaMs(parada.fin);

      if (
        Number.isFinite(fin) &&
        instanteMs >= fin
      ) {
        return {
          parada,
          estado: "COMPLETADA",
        };
      }

      if (
        !Number.isFinite(fin) &&
        Number.isFinite(llegada) &&
        instanteMs >= llegada
      ) {
        return {
          parada,
          estado: "COMPLETADA",
        };
      }

      if (
        paradaEnServicio?.orden ===
        parada.orden
      ) {
        return {
          parada,
          estado: "EN_SERVICIO",
        };
      }

      if (
        siguienteParada?.orden ===
        parada.orden
      ) {
        return {
          parada,
          estado: "SIGUIENTE",
        };
      }

      return {
        parada,
        estado: "FUTURA",
      };
    });

  const salida = fechaMs(ruta.salida);

  const final = fechaMs(
    ruta.llegadaFinal ??
      paradasOrdenadas.at(-1)?.fin ??
      paradasOrdenadas.at(-1)?.llegada,
  );

  const progresoRuta =
    Number.isFinite(salida) &&
    Number.isFinite(final) &&
    final > salida
      ? clamp01(
          (instanteMs - salida) /
            (final - salida),
        )
      : 0;

  return {
    posicionVisual: {
      x: posicion.x,
      y: posicion.y,
    },

    enMovimiento:
      posicion.enMovimiento,

    progresoTramo:
      posicion.progresoTramo,

    progresoRuta,

    siguienteParada,

    paradaEnServicio,

    eta:
      siguienteParada?.llegada ??
      null,

    paradas,
  };
}

export function nombreParada(
  parada: ParadaMapa,
) {
  if (
    parada.tipo ===
    "ALMACEN"
  ) {
    return `Almacén ${
      parada.almacenId ?? ""
    }`.trim();
  }

  return `Pedido ${
    parada.pedidoId ?? ""
  }`.trim();
}