import type {
  ConfiguracionGrasp,
  ParadaAlgoritmo,
  PedidoAlgoritmo,
  RutaAlgoritmo,
  SolucionAlgoritmo,
  VehiculoAlgoritmo,
} from "./algoritmo.types";

const ALMACEN_CENTRAL = {
  x: 25,
  y: 15,
};

function distanciaManhattan(
  x1: number,
  y1: number,
  x2: number,
  y2: number,
): number {
  return Math.abs(x1 - x2) + Math.abs(y1 - y2);
}

/**
 * Generador pseudoaleatorio simple con semilla.
 * Nos permite repetir exactamente el mismo experimento.
 */
function crearRandom(semillaInicial: number) {
  let semilla = semillaInicial;

  return () => {
    semilla =
      (semilla * 1664525 + 1013904223) % 4294967296;

    return semilla / 4294967296;
  };
}

function evaluarRuta(
  pedidos: PedidoAlgoritmo[],
  vehiculo: VehiculoAlgoritmo,
): RutaAlgoritmo | null {
  let xActual = ALMACEN_CENTRAL.x;
  let yActual = ALMACEN_CENTRAL.y;

  let tiempoAcumulado = 0;
  let distanciaTotal = 0;
  let cargaTotal = 0;

  const paradas: ParadaAlgoritmo[] = [];

  for (const pedido of pedidos) {
    cargaTotal += pedido.cantidad;

    if (cargaTotal > vehiculo.capacidad) {
      return null;
    }

    const distancia = distanciaManhattan(
      xActual,
      yActual,
      pedido.x,
      pedido.y,
    );

    distanciaTotal += distancia;

    const horasViaje =
      distancia / vehiculo.velocidad;

    tiempoAcumulado += horasViaje;

    /**
     * En esta primera versión:
     * si llegamos luego del plazo, la ruta es infactible.
     */
    if (tiempoAcumulado > pedido.plazoHoras) {
      return null;
    }

    paradas.push({
      pedidoId: pedido.id,
      x: pedido.x,
      y: pedido.y,
      cantidad: pedido.cantidad,
      horaLlegada: tiempoAcumulado,
    });

    xActual = pedido.x;
    yActual = pedido.y;
  }

  /**
   * Regresamos al almacén central
   * al terminar la ruta.
   */
  distanciaTotal += distanciaManhattan(
    xActual,
    yActual,
    ALMACEN_CENTRAL.x,
    ALMACEN_CENTRAL.y,
  );

  return {
    vehiculoId: vehiculo.id,
    tipoVehiculo: vehiculo.tipo,
    paradas,
    cargaTotal,
    distanciaTotal,
    costoTotal:
      distanciaTotal * vehiculo.costoKm,
  };
}

/**
 * Incremento aproximado de distancia al añadir
 * un pedido al final de la ruta.
 */
function calcularIncrementoDistancia(
  pedidosRuta: PedidoAlgoritmo[],
  candidato: PedidoAlgoritmo,
): number {
  const ultimo =
    pedidosRuta.length > 0
      ? pedidosRuta[pedidosRuta.length - 1]
      : ALMACEN_CENTRAL;

  const distanciaActualRegreso =
    distanciaManhattan(
      ultimo.x,
      ultimo.y,
      ALMACEN_CENTRAL.x,
      ALMACEN_CENTRAL.y,
    );

  const distanciaConCandidato =
    distanciaManhattan(
      ultimo.x,
      ultimo.y,
      candidato.x,
      candidato.y,
    ) +
    distanciaManhattan(
      candidato.x,
      candidato.y,
      ALMACEN_CENTRAL.x,
      ALMACEN_CENTRAL.y,
    );

  return distanciaConCandidato - distanciaActualRegreso;
}

function construirRutaGrasp(
  vehiculo: VehiculoAlgoritmo,
  pendientes: PedidoAlgoritmo[],
  alpha: number,
  random: () => number,
): {
  ruta: RutaAlgoritmo | null;
  pedidosUsados: PedidoAlgoritmo[];
} {
  const pedidosRuta: PedidoAlgoritmo[] = [];

  while (true) {
    const candidatos = pendientes
      .filter(
        (pedido) =>
          !pedidosRuta.some(
            (actual) => actual.id === pedido.id,
          ),
      )
      .map((pedido) => {
        const rutaTentativa = [
          ...pedidosRuta,
          pedido,
        ];

        const evaluacion = evaluarRuta(
          rutaTentativa,
          vehiculo,
        );

        if (!evaluacion) {
          return null;
        }

        return {
          pedido,
          puntuacion:
            calcularIncrementoDistancia(
              pedidosRuta,
              pedido,
            ) * vehiculo.costoKm,
        };
      })
      .filter(
        (
          candidato,
        ): candidato is {
          pedido: PedidoAlgoritmo;
          puntuacion: number;
        } => candidato !== null,
      );

    if (candidatos.length === 0) {
      break;
    }

    const puntuaciones = candidatos.map(
      (candidato) => candidato.puntuacion,
    );

    const minimo = Math.min(...puntuaciones);
    const maximo = Math.max(...puntuaciones);

    const limiteRcl =
      minimo + alpha * (maximo - minimo);

    const rcl = candidatos.filter(
      (candidato) =>
        candidato.puntuacion <= limiteRcl,
    );

    const indice = Math.floor(
      random() * rcl.length,
    );

    const seleccionado = rcl[indice];

    pedidosRuta.push(seleccionado.pedido);
  }

  if (pedidosRuta.length === 0) {
    return {
      ruta: null,
      pedidosUsados: [],
    };
  }

  const ruta = mejorarRuta2Opt(
    pedidosRuta,
    vehiculo,
  );

  return {
    ruta,
    pedidosUsados: pedidosRuta,
  };
}

/**
 * Búsqueda local muy sencilla:
 * prueba inversiones 2-opt.
 */
function mejorarRuta2Opt(
  pedidos: PedidoAlgoritmo[],
  vehiculo: VehiculoAlgoritmo,
): RutaAlgoritmo {
  let mejorOrden = [...pedidos];

  let mejorRuta = evaluarRuta(
    mejorOrden,
    vehiculo,
  );

  if (!mejorRuta) {
    throw new Error(
      "La ruta inicial de GRASP debería ser factible.",
    );
  }

  let huboMejora = true;

  while (huboMejora) {
    huboMejora = false;

    for (let i = 0; i < mejorOrden.length - 1; i++) {
      for (
        let j = i + 1;
        j < mejorOrden.length;
        j++
      ) {
        const candidato = [...mejorOrden];

        const segmentoInvertido = candidato
          .slice(i, j + 1)
          .reverse();

        candidato.splice(
          i,
          j - i + 1,
          ...segmentoInvertido,
        );

        const rutaCandidata = evaluarRuta(
          candidato,
          vehiculo,
        );

        if (
          rutaCandidata &&
          rutaCandidata.costoTotal <
            mejorRuta.costoTotal
        ) {
          mejorOrden = candidato;
          mejorRuta = rutaCandidata;
          huboMejora = true;
        }
      }
    }
  }

  return mejorRuta;
}

function construirSolucion(
  pedidos: PedidoAlgoritmo[],
  vehiculos: VehiculoAlgoritmo[],
  alpha: number,
  random: () => number,
): SolucionAlgoritmo {
  let pendientes = [...pedidos];

  const rutas: RutaAlgoritmo[] = [];

  for (const vehiculo of vehiculos) {
    if (pendientes.length === 0) {
      break;
    }

    const {
      ruta,
      pedidosUsados,
    } = construirRutaGrasp(
      vehiculo,
      pendientes,
      alpha,
      random,
    );

    if (!ruta) {
      continue;
    }

    rutas.push(ruta);

    const idsUsados = new Set(
      pedidosUsados.map((pedido) => pedido.id),
    );

    pendientes = pendientes.filter(
      (pedido) => !idsUsados.has(pedido.id),
    );
  }

  const distanciaTotal = rutas.reduce(
    (total, ruta) =>
      total + ruta.distanciaTotal,
    0,
  );

  const costoTotal = rutas.reduce(
    (total, ruta) =>
      total + ruta.costoTotal,
    0,
  );

  return {
    rutas,
    pedidosNoAsignados: pendientes,
    distanciaTotal,
    costoTotal,
    factible: pendientes.length === 0,
  };
}

function esMejorSolucion(
  candidata: SolucionAlgoritmo,
  actual: SolucionAlgoritmo | null,
): boolean {
  if (!actual) {
    return true;
  }

  /**
   * Primero priorizamos factibilidad.
   */
  if (candidata.factible && !actual.factible) {
    return true;
  }

  if (!candidata.factible && actual.factible) {
    return false;
  }

  /**
   * Si ambas son infactibles,
   * gana la que deja menos pedidos sin asignar.
   */
  if (!candidata.factible && !actual.factible) {
    if (
      candidata.pedidosNoAsignados.length !==
      actual.pedidosNoAsignados.length
    ) {
      return (
        candidata.pedidosNoAsignados.length <
        actual.pedidosNoAsignados.length
      );
    }
  }

  /**
   * Si están empatadas en factibilidad,
   * elegimos menor costo.
   */
  return candidata.costoTotal < actual.costoTotal;
}

export function ejecutarGrasp(
  pedidos: PedidoAlgoritmo[],
  vehiculos: VehiculoAlgoritmo[],
  configuracion: ConfiguracionGrasp,
): SolucionAlgoritmo {
  const alpha = Math.min(
    1,
    Math.max(0, configuracion.alpha),
  );

  const random = crearRandom(
    configuracion.semilla,
  );

  let mejorSolucion: SolucionAlgoritmo | null =
    null;

  for (
    let iteracion = 0;
    iteracion < configuracion.maxIteraciones;
    iteracion++
  ) {
    /**
     * Cambiamos el orden de vehículos entre
     * iteraciones para diversificar soluciones.
     */
    const vehiculosAleatorios = [
      ...vehiculos,
    ].sort(() => random() - 0.5);

    const solucion = construirSolucion(
      pedidos,
      vehiculosAleatorios,
      alpha,
      random,
    );

    if (
      esMejorSolucion(solucion, mejorSolucion)
    ) {
      mejorSolucion = solucion;
    }
  }

  if (!mejorSolucion) {
    return {
      rutas: [],
      pedidosNoAsignados: [...pedidos],
      distanciaTotal: 0,
      costoTotal: 0,
      factible: false,
    };
  }

  return mejorSolucion;
}