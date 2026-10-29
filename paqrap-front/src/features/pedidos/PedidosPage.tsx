import {
  useEffect,
  useState,
} from "react";

import {
  Loader2,
  RefreshCcw,
  Search,
  X,
} from "lucide-react";

import type {
  EstadoPedidoBackend,
  PedidoDetalleResponseDto,
  PedidoResponseDto,
} from "@/api/pedidos.dto";

import {
  ApiError,
} from "@/services/api";

import {
  pedidosService,
} from "@/services/pedidos.service";

export default function PedidosPage() {
  const [pedidos, setPedidos] =
    useState<PedidoResponseDto[]>([]);

  const [clienteId, setClienteId] =
    useState("");

  const [estado, setEstado] =
    useState<EstadoPedidoBackend | "">("");

  const [pagina, setPagina] =
    useState(0);

  const [totalPaginas, setTotalPaginas] =
    useState(0);

  const [totalElementos, setTotalElementos] =
    useState(0);

  const [cargando, setCargando] =
    useState(true);

  const [error, setError] =
    useState<string | null>(null);

  const [detalle, setDetalle] =
    useState<PedidoDetalleResponseDto | null>(
      null,
    );

  const [cargandoDetalle, setCargandoDetalle] =
    useState(false);

  useEffect(() => {
    void cargarPedidos(pagina);
  }, [pagina, estado]);

  async function cargarPedidos(
    paginaSolicitada = pagina,
  ) {
    try {
      setCargando(true);
      setError(null);

      const respuesta =
        await pedidosService.listar({
          clienteId:
            clienteId.trim() || undefined,
          estado: estado || undefined,
          page: paginaSolicitada,
          size: 50,
          sort: "registradoEn,desc",
        });

      setPedidos(respuesta.contenido);
      setTotalPaginas(
        respuesta.totalPaginas,
      );
      setTotalElementos(
        respuesta.totalElementos,
      );
    } catch (error) {
      setError(
        mensajeError(
          error,
          "No fue posible cargar los pedidos.",
        ),
      );
    } finally {
      setCargando(false);
    }
  }

  async function abrirDetalle(
    id: string,
  ) {
    try {
      setCargandoDetalle(true);
      setError(null);

      const respuesta =
        await pedidosService.obtener(id);

      setDetalle(respuesta);
    } catch (error) {
      setError(
        mensajeError(
          error,
          "No fue posible cargar el detalle del pedido.",
        ),
      );
    } finally {
      setCargandoDetalle(false);
    }
  }

  function buscar() {
    setPagina(0);
    void cargarPedidos(0);
  }

  return (
    <div className="min-h-screen bg-[#0d1117] p-6 text-white">
      <div className="mb-6 flex flex-col justify-between gap-4 lg:flex-row lg:items-end">
        <div>
          <h1 className="text-2xl font-semibold">
            Pedidos
          </h1>

          <p className="mt-1 text-sm text-slate-400">
            Consulta del registro persistente y su trazabilidad de estados.
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-500" />

            <input
              value={clienteId}
              onChange={(event) =>
                setClienteId(
                  event.target.value,
                )
              }
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  buscar();
                }
              }}
              placeholder="Cliente"
              className="h-10 rounded-md border border-slate-700 bg-[#151b23] pl-9 pr-3 text-sm outline-none focus:border-orange-500"
            />
          </div>

          <select
            value={estado}
            onChange={(event) => {
              setEstado(
                event.target.value as EstadoPedidoBackend | "",
              );
              setPagina(0);
            }}
            className="h-10 rounded-md border border-slate-700 bg-[#151b23] px-3 text-sm outline-none focus:border-orange-500"
          >
            <option value="">
              Todos los estados
            </option>
            <option value="REGISTERED">
              Registrado
            </option>
            <option value="ASSIGNED">
              Asignado
            </option>
            <option value="IN_TRANSIT">
              En tránsito
            </option>
            <option value="DELIVERED">
              Entregado
            </option>
          </select>

          <button
            type="button"
            onClick={buscar}
            className="rounded-md bg-orange-500 px-4 py-2 text-sm font-semibold hover:bg-orange-600"
          >
            Buscar
          </button>

          <button
            type="button"
            onClick={() =>
              void cargarPedidos()
            }
            className="rounded-md border border-slate-700 p-2 text-slate-300 hover:bg-slate-800"
            title="Actualizar"
          >
            <RefreshCcw className="size-5" />
          </button>
        </div>
      </div>

      {error && (
        <div className="mb-4 rounded-md border border-red-900 bg-red-950/30 px-4 py-3 text-sm text-red-400">
          {error}
        </div>
      )}

      <div className="overflow-hidden rounded-lg border border-slate-800 bg-[#11161d]">
        <div className="border-b border-slate-800 px-4 py-3 text-xs text-slate-500">
          {totalElementos} pedidos encontrados
        </div>

        {cargando ? (
          <div className="flex h-72 items-center justify-center text-slate-400">
            <Loader2 className="mr-2 size-5 animate-spin" />
            Cargando pedidos...
          </div>
        ) : pedidos.length === 0 ? (
          <div className="flex h-72 items-center justify-center text-slate-500">
            No hay pedidos para los filtros seleccionados.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[950px] text-left text-sm">
              <thead className="bg-[#151b23] text-[11px] uppercase text-slate-500">
                <tr>
                  <th className="px-4 py-3">ID</th>
                  <th className="px-4 py-3">Cliente</th>
                  <th className="px-4 py-3">Destino</th>
                  <th className="px-4 py-3">Paquetes</th>
                  <th className="px-4 py-3">Tipo</th>
                  <th className="px-4 py-3">Registro</th>
                  <th className="px-4 py-3">Vence</th>
                  <th className="px-4 py-3">Estado</th>
                </tr>
              </thead>

              <tbody>
                {pedidos.map((pedido) => (
                  <tr
                    key={pedido.id}
                    onClick={() =>
                      void abrirDetalle(
                        pedido.id,
                      )
                    }
                    className="cursor-pointer border-t border-slate-800 text-slate-300 hover:bg-slate-800/50"
                  >
                    <td className="px-4 py-3 font-mono text-xs text-slate-200">
                      {pedido.id}
                    </td>
                    <td className="px-4 py-3">
                      {pedido.clienteId}
                    </td>
                    <td className="px-4 py-3">
                      ({pedido.x}, {pedido.y})
                    </td>
                    <td className="px-4 py-3">
                      {pedido.paquetes}
                    </td>
                    <td className="px-4 py-3">
                      {pedido.tipoEntrega}
                    </td>
                    <td className="px-4 py-3">
                      {formatearFecha(
                        pedido.registradoEn,
                      )}
                    </td>
                    <td className="px-4 py-3">
                      {formatearFecha(
                        pedido.vence,
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <EstadoPedidoBadge
                        estado={pedido.estado}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="flex items-center justify-between border-t border-slate-800 px-4 py-3 text-sm">
          <button
            type="button"
            disabled={pagina <= 0}
            onClick={() =>
              setPagina((actual) =>
                Math.max(0, actual - 1),
              )
            }
            className="rounded-md border border-slate-700 px-3 py-2 disabled:opacity-40"
          >
            Anterior
          </button>

          <span className="text-slate-500">
            Página {pagina + 1} de {Math.max(totalPaginas, 1)}
          </span>

          <button
            type="button"
            disabled={
              pagina + 1 >= totalPaginas
            }
            onClick={() =>
              setPagina((actual) =>
                actual + 1,
              )
            }
            className="rounded-md border border-slate-700 px-3 py-2 disabled:opacity-40"
          >
            Siguiente
          </button>
        </div>
      </div>

      {(detalle || cargandoDetalle) && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/50">
          <div className="h-full w-full max-w-lg overflow-y-auto border-l border-slate-800 bg-[#11161d] p-6 shadow-2xl">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2 className="text-xl font-semibold">
                  Detalle del pedido
                </h2>

                <p className="mt-1 text-xs text-slate-500">
                  Datos e historial persistido
                </p>
              </div>

              <button
                type="button"
                onClick={() => {
                  setDetalle(null);
                  setCargandoDetalle(false);
                }}
                className="rounded-md p-2 hover:bg-slate-800"
              >
                <X className="size-5" />
              </button>
            </div>

            {cargandoDetalle || !detalle ? (
              <div className="flex h-64 items-center justify-center text-slate-400">
                <Loader2 className="mr-2 size-5 animate-spin" />
                Cargando detalle...
              </div>
            ) : (
              <DetallePedido detalle={detalle} />
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function DetallePedido({
  detalle,
}: {
  detalle: PedidoDetalleResponseDto;
}) {
  const pedido = detalle.pedido;

  return (
    <>
      <div className="mt-6 grid grid-cols-2 gap-3">
        <Dato label="ID" value={pedido.id} />
        <Dato label="Cliente" value={pedido.clienteId} />
        <Dato label="Destino" value={`(${pedido.x}, ${pedido.y})`} />
        <Dato label="Paquetes" value={String(pedido.paquetes)} />
        <Dato label="Tipo" value={pedido.tipoEntrega} />
        <Dato label="Plazo" value={`${pedido.horasPrometidas} h`} />
        <Dato label="Registrado" value={formatearFecha(pedido.registradoEn)} />
        <Dato label="Vence" value={formatearFecha(pedido.vence)} />
        <Dato label="Unidad asignada" value={detalle.unidadAsignadaId ?? "Sin asignar"} />
        <Dato
          label="Tiempo restante"
          value={
            detalle.minutosRestantes === null
              ? "—"
              : detalle.minutosRestantes >= 0
                ? `${detalle.minutosRestantes} min`
                : `${Math.abs(detalle.minutosRestantes)} min vencido`
          }
        />
        <Dato
          label="Cumplimiento"
          value={detalle.incumplido ? "Incumplido" : "Dentro del plazo"}
        />
        <Dato
          label="Entregado"
          value={pedido.entregadoEn ? formatearFecha(pedido.entregadoEn) : "—"}
        />
      </div>

      <div className="mt-6">
        <h3 className="font-semibold">
          Historial
        </h3>

        <div className="mt-3 space-y-3">
          {detalle.historial.map((cambio, index) => (
            <div
              key={`${cambio.estado}-${cambio.ocurridoEn}-${index}`}
              className="rounded-md border border-slate-800 bg-[#151b23] p-3"
            >
              <div className="flex items-center justify-between gap-3">
                <EstadoPedidoBadge estado={cambio.estado} />
                <span className="text-xs text-slate-500">
                  {formatearFecha(cambio.ocurridoEn)}
                </span>
              </div>

              {(cambio.vehiculoId || cambio.almacenId) && (
                <p className="mt-2 text-xs text-slate-400">
                  {cambio.vehiculoId
                    ? `Vehículo: ${cambio.vehiculoId}`
                    : ""}
                  {cambio.vehiculoId && cambio.almacenId
                    ? " · "
                    : ""}
                  {cambio.almacenId
                    ? `Almacén: ${cambio.almacenId}`
                    : ""}
                </p>
              )}
            </div>
          ))}
        </div>
      </div>
    </>
  );
}

function Dato({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div className="rounded-md bg-[#151b23] p-3">
      <p className="text-[10px] uppercase text-slate-500">
        {label}
      </p>
      <p className="mt-1 break-words text-sm text-slate-200">
        {value}
      </p>
    </div>
  );
}

function EstadoPedidoBadge({
  estado,
}: {
  estado: EstadoPedidoBackend;
}) {
  const estilos: Record<
    EstadoPedidoBackend,
    string
  > = {
    REGISTERED:
      "bg-violet-500/15 text-violet-400",
    ASSIGNED:
      "bg-amber-500/15 text-amber-400",
    IN_TRANSIT:
      "bg-sky-500/15 text-sky-400",
    DELIVERED:
      "bg-green-500/15 text-green-400",
  };

  return (
    <span
      className={`rounded-full px-2 py-1 text-[10px] font-semibold ${estilos[estado]}`}
    >
      {nombreEstado(estado)}
    </span>
  );
}

function nombreEstado(
  estado: EstadoPedidoBackend,
) {
  switch (estado) {
    case "REGISTERED":
      return "Registrado";
    case "ASSIGNED":
      return "Asignado";
    case "IN_TRANSIT":
      return "En tránsito";
    case "DELIVERED":
      return "Entregado";
  }
}

function formatearFecha(
  valor: string,
) {
  return new Intl.DateTimeFormat(
    "es-PE",
    {
      timeZone: "America/Lima",
      dateStyle: "short",
      timeStyle: "short",
    },
  ).format(new Date(valor));
}

function mensajeError(
  error: unknown,
  fallback: string,
) {
  if (error instanceof ApiError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return fallback;
}
