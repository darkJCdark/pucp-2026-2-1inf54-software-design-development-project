import { useState } from "react";
import { Activity, ArrowUpRight, CalendarDays, FlaskConical, History, Package, PanelLeftClose, PanelLeftOpen, Radio, Settings2, type LucideIcon } from "lucide-react";
import { Link, NavLink, useLocation } from "react-router-dom";
import type { EscenarioResponseDto } from "@/api/escenarios.dto";
import { useEjecucionesActivas } from "@/context/EjecucionesActivasContext";
import { nombreEstadoEjecucion, nombreTipoEjecucion } from "@/lib/ejecuciones";

const enlaces = [
  { nombre: "Historial", ruta: "/ejecuciones", icono: History },
  { nombre: "Pedidos", ruta: "/pedidos", icono: Package },
  { nombre: "Mantenimientos", ruta: "/mantenimientos", icono: CalendarDays },
];

export default function Sidebar() {
  const { pathname } = useLocation();
  const { operacion, simulacion, cargando, error } = useEjecucionesActivas();
  const enEjecucion = /^\/ejecucion\//.test(pathname);
  const [preferencia, setPreferencia] = useState<{ enEjecucion: boolean; plegada: boolean } | null>(null);
  const plegada = preferencia?.enEjecucion === enEjecucion ? preferencia.plegada : enEjecucion;
  const etiquetas = plegada ? "hidden" : "hidden md:block";

  return (
    <aside className={`sticky top-0 flex h-dvh shrink-0 flex-col border-r border-slate-800 bg-[#11161d] transition-[width] duration-150 ${plegada ? "w-16" : "w-16 md:w-60"}`}>
      <div className="flex h-20 shrink-0 items-center justify-center gap-3 border-b border-slate-800 px-3 md:justify-between md:px-4">
        {!plegada && <Link to="/operacion" className="hidden items-center gap-2.5 md:flex" aria-label="PaqRap, inicio">
          <span className="flex size-9 items-center justify-center rounded-xl bg-orange-500 text-lg font-bold text-white">P</span>
          <span><span className="block text-lg font-semibold tracking-tight text-white">PaqRap</span><span className="block text-[10px] tracking-wide text-slate-500">Gestión de entregas</span></span>
        </Link>}
        <button type="button" onClick={() => setPreferencia({ enEjecucion, plegada: !plegada })}
          aria-expanded={!plegada} aria-label={plegada ? "Expandir menú" : "Plegar menú"} title={plegada ? "Expandir menú" : "Plegar menú"}
          className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-slate-800 hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-orange-400">
          {plegada ? <PanelLeftOpen className="size-4" /> : <PanelLeftClose className="size-4" />}
        </button>
      </div>

      <nav aria-label="Navegación principal" className="flex-1 space-y-5 overflow-y-auto px-2 py-5">
        <div>
          <p className={`${etiquetas} mb-2 px-3 text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-500`}>Operación</p>
          <EnlaceActividad nombre="Día a Día" icono={Radio} ruta="/operacion" activa={operacion} plegada={plegada} color="teal" />
        </div>
        <div>
          <p className={`${etiquetas} mb-2 px-3 text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-500`}>Planificación</p>
          <EnlaceActividad nombre="Simulaciones" icono={FlaskConical} ruta="/simulacion" activa={simulacion} plegada={plegada} color="orange" />
        </div>
        <div className="border-t border-slate-800 pt-5">
          <p className={`${etiquetas} mb-2 px-3 text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-500`}>Gestión</p>
          <div className="space-y-1">
            {enlaces.map(({ nombre, ruta, icono: Icono }) => <NavLink key={ruta} to={ruta} title={nombre} aria-label={nombre}
              className={({ isActive }) => `flex min-h-11 items-center gap-3 rounded-lg px-3 py-3 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-orange-400 ${plegada ? "justify-center" : "justify-center md:justify-start"} ${isActive ? "bg-slate-800 text-white" : "text-slate-400 hover:bg-slate-800/60 hover:text-white"}`}>
              <Icono className="size-4 shrink-0" /><span className={etiquetas}>{nombre}</span>
            </NavLink>)}
          </div>
        </div>
      </nav>

      <div className="border-t border-slate-800 px-3 py-4" aria-live="polite">
        <div className={`flex items-center gap-2 ${plegada ? "justify-center" : "justify-center md:justify-start"}`} title={error ? "No se pudo actualizar la actividad" : `${Number(!!operacion) + Number(!!simulacion)} ejecuciones activas`}>
          <Activity className={`size-4 shrink-0 ${error ? "text-amber-400" : operacion || simulacion ? "text-emerald-400" : "text-slate-500"}`} />
          <span className={`${etiquetas} text-xs text-slate-400`}>
            {error ? "Sin actualizar" : cargando ? "Consultando actividad…" : operacion && simulacion ? "Ambas en actividad" : operacion || simulacion ? "1 ejecución activa" : "Sin ejecuciones activas"}
          </span>
        </div>
        {!plegada && <p className="mt-2 hidden text-[10px] leading-relaxed text-slate-600 md:block">Operación diaria y simulación con controles independientes.</p>}
      </div>
    </aside>
  );
}

function EnlaceActividad({ nombre, icono: Icono, ruta, activa, plegada, color }: {
  nombre: string; icono: LucideIcon; ruta: string; activa: EscenarioResponseDto | null; plegada: boolean; color: "teal" | "orange";
}) {
  const { pathname } = useLocation();
  const destino = activa ? `/ejecucion/${activa.id}` : ruta;
  const seleccionado = pathname === ruta || (activa && pathname === destino);
  const acento = color === "teal" ? "text-teal-300" : "text-orange-300";
  const fondo = color === "teal" ? "border-teal-900/60 bg-teal-950/35" : "border-orange-900/60 bg-orange-950/30";
  const titulo = activa ? `${nombreTipoEjecucion(activa.tipo)} #${activa.id} · ${nombreEstadoEjecucion(activa.estado)}` : nombre;
  return <div>
    <Link to={destino} title={titulo} aria-label={activa ? `Abrir ${titulo}` : nombre} aria-current={seleccionado ? "page" : undefined}
      className={`relative flex min-h-14 items-center gap-3 rounded-xl border px-3 py-3 transition-colors focus-visible:outline-2 focus-visible:outline-orange-400 ${plegada ? "justify-center" : "justify-center md:justify-start"} ${seleccionado ? `${fondo} ${acento}` : "border-transparent text-slate-400 hover:bg-slate-800/60 hover:text-white"}`}>
      <span className="relative shrink-0"><Icono className="size-[18px]" />
        {activa && <span className={`absolute -right-1 -top-1 size-2 rounded-full ring-2 ring-[#11161d] ${activa.estado === "PAUSED" ? "bg-amber-400" : "bg-emerald-400"}`} />}
      </span>
      {!plegada && <span className="hidden min-w-0 flex-1 md:block">
        <span className="block text-sm font-semibold">{nombre}</span>
        <span className={`mt-1 block text-[10px] ${activa ? acento : "text-slate-500"}`}>
          {activa ? `#${activa.id} · ${nombreEstadoEjecucion(activa.estado)}` : color === "teal" ? "Entregas en tiempo real" : "5 días o hasta colapso"}
        </span>
      </span>}
      {activa && !plegada && <ArrowUpRight className="hidden size-3.5 shrink-0 text-slate-500 md:block" />}
    </Link>
    {activa && !plegada && <Link to={ruta} className="ml-3 mt-1 hidden items-center gap-2 rounded-md px-3 py-2 text-[11px] text-slate-500 transition-colors hover:text-slate-200 md:flex">
      <Settings2 className="size-3.5" /> Ver configuración
    </Link>}
  </div>;
}
