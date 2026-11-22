import { afterEach, describe, expect, it, vi } from "vitest";
import { detallesService } from "./detalles.service";
import { pedidosService } from "./pedidos.service";

function prepararRespuesta(data: unknown = {}) {
  const fetchMock = vi.fn<typeof fetch>(async () => new Response(JSON.stringify(data), {
    headers: { "Content-Type": "application/json" },
  }));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("pedidos de ejecuciones simultáneas", () => {
  it("consulta el mismo pedido con el identificador de cada ejecución", async () => {
    const fetchMock = prepararRespuesta();
    const instante = "2026-11-21T15:00:00Z";

    await detallesService.pedido("202611-00001", instante, 8);
    await detallesService.pedido("202611-00001", instante, 9);

    const urls = fetchMock.mock.calls.map(([url]) => new URL(String(url), "http://localhost"));
    expect(urls.map((url) => url.searchParams.get("ejecucionId"))).toEqual(["8", "9"]);
    expect(urls.map((url) => url.searchParams.get("instante"))).toEqual([instante, instante]);
    expect(urls.every((url) => url.pathname === "/api/pedidos/202611-00001")).toBe(true);
  });

  it("registra el pedido manual en la operación elegida sin cambiar el contrato JSON", async () => {
    const fetchMock = prepararRespuesta();
    const pedido = { clienteId: "CLI01", x: 10, y: 20, paquetes: 3, tipoEntrega: "REGULAR" as const };

    await pedidosService.registrar(pedido, 17);

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(fetchMock.mock.calls[0][0]).toBe("/api/pedidos?ejecucionId=17");
    expect(fetchMock.mock.calls[0][1]?.method).toBe("POST");
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual(pedido);
  });

  it("conserva las consultas de catálogo sin ejecución y codifica el identificador", async () => {
    const fetchMock = prepararRespuesta();

    await pedidosService.obtener("pedido / 1");

    expect(fetchMock.mock.calls[0][0]).toBe("/api/pedidos/pedido%20%2F%201");
  });
});
