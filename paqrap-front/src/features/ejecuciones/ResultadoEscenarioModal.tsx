import { useEffect, useState } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  Download,
  Loader2,
  Route,
  Timer,
  Truck,
  X,
} from "lucide-react";

import type { ResultadoEscenarioResponseDto } from "@/api/resultados.dto";
import { mensajeDeError } from "@/lib/api-error";
import { resultadosService } from "@/services/resultados.service";

interface Props {
  ejecucionId: number | null;
  onCerrar: () => void;
}

export function ResultadoEscenarioModal({ ejecucionId, onCerrar }: Props) {
  const [resultado, setResultado] = useState<ResultadoEscenarioResponseDto | null>(null);
  const [cargando, setCargando] = useState(false);
  const [descargando, setDescargando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (ejecucionId === null) return;
    let cancelado = false;
    setCargando(true);
    setResultado(null);
    setError(null);

    void resultadosService
      .obtener(ejecucionId)
      .then((data) => {
        if (!cancelado) setResultado(data);
      })
      .catch((cause) => {
        if (!cancelado) {
          setError(
            mensajeDeError(
              cause,
              "El resultado todavía no está disponible. Finaliza la ejecución y vuelve a intentarlo.",
            ),
          );
        }
      })
      .finally(() => {
        if (!cancelado) setCargando(false);
      });

    return () => {
      cancelado = true;
    };
  }, [ejecucionId]);

  if (ejecucionId === null) return null;

  async function descargar() {
  const id = ejecucionId;

  if (id === null) return;

  try {
    setDescargando(true);
    setError(null);

    await resultadosService.descargarCsv(id);
  } catch (cause) {
    setError(
      mensajeDeError(
        cause,
        "No fue posible descargar el CSV.",
      ),
    );
  } finally {
    setDescargando(false);
  }
}

  return (
    <div className="fixed inset-0 z-[190] flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm">
      <div className="w-full max-w-3xl overflow-hidden rounded-xl border border-slate-700 bg-[#11161d] text-white shadow-2xl">
        <div className="flex items-start justify-between border-b border-slate-800 px-5 py-4">
          <div>
            <p className="text-[10px] font-semibold uppercase text-orange-400">P-08 · Resultado final</p>
            <h2 className="mt-1 text-xl font-semibold">Ejecución #{ejecucionId}</h2>
          </div>
          <button
            type="button"
            onClick={onCerrar}
            className="rounded p-1 text-slate-500 hover:bg-slate-800 hover:text-white"
            aria-label="Cerrar"
          >
            <X className="size-5" />
          </button>
        </div>

        <div className="max-h-[75vh] overflow-y-auto p-5">
          {cargando && (
            <div className="flex items-center justify-center py-16 text-slate-400">
              <Loader2 className="mr-2 size-5 animate-spin" /> Cargando resultado...
            </div>
          )}

          {error && (
            <div className="mb-4 rounded-md border border-red-900 bg-red-950/40 p-3 text-sm text-red-300">
              {error}
            </div>
          )}

          {resultado && (
            <>
              <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-800 bg-slate-900/50 p-4">
                <div>
                  <p className="text-sm font-semibold">{nombreTipo(resultado.tipo)} · {nombreEstado(resultado.estado)}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    {formatearFecha(resultado.inicioSimulado)} → {formatearFecha(resultado.finSimulado)}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => void descargar()}
                  disabled={descargando}
                  className="flex items-center gap-2 rounded-md bg-orange-500 px-4 py-2 text-sm font-semibold hover:bg-orange-600 disabled:opacity-50"
                >
                  {descargando ? <Loader2 className="size-4 animate-spin" /> : <Download className="size-4" />}
                  Exportar CSV
                </button>
              </div>

              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                <Tarjeta icono={<Truck />} titulo="Pedidos" valor={String(resultado.totalOrders)} detalle={`${resultado.deliveredOrders} entregados`} />
                <Tarjeta icono={<CheckCircle2 />} titulo="A tiempo" valor={String(resultado.onTimeOrders)} detalle={`${resultado.undeliveredOrders} no entregados`} />
                <Tarjeta icono={<Route />} titulo="Distancia" valor={`${resultado.totalDistanceKm.toFixed(2)} km`} detalle={`Costo S/ ${resultado.totalCost.toFixed(2)}`} />
                <Tarjeta icono={<Timer />} titulo="Cómputo SA" valor={`${resultado.computationTimeMs} ms`} detalle={`${resultado.replanningCount} replanificaciones`} />
              </div>

              <div className="mt-5 grid gap-3 md:grid-cols-3">
                <Dato label="Averías" value={String(resultado.breakdownCount)} />
                <Dato label="Bloqueos" value={String(resultado.roadBlockCount)} />
                <Dato label="Colapso" value={resultado.colapsoEn ? formatearFecha(resultado.colapsoEn) : "No detectado"} />
              </div>

              {resultado.undeliveredOrders > 0 && (
                <div className="mt-5 flex gap-3 rounded-md border border-amber-800 bg-amber-950/25 p-3 text-sm text-amber-200">
                  <AlertTriangle className="mt-0.5 size-5 shrink-0" />
                  <p>La ejecución terminó con {resultado.undeliveredOrders} pedidos sin entregar. Revisa el CPA y la última planificación.</p>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function Tarjeta({ icono, titulo, valor, detalle }: { icono: React.ReactNode; titulo: string; valor: string; detalle: string }) {
  return (
    <div className="rounded-lg border border-slate-800 bg-[#151b23] p-4">
      <div className="flex items-center gap-2 text-sky-400">{icono}<span className="text-[10px] uppercase text-slate-500">{titulo}</span></div>
      <p className="mt-3 text-xl font-semibold">{valor}</p>
      <p className="mt-1 text-xs text-slate-500">{detalle}</p>
    </div>
  );
}

function Dato({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md bg-slate-800/60 p-3">
      <p className="text-[10px] uppercase text-slate-500">{label}</p>
      <p className="mt-1 text-sm font-medium">{value}</p>
    </div>
  );
}

function nombreTipo(tipo: ResultadoEscenarioResponseDto["tipo"]) {
  return tipo === "DAY_TO_DAY" ? "Día a Día" : tipo === "FIVE_DAY" ? "Simulación 5D" : "Colapso";
}

function nombreEstado(estado: ResultadoEscenarioResponseDto["estado"]) {
  const nombres: Record<ResultadoEscenarioResponseDto["estado"], string> = {
    CREATED: "Creado",
    RUNNING: "En ejecución",
    PAUSED: "Pausado",
    STOPPED: "Detenido",
    COMPLETED: "Completado",
    COLLAPSED: "Colapsado",
    FAILED: "Error",
  };
  return nombres[estado];
}

function formatearFecha(valor: string | null) {
  if (!valor) return "—";
  return new Intl.DateTimeFormat("es-PE", {
    timeZone: "America/Lima",
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(valor));
}
