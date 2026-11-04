import { useMemo, useState } from "react";
import { Search, Warehouse } from "lucide-react";

import type { MonitoreoResponseDto } from "@/api/monitoreo.dto";
import type { PlanResponseDto } from "@/api/planificacion.dto";
import { Card } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { useConfiguracion } from "@/context/ConfiguracionContext";

import type { SeleccionLista } from "./ListaPedidosEjecucion";

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
  const { configuracion } = useConfiguracion();
  const ahora = new Date(monitoreo.instante).getTime();
  const cuatroHoras = 4 * 60 * 60 * 1000;

  const estadisticas = useMemo(() => {
    const resultado = new Map<
      string,
      {
        enTransito: number;
        paquetesEnTransito: number;
        proximas4h: number;
        paquetesProximas4h: number;
        atendidas4h: number;
        paquetesAtendidas4h: number;
      }
    >();

    for (const ruta of plan?.rutas ?? []) {
      const salida = new Date(ruta.salida).getTime();

      for (const parada of ruta.paradas) {
        if (parada.tipo !== "ALMACEN" || !parada.almacenId || !parada.llegada) continue;

        const llegada = new Date(parada.llegada).getTime();
        const actual = resultado.get(parada.almacenId) ?? {
          enTransito: 0,
          paquetesEnTransito: 0,
          proximas4h: 0,
          paquetesProximas4h: 0,
          atendidas4h: 0,
          paquetesAtendidas4h: 0,
        };

        const paquetes = Math.max(0, parada.paquetes);

        if (salida <= ahora && llegada > ahora) {
          actual.enTransito += 1;
          actual.paquetesEnTransito += paquetes;
        }

        if (llegada >= ahora && llegada <= ahora + cuatroHoras) {
          actual.proximas4h += 1;
          actual.paquetesProximas4h += paquetes;
        }

        if (llegada < ahora && llegada >= ahora - cuatroHoras) {
          actual.atendidas4h += 1;
          actual.paquetesAtendidas4h += paquetes;
        }
        resultado.set(parada.almacenId, actual);
      }
    }

    return resultado;
  }, [ahora, plan]);

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
            : nivelStock(
                porcentaje ?? 0,
                configuracion.almacenVerdeHasta,
                configuracion.almacenAmbarHasta,
              );
          const datos = estadisticas.get(almacen.id) ?? {
            enTransito: 0,
            paquetesEnTransito: 0,
            proximas4h: 0,
            paquetesProximas4h: 0,
            atendidas4h: 0,
            paquetesAtendidas4h: 0,
          };
          const seleccionado = seleccionId === almacen.id;

          return (
            <li key={almacen.id}>
              <button
                type="button"
                onClick={() => onSeleccionar({ tipo: "ALMACEN", id: almacen.id })}
                className={[
                  "w-full rounded-md border p-3 text-left transition",
                  seleccionado
                    ? "border-orange-500 bg-orange-950/25 ring-1 ring-orange-500/30"
                    : "border-slate-800 bg-[#11161d] hover:border-orange-500/60",
                ].join(" ")}
              >
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

                <div className="mt-3 grid grid-cols-3 gap-1 text-center text-[9px]">
                  <MiniDato
                    label="En tránsito"
                    envios={datos.enTransito}
                    paquetes={datos.paquetesEnTransito}
                  />
                  <MiniDato
                    label="Próx. 4 h"
                    envios={datos.proximas4h}
                    paquetes={datos.paquetesProximas4h}
                  />
                  <MiniDato
                    label="Últ. 4 h"
                    envios={datos.atendidas4h}
                    paquetes={datos.paquetesAtendidas4h}
                  />
                </div>
              </button>
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
  paquetes,
}: {
  label: string;
  envios: number;
  paquetes: number;
}) {
  return (
    <div className="rounded bg-slate-800/70 px-1 py-1.5">
      <p className="font-semibold text-slate-200">{envios} env.</p>
      <p className="mt-0.5 text-slate-400">{paquetes} paq.</p>
      <p className="mt-0.5 text-slate-500">{label}</p>
    </div>
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

function nivelStock(porcentaje: number, verdeDesde: number, ambarDesde: number) {
  if (porcentaje <= 0) return "VACIO" as const;
  if (porcentaje >= ambarDesde) return "VERDE" as const;
  if (porcentaje >= verdeDesde) return "AMBAR" as const;
  return "ROJO" as const;
}

type Nivel = ReturnType<typeof nivelStock> | "ILIMITADO";

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
