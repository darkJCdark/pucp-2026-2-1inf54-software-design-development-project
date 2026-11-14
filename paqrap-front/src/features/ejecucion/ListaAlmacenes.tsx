import { useMemo, useState } from "react";
import { Search, Warehouse } from "lucide-react";

import type { MonitoreoResponseDto } from "@/api/monitoreo.dto";
import type { PlanResponseDto } from "@/api/planificacion.dto";
import { Card } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { useUmbralesEjecucion } from "@/context/ConfiguracionContext";
import { nivelSemaforo, type NivelSemaforo } from "@/features/configuracion/semaforo";

import type { SeleccionLista } from "./ListaPedidosEjecucion";
import { operacionesAlmacen } from "./operacionesAlmacen";

type VentanaAlmacen = keyof ReturnType<typeof operacionesAlmacen>;
const TITULOS = {
  enTransito: "En tránsito al almacén (previsto)",
  proximas4h: "Llegadas previstas en próximas 4 h",
  atendidas4h: "Llegadas programadas en últimas 4 h",
};

interface ListaAlmacenesProps {
  monitoreo: MonitoreoResponseDto;
  ejecucionId: number;
  plan: PlanResponseDto | null;
  seleccionId?: string | null;
  onSeleccionar: (seleccion: SeleccionLista) => void;
}

export function ListaAlmacenes({
  monitoreo,
  plan,
  seleccionId,
  onSeleccionar,
}: ListaAlmacenesProps) {
  const [busqueda, setBusqueda] = useState("");
  const [ventana, setVentana] = useState<{ almacenId: string; tipo: VentanaAlmacen } | null>(null);
  const { umbrales: configuracion } = useUmbralesEjecucion();
  const ahora = new Date(monitoreo.instante).getTime();
  const estadisticas = useMemo(() => new Map(monitoreo.almacenes.map((almacen) =>
    [almacen.id, operacionesAlmacen(plan, almacen.id, ahora)])), [ahora, plan, monitoreo.almacenes]);

  const filtrados = monitoreo.almacenes.filter((almacen) => {
    const texto = busqueda.trim().toLowerCase();
    return !texto || almacen.id.toLowerCase().includes(texto) || nombreAlmacen(almacen.id).toLowerCase().includes(texto);
  });

  return (
    <Card className="border-slate-800 bg-[#151b23] p-4">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Warehouse className="size-4 text-green-400" />
          <h3 className="font-semibold">Almacenes</h3>
        </div>
        <span className="text-[11px] text-slate-500">{filtrados.length}</span>
      </div>

      <div className="relative mb-3">
        <Search className="absolute left-2 top-1/2 size-4 -translate-y-1/2 text-slate-500" />
        <input
          type="search"
          value={busqueda}
          onChange={(event) => setBusqueda(event.target.value)}
          placeholder="Buscar por nombre"
          aria-label="Buscar almacén por nombre"
          className="w-full rounded-md border border-slate-700 bg-[#11161d] py-1.5 pl-8 pr-2 text-sm text-white placeholder:text-slate-500"
        />
      </div>

      <ul className="space-y-2">
        {filtrados.map((almacen) => {
          const central = almacen.tipo === "CENTRAL";
          const porcentaje =
            !central && almacen.stock !== null && almacen.capacidad
              ? (almacen.stock / almacen.capacidad) * 100
              : null;
          const nivel = central
            ? "ILIMITADO"
            : nivelSemaforo(
                porcentaje ?? 0,
                configuracion.almacenVerdeHasta,
                configuracion.almacenAmbarHasta,
              );
          const datos = estadisticas.get(almacen.id)!;
          const seleccionado = seleccionId === almacen.id;

          return (
            <li key={almacen.id}>
              <div
                className={[
                  "w-full rounded-md border p-3 text-left transition",
                  seleccionado
                    ? "border-orange-500 bg-orange-950/25 ring-1 ring-orange-500/30"
                    : "border-slate-800 bg-[#11161d] hover:border-orange-500/60",
                ].join(" ")}
              >
                <button type="button" className="w-full text-left"
                  onClick={() => onSeleccionar({ tipo: "ALMACEN", id: almacen.id })}>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-sm font-medium text-white">{nombreAlmacen(almacen.id)}</p>
                    <p className="text-[10px] text-slate-500">{almacen.id}</p>
                  </div>
                  <span className={`rounded-full px-2 py-0.5 text-[9px] font-semibold ${claseNivel(nivel)}`}>
                    {etiquetaNivel(nivel)}
                  </span>
                </div>

                {central ? (
                  <p className="mt-2 text-xs text-slate-400">Inventario ilimitado</p>
                ) : (
                  <>
                    <div className="mt-2 flex items-center justify-between text-[11px] text-slate-400">
                      <span>{almacen.stock ?? 0} / {almacen.capacidad ?? 0} unidades</span>
                      <span>{(porcentaje ?? 0).toFixed(1)}%</span>
                    </div>
                    <Progress value={porcentaje ?? 0} className="mt-1.5 h-1.5" />
                  </>
                )}

                </button>
                <p className="mt-2 text-[10px] text-slate-500">Operaciones de almacén · plan vigente, no entregas confirmadas</p>
                <div className="mt-3 grid grid-cols-3 gap-1 text-center text-[9px]">
                  <MiniDato
                    label="En tránsito"
                    envios={datos.enTransito}
                    onClick={() => setVentana({ almacenId: almacen.id, tipo: "enTransito" })}
                  />
                  <MiniDato
                    label="Próx. 4 h"
                    envios={datos.proximas4h}
                    onClick={() => setVentana({ almacenId: almacen.id, tipo: "proximas4h" })}
                  />
                  <MiniDato
                    label="Últ. 4 h"
                    envios={datos.atendidas4h}
                    onClick={() => setVentana({ almacenId: almacen.id, tipo: "atendidas4h" })}
                  />
                </div>
                {ventana?.almacenId === almacen.id && (
                  <section className="mt-3 space-y-2 border-t border-slate-700 pt-2" aria-label={TITULOS[ventana.tipo]}>
                    <div className="flex items-start justify-between gap-2">
                      <h4 className="text-xs font-semibold">{TITULOS[ventana.tipo]}</h4>
                      <button type="button" onClick={() => setVentana(null)} className="text-xs text-slate-400">Cerrar</button>
                    </div>
                    <p className="text-[10px] text-amber-300">Recargas/llegadas de rutas, sin identificador de pedido. El horario pasado no acredita una entrega. Historial limitado al plan vigente.</p>
                    {datos[ventana.tipo].length === 0 && <p className="text-xs text-slate-500">Sin operaciones en esta ventana del plan vigente.</p>}
                    {datos[ventana.tipo].map((op) => (
                      <div key={`${op.rutaId}-${op.orden}`} className="break-words rounded bg-slate-800/60 p-2 text-[11px]">
                        <p>Ruta {op.rutaId} · parada {op.orden}</p>
                        <button type="button" className="text-orange-300 underline"
                          onClick={() => onSeleccionar({ tipo: "VEHICULO", id: op.vehiculoId })}>UT {op.vehiculoId}</button>
                        <p>{op.paquetes} paq. · llegada prevista {new Date(op.llegada).toLocaleString("es-PE", { timeZone: "America/Lima" })}</p>
                        <p className="text-slate-400">{Date.parse(op.llegada) < ahora ? "Horario transcurrido; ejecución no confirmada" : Date.parse(op.salida) <= ahora ? "En ruta según programación" : "Programada"}</p>
                      </div>
                    ))}
                  </section>
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </Card>
  );
}

function MiniDato({
  label,
  envios,
  onClick,
}: {
  label: string;
  envios: ReturnType<typeof operacionesAlmacen>[VentanaAlmacen];
  onClick: () => void;
}) {
  return (
    <button type="button" onClick={onClick} aria-label={`Ver ${label}`} className="rounded bg-slate-800/70 px-1 py-1.5 hover:bg-slate-700 focus-visible:outline focus-visible:outline-orange-400">
      <p className="font-semibold text-slate-200">{envios.length} op.</p>
      <p className="mt-0.5 text-slate-400">{envios.reduce((total, op) => total + Math.max(0, op.paquetes), 0)} paq.</p>
      <p className="mt-0.5 text-slate-500">{label}</p>
    </button>
  );
}

function nombreAlmacen(id: string) {
  switch (id) {
    case "CENTRAL":
      return "Central";
    case "NORTHWEST":
      return "Nor-Oeste";
    case "EAST":
      return "Este";
    default:
      return id;
  }
}

type Nivel = NivelSemaforo | "ILIMITADO";

function claseNivel(nivel: Nivel) {
  switch (nivel) {
    case "ILIMITADO":
      return "bg-blue-500/15 text-blue-300";
    case "VACIO":
      return "bg-slate-700 text-slate-200";
    case "VERDE":
      return "bg-green-500/15 text-green-400";
    case "AMBAR":
      return "bg-amber-500/15 text-amber-300";
    case "ROJO":
      return "bg-red-500/15 text-red-300";
  }
}

function etiquetaNivel(nivel: Nivel) {
  switch (nivel) {
    case "ILIMITADO":
      return "Ilimitado";
    case "VACIO":
      return "Vacío";
    case "VERDE":
      return "Verde";
    case "AMBAR":
      return "Ámbar";
    case "ROJO":
      return "Rojo";
  }
}
