import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

import { BarraEstadoMapa } from "./BarraEstadoMapa";
import { resumenAlertas, teclaCierraPanel } from "./barraEstadoMapa.logica";

function renderizar(abierto: boolean, alertas = 0, colapso = false) {
  return renderToStaticMarkup(
    <BarraEstadoMapa
      titulo="Simulación #30"
      estadoTexto="En ejecución"
      estadoTono="activo"
      pendientes={4}
      vencidos={0}
      entregados={26}
      alertas={alertas}
      colapso={colapso}
      tiempos={<span>franja-de-tiempos</span>}
      abierto={abierto}
      onCambiarAbierto={vi.fn()}
      onAbrirAlertas={vi.fn()}
    >
      <p>tarjetas-del-panel</p>
    </BarraEstadoMapa>,
  );
}

describe("resumenAlertas", () => {
  it("muestra 'Sin alertas' cuando no hay ninguna", () => {
    expect(resumenAlertas(0, false)).toEqual({ tono: "ok", texto: "Sin alertas" });
  });

  it("cuenta las alertas con singular y plural", () => {
    expect(resumenAlertas(1, false)).toEqual({ tono: "aviso", texto: "1 alerta" });
    expect(resumenAlertas(2, false)).toEqual({ tono: "aviso", texto: "2 alertas" });
  });

  it("el colapso tiene prioridad sobre las alertas", () => {
    expect(resumenAlertas(3, true)).toEqual({ tono: "colapso", texto: "Colapso registrado" });
  });
});

describe("teclaCierraPanel", () => {
  it("Esc cierra solo si el panel está abierto", () => {
    expect(teclaCierraPanel("Escape", true)).toBe(true);
    expect(teclaCierraPanel("Escape", false)).toBe(false);
    expect(teclaCierraPanel("Enter", true)).toBe(false);
  });
});

describe("BarraEstadoMapa", () => {
  it("muestra siempre escenario, estado, contadores, alertas y tiempos con palabras completas", () => {
    const html = renderizar(false);
    expect(html).toContain("Simulación #30");
    expect(html).toContain("En ejecución");
    expect(html).toContain("Pendientes");
    expect(html).toContain("Vencidos");
    expect(html).toContain("Entregados");
    expect(html).toContain("Sin alertas");
    expect(html).toContain("franja-de-tiempos");
  });

  it("con el panel plegado oculta las tarjetas sin quitarlas (conserva su estado)", () => {
    const plegado = renderizar(false);
    expect(plegado).toContain("tarjetas-del-panel");
    expect(plegado).toContain('aria-expanded="false"');
    expect(plegado).toMatch(/class="[^"]*hidden[^"]*"[^>]*><p>tarjetas-del-panel/);
  });

  it("con el panel abierto muestra las tarjetas", () => {
    const abierto = renderizar(true);
    expect(abierto).toContain('aria-expanded="true"');
    expect(abierto).not.toMatch(/class="[^"]*\bhidden\b[^"]*"[^>]*><p>tarjetas-del-panel/);
  });

  it("avisa las alertas y el colapso en el botón", () => {
    expect(renderizar(false, 2)).toContain("2 alertas");
    expect(renderizar(false, 0, true)).toContain("Colapso registrado");
  });
});
