import type { PlanResponseDto, ParadaResponseDto } from "@/api/planificacion.dto";

/** Paradas de almacén del plan vigente: no representan entregas de pedidos. */
export function operacionesAlmacen(plan: Pick<PlanResponseDto, "rutas"> | null, almacenId: string, ahora: number) {
  const operaciones = (plan?.rutas ?? []).flatMap((ruta) => ruta.paradas
    .filter((parada) => parada.tipo === "ALMACEN" && parada.almacenId === almacenId && parada.llegada)
    .map((parada: ParadaResponseDto) => ({
      rutaId: ruta.id,
      vehiculoId: ruta.vehiculoId,
      orden: parada.orden,
      paquetes: parada.paquetes,
      salida: ruta.salida,
      llegada: parada.llegada!,
    })));
  const cuatroHoras = 4 * 60 * 60 * 1000;
  return {
    enTransito: operaciones.filter((op) => Date.parse(op.salida) <= ahora && Date.parse(op.llegada) > ahora),
    proximas4h: operaciones.filter((op) => Date.parse(op.llegada) >= ahora && Date.parse(op.llegada) <= ahora + cuatroHoras),
    atendidas4h: operaciones.filter((op) => Date.parse(op.llegada) < ahora && Date.parse(op.llegada) >= ahora - cuatroHoras),
  };
}
