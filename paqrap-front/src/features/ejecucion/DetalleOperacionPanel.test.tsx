import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { DetalleOperacionPanel, type SeleccionDetalleOperacion } from "./DetalleOperacionPanel";

function renderizar(seleccion: SeleccionDetalleOperacion, ejecucionId = 3) {
  return renderToStaticMarkup(<DetalleOperacionPanel seleccion={seleccion} ejecucionId={ejecucionId}
    instante="2026-11-12T12:00:00Z" onCerrar={vi.fn()} onSeleccionar={vi.fn()} />);
}

describe("navegación desde detalle de UT", () => {
  it("ofrece volver a la UT de origen del pedido en la misma ejecución", () => {
    expect(renderizar({ tipo: "PEDIDO", id: "P1", origenVehiculo: { id: "TM07", ejecucionId: 3 } }))
      .toContain("Volver a TM07");
  });
  it("no ofrece volver para un pedido seleccionado directamente o de otra ejecución", () => {
    expect(renderizar({ tipo: "PEDIDO", id: "P1" })).not.toContain("Volver a");
    expect(renderizar({ tipo: "PEDIDO", id: "P1", origenVehiculo: { id: "TM07", ejecucionId: 3 } }, 4))
      .not.toContain("Volver a");
    expect(renderizar({ tipo: "VEHICULO", id: "TM07" })).not.toContain("Volver a");
  });
});
