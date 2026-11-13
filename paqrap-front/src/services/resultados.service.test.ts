import { afterEach, expect, it, vi } from "vitest";
import { resultadosService } from "./resultados.service";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

it("solicita CSV y descarga el archivo recibido", async () => {
  const fetchMock = vi.fn<typeof fetch>(async () => new Response("id;estado\n8;COMPLETED\n", {
    headers: { "Content-Type": "text/csv", "Content-Disposition": 'attachment; filename="resultado-8.csv"' },
  }));
  vi.stubGlobal("fetch", fetchMock);
  const enlace = { href: "", download: "", click: vi.fn(), remove: vi.fn() };
  vi.stubGlobal("document", { createElement: () => enlace, body: { appendChild: vi.fn() } });
  vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:test");
  vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {});

  await resultadosService.descargarCsv(8);

  expect(fetchMock).toHaveBeenCalledOnce();
  expect(fetchMock.mock.calls[0][0]).toBe("/api/escenarios/8/resultado/csv");
  expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get("Accept")).toBe("text/csv");
  expect(enlace.download).toBe("resultado-8.csv");
  expect(enlace.click).toHaveBeenCalledOnce();
});
