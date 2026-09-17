import type {
  PedidoTemporal,
  ResultadoAsignacionTemporal,
  VehiculoTemporal,
} from "./simulacion.types";

const CENTRAL = {
  x: 25,
  y: 15,
};

const DURACION_ACONDICIONAMIENTO_MIN = 60;

function distanciaManhattan(
  x1: number,
  y1: number,
  x2: number,
  y2: number,
) {
  return (
    Math.abs(x1 - x2) +
    Math.abs(y1 - y2)
  );
}

export function asignarPedidoTemporal(
  pedido: PedidoTemporal,
  vehiculos: VehiculoTemporal[],
  tiempoActualMin: number,
): ResultadoAsignacionTemporal {
  const candidatos = vehiculos
    .map((vehiculo) => {
      // El vehículo no tiene capacidad suficiente
      if (
        pedido.cantidad >
        vehiculo.capacidad
      ) {
        return null;
      }

      /*
       * Si está ocupado, tendrá que esperar
       * hasta regresar al Central.
       */
      const inicioMin = Math.max(
        tiempoActualMin,
        vehiculo.disponibleDesdeMin,
      );

      const distanciaIdaKm =
        distanciaManhattan(
          CENTRAL.x,
          CENTRAL.y,
          pedido.x,
          pedido.y,
        );

      const tiempoIdaMin =
        (distanciaIdaKm /
          vehiculo.velocidad) *
        60;

      const llegadaPedidoMin =
        inicioMin + tiempoIdaMin;

      const limitePedidoMin =
        pedido.creadoEnMin +
        pedido.plazoHoras * 60;

      /*
       * El tiempo de acondicionamiento
       * no forma parte del plazo.
       */
      if (
        llegadaPedidoMin >
        limitePedidoMin
      ) {
        return null;
      }

      const finAcondicionamientoMin =
        llegadaPedidoMin +
        DURACION_ACONDICIONAMIENTO_MIN;

      const retornoCentralMin =
        finAcondicionamientoMin +
        tiempoIdaMin;

      const distanciaTotalKm =
        distanciaIdaKm * 2;

      const costoTotal =
        distanciaTotalKm *
        vehiculo.costoKm;

      return {
        vehiculo,
        inicioMin,
        llegadaPedidoMin,
        finAcondicionamientoMin,
        retornoCentralMin,
        distanciaIdaKm,
        distanciaTotalKm,
        costoTotal,
      };
    })
    .filter(
      (
        candidato,
      ): candidato is NonNullable<
        typeof candidato
      > => candidato !== null,
    );

  if (candidatos.length === 0) {
    return {
      asignacion: null,
      vehiculosActualizados: vehiculos,
      motivo:
        "No existe un vehículo que pueda atender el pedido dentro del plazo.",
    };
  }

  /*
   * Por ahora escogemos el candidato
   * de menor costo.
   *
   * Luego volvemos a meter aquí la RCL
   * de GRASP.
   */
  candidatos.sort(
    (a, b) =>
      a.costoTotal - b.costoTotal,
  );

  const seleccionado =
    candidatos[0];

  const vehiculosActualizados =
    vehiculos.map((vehiculo) =>
      vehiculo.id ===
      seleccionado.vehiculo.id
        ? {
            ...vehiculo,
            disponibleDesdeMin:
              seleccionado.retornoCentralMin,
          }
        : vehiculo,
    );

  return {
    asignacion: {
      pedidoId: pedido.id,
      vehiculoId:
        seleccionado.vehiculo.id,
      tipoVehiculo:
        seleccionado.vehiculo.tipo,

      inicioMin:
        seleccionado.inicioMin,

      llegadaPedidoMin:
        seleccionado.llegadaPedidoMin,

      finAcondicionamientoMin:
        seleccionado.finAcondicionamientoMin,

      retornoCentralMin:
        seleccionado.retornoCentralMin,

      distanciaIdaKm:
        seleccionado.distanciaIdaKm,

      distanciaTotalKm:
        seleccionado.distanciaTotalKm,

      costoTotal:
        seleccionado.costoTotal,
    },

    vehiculosActualizados,
  };
}