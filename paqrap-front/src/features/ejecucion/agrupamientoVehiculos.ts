/** Mantiene visibles individualmente los vehículos en movimiento y el seleccionado. */
export function claveGrupoVehiculo(
  id: string,
  x: number,
  y: number,
  enMovimiento: boolean,
  seleccionado: boolean,
): string {
  return enMovimiento || seleccionado ? `individual-${id}` : `fijo-${x.toFixed(3)},${y.toFixed(3)}`;
}

/** Desde dos vehículos quietos en el mismo punto, no sólo a partir de siete. */
export function debeAgruparVehiculos(cantidadVisible: number, contieneSeleccionado: boolean): boolean {
  return cantidadVisible > 1 && !contieneSeleccionado;
}
