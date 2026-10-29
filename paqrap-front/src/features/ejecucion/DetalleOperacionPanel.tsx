import { useEffect, useState } from "react";
import {
  Clock3,
  Loader2,
  Package,
  Truck,
  Warehouse,
  X,
} from "lucide-react";

import type {
  AlmacenDetalleResponseDto,
  VehiculoDetalleResponseDto,
} from "@/api/detalles.dto";
import type { PedidoDetalleResponseDto } from "@/api/pedidos.dto";
import { mensajeDeError } from "@/lib/api-error";
import { detallesService } from "@/services/detalles.service";

export type SeleccionDetalleOperacion =
  | { tipo: "VEHICULO"; id: string }
  | { tipo: "PEDIDO"; id: string }
  | { tipo: "ALMACEN"; id: string };

interface Props {
  seleccion: SeleccionDetalleOperacion | null;
  ejecucionId: number;
  instante: string;
  onCerrar: () => void;
}

type RespuestaDetalle =
  | { tipo: "VEHICULO"; data: VehiculoDetalleResponseDto }
  | { tipo: "PEDIDO"; data: PedidoDetalleResponseDto }
  | { tipo: "ALMACEN"; data: AlmacenDetalleResponseDto };

export function DetalleOperacionPanel({ seleccion, ejecucionId, instante, onCerrar }: Props) {
  const [respuesta, setRespuesta] = useState<RespuestaDetalle | null>(null);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!seleccion) {
      setRespuesta(null);
      setError(null);
      return;
    }

    let cancelado = false;
    setCargando(true);
    setError(null);

    const promesa =
      seleccion.tipo === "VEHICULO"
        ? detallesService
            .vehiculo(seleccion.id, ejecucionId)
            .then((data) => ({ tipo: "VEHICULO" as const, data }))
        : seleccion.tipo === "ALMACEN"
          ? detallesService
              .almacen(seleccion.id, ejecucionId, 24)
              .then((data) => ({ tipo: "ALMACEN" as const, data }))
          : detallesService
              .pedido(seleccion.id, instante)
              .then((data) => ({ tipo: "PEDIDO" as const, data }));

    void promesa
      .then((data) => {
        if (!cancelado) setRespuesta(data);
      })
      .catch((cause) => {
        if (!cancelado) {
          setError(mensajeDeError(cause, "No fue posible consultar el detalle."));
        }
      })
      .finally(() => {
        if (!cancelado) setCargando(false);
      });

    return () => {
      cancelado = true;
    };
  }, [ejecucionId, instante, seleccion]);

  if (!seleccion) return null;

  return (
    <aside className="absolute right-5 top-16 z-[70] w-[370px] overflow-hidden rounded-xl border border-slate-700 bg-[#11161d]/98 text-white shadow-2xl">
      <div className="flex items-start justify-between border-b border-slate-800 px-4 py-3">
        <div>
          <p className="text-[10px] font-semibold uppercase text-orange-400">
            Detalle operativo
          </p>
          <h3 className="mt-1 font-semibold">{seleccion.id}</h3>
        </div>
        <button
          type="button"
          onClick={onCerrar}
          className="rounded p-1 text-slate-500 hover:bg-slate-800 hover:text-white"
          aria-label="Cerrar detalle"
        >
          <X className="size-4" />
        </button>
      </div>

      <div className="max-h-[calc(100vh-150px)] overflow-y-auto p-4">
        {cargando && (
          <div className="flex items-center justify-center py-10 text-sm text-slate-400">
            <Loader2 className="mr-2 size-4 animate-spin" /> Cargando detalle...
          </div>
        )}

        {error && (
          <div className="rounded-md border border-red-900 bg-red-950/40 p-3 text-xs text-red-300">
            {error}
          </div>
        )}

        {!cargando && respuesta?.tipo === "VEHICULO" && (
          <DetalleVehiculo detalle={respuesta.data} />
        )}
        {!cargando && respuesta?.tipo === "PEDIDO" && (
          <DetallePedido detalle={respuesta.data} />
        )}
        {!cargando && respuesta?.tipo === "ALMACEN" && (
          <DetalleAlmacen detalle={respuesta.data} />
        )}
      </div>
    </aside>
  );
}

function DetalleVehiculo({ detalle }: { detalle: VehiculoDetalleResponseDto }) {
  return (
    <div className="space-y-4">
      <ResumenIcono icono={<Truck className="size-5" />} titulo={`${detalle.tipo} · ${detalle.estado}`}>
        Posición ({detalle.x}, {detalle.y}) · carga {detalle.cargaActual}/{detalle.capacidad}
      </ResumenIcono>

      <Bloque titulo="Estado">
        <Fila label="Ruta vigente" value={detalle.rutaId ?? "Sin ruta pendiente"} />
        <Fila label="Disponible desde" value={formatearFecha(detalle.disponibleDesde)} />
        <Fila label="Motivo de detención" value={nombreMotivo(detalle.motivoDetencion)} />
        <Fila label="Detenido hasta" value={formatearFecha(detalle.detenidoHasta)} />
        <Fila label="Turno" value={formatearRango(detalle.turnoVigente)} />
        <Fila label="Ventana de refrigerio" value={formatearRango(detalle.ventanaAlimentacion)} />
      </Bloque>

      <Bloque titulo={`Próximas entregas (${detalle.proximasEntregas.length})`}>
        {detalle.proximasEntregas.length === 0 ? (
          <Vacio texto="No tiene entregas pendientes." />
        ) : (
          detalle.proximasEntregas.map((entrega) => (
            <div key={`${entrega.pedidoId}-${entrega.orden}`} className="rounded-md bg-slate-800/60 p-2">
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-medium">#{entrega.orden} · {entrega.pedidoId}</span>
                <span className="text-[10px] text-slate-500">{entrega.paquetes} paq.</span>
              </div>
              <p className="mt-1 text-[10px] text-slate-400">
                ({entrega.x}, {entrega.y}) · ETA {formatearFecha(entrega.llegadaPrevista)}
              </p>
            </div>
          ))
        )}
      </Bloque>
    </div>
  );
}

function DetallePedido({ detalle }: { detalle: PedidoDetalleResponseDto }) {
  const pedido = detalle.pedido;
  return (
    <div className="space-y-4">
      <ResumenIcono icono={<Package className="size-5" />} titulo={`${pedido.estado} · ${pedido.tipoEntrega}`}>
        Cliente {pedido.clienteId} · {pedido.paquetes} paquetes · ({pedido.x}, {pedido.y})
      </ResumenIcono>

      <Bloque titulo="Cumplimiento">
        <Fila label="Unidad asignada" value={detalle.unidadAsignadaId ?? "Sin asignar"} />
        <Fila
          label="Tiempo restante"
          value={
            detalle.minutosRestantes === null
              ? "—"
              : detalle.minutosRestantes >= 0
                ? `${detalle.minutosRestantes} min`
                : `${Math.abs(detalle.minutosRestantes)} min vencido`
          }
        />
        <Fila label="Plazo" value={formatearFecha(pedido.vence)} />
        <Fila label="Resultado" value={detalle.incumplido ? "Incumplido" : "Dentro del plazo"} />
      </Bloque>

      <Bloque titulo="Trazabilidad">
        {detalle.historial.map((cambio, index) => (
          <div key={`${cambio.estado}-${cambio.ocurridoEn}-${index}`} className="flex gap-2 border-l border-slate-700 pb-3 pl-3 last:pb-0">
            <Clock3 className="mt-0.5 size-3.5 shrink-0 text-sky-400" />
            <div>
              <p className="text-xs font-medium">{cambio.estado}</p>
              <p className="text-[10px] text-slate-500">{formatearFecha(cambio.ocurridoEn)}</p>
              {(cambio.vehiculoId || cambio.almacenId) && (
                <p className="mt-0.5 text-[10px] text-slate-400">
                  {cambio.vehiculoId ? `Vehículo ${cambio.vehiculoId}` : ""}
                  {cambio.vehiculoId && cambio.almacenId ? " · " : ""}
                  {cambio.almacenId ? `Almacén ${cambio.almacenId}` : ""}
                </p>
              )}
            </div>
          </div>
        ))}
      </Bloque>
    </div>
  );
}

function DetalleAlmacen({ detalle }: { detalle: AlmacenDetalleResponseDto }) {
  return (
    <div className="space-y-4">
      <ResumenIcono icono={<Warehouse className="size-5" />} titulo={`${detalle.tipo} · ${detalle.nivelAlerta ?? "VERDE"}`}>
        Posición ({detalle.x}, {detalle.y})
      </ResumenIcono>

      <Bloque titulo="Inventario">
        <Fila
          label="Stock"
          value={detalle.stock === null ? "Ilimitado" : `${detalle.stock}/${detalle.capacidad ?? "—"}`}
        />
        <Fila
          label="Ocupación"
          value={detalle.ocupacionPct === null ? "—" : `${detalle.ocupacionPct.toFixed(1)}%`}
        />
        <Fila label={`Consumo (${detalle.ventanaHoras} h)`} value={`${detalle.consumoVentana} unidades`} />
        <Fila label="Última recarga" value={formatearFecha(detalle.ultimaRecarga)} />
        <Fila label="Próxima recarga" value={formatearFecha(detalle.proximaRecarga)} />
      </Bloque>

      <Bloque titulo={`Movimientos recientes (${detalle.movimientos?.length ?? 0})`}>
        {!detalle.movimientos?.length ? (
          <Vacio texto="No hay movimientos en la ventana consultada." />
        ) : (
          detalle.movimientos.slice(-10).reverse().map((movimiento) => (
            <div key={movimiento.id} className="rounded-md bg-slate-800/60 p-2">
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-medium">{movimiento.tipo}</span>
                <span className="text-[10px] text-slate-400">{movimiento.cantidad} uds.</span>
              </div>
              <p className="mt-1 text-[10px] text-slate-500">
                {formatearFecha(movimiento.instante)}
                {movimiento.pedidoId ? ` · pedido ${movimiento.pedidoId}` : ""}
              </p>
            </div>
          ))
        )}
      </Bloque>
    </div>
  );
}

function ResumenIcono({ icono, titulo, children }: { icono: React.ReactNode; titulo: string; children: React.ReactNode }) {
  return (
    <div className="flex gap-3 rounded-lg border border-slate-800 bg-slate-900/60 p-3">
      <div className="rounded-md bg-orange-500/15 p-2 text-orange-400">{icono}</div>
      <div>
        <p className="text-sm font-semibold">{titulo}</p>
        <p className="mt-1 text-xs text-slate-400">{children}</p>
      </div>
    </div>
  );
}

function Bloque({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return (
    <section>
      <h4 className="mb-2 text-[10px] font-semibold uppercase tracking-wide text-slate-500">{titulo}</h4>
      <div className="space-y-2">{children}</div>
    </section>
  );
}

function Fila({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-3 border-b border-slate-800 py-2 text-xs last:border-0">
      <span className="text-slate-500">{label}</span>
      <span className="max-w-[62%] text-right text-slate-200">{value}</span>
    </div>
  );
}

function Vacio({ texto }: { texto: string }) {
  return <p className="rounded-md bg-slate-800/60 p-3 text-xs text-slate-500">{texto}</p>;
}

function formatearRango(rango: { inicio: string; fin: string } | null) {
  return rango ? `${formatearHora(rango.inicio)}–${formatearHora(rango.fin)}` : "—";
}

function formatearHora(valor: string) {
  return new Intl.DateTimeFormat("es-PE", {
    timeZone: "America/Lima",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(valor));
}

function formatearFecha(valor: string | null) {
  if (!valor) return "—";
  return new Intl.DateTimeFormat("es-PE", {
    timeZone: "America/Lima",
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(valor));
}


function nombreMotivo(motivo: VehiculoDetalleResponseDto["motivoDetencion"]) {
  switch (motivo) {
    case "AVERIA":
      return "Avería";
    case "MANTENIMIENTO":
      return "Mantenimiento";
    case "REFRIGERIO":
      return "Refrigerio";
    default:
      return "Sin detención activa";
  }
}
