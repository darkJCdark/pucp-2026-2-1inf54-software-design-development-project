import {
  useEffect,
  useState,
} from "react";

import {
  ExternalLink,
  Loader2,
  Play,
  RefreshCcw,
  Square,
} from "lucide-react";

import {
  useNavigate,
} from "react-router-dom";

import type {
  EscenarioResponseDto,
} from "@/api/escenarios.dto";

import {
  ApiError,
} from "@/services/api";

import {
  escenariosService,
} from "@/services/escenarios.service";

export default function EjecucionesPage() {
  const navigate = useNavigate();

  const [ejecuciones, setEjecuciones] =
    useState<EscenarioResponseDto[]>([]);

  const [cargando, setCargando] =
    useState(true);

  const [accionandoId, setAccionandoId] =
    useState<number | null>(null);

  const [error, setError] =
    useState<string | null>(null);

  useEffect(() => {
    void cargar();

    const intervalo =
      window.setInterval(() => {
        void cargar(false);
      }, 5000);

    return () =>
      window.clearInterval(intervalo);
  }, []);

  async function cargar(
    mostrarCarga = true,
  ) {
    try {
      if (mostrarCarga) {
        setCargando(true);
      }

      const respuesta =
        await escenariosService.listar(
          0,
          100,
        );

      setEjecuciones(
        [...respuesta.contenido].sort(
          (a, b) => b.id - a.id,
        ),
      );

      setError(null);
    } catch (error) {
      setError(
        mensajeError(
          error,
          "No fue posible cargar las ejecuciones.",
        ),
      );
    } finally {
      if (mostrarCarga) {
        setCargando(false);
      }
    }
  }

  async function iniciar(
    id: number,
  ) {
    try {
      setAccionandoId(id);
      await escenariosService.iniciar(id);
      navigate(`/ejecucion/${id}`);
    } catch (error) {
      setError(
        mensajeError(
          error,
          "No fue posible iniciar la ejecución.",
        ),
      );
    } finally {
      setAccionandoId(null);
    }
  }

  async function detener(
    id: number,
  ) {
    try {
      setAccionandoId(id);
      await escenariosService.detener(id);
      await cargar(false);
    } catch (error) {
      setError(
        mensajeError(
          error,
          "No fue posible detener la ejecución.",
        ),
      );
    } finally {
      setAccionandoId(null);
    }
  }

  return (
    <div className="min-h-screen bg-[#0d1117] p-6 text-white">
      <div className="mb-6 flex items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">
            Ejecuciones
          </h1>
          <p className="mt-1 text-sm text-slate-400">
            Historial y acceso a los escenarios creados.
          </p>
        </div>

        <button
          type="button"
          onClick={() => void cargar()}
          className="rounded-md border border-slate-700 p-2 text-slate-300 hover:bg-slate-800"
          title="Actualizar"
        >
          <RefreshCcw className="size-5" />
        </button>
      </div>

      {error && (
        <div className="mb-4 rounded-md border border-red-900 bg-red-950/30 px-4 py-3 text-sm text-red-400">
          {error}
        </div>
      )}

      {cargando ? (
        <div className="flex h-72 items-center justify-center text-slate-400">
          <Loader2 className="mr-2 size-5 animate-spin" />
          Cargando ejecuciones...
        </div>
      ) : ejecuciones.length === 0 ? (
        <div className="rounded-lg border border-slate-800 bg-[#11161d] p-10 text-center text-slate-500">
          Todavía no existen ejecuciones.
        </div>
      ) : (
        <div className="grid gap-3 xl:grid-cols-2">
          {ejecuciones.map((ejecucion) => (
            <div
              key={ejecucion.id}
              className="rounded-lg border border-slate-800 bg-[#11161d] p-4"
            >
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-xs uppercase text-slate-500">
                    Ejecución #{ejecucion.id}
                  </p>
                  <h2 className="mt-1 font-semibold">
                    {nombreTipo(ejecucion.tipo)}
                  </h2>
                </div>

                <EstadoBadge estado={ejecucion.estado} />
              </div>

              <div className="mt-4 grid grid-cols-2 gap-2 text-xs">
                <Dato
                  label="Inicio simulado"
                  value={formatearFecha(ejecucion.inicioSimulado)}
                />
                <Dato
                  label="Factor"
                  value={`${ejecucion.factorAceleracion}×`}
                />
                <Dato
                  label="Instante / fin"
                  value={
                    ejecucion.instanteSimulado
                      ? formatearFecha(ejecucion.instanteSimulado)
                      : ejecucion.finSimulado
                        ? formatearFecha(ejecucion.finSimulado)
                        : "—"
                  }
                />
                <Dato
                  label="Inicio real"
                  value={
                    ejecucion.inicioReal
                      ? formatearFecha(ejecucion.inicioReal)
                      : "—"
                  }
                />
              </div>

              <div className="mt-4 flex flex-wrap justify-end gap-2">
                {ejecucion.estado === "CREATED" && (
                  <button
                    type="button"
                    disabled={accionandoId === ejecucion.id}
                    onClick={() => void iniciar(ejecucion.id)}
                    className="flex items-center gap-2 rounded-md bg-green-700 px-3 py-2 text-xs font-semibold hover:bg-green-600 disabled:opacity-50"
                  >
                    <Play className="size-4" />
                    Iniciar
                  </button>
                )}

                {(ejecucion.estado === "RUNNING" ||
                  ejecucion.estado === "PAUSED") && (
                  <button
                    type="button"
                    disabled={accionandoId === ejecucion.id}
                    onClick={() => void detener(ejecucion.id)}
                    className="flex items-center gap-2 rounded-md bg-red-800 px-3 py-2 text-xs font-semibold hover:bg-red-700 disabled:opacity-50"
                  >
                    <Square className="size-4" />
                    Detener
                  </button>
                )}

                <button
                  type="button"
                  onClick={() =>
                    navigate(`/ejecucion/${ejecucion.id}`)
                  }
                  className="flex items-center gap-2 rounded-md border border-slate-700 px-3 py-2 text-xs font-semibold hover:bg-slate-800"
                >
                  <ExternalLink className="size-4" />
                  Abrir
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function EstadoBadge({
  estado,
}: {
  estado: EscenarioResponseDto["estado"];
}) {
  const estilos =
    estado === "RUNNING"
      ? "bg-green-500/15 text-green-400"
      : estado === "PAUSED"
        ? "bg-amber-500/15 text-amber-400"
        : estado === "FAILED" || estado === "COLLAPSED"
          ? "bg-red-500/15 text-red-400"
          : "bg-slate-500/15 text-slate-300";

  return (
    <span className={`rounded-full px-2 py-1 text-[10px] font-semibold ${estilos}`}>
      {nombreEstado(estado)}
    </span>
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
      <p className="text-[9px] uppercase text-slate-500">
        {label}
      </p>
      <p className="mt-1 text-slate-300">
        {value}
      </p>
    </div>
  );
}

function nombreTipo(
  tipo: EscenarioResponseDto["tipo"],
) {
  switch (tipo) {
    case "DAY_TO_DAY":
      return "Día a Día";
    case "FIVE_DAY":
      return "Simulación 5D";
    case "COLLAPSE":
      return "Colapso";
  }
}

function nombreEstado(
  estado: EscenarioResponseDto["estado"],
) {
  switch (estado) {
    case "CREATED":
      return "Creado";
    case "RUNNING":
      return "En ejecución";
    case "PAUSED":
      return "Pausado";
    case "STOPPED":
      return "Detenido";
    case "COMPLETED":
      return "Completado";
    case "COLLAPSED":
      return "Colapsado";
    case "FAILED":
      return "Error";
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
