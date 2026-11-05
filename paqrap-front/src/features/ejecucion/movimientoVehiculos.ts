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
  valor: string | null | undefined,
) {
  return valor
    ? Date.parse(valor)
    : Number.NaN;
}

export function arriboFisicoMs(
  parada: ParadaMapa,
) {
  return fechaMs(
    parada.inicioRefrigerio ??
      parada.llegada,
  );
}

function detenido(
  posicion: PosicionMapa,
  destino: PosicionMapa | null = null,
  progresoTramo = 0,
): PosicionVehiculoAnimada {
  return {
    ...posicion,
    enMovimiento: false,
    progresoTramo,
    destino,
    direccionGrados: null,
  };
}

/**
 * Verifica que exista un camino con
 * al menos origen y destino.
 *
 * Se conserva porque develop también
 * publica camino a nivel de parada.
 */
export function caminoValido(
  camino:
    | PosicionMapa[]
    | null
    | undefined,
): camino is PosicionMapa[] {
  return (
    Array.isArray(camino) &&
    camino.length >= 2
  );
}

/**
 * Recorre un camino real usando un
 * progreso continuo entre 0 y 1.
 *
 * Esta función queda disponible para
 * compatibilidad con el nuevo campo
 * `camino`, aunque el movimiento visual
 * principal utiliza los RoadLeg/tramos
 * temporizados del backend.
 */
export function interpolarEnCamino(
  camino: PosicionMapa[],
  progreso: number,
): PosicionMapa {
  const calles =
    camino.length - 1;

  const recorrido =
    clamp01(progreso) *
    calles;

  const indice =
    Math.min(
      Math.floor(recorrido),
      calles - 1,
    );

  const fraccion =
    recorrido - indice;

  const desde =
    camino[indice];

  const hasta =
    camino[indice + 1];

  return {
    x:
      desde.x +
      (hasta.x - desde.x) *
        fraccion,

    y:
      desde.y +
      (hasta.y - desde.y) *
        fraccion,
  };
}

/**
 * Devuelve la posición continua de un
 * punto dentro de un camino.
 */
function avanceEnCamino(
  camino: PosicionMapa[],
  punto: PosicionMapa,
): number {
  let mejor = 0;

  let menorDistancia =
    Number.POSITIVE_INFINITY;

  for (
    let indice = 0;
    indice < camino.length - 1;
    indice += 1
  ) {
    const a =
      camino[indice];

    const b =
      camino[indice + 1];

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
              (punto.x - a.x) * dx +
              (punto.y - a.y) * dy
            ) /
              largoCuadrado,
          );

    const x =
      a.x + dx * t;

    const y =
      a.y + dy * t;

    const distancia =
      Math.hypot(
        punto.x - x,
        punto.y - y,
      );

    if (
      distancia <
      menorDistancia - 1e-9
    ) {
      menorDistancia =
        distancia;

      mejor =
        indice + t;
    }
  }

  return mejor;
}

/**
 * Obtiene la parte de un camino comprendida
 * entre dos puntos.
 */
export function tramoDeCamino(
  camino: PosicionMapa[],
  desde: PosicionMapa,
  hasta: PosicionMapa,
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
      (_, indice) =>
        indice >
          inicio + 1e-9 &&
        indice <
          fin - 1e-9,
    );

  return [
    desde,
    ...intermedios,
    hasta,
  ];
}

/**
 * Calcula únicamente la posición visual.
 *
 * La fuente de verdad son los tramos reales
 * temporizados enviados por el backend.
 *
 * Los huecos entre RoadLeg representan
 * esperas: el vehículo permanece detenido.
 *
 * Si no hay geometría real válida, se usa
 * la posición confirmada por monitoreo.
 * No se inventa una ruta en L.
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
   * Durante refrigerio o servicio,
   * el vehículo permanece en la parada.
   */
  for (
    const parada of
      ruta.paradas
  ) {
    const arribo =
      arriboFisicoMs(
        parada,
      );

    const fin =
      fechaMs(
        parada.fin,
      );

    if (
      Number.isFinite(
        arribo,
      ) &&
      Number.isFinite(
        fin,
      ) &&
      instanteSimuladoMs >=
        arribo &&
      instanteSimuladoMs <
        fin
    ) {
      const lugar = {
        x: parada.x,
        y: parada.y,
      };

      return detenido(
        lugar,
        lugar,
        1,
      );
    }
  }

  /*
   * RoadLeg reales publicados por backend.
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
    tramos[indice];

  /*
   * Ya terminó todos los tramos.
   */
  if (!tramo) {
    const ultimo =
      tramos[
        tramos.length - 1
      ];

    return detenido(
      {
        x: ultimo.hastaX,
        y: ultimo.hastaY,
      },
      null,
      1,
    );
  }

  const destino = {
    x: tramo.hastaX,
    y: tramo.hastaY,
  };

  /*
   * Existe un hueco temporal antes del
   * siguiente RoadLeg: es una espera.
   */
  if (
    instanteSimuladoMs <
    tramo.salidaMs
  ) {
    return detenido(
      {
        x: tramo.desdeX,
        y: tramo.desdeY,
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