import type {
  RutaMapa,
  VehiculoMapa,
} from "./ejecucion.types";

export interface PosicionMapa {
  x: number;
  y: number;
}

export interface PosicionVehiculoAnimada
  extends PosicionMapa {
  enMovimiento: boolean;
  progresoTramo: number;
  destino: PosicionMapa | null;
}

function fechaMs(
  valor:
    | string
    | null
    | undefined,
) {
  if (!valor) {
    return Number.NaN;
  }

  return new Date(
    valor,
  ).getTime();
}

function clamp01(
  valor: number,
) {
  return Math.min(
    1,
    Math.max(
      0,
      valor,
    ),
  );
}

/**
 * Decide de manera estable si la ruta
 * visual va primero horizontal o vertical.
 */
export function horizontalPrimeroParaVehiculo(
  vehiculoId: string,
) {
  let hash = 0;

  for (
    let index = 0;
    index <
    vehiculoId.length;
    index += 1
  ) {
    hash =
      (hash * 31 +
        vehiculoId.charCodeAt(
          index,
        )) |
      0;
  }

  return (
    Math.abs(hash) %
      2 ===
    0
  );
}

/**
 * Interpola en trayectoria Manhattan.
 *
 * Nunca dibuja diagonales.
 */
export function interpolarOrtogonal(
  origen: PosicionMapa,
  destino: PosicionMapa,
  progreso: number,
  horizontalPrimero: boolean,
): PosicionMapa {
  const p =
    clamp01(
      progreso,
    );

  const dx =
    destino.x -
    origen.x;

  const dy =
    destino.y -
    origen.y;

  const largoX =
    Math.abs(dx);

  const largoY =
    Math.abs(dy);

  const total =
    largoX +
    largoY;

  if (total === 0) {
    return {
      ...destino,
    };
  }

  const recorrido =
    total * p;

  const signoX =
    Math.sign(dx);

  const signoY =
    Math.sign(dy);

  if (horizontalPrimero) {
    if (
      recorrido <=
      largoX
    ) {
      return {
        x:
          origen.x +
          signoX *
            recorrido,

        y: origen.y,
      };
    }

    return {
      x: destino.x,

      y:
        origen.y +
        signoY *
          (recorrido -
            largoX),
    };
  }

  if (
    recorrido <=
    largoY
  ) {
    return {
      x: origen.x,

      y:
        origen.y +
        signoY *
          recorrido,
    };
  }

  return {
    x:
      origen.x +
      signoX *
        (recorrido -
          largoY),

    y: destino.y,
  };
}

/**
 * Calcula únicamente dónde debe DIBUJARSE
 * el vehículo.
 *
 * El backend continúa siendo autoridad
 * sobre estado, carga, entregas y posición
 * confirmada.
 */
export function calcularPosicionVehiculoAnimada(
  vehiculo: VehiculoMapa,
  ruta: RutaMapa | undefined,
  instanteSimuladoMs: number,
): PosicionVehiculoAnimada {
  const posicionBackend = {
    x: vehiculo.x,
    y: vehiculo.y,
  };

  if (
    vehiculo.estado !==
      "EN_RUTA" ||
    !ruta ||
    ruta.paradas.length ===
      0 ||
    !Number.isFinite(
      instanteSimuladoMs,
    )
  ) {
    return {
      ...posicionBackend,
      enMovimiento: false,
      progresoTramo: 0,
      destino: null,
    };
  }

  const paradas = [
    ...ruta.paradas,
  ].sort(
    (a, b) =>
      a.orden -
      b.orden,
  );

  /*
   * Si llegó a una parada y está
   * realizando el tiempo de servicio,
   * se queda quieto ahí.
   */
  for (
    const parada of paradas
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
      return {
        x: parada.x,
        y: parada.y,

        enMovimiento:
          false,

        progresoTramo: 1,

        destino: {
          x: parada.x,
          y: parada.y,
        },
      };
    }
  }

  /*
   * Primera parada cuya llegada
   * aún está en el futuro.
   */
  const indiceSiguiente =
    paradas.findIndex(
      (parada) => {
        const llegada =
          fechaMs(
            parada.llegada,
          );

        return (
          Number.isFinite(
            llegada,
          ) &&
          llegada >
            instanteSimuladoMs
        );
      },
    );

  if (
    indiceSiguiente < 0
  ) {
    return {
      ...posicionBackend,

      enMovimiento:
        false,

      progresoTramo: 1,

      destino: null,
    };
  }

  const siguiente =
    paradas[
      indiceSiguiente
    ];

  const anterior =
    indiceSiguiente > 0
      ? paradas[
          indiceSiguiente -
            1
        ]
      : null;

  const inicioTramo =
    anterior
      ? fechaMs(
          anterior.fin ??
            anterior.llegada,
        )
      : fechaMs(
          ruta.salida,
        );

  const finTramo =
    fechaMs(
      siguiente.llegada,
    );

  const origen:
    PosicionMapa =
    anterior
      ? {
          x: anterior.x,
          y: anterior.y,
        }
      : posicionBackend;

  const destino:
    PosicionMapa = {
    x: siguiente.x,
    y: siguiente.y,
  };

  if (
    !Number.isFinite(
      inicioTramo,
    ) ||
    !Number.isFinite(
      finTramo,
    ) ||
    finTramo <=
      inicioTramo
  ) {
    return {
      ...posicionBackend,

      enMovimiento:
        false,

      progresoTramo: 0,

      destino,
    };
  }

  if (
    instanteSimuladoMs <=
    inicioTramo
  ) {
    return {
      ...origen,

      enMovimiento:
        false,

      progresoTramo: 0,

      destino,
    };
  }

  const progreso =
    clamp01(
      (instanteSimuladoMs -
        inicioTramo) /
        (finTramo -
          inicioTramo),
    );

  const posicion =
    interpolarOrtogonal(
      origen,
      destino,
      progreso,
      horizontalPrimeroParaVehiculo(
        vehiculo.id,
      ),
    );

  return {
    ...posicion,

    enMovimiento:
      progreso > 0 &&
      progreso < 1,

    progresoTramo:
      progreso,

    destino,
  };
}