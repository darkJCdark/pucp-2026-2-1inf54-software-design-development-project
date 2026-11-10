import type {
  ParadaMapa,
  RutaMapa,
  VehiculoMapa,
} from "./ejecucion.types";

import {
  buscarTramoPendiente,
  obtenerTramosTemporizados,
  puntoEnTramo,
} from "./geometriaRutas";

export interface PosicionMapa {
  x: number;
  y: number;
}

export interface PosicionVehiculoAnimada
  extends PosicionMapa {
  enMovimiento: boolean;
  progresoTramo: number;
  destino: PosicionMapa | null;
  direccionGrados: number | null;
}

export function fechaMs(
  valor:
    | string
    | null
    | undefined,
) {
  return valor
    ? Date.parse(valor)
    : Number.NaN;
}

/**
 * Ahora llegada representa siempre
 * la llegada física real a la parada.
 */
export function arriboFisicoMs(
  parada:
    ParadaMapa,
) {
  return fechaMs(
    parada.llegada,
  );
}

function detenido(
  posicion:
    PosicionMapa,

  destino:
    PosicionMapa | null =
      null,

  progresoTramo =
    0,
): PosicionVehiculoAnimada {
  return {
    ...posicion,

    enMovimiento:
      false,

    progresoTramo,

    destino,

    direccionGrados:
      null,
  };
}

export function caminoValido(
  camino:
    | PosicionMapa[]
    | null
    | undefined,
): camino is PosicionMapa[] {
  return (
    Array.isArray(
      camino,
    ) &&
    camino.length >= 2
  );
}

export function interpolarEnCamino(
  camino:
    PosicionMapa[],

  progreso:
    number,
): PosicionMapa {
  const calles =
    camino.length - 1;

  const recorrido =
    clamp01(
      progreso,
    ) *
    calles;

  const indice =
    Math.min(
      Math.floor(
        recorrido,
      ),
      calles - 1,
    );

  const fraccion =
    recorrido -
    indice;

  const desde =
    camino[indice];

  const hasta =
    camino[
      indice + 1
    ];

  return {
    x:
      desde.x +
      (
        hasta.x -
        desde.x
      ) *
        fraccion,

    y:
      desde.y +
      (
        hasta.y -
        desde.y
      ) *
        fraccion,
  };
}

function avanceEnCamino(
  camino:
    PosicionMapa[],

  punto:
    PosicionMapa,
): number {
  let mejor =
    0;

  let menorDistancia =
    Number
      .POSITIVE_INFINITY;

  for (
    let indice = 0;
    indice <
      camino.length - 1;
    indice += 1
  ) {
    const a =
      camino[
        indice
      ];

    const b =
      camino[
        indice + 1
      ];

    const dx =
      b.x - a.x;

    const dy =
      b.y - a.y;

    const largoCuadrado =
      dx * dx +
      dy * dy;

    const t =
      largoCuadrado === 0
        ? 0
        : clamp01(
            (
              (
                punto.x -
                a.x
              ) *
                dx +
              (
                punto.y -
                a.y
              ) *
                dy
            ) /
              largoCuadrado,
          );

    const x =
      a.x +
      dx * t;

    const y =
      a.y +
      dy * t;

    const distancia =
      Math.hypot(
        punto.x -
          x,

        punto.y -
          y,
      );

    if (
      distancia <
      menorDistancia -
        1e-9
    ) {
      menorDistancia =
        distancia;

      mejor =
        indice + t;
    }
  }

  return mejor;
}

export function tramoDeCamino(
  camino:
    PosicionMapa[],

  desde:
    PosicionMapa,

  hasta:
    PosicionMapa,
): PosicionMapa[] {
  const inicio =
    avanceEnCamino(
      camino,
      desde,
    );

  const fin =
    avanceEnCamino(
      camino,
      hasta,
    );

  if (
    fin < inicio
  ) {
    return [
      desde,
      hasta,
    ];
  }

  const intermedios =
    camino.filter(
      (
        _,
        indice,
      ) =>
        indice >
          inicio +
            1e-9 &&
        indice <
          fin -
            1e-9,
    );

  return [
    desde,
    ...intermedios,
    hasta,
  ];
}

/**
 * Posición exclusivamente visual.
 *
 * Fuente de verdad:
 * RoadLeg/tramos temporizados del backend.
 *
 * Durante servicio o refrigerio:
 * el vehículo permanece en la parada.
 *
 * Si no existe geometría:
 * mantiene la posición de monitoreo.
 */
export function calcularPosicionVehiculoAnimada(
  vehiculo:
    VehiculoMapa,

  ruta:
    RutaMapa |
    undefined,

  instanteSimuladoMs:
    number,
): PosicionVehiculoAnimada {
  const posicionBackend = {
    x:
      vehiculo.x,

    y:
      vehiculo.y,
  };

  if (
    vehiculo.estado !==
      "EN_RUTA" ||
    !ruta ||
    !Number.isFinite(
      instanteSimuladoMs,
    )
  ) {
    return detenido(
      posicionBackend,
    );
  }

  const salida =
    fechaMs(
      ruta.salida,
    );

  if (
    !Number.isFinite(
      salida,
    ) ||
    instanteSimuladoMs <
      salida
  ) {
    return detenido(
      posicionBackend,
    );
  }

  /*
   * =====================================================
   * SERVICIO / REFRIGERIO
   * =====================================================
   *
   * llegada -> fin:
   *
   * el vehículo permanece físicamente
   * en la parada.
   *
   * seguimientoRuta.ts distingue
   * internamente servicio y refrigerio.
   */

  for (
    const parada of
      ruta.paradas
  ) {
    const llegada =
      fechaMs(
        parada.llegada,
      );

    const fin =
      fechaMs(
        parada.fin,
      );

    if (
      Number.isFinite(
        llegada,
      ) &&
      Number.isFinite(
        fin,
      ) &&
      instanteSimuladoMs >=
        llegada &&
      instanteSimuladoMs <
        fin
    ) {
      const lugar = {
        x:
          parada.x,

        y:
          parada.y,
      };

      return detenido(
        lugar,
        lugar,
        1,
      );
    }
  }

  /*
   * =====================================================
   * MOVIMIENTO REAL
   * =====================================================
   */

  const tramos =
    obtenerTramosTemporizados(
      ruta,
    );

  if (
    tramos.length === 0
  ) {
    return detenido(
      posicionBackend,
    );
  }

  const indice =
    buscarTramoPendiente(
      tramos,
      instanteSimuladoMs,
    );

  const tramo =
    tramos[
      indice
    ];

  if (!tramo) {
    const ultimo =
      tramos[
        tramos.length -
          1
      ];

    return detenido(
      {
        x:
          ultimo.hastaX,

        y:
          ultimo.hastaY,
      },
      null,
      1,
    );
  }

  const destino = {
    x:
      tramo.hastaX,

    y:
      tramo.hastaY,
  };

  /*
   * Hueco temporal entre dos RoadLeg:
   * espera, servicio o refrigerio.
   */
  if (
    instanteSimuladoMs <
    tramo.salidaMs
  ) {
    return detenido(
      {
        x:
          tramo.desdeX,

        y:
          tramo.desdeY,
      },
      destino,
    );
  }

  const duracion =
    tramo.llegadaMs -
    tramo.salidaMs;

  const progreso =
    duracion > 0
      ? clamp01(
          (
            instanteSimuladoMs -
            tramo.salidaMs
          ) /
            duracion,
        )
      : 1;

  return {
    ...puntoEnTramo(
      tramo,
      instanteSimuladoMs,
    ),

    enMovimiento:
      duracion > 0 &&
      progreso < 1,

    progresoTramo:
      progreso,

    destino,

    direccionGrados:
      Math.atan2(
        -(
          tramo.hastaY -
          tramo.desdeY
        ),

        tramo.hastaX -
          tramo.desdeX,
      ) *
      180 /
      Math.PI,
  };
}

function clamp01(
  valor:
    number,
) {
  return Math.min(
    1,
    Math.max(
      0,
      valor,
    ),
  );
}