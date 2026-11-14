import { describe, expect, it } from "vitest";
import type { ParadaResponseDto, RutaResponseDto } from "@/api/planificacion.dto";
import { operacionesAlmacen } from "./operacionesAlmacen";

const ahora = Date.parse("2026-11-12T12:00:00Z");
function parada(orden: number, horas: number, extra: Partial<ParadaResponseDto> = {}): ParadaResponseDto {
  return { orden, tipo: "ALMACEN", almacenId: "EAST", pedidoId: null,
    x: 57, y: 27, paquetes: 8, llegada: new Date(ahora + horas * 3_600_000).toISOString(),
    fin: null, cargaAntes: null, cargaDespues: null, ...extra };
}
function plan(paradas: ParadaResponseDto[], salida = "2026-11-12T11:00:00Z") {
  const ruta: RutaResponseDto = { id: "R1", vehiculoId: "TM02", tipoVehiculo: "MOTORCYCLE",
    salida, llegadaFinal: null, distanciaKm: 0, costo: 0, paradas };
  return { rutas: [ruta] };
}

describe("operacionesAlmacen", () => {
  it("conserva los límites de cuatro horas sin duplicar el instante actual como pasado", () => {
    const resultado = operacionesAlmacen(plan([-4.01, -4, -1, 0, 4, 4.01].map((h, i) => parada(i, h))), "EAST", ahora);
    expect(resultado.atendidas4h.map((op) => op.orden)).toEqual([1, 2]);
    expect(resultado.proximas4h.map((op) => op.orden)).toEqual([3, 4]);
    expect(resultado.enTransito.map((op) => op.orden)).toEqual([4, 5]);
  });
  it("excluye pedidos, trasvases, otros almacenes y horarios ausentes", () => {
    const resultado = operacionesAlmacen(plan([
      parada(1, 1, { tipo: "ENTREGA", pedidoId: "P1", almacenId: null }),
      parada(2, 1, { tipo: "TRASVASE", almacenId: null }),
      parada(3, 1, { almacenId: "CENTRAL" }), parada(4, 1, { llegada: null }),
    ]), "EAST", ahora);
    expect(resultado).toEqual({ enTransito: [], proximas4h: [], atendidas4h: [] });
  });
  it("conserva ruta, UT y cantidad sin inventar pedidos ni una entrega confirmada", () => {
    const resultado = operacionesAlmacen(plan([parada(1, 1)], "2026-11-12T12:30:00Z"), "EAST", ahora);
    expect(resultado.enTransito).toHaveLength(0);
    expect(resultado.proximas4h[0]).toMatchObject({ rutaId: "R1", vehiculoId: "TM02", paquetes: 8, orden: 1 });
    expect(resultado.proximas4h[0]).not.toHaveProperty("pedidoId");
    expect(resultado.proximas4h[0]).not.toHaveProperty("entregadoEn");
  });
  it("admite un plan ausente", () => {
    expect(operacionesAlmacen(null, "EAST", ahora)).toEqual({ enTransito: [], proximas4h: [], atendidas4h: [] });
  });
});
