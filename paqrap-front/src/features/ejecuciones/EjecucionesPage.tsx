import { useCallback, useEffect, useRef, useState } from "react";
import {
  BarChart3,
  Clock3,
  ExternalLink,
  FlaskConical,
  History,
  Loader2,
  Play,
  RefreshCcw,
  Square,
  Truck,
} from "lucide-react";
import { useNavigate } from "react-router-dom";

import type { EscenarioResponseDto } from "@/api/escenarios.dto";
import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import { useToast } from "@/components/feedback/ToastProvider";
import { useEjecucionesActivas } from "@/context/EjecucionesActivasContext";
import { mensajeDeError } from "@/lib/api-error";
import {
  esEjecucionActiva,
  mismoGrupo,
  nombreEstadoEjecucion,
  nombreTipoEjecucion,
} from "@/lib/ejecuciones";
import { escenariosService } from "@/services/escenarios.service";
import { ResultadoEscenarioModal } from "./ResultadoEscenarioModal";

type GrupoHistorial = "OPERACION" | "SIMULACIONES";
type FiltroEstado = "TODOS" | "ACTIVOS" | "FINALIZADOS" | "CREADOS";

export default function EjecucionesPage() {
  const navigate = useNavigate();
  const { mostrarToast } = useToast();
  const { activas, cargando: consultandoActivas, error: errorActivas, actualizar } = useEjecucionesActivas();
  const [ejecuciones, setEjecuciones] = useState<EscenarioResponseDto[]>([]);
  const [cargando, setCargando] = useState(true);
  const [actualizando, setActualizando] = useState(false);
  const [accionandoId, setAccionandoId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [resultadoId, setResultadoId] = useState<number | null>(null);
  const [porDetener, setPorDetener] = useState<EscenarioResponseDto | null>(null);
  const [grupo, setGrupo] = useState<GrupoHistorial>("OPERACION");
  const [filtroEstado, setFiltroEstado] = useState<FiltroEstado>("TODOS");
  const vigente = useRef(false);
  const solicitud = useRef(0);
  const tabs = useRef<Array<HTMLButtonElement | null>>([]);

  const cargar = useCallback(async (mostrarCarga = false) => {
    const turno = ++solicitud.current;
    try {
      if (mostrarCarga) setActualizando(true);
      const primera = await escenariosService.listar(0, 100);
      const todas = [...primera.contenido];
      for (let pagina = 1; pagina < primera.totalPaginas; pagina += 1) {
        if (!vigente.current || solicitud.current !== turno) return;
        const siguiente = await escenariosService.listar(pagina, 100);
        todas.push(...siguiente.contenido);
      }
      if (!vigente.current || solicitud.current !== turno) return;
      setEjecuciones([...new Map(todas.map((item) => [item.id, item])).values()].sort((a, b) => b.id - a.id));
      setError(null);
    } catch (cause) {
      if (vigente.current && solicitud.current === turno) {
        setError(mensajeDeError(cause, "No fue posible cargar el historial."));
      }
    } finally {
      if (vigente.current && solicitud.current === turno) {
        setCargando(false);
        setActualizando(false);
      }
    }
  }, []);

  useEffect(() => {
    vigente.current = true;
    let temporizador: number | undefined;
    let cancelado = false;
    async function consultar() {
      await cargar();
      if (!cancelado) temporizador = window.setTimeout(() => void consultar(), 5000);
    }
    void consultar();
    return () => {
      cancelado = true;
      vigente.current = false;
      solicitud.current += 1;
      window.clearTimeout(temporizador);
    };
  }, [cargar]);

  async function iniciar(ejecucion: EscenarioResponseDto) {
    if (accionandoId !== null) return;
    try {
      setAccionandoId(ejecucion.id);
      setError(null);
      const actuales = await actualizar();
      const conflicto = actuales.find((item) => esEjecucionActiva(item) && item.id !== ejecucion.id && mismoGrupo(item.tipo, ejecucion.tipo));
      if (conflicto) {
        throw new Error(ejecucion.tipo === "DAY_TO_DAY"
          ? `La operación Día a Día #${conflicto.id} sigue activa. Finalízala antes de iniciar otra operación.`
          : `La simulación #${conflicto.id} sigue activa. Finalízala antes de iniciar otra simulación.`);
      }
      const iniciada = await escenariosService.iniciar(ejecucion.id);
      setEjecuciones((anteriores) => anteriores.map((item) => item.id === iniciada.id ? iniciada : item));
      await actualizar().catch(() => undefined);
      mostrarToast({
        titulo: ejecucion.tipo === "DAY_TO_DAY" ? `Operación #${ejecucion.id} iniciada` : `Simulación #${ejecucion.id} iniciada`,
        variante: "success",
      });
      navigate(`/ejecucion/${ejecucion.id}`);
    } catch (cause) {
      const mensaje = mensajeDeError(cause, "No fue posible iniciar.");
      setError(mensaje);
      mostrarToast({ titulo: "No se pudo iniciar", descripcion: mensaje, variante: "error" });
    } finally {
      setAccionandoId(null);
    }
  }

  async function detener(ejecucion: EscenarioResponseDto) {
    if (accionandoId !== null) return;
    try {
      setAccionandoId(ejecucion.id);
      const detenida = await escenariosService.detener(ejecucion.id);
      setEjecuciones((anteriores) => anteriores.map((item) => item.id === detenida.id ? detenida : item));
      setPorDetener(null);
      mostrarToast({
        titulo: ejecucion.tipo === "DAY_TO_DAY" ? `Operación #${ejecucion.id} detenida` : `Simulación #${ejecucion.id} detenida`,
        variante: "success",
      });
      await Promise.allSettled([cargar(), actualizar()]);
    } catch (cause) {
      const mensaje = mensajeDeError(cause, "No fue posible detener.");
      setError(mensaje);
      mostrarToast({ titulo: "No se pudo detener", descripcion: mensaje, variante: "error" });
    } finally {
      setAccionandoId(null);
    }
  }

  const esOperacion = grupo === "OPERACION";
  const delGrupo = ejecuciones.filter((item) => (item.tipo === "DAY_TO_DAY") === esOperacion);
  const visibles = delGrupo.filter((item) => {
    if (filtroEstado === "ACTIVOS") return esEjecucionActiva(item);
    if (filtroEstado === "CREADOS") return item.estado === "CREATED";
    if (filtroEstado === "FINALIZADOS") return esFinalizada(item);
    return true;
  });
  const operacionPorDetener = porDetener?.tipo === "DAY_TO_DAY";

  return (
    <div className="min-h-screen bg-[#0d1117] px-4 py-6 text-white sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <header className="mb-7 flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="mb-2 flex items-center gap-2 text-xs font-medium uppercase tracking-[0.16em] text-slate-500">
              <History className="size-4" aria-hidden="true" /> Seguimiento y resultados
            </p>
            <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">Historial</h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-400">
              Consulta las jornadas de operación y los resultados de tus simulaciones, cada uno en su propio espacio.
            </p>
          </div>
          <button
            type="button"
            onClick={() => void Promise.allSettled([cargar(true), actualizar()])}
            disabled={actualizando || cargando}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-700 bg-[#111820] px-4 py-2.5 text-sm text-slate-300 transition hover:border-slate-500 hover:bg-slate-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-400 disabled:opacity-50"
          >
            <RefreshCcw className={`size-4 ${actualizando ? "animate-spin" : ""}`} aria-hidden="true" /> Actualizar
          </button>
        </header>

        <div className="mb-6 grid gap-2 rounded-2xl border border-slate-800 bg-[#10161e] p-2 sm:inline-flex" role="tablist" aria-label="Tipo de historial">
          {([
            { id: "OPERACION", texto: "Operación Día a Día", Icono: Truck },
            { id: "SIMULACIONES", texto: "Simulaciones", Icono: FlaskConical },
          ] as const).map(({ id, texto, Icono }, indice) => (
            <button
              key={id}
              ref={(elemento) => { tabs.current[indice] = elemento; }}
              type="button"
              role="tab"
              id={`historial-tab-${id}`}
              aria-controls="historial-panel"
              aria-selected={grupo === id}
              tabIndex={grupo === id ? 0 : -1}
              onClick={() => { setGrupo(id); setFiltroEstado("TODOS"); }}
              onKeyDown={(evento) => {
                if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(evento.key)) return;
                evento.preventDefault();
                const siguiente = evento.key === "Home" ? 0 : evento.key === "End" ? 1 : 1 - indice;
                setGrupo(siguiente === 0 ? "OPERACION" : "SIMULACIONES");
                setFiltroEstado("TODOS");
                tabs.current[siguiente]?.focus();
              }}
              className={`inline-flex items-center justify-center gap-2.5 rounded-xl px-5 py-3 text-sm font-medium transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-400 ${grupo === id
                ? id === "OPERACION" ? "bg-teal-500/15 text-teal-300 ring-1 ring-teal-500/30" : "bg-orange-500/15 text-orange-300 ring-1 ring-orange-500/30"
                : "text-slate-400 hover:bg-slate-800/70 hover:text-slate-200"}`}
            >
              <Icono className="size-4" aria-hidden="true" /> {texto}
              <span className="rounded-md bg-black/20 px-2 py-0.5 text-xs tabular-nums">
                {ejecuciones.filter((item) => (item.tipo === "DAY_TO_DAY") === (id === "OPERACION")).length}
              </span>
            </button>
          ))}
        </div>

        {(error || errorActivas) && (
          <div role="alert" className="mb-5 rounded-xl border border-red-900/70 bg-red-950/30 px-4 py-3 text-sm text-red-300">
            {error || errorActivas}
          </div>
        )}

        <section id="historial-panel" role="tabpanel" aria-labelledby={`historial-tab-${grupo}`} tabIndex={0}>
          <div className="mb-5 flex flex-wrap items-end justify-between gap-4">
            <div>
              <h2 className="text-lg font-semibold">{esOperacion ? "Jornadas de operación" : "Simulaciones de planificación"}</h2>
              <p className="mt-1 text-sm text-slate-500">
                {esOperacion ? "Actividad Día a Día, controles y resultados de cada jornada." : "Pruebas de cinco días y de colapso, con sus resultados independientes."}
              </p>
            </div>
            <label className="flex items-center gap-3 text-xs text-slate-400">
              Estado
              <select
                aria-label="Filtrar historial por estado"
                value={filtroEstado}
                onChange={(evento) => setFiltroEstado(evento.target.value as FiltroEstado)}
                className="rounded-lg border border-slate-700 bg-[#151c26] px-3 py-2.5 text-sm text-slate-200 focus:outline-teal-400"
              >
                <option value="TODOS">Todos</option>
                <option value="ACTIVOS">En curso o en pausa</option>
                <option value="FINALIZADOS">Finalizados</option>
                <option value="CREADOS">Pendientes de iniciar</option>
              </select>
            </label>
          </div>

          {cargando ? (
            <div className="flex h-64 items-center justify-center text-slate-400" role="status">
              <Loader2 className="mr-2 size-5 animate-spin" aria-hidden="true" /> Cargando historial...
            </div>
          ) : visibles.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-slate-700 bg-[#111820] px-5 py-16 text-center">
              {esOperacion ? <Truck className="mx-auto mb-4 size-9 text-teal-500/60" aria-hidden="true" /> : <FlaskConical className="mx-auto mb-4 size-9 text-orange-500/60" aria-hidden="true" />}
              <h3 className="font-medium text-slate-200">{filtroEstado !== "TODOS" ? "No hay registros con este estado" : esOperacion ? "Todavía no hay jornadas de operación" : "Todavía no hay simulaciones"}</h3>
              <p className="mt-2 text-sm text-slate-500">{filtroEstado !== "TODOS" ? "Prueba con otro filtro para consultar el historial." : "Los registros aparecerán aquí cuando inicies desde su sección."}</p>
            </div>
          ) : (
            <div className="grid gap-4 xl:grid-cols-2">
              {visibles.map((ejecucion) => {
                const esDiaADia = ejecucion.tipo === "DAY_TO_DAY";
                const activa = esEjecucionActiva(ejecucion);
                const conflicto = activas.find((item) => item.id !== ejecucion.id && mismoGrupo(item.tipo, ejecucion.tipo));
                const bloqueoInicio = conflicto
                  ? `${esDiaADia ? "Operación" : "Simulación"} #${conflicto.id} ${conflicto.estado === "PAUSED" ? "en pausa" : "en curso"}. Finalízala antes de iniciar otra.`
                  : null;
                const color = esDiaADia ? "text-teal-300" : "text-orange-300";
                return (
                  <article key={ejecucion.id} className={`overflow-hidden rounded-2xl border bg-[#111820] ${activa ? esDiaADia ? "border-teal-800/70" : "border-orange-800/70" : "border-slate-800"}`}>
                    <div className={`h-1 ${esDiaADia ? "bg-teal-500/60" : "bg-orange-500/60"}`} />
                    <div className="p-5">
                      <div className="flex items-start justify-between gap-3">
                        <div className="flex min-w-0 items-center gap-3">
                          <div className={`rounded-xl p-2.5 ${esDiaADia ? "bg-teal-500/10 text-teal-400" : "bg-orange-500/10 text-orange-400"}`}>
                            {esDiaADia ? <Truck className="size-5" aria-hidden="true" /> : <FlaskConical className="size-5" aria-hidden="true" />}
                          </div>
                          <div className="min-w-0">
                            <p className="text-xs text-slate-500">{esDiaADia ? "Operación" : "Simulación"} #{ejecucion.id}</p>
                            <h3 className="mt-1 font-semibold">{nombreTipoEjecucion(ejecucion.tipo)}</h3>
                          </div>
                        </div>
                        <EstadoBadge estado={ejecucion.estado} />
                      </div>

                      <dl className="mt-5 grid grid-cols-2 gap-3 text-xs">
                        <Dato label={esDiaADia ? "Inicio de operación" : "Inicio simulado"} value={formatearFecha(ejecucion.inicioSimulado)} />
                        <Dato label="Ritmo" value={esDiaADia ? "Tiempo real · 1×" : `${ejecucion.factorAceleracion}×`} />
                        <Dato label={esDiaADia ? "Hora de operación" : "Último instante simulado"} value={formatearFecha(ejecucion.instanteSimulado)} />
                        <Dato label={esFinalizada(ejecucion) ? "Cierre real" : "Inicio real"} value={formatearFecha(esFinalizada(ejecucion) ? ejecucion.finReal : ejecucion.inicioReal)} />
                      </dl>

                      {ejecucion.configuracionSa && (
                        <details className="mt-4 text-xs text-slate-500">
                          <summary className="cursor-pointer rounded py-1 hover:text-slate-300">Parámetros del planificador</summary>
                          <p className="mt-2 rounded-lg bg-slate-800/40 px-3 py-2 leading-5">
                            SA · Semilla {ejecucion.configuracionSa.semilla} · Temperatura inicial {ejecucion.configuracionSa.temperaturaInicial} · Enfriamiento {ejecucion.configuracionSa.factorEnfriamiento}
                          </p>
                        </details>
                      )}

                      {ejecucion.estado === "CREATED" && bloqueoInicio && (
                        <p className="mt-4 flex items-start gap-2 rounded-lg border border-amber-500/15 bg-amber-500/5 px-3 py-2 text-xs leading-5 text-amber-300">
                          <Clock3 className="mt-0.5 size-4 shrink-0" aria-hidden="true" />{bloqueoInicio}
                        </p>
                      )}

                      <div className="mt-5 flex flex-wrap justify-end gap-2 border-t border-slate-800 pt-4">
                        {ejecucion.estado === "CREATED" && (
                          <button
                            type="button"
                            disabled={accionandoId !== null || Boolean(bloqueoInicio) || consultandoActivas || Boolean(errorActivas)}
                            onClick={() => void iniciar(ejecucion)}
                            title={bloqueoInicio ?? undefined}
                            className={`inline-flex items-center gap-2 rounded-lg px-3.5 py-2.5 text-xs font-semibold transition disabled:cursor-not-allowed disabled:opacity-40 ${esDiaADia ? "bg-teal-600 text-white hover:bg-teal-500" : "bg-orange-500 text-slate-950 hover:bg-orange-400"}`}
                          >
                            {accionandoId === ejecucion.id ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Play className="size-4" aria-hidden="true" />} Iniciar
                          </button>
                        )}
                        {activa && (
                          <button
                            type="button"
                            disabled={accionandoId !== null}
                            onClick={() => setPorDetener(ejecucion)}
                            className="inline-flex items-center gap-2 rounded-lg border border-red-900/70 px-3.5 py-2.5 text-xs font-medium text-red-300 transition hover:bg-red-950/40 disabled:opacity-50"
                          >
                            <Square className="size-4" aria-hidden="true" /> Detener
                          </button>
                        )}
                        {esFinalizada(ejecucion) && (
                          <button
                            type="button"
                            onClick={() => setResultadoId(ejecucion.id)}
                            className={`inline-flex items-center gap-2 rounded-lg border border-slate-700 px-3.5 py-2.5 text-xs font-medium transition hover:bg-slate-800 ${color}`}
                          >
                            <BarChart3 className="size-4" aria-hidden="true" /> Resultados
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => navigate(`/ejecucion/${ejecucion.id}`)}
                          className="inline-flex items-center gap-2 rounded-lg border border-slate-700 bg-slate-800/40 px-3.5 py-2.5 text-xs font-medium text-slate-200 transition hover:bg-slate-800"
                        >
                          <ExternalLink className="size-4" aria-hidden="true" /> {activa ? "Ver en vivo" : "Abrir mapa"}
                        </button>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </section>
      </div>

      <ResultadoEscenarioModal ejecucionId={resultadoId} onCerrar={() => setResultadoId(null)} />
      <ConfirmDialog
        abierto={porDetener !== null}
        titulo={operacionPorDetener ? `Detener operación Día a Día #${porDetener?.id}` : `Detener simulación #${porDetener?.id}`}
        descripcion={operacionPorDetener
          ? "Se cerrará esta jornada y se consolidarán sus resultados. La simulación que esté en curso continuará."
          : "Esta simulación dejará de avanzar y se consolidarán sus resultados. La operación Día a Día continuará."}
        textoConfirmar={operacionPorDetener ? "Detener operación" : "Detener simulación"}
        variante="danger"
        confirmando={accionandoId !== null}
        onCancelar={() => setPorDetener(null)}
        onConfirmar={() => porDetener !== null && void detener(porDetener)}
      />
    </div>
  );
}

function esFinalizada(ejecucion: EscenarioResponseDto) {
  return ["STOPPED", "COMPLETED", "COLLAPSED", "FAILED"].includes(ejecucion.estado);
}

function EstadoBadge({ estado }: { estado: EscenarioResponseDto["estado"] }) {
  const estilos = estado === "RUNNING" ? "border-green-500/20 bg-green-500/10 text-green-300"
    : estado === "PAUSED" ? "border-amber-500/20 bg-amber-500/10 text-amber-300"
      : estado === "FAILED" || estado === "COLLAPSED" ? "border-red-500/20 bg-red-500/10 text-red-300"
        : "border-slate-700 bg-slate-800 text-slate-400";
  return <span className={`shrink-0 rounded-full border px-2.5 py-1 text-[11px] font-medium ${estilos}`}>{nombreEstadoEjecucion(estado)}</span>;
}

function Dato({ label, value }: { label: string; value: string }) {
  return <div className="min-w-0 rounded-xl bg-[#17202a]/70 p-3">
    <dt className="text-[10px] font-medium uppercase tracking-wide text-slate-500">{label}</dt>
    <dd className="mt-1.5 break-words leading-5 text-slate-300">{value}</dd>
  </div>;
}

function formatearFecha(valor: string | null) {
  if (!valor) return "—";
  return new Intl.DateTimeFormat("es-PE", {
    timeZone: "America/Lima",
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(valor));
}
