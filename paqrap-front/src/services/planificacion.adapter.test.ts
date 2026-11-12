import { describe, expect, it } from "vitest";

import type { RutaResponseDto } from "@/api/planificacion.dto";
import { nombreParada } from "@/features/ejecucion/seguimientoRuta";

import { adaptarRuta } from "./planificacion.adapter";

describe("adaptarRuta", () => {
  it("conserva una parada TRASVASE junto a entregas y almacenes", () => {
    const ruta: RutaResponseDto = {
      id: "R-TRASVASE",
      vehiculoId: "TM02",
      tipoVehiculo: "MOTORCYCLE",
      salida: "2026-11-12T10:00:00Z",
      llegadaFinal: "2026-11-12T12:00:00Z",
      distanciaKm: 12,
      costo: 5,
      paradas: [
        {
          orden: 1,
          tipo: "ALMACEN",
          pedidoId: null,
          almacenId: "ALM-CENTRAL",
          x: 12,
          y: 8,
          paquetes: 0,
          llegada: "2026-11-12T10:10:00Z",
          fin: "2026-11-12T10:15:00Z",
          cargaAntes: 0,
          cargaDespues: 4,
        },
        {
          orden: 2,
          tipo: "TRASVASE",
          pedidoId: null,
          almacenId: null,
          x: 27,
          y: 20,
          paquetes: 4,
          llegada: "2026-11-12T10:30:00Z",
          fin: "2026-11-12T10:35:00Z",
          cargaAntes: 0,
          cargaDespues: 4,
          camino: [
            { x: 12, y: 8 },
            { x: 27, y: 20 },
          ],
        },
        {
          orden: 3,
          tipo: "ENTREGA",
          pedidoId: "PED-01",
          almacenId: null,
          x: 30,
          y: 23,
          paquetes: 4,
          llegada: "2026-11-12T10:45:00Z",
          fin: "2026-11-12T10:50:00Z",
          cargaAntes: 4,
          cargaDespues: 0,
        },
      ],
    };

    const adaptada = adaptarRuta(ruta);

    expect(adaptada.paradas.map((parada) => parada.tipo)).toEqual([
      "ALMACEN",
      "TRASVASE",
      "ENTREGA",
    ]);
    expect(adaptada.paradas[1]).toMatchObject({
      pedidoId: null,
      almacenId: null,
      x: 27,
      y: 20,
      paquetes: 4,
      llegada: "2026-11-12T10:30:00Z",
      fin: "2026-11-12T10:35:00Z",
      camino: [
        { x: 12, y: 8 },
        { x: 27, y: 20 },
      ],
    });
    expect(nombreParada(adaptada.paradas[0])).toBe("Almacén ALM-CENTRAL");
    expect(nombreParada(adaptada.paradas[1])).toBe("Trasvase");
    expect(nombreParada(adaptada.paradas[2])).toBe("Pedido PED-01");
  });
});
