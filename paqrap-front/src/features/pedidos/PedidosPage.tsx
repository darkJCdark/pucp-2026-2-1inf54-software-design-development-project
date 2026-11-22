import {
  useEffect,
  useRef,
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

import type { EscenarioResponseDto } from "@/api/escenarios.dto";
import { useEjecucionesActivas } from "@/context/EjecucionesActivasContext";
import { nombreTipoEjecucion } from "@/lib/ejecuciones";

export default function PedidosPage() {
  const { operacion, simulacion, cargando, error } = useEjecucionesActivas();
  const [seleccion, setSeleccion] = useState("catalogo");
  const ejecucion = [operacion, simulacion].find((item) => item && String(item.id) === seleccion) ?? null;
  const color = ejecucion?.tipo === "DAY_TO_DAY"
    ? "border-teal-800/60 bg-teal-950/25 text-teal-200"
    : ejecucion ? "border-orange-800/60 bg-orange-950/20 text-orange-200" : "border-slate-700 bg-[#11161d] text-slate-200";
  // Una consulta, un reloj y una selección por vista. Al cambiar de contexto
  // se descartan filtros y respuestas que pertenecían a la vista anterior.
  const claveVista = ejecucion
    ? `ejecucion-${ejecucion.id}`
    : `catalogo-${operacion?.id ?? "sin-operacion"}-${simulacion?.id ?? "sin-simulacion"}`;

  return (
    <main className="min-h-screen space-y-6 bg-[#0d1117] p-4 text-white sm:p-6">
      <header className="flex flex-col justify-between gap-4 lg:flex-row lg:items-end">
        <div>
          <p className="text-xs font-semibold uppercase tracking-widest text-slate-500">Consulta de pedidos</p>
          <h1 className="mt-2 text-2xl font-semibold">Pedidos</h1>
          <p className="mt-2 max-w-xl text-sm text-slate-400">Consulta el catálogo o el avance de una actividad concreta.</p>
        </div>
        <div className={`min-w-0 max-w-full rounded-xl border px-4 py-3 ${color}`}>
          <label htmlFor="contexto-pedidos" className="mb-2 block text-xs font-semibold">Consultar pedidos de</label>
          <select id="contexto-pedidos" value={ejecucion ? String(ejecucion.id) : "catalogo"}
            disabled={cargando} onChange={(event) => setSeleccion(event.target.value)}
            className="w-full min-w-0 rounded-md sm:min-w-[250px] border border-current/20 bg-[#151b23] px-3 py-2 text-sm text-white outline-none focus:ring-2 focus:ring-current disabled:opacity-50">
            <option value="catalogo">Catálogo de pedidos</option>
            {operacion && <option value={String(operacion.id)}>Operación Día a Día · #{operacion.id}</option>}
            {simulacion && <option value={String(simulacion.id)}>{nombreTipoEjecucion(simulacion.tipo)} · #{simulacion.id}</option>}
          </select>
        </div>
      </header>
      {error && <p role="alert" className="rounded-lg border border-amber-800 bg-amber-950/20 px-4 py-3 text-sm text-amber-200">{error} El catálogo sigue disponible.</p>}
      {!ejecucion && seleccion !== "catalogo" && !cargando && !error && (
        <p role="status" className="text-sm text-slate-400">La ejecución seleccionada ya no está activa. Se muestra el catálogo de pedidos.</p>
      )}
      <ContenidoPedidos key={claveVista} ejecucion={ejecucion} />
    </main>
  );
}

function ContenidoPedidos({ ejecucion }: { ejecucion: EscenarioResponseDto | null }) {
  const { actualizar: actualizarActivas } = useEjecucionesActivas();
  const consultaLista = useRef(0);
  const consultaDetalle = useRef(0);
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
  const [detalleCatalogo, setDetalleCatalogo] = useState<PedidoResponseDto | null>(null);
  const [instanteConsulta, setInstanteConsulta] = useState<string | null>(null);

  useEffect(() => () => {
    consultaLista.current += 1;
    consultaDetalle.current += 1;
  }, []);

  useEffect(() => {
    void cargarPedidos(pagina);
  }, [pagina, estado]);

  async function cargarPedidos(
    paginaSolicitada = pagina,
  ) {
    const consulta = ++consultaLista.current;
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
          catalogo: !ejecucion,
          ejecucionId: ejecucion?.id,
          registradoDesde: ejecucion?.inicioSimulado,
          // El backend usa un límite exclusivo y exige hasta > desde.
          // +1 ms incluye los pedidos del instante actual incluso al iniciar.
          registradoHasta: ejecucion ? new Date(Math.max(
            Date.parse(ejecucion.inicioSimulado),
            Date.parse(ejecucion.instanteSimulado ?? ejecucion.inicioSimulado),
          ) + 1).toISOString() : undefined,
        });

      if (consulta !== consultaLista.current) return;
      setPedidos(respuesta.contenido);
      setInstanteConsulta(ejecucion ? ejecucion.instanteSimulado ?? ejecucion.inicioSimulado : null);
      setTotalPaginas(
        respuesta.totalPaginas,
      );
      setTotalElementos(
        respuesta.totalElementos,
      );
    } catch (error) {
      if (consulta !== consultaLista.current) return;
      setError(
        mensajeError(
          error,
          "No fue posible cargar los pedidos.",
        ),
      );
    } finally {
      if (consulta === consultaLista.current) setCargando(false);
    }
  }

  async function abrirDetalle(pedido: PedidoResponseDto) {
    const consulta = ++consultaDetalle.current;
    try {
      setCargandoDetalle(true);
      setDetalle(null);
      setDetalleCatalogo(null);
      setError(null);
      let idDetalle = ejecucion?.id;
      let instante = ejecucion?.instanteSimulado ?? ejecucion?.inicioSimulado;
      if (!ejecucion) {
        const activas = await actualizarActivas();
        if (consulta !== consultaDetalle.current) return;
        // El endpoint de detalle sin id puede resolver la simulación activa.
        // En catálogo se muestran entonces solo los datos de la fila consultada.
        if (activas.some((item) => item.tipo !== "DAY_TO_DAY")) {
          setDetalleCatalogo(pedido);
          return;
        }
        const operacion = activas.find((item) => item.tipo === "DAY_TO_DAY");
        idDetalle = operacion?.id;
        instante = operacion?.instanteSimulado ?? operacion?.inicioSimulado;
      }
      const respuesta = await pedidosService.obtener(pedido.id, instante, idDetalle);
      if (consulta !== consultaDetalle.current) return;
      if (!ejecucion && idDetalle === undefined) {
        const activas = await actualizarActivas();
        if (consulta !== consultaDetalle.current) return;
        if (activas.some((item) => item.tipo !== "DAY_TO_DAY")) {
          setDetalleCatalogo(pedido);
          return;
        }
      }
      setDetalle(respuesta);
    } catch (error) {
      if (consulta !== consultaDetalle.current) return;
      setError(mensajeError(error, "No fue posible cargar el detalle del pedido."));
    } finally {
      if (consulta === consultaDetalle.current) setCargandoDetalle(false);
    }
  }

  function buscar() {
    setPagina(0);
    void cargarPedidos(0);
  }

  return (
    <section>
      <div className="mb-6 flex flex-col justify-between gap-4 lg:flex-row lg:items-end">
        <div>
          <h2 className={`text-lg font-semibold ${ejecucion?.tipo === "DAY_TO_DAY" ? "text-teal-200" : ejecucion ? "text-orange-200" : "text-slate-200"}`}>
            {ejecucion ? `${nombreTipoEjecucion(ejecucion.tipo)} · #${ejecucion.id}` : "Catálogo de pedidos"}
          </h2>
          <p className="mt-1 max-w-xl text-sm leading-relaxed text-slate-400">
            {ejecucion
              ? "Estados e historial de esta ejecución, consultados con su propio reloj."
              : "Datos y estados registrados. El avance de las simulaciones se consulta seleccionando su ejecución."}
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
              aria-label="Filtrar por cliente"
              className="h-10 rounded-md border border-slate-700 bg-[#151b23] pl-9 pr-3 text-sm outline-none focus:border-orange-500"
            />
          </div>

          <select
            aria-label="Filtrar por estado del pedido"
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
          {instanteConsulta && <span className="ml-2">· Datos al {formatearFecha(instanteConsulta)}. Usa Actualizar para consultar el avance.</span>}
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
                  <th className="px-4 py-3">{ejecucion ? "Estado" : "Estado registrado"}</th>
                </tr>
              </thead>

              <tbody>
                {pedidos.map((pedido) => (
                  <tr
                    key={pedido.id}
                    onClick={() =>
                      void abrirDetalle(pedido)
                    }
                    tabIndex={0}
                    aria-label={`Ver pedido ${pedido.id}`}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        void abrirDetalle(pedido);
                      }
                    }}
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

      {(detalle || detalleCatalogo || cargandoDetalle) && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/50">
          <div role="dialog" aria-modal="true" aria-label="Detalle del pedido" className="h-full w-full max-w-lg overflow-y-auto border-l border-slate-800 bg-[#11161d] p-6 shadow-2xl">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2 className="text-xl font-semibold">
                  Detalle del pedido
                </h2>

                <p className="mt-1 text-xs text-slate-500">
                  {detalleCatalogo ? "Datos del catálogo" : ejecucion ? `${nombreTipoEjecucion(ejecucion.tipo)} · #${ejecucion.id}` : "Datos e historial registrado"}
                </p>
              </div>

              <button
                type="button"
                aria-label="Cerrar detalle del pedido"
                onClick={() => {
                  consultaDetalle.current += 1;
                  setDetalle(null);
                  setDetalleCatalogo(null);
                  setCargandoDetalle(false);
                }}
                className="rounded-md p-2 hover:bg-slate-800"
              >
                <X className="size-5" />
              </button>
            </div>

            {cargandoDetalle ? (
              <div className="flex h-64 items-center justify-center text-slate-400">
                <Loader2 className="mr-2 size-5 animate-spin" />
                Cargando detalle...
              </div>
            ) : detalleCatalogo ? (
              <DetalleCatalogo pedido={detalleCatalogo} />
            ) : detalle ? <DetallePedido detalle={detalle} /> : null}
          </div>
        </div>
      )}
    </section>
  );
}

function DetalleCatalogo({ pedido }: { pedido: PedidoResponseDto }) {
  return (
    <>
      <p className="mt-5 rounded-lg border border-slate-700 bg-slate-900/50 p-3 text-sm leading-relaxed text-slate-300">
        Esta ficha muestra los datos del catálogo. Para ver entregas, asignación e historial de una actividad, selecciónala en «Consultar pedidos de».
      </p>
      <div className="mt-5 grid grid-cols-2 gap-3">
        <Dato label="ID" value={pedido.id} />
        <Dato label="Cliente" value={pedido.clienteId} />
        <Dato label="Destino" value={`(${pedido.x}, ${pedido.y})`} />
        <Dato label="Paquetes" value={String(pedido.paquetes)} />
        <Dato label="Tipo" value={pedido.tipoEntrega} />
        <Dato label="Plazo" value={`${pedido.horasPrometidas} h`} />
        <Dato label="Registrado" value={formatearFecha(pedido.registradoEn)} />
        <Dato label="Vence" value={formatearFecha(pedido.vence)} />
      </div>
    </>
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
