import { afterEach, describe, expect, it, vi } from "vitest";
import { escenariosService } from "./escenarios.service";
import type { EscenarioResponseDto, EstadoEscenarioBackend, TipoEscenarioBackend } from "@/api/escenarios.dto";

function ejecucion(id: number, tipo: TipoEscenarioBackend, estado: EstadoEscenarioBackend): EscenarioResponseDto {
  return {
    id, tipo, estado, factorAceleracion: tipo === "DAY_TO_DAY" ? 1 : 160,
    inicioSimulado: "2026-11-21T05:00:00Z", instanteSimulado: "2026-11-21T06:00:00Z",
    finSimulado: null, colapsoEn: null, inicioReal: "2026-11-21T05:00:00Z", finReal: null,
  };
}

function respuesta(data: unknown) {
  return new Response(JSON.stringify(data), { headers: { "Content-Type": "application/json" } });
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("detección completa de ejecuciones activas", () => {
  it("encuentra una operación antigua en la segunda página detrás de 100 simulaciones", async () => {
    const antiguas = Array.from({ length: 100 }, (_, i) => ejecucion(102 - i, "FIVE_DAY", "COMPLETED"));
    antiguas[0] = ejecucion(102, "COLLAPSE", "PAUSED");
    const operacion = ejecucion(1, "DAY_TO_DAY", "RUNNING");
    const fetchMock = vi.fn<typeof fetch>(async (url) => {
      const pagina = new URL(String(url), "http://localhost").searchParams.get("page");
      return respuesta({ contenido: pagina === "0" ? antiguas : [operacion], totalPaginas: 2 });
    });
    vi.stubGlobal("fetch", fetchMock);

    expect(await escenariosService.listarActivas()).toEqual([antiguas[0], operacion]);
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      "/api/escenarios?page=0&size=100",
      "/api/escenarios?page=1&size=100",
    ]);
  });

  it("no permite dar por libre una plaza cuando falla una página posterior", async () => {
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(respuesta({ contenido: [], totalPaginas: 2 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ mensaje: "Servicio no disponible" }), { status: 503 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(escenariosService.listarActivas()).rejects.toThrow("Servicio no disponible");
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("rechaza un contrato incompleto en lugar de tratarlo como ausencia de actividad", async () => {
    vi.stubGlobal("fetch", vi.fn<typeof fetch>(async () => respuesta({ contenido: [] })));

    await expect(escenariosService.listarActivas()).rejects.toThrow("No se pudo verificar el estado de las ejecuciones.");
  });
});
