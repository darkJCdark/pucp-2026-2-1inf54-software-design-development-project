import {
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import {
  Package,
  Search,
} from "lucide-react";

import type {
  MonitoreoResponseDto,
} from "@/api/monitoreo.dto";

import type {
  EstadoPedidoBackend,
  PedidoResponseDto,
} from "@/api/pedidos.dto";

import type {
  PlanResponseDto,
} from "@/api/planificacion.dto";

import {
  Card,
} from "@/components/ui/card";

import {
  pedidosService,
} from "@/services/pedidos.service";

import {
  planificacionService,
} from "@/services/planificacion.service";

/** Selección común de los componentes de lista (contrato del plan de la semana 12). */
export type SeleccionLista =
  | { tipo: "VEHICULO"; id: string }
  | { tipo: "PEDIDO"; id: string }
  | { tipo: "ALMACEN"; id: string };

interface ListaPedidosEjecucionProps {
  monitoreo: MonitoreoResponseDto;
  ejecucionId: number;
  seleccionId?: string | null;
  onSeleccionar: (seleccion: SeleccionLista) => void;
}

type Pestana = "PLANIFICADOS" | "ENTREGADOS" | "TODOS";

interface RutaAsignada {
  id: string;
  vehiculoId: string;
  salida: string;
  llegada: string | null;
}

interface Asignacion {
  unidades: string[];
  rutas: RutaAsignada[];
}

/** Cada cuánto se vuelven a pedir los pedidos y el plan (no hace falta cada segundo). */
const INTERVALO_MS = 5000;

/** Tope de filas para no saturar el navegador; el resto se encuentra con la búsqueda. */
const MAXIMO_FILAS = 100;

const PESTANAS: { valor: Pestana; etiqueta: string }[] = [
  { valor: "PLANIFICADOS", etiqueta: "Planificados" },
  { valor: "ENTREGADOS", etiqueta: "Entregados" },
  { valor: "TODOS", etiqueta: "Todos" },
];

/**
 * Lista de pedidos de la ejecución (Guía 65–69): planificados y entregados, búsqueda por id o cliente, y para cada
 * pedido las unidades que lo atienden según el plan vigente (más de una ruta = fraccionado). Al elegir una fila se
 * abre el detalle del pedido, que muestra además su historial.
 */
export function ListaPedidosEjecucion({
  monitoreo,
  ejecucionId,
  seleccionId,
  onSeleccionar,
}: ListaPedidosEjecucionProps) {
  const [pedidos, setPedidos] = useState<PedidoResponseDto[]>([]);
  const [plan, setPlan] = useState<PlanResponseDto | null>(null);
  const [pestana, setPestana] = useState<Pestana>("PLANIFICADOS");
  const [busqueda, setBusqueda] = useState("");
  const [error, setError] = useState<string | null>(null);

  /* Último periodo visible: el intervalo lo lee sin reiniciarse en cada tick del monitoreo. */
  const periodoRef = useRef({
    desde: monitoreo.escenario.inicioSimulado,
    hasta: monitoreo.instante,
  });

  useEffect(() => {
    periodoRef.current = {
      desde: monitoreo.escenario.inicioSimulado,
      hasta: monitoreo.instante,
    };
  }, [monitoreo.escenario.inicioSimulado, monitoreo.instante]);

  useEffect(() => {
    if (!seleccionId) return;
    setPestana("TODOS");
    setBusqueda(seleccionId);
  }, [seleccionId]);

  useEffect(() => {
    let activo = true;

    async function refrescar() {
      const { desde, hasta } = periodoRef.current;

      try {
        const [lista, planVigente] = await Promise.all([
          pedidosService.listarTodos({
            registradoDesde: desde,
            registradoHasta: hasta,
            sort: "registradoEn,asc",
          }),
          /* Antes del primer plan el backend responde error: se muestra la lista sin unidades. */
          planificacionService.obtenerPlan(ejecucionId).catch(() => null),
        ]);

        if (!activo) return;

        setPedidos(lista);
        setPlan(planVigente);
        setError(null);
      } catch {
        if (activo) {
          setError("No fue posible cargar los pedidos.");
        }
      }
    }

    void refrescar();
    const intervalo = window.setInterval(() => void refrescar(), INTERVALO_MS);

    return () => {
      activo = false;
      window.clearInterval(intervalo);
    };
  }, [ejecucionId]);

  const asignaciones = useMemo(() => {
    const mapa = new Map<string, Asignacion>();

    for (const ruta of plan?.rutas ?? []) {
      for (const parada of ruta.paradas) {
        if (parada.tipo !== "ENTREGA" || !parada.pedidoId) continue;

        const actual = mapa.get(parada.pedidoId) ?? { unidades: [], rutas: [] };
        if (!actual.unidades.includes(ruta.vehiculoId)) actual.unidades.push(ruta.vehiculoId);
        if (!actual.rutas.some((item) => item.id === ruta.id)) {
          actual.rutas.push({
            id: ruta.id,
            vehiculoId: ruta.vehiculoId,
            salida: ruta.salida,
            llegada: parada.llegada,
          });
        }
        mapa.set(parada.pedidoId, actual);
      }
    }

    return mapa;
  }, [plan]);

  const filtrados = useMemo(() => {
    const texto = busqueda.trim().toLowerCase();

    return pedidos.filter((pedido) => {
      if (pestana === "ENTREGADOS" && pedido.estado !== "DELIVERED") return false;
      if (
        pestana === "PLANIFICADOS" &&
        (pedido.estado === "DELIVERED" || !asignaciones.has(pedido.id))
      ) {
        return false;
      }
      if (!texto) return true;

      return (
        pedido.id.toLowerCase().includes(texto) ||
        pedido.clienteId.toLowerCase().includes(texto)
      );
    });
  }, [pedidos, pestana, busqueda, asignaciones]);

  const visibles = filtrados.slice(0, MAXIMO_FILAS);

  return (
    <Card className="border-slate-800 bg-[#151b23] p-4">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Package className="size-4 text-orange-400" />
          <h3 className="font-semibold">Pedidos</h3>
        </div>

        <span className="text-[11px] text-slate-500">
          {filtrados.length} de {pedidos.length}
        </span>
      </div>

      <div className="mb-3 flex gap-1">
        {PESTANAS.map(({ valor, etiqueta }) => (
          <button
            key={valor}
            type="button"
            onClick={() => setPestana(valor)}
            className={[
              "rounded-md px-2 py-1 text-xs",
              pestana === valor
                ? "bg-orange-500 text-white"
                : "bg-slate-800 text-slate-300 hover:bg-slate-700",
            ].join(" ")}
          >
            {etiqueta}
          </button>
        ))}
      </div>

      <div className="relative mb-3">
        <Search className="absolute left-2 top-1/2 size-4 -translate-y-1/2 text-slate-500" />
        <input
          type="search"
          value={busqueda}
          onChange={(event) => setBusqueda(event.target.value)}
          placeholder="Buscar por id o cliente"
          aria-label="Buscar pedido por id o cliente"
          className="w-full rounded-md border border-slate-700 bg-[#11161d] py-1.5 pl-8 pr-2 text-sm text-white placeholder:text-slate-500"
        />
      </div>

      {error && (
        <div className="mb-3 rounded-md border border-red-900 bg-red-950/40 p-2 text-xs text-red-300">
          {error}
        </div>
      )}

      {visibles.length === 0 && !error && (
        <div className="rounded-md border border-slate-800 bg-[#11161d] p-3 text-xs text-slate-400">
          No hay pedidos para mostrar.
        </div>
      )}

      <ul className="max-h-80 space-y-1 overflow-y-auto pr-1">
        {visibles.map((pedido) => {
          const asignacion = asignaciones.get(pedido.id);

          return (
            <li key={pedido.id}>
              <button
                type="button"
                onClick={() => onSeleccionar({ tipo: "PEDIDO", id: pedido.id })}
                className={[
                  "w-full rounded-md border p-2 text-left transition",
                  seleccionId === pedido.id
                    ? "border-orange-500 bg-orange-950/25 ring-1 ring-orange-500/30"
                    : "border-slate-800 bg-[#11161d] hover:border-orange-500/60",
                ].join(" ")}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-mono text-xs text-white">{pedido.id}</span>
                  <EstadoPedido estado={pedido.estado} />
                </div>

                <div className="mt-1 flex flex-wrap gap-x-3 text-[11px] text-slate-400">
                  <span>Cliente {pedido.clienteId}</span>
                  <span>{pedido.paquetes} paq.</span>
                  <span>Destino ({pedido.x}, {pedido.y})</span>
                  <span>Vence {formatearFecha(pedido.vence)}</span>
                </div>

                {asignacion && (
                  <div className="mt-2 space-y-1">
                    {asignacion.rutas.map((ruta) => (
                      <div
                        key={ruta.id}
                        className="flex items-center justify-between gap-2 rounded bg-slate-800/60 px-2 py-1 text-[10px] text-slate-300"
                      >
                        <span>{ruta.vehiculoId} · {ruta.id}</span>
                        <span className="text-slate-500">
                          {formatearHora(ruta.salida)} → {ruta.llegada ? formatearHora(ruta.llegada) : "—"}
                        </span>
                      </div>
                    ))}
                    {asignacion.rutas.length > 1 && (
                      <p className="text-[10px] text-amber-400">Pedido fraccionado en {asignacion.rutas.length} rutas.</p>
                    )}
                  </div>
                )}
              </button>
            </li>
          );
        })}
      </ul>

      {filtrados.length > MAXIMO_FILAS && (
        <p className="mt-2 text-[11px] text-slate-500">
          Mostrando {MAXIMO_FILAS} de {filtrados.length}; use la búsqueda.
        </p>
      )}
    </Card>
  );
}

function EstadoPedido({ estado }: { estado: EstadoPedidoBackend }) {
  const estilos: Record<EstadoPedidoBackend, { texto: string; clase: string }> = {
    REGISTERED: { texto: "Registrado", clase: "bg-slate-700 text-slate-200" },
    ASSIGNED: { texto: "Asignado", clase: "bg-blue-500/15 text-blue-300" },
    IN_TRANSIT: { texto: "En ruta", clase: "bg-amber-500/15 text-amber-300" },
    DELIVERED: { texto: "Entregado", clase: "bg-green-500/15 text-green-400" },
  };
  const { texto, clase } = estilos[estado];

  return (
    <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${clase}`}>
      {texto}
    </span>
  );
}

function formatearFecha(valor: string) {
  return new Intl.DateTimeFormat("es-PE", {
    timeZone: "America/Lima",
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(valor));
}

function formatearHora(valor: string) {
  return new Intl.DateTimeFormat("es-PE", {
    timeZone: "America/Lima",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(valor));
}
