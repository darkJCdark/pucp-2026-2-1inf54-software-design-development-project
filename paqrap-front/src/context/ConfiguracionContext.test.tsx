import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, expect, it, vi } from "vitest";
import { claveUmbralesActuales, claveUmbralesEjecucion } from "@/features/configuracion/semaforo";
import { ConfiguracionProvider, UmbralesEjecucionProvider, useUmbralesEjecucion } from "./ConfiguracionContext";

function Vista() {
  const { umbrales, guardados } = useUmbralesEjecucion();
  return <span>{umbrales.almacenVerdeHasta}:{String(guardados)}</span>;
}

afterEach(() => vi.unstubAllGlobals());

it("recupera el snapshot de la ejecución y avisa cuando solo hay configuración actual", () => {
  const base = { almacenVerdeHasta: 33, almacenAmbarHasta: 66, vehiculoVerdeHasta: 60, vehiculoAmbarHasta: 85 };
  const datos = new Map([[claveUmbralesActuales, JSON.stringify({ ...base, almacenVerdeHasta: 40 })],
    [claveUmbralesEjecucion(1), JSON.stringify(base)]]);
  vi.stubGlobal("localStorage", { getItem: (clave: string) => datos.get(clave) ?? null });
  const renderizar = (id: number) => renderToStaticMarkup(<ConfiguracionProvider>
    <UmbralesEjecucionProvider ejecucionId={id}><Vista /></UmbralesEjecucionProvider>
  </ConfiguracionProvider>);
  expect(renderizar(1)).toContain("33:true");
  expect(renderizar(2)).toContain("40:false");
});
