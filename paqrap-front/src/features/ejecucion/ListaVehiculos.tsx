import { useMemo, useState } from "react";
import { ArrowDownUp, Search, Truck } from "lucide-react";

import type { MonitoreoResponseDto } from "@/api/monitoreo.dto";
import type { PlanResponseDto, RutaResponseDto } from "@/api/planificacion.dto";
import type { TipoVehiculoResponseDto } from "@/api/flota.dto";
import { Card } from "@/components/ui/card";
import { useConfiguracion } from "@/context/ConfiguracionContext";

import type { SeleccionLista } from "./ListaPedidosEjecucion";

interface ListaVehiculosProps {
  monitoreo: MonitoreoResponseDto;
  ejecucionId: number;
  tiposFlota: TipoVehiculoResponseDto[];
  plan: PlanResponseDto | null;
  seleccionId?: string | null;
  onSeleccionar: (seleccion: SeleccionLista) => void;
}

type OrdenVehiculos =
  | "OCUPACION_DESC"
  | "OCUPACION_ASC"
  | "SALIDA_ASC"
  | "SALIDA_DESC"
  | "LLEGADA_ASC"
  | "LLEGADA_DESC"
  | "CODIGO";

export function ListaVehiculos({
  monitoreo,
  tiposFlota,
  plan,
  seleccionId,
  onSeleccionar,
}: ListaVehiculosProps) {
  const [busqueda, setBusqueda] = useState("");
  const [orden, setOrden] = useState<OrdenVehiculos>("OCUPACION_DESC");
  const { configuracion } = useConfiguracion();

  const capacidades = useMemo(
    () => new Map(tiposFlota.map((tipo) => [tipo.tipo, tipo.capacidadPaquetes])),
    [tiposFlota],
  );

  const rutasPorVehiculo = useMemo(
    () => new Map((plan?.rutas ?? []).map((ruta) => [ruta.vehiculoId, ruta])),
    [plan],
  );

  const filas = useMemo(() => {
    const texto = busqueda.trim().toLowerCase();

    return monitoreo.vehiculos
      .map((vehiculo) => {
        const capacidad = capacidades.get(vehiculo.tipo) ?? 0;
        const ocupacion = capacidad > 0 ? (vehiculo.cargaActual / capacidad) * 100 : 0;
        const ruta = rutasPorVehiculo.get(vehiculo.id);
        const entregas = ruta?.paradas.filter((parada) => parada.tipo === "ENTREGA") ?? [];

        return {
          vehiculo,
          capacidad,
          ocupacion,
          ruta,
          entregas: entregas.length,
          paquetes: entregas.reduce((total, parada) => total + parada.paquetes, 0),
        };
      })
      .filter(({ vehiculo }) => !texto || vehiculo.id.toLowerCase().includes(texto))
      .sort((a, b) => compararVehiculos(a, b, orden));
  }, [busqueda, capacidades, monitoreo.vehiculos, orden, rutasPorVehiculo]);

  return (
    <Card className="border-slate-800 bg-[#151b23] p-4">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Truck className="size-4 text-sky-400" />
          <h3 className="font-semibold">Unidades de transporte</h3>
        </div>
        <span className="text-[11px] text-slate-500">{filas.length}</span>
      </div>

      <div className="mb-3 grid grid-cols-[minmax(0,1fr)_150px] gap-2">
        <div className="relative">
          <Search className="absolute left-2 top-1/2 size-4 -translate-y-1/2 text-slate-500" />
          <input
            type="search"
            value={busqueda}
            onChange={(event) => setBusqueda(event.target.value)}
            placeholder="Buscar TA01, TM03..."
            aria-label="Buscar unidad por código"
            className="w-full rounded-md border border-slate-700 bg-[#11161d] py-1.5 pl-8 pr-2 text-sm text-white placeholder:text-slate-500"
          />
        </div>

        <label className="relative">
          <ArrowDownUp className="pointer-events-none absolute left-2 top-1/2 size-3.5 -translate-y-1/2 text-slate-500" />
          <select
            value={orden}
            onChange={(event) => setOrden(event.target.value as OrdenVehiculos)}
            aria-label="Ordenar unidades"
            className="w-full rounded-md border border-slate-700 bg-[#11161d] py-1.5 pl-7 pr-2 text-xs text-slate-200"
          >
            <option value="OCUPACION_DESC">Ocupación ↓</option>
            <option value="OCUPACION_ASC">Ocupación ↑</option>
            <option value="SALIDA_ASC">Salida ↑</option>
            <option value="SALIDA_DESC">Salida ↓</option>
            <option value="LLEGADA_ASC">Llegada ↑</option>
            <option value="LLEGADA_DESC">Llegada ↓</option>
            <option value="CODIGO">Código</option>
          </select>
        </label>
      </div>

      <ul className="max-h-[430px] space-y-1 overflow-y-auto pr-1">
        {filas.map(({ vehiculo, capacidad, ocupacion, ruta, entregas, paquetes }) => {
          const seleccionado = seleccionId === vehiculo.id;
          const nivel = nivelOcupacion(
            ocupacion,
            vehiculo.cargaActual,
            configuracion.vehiculoVerdeHasta,
            configuracion.vehiculoAmbarHasta,
          );

          return (
            <li key={vehiculo.id}>
              <button
                type="button"
                onClick={() => onSeleccionar({ tipo: "VEHICULO", id: vehiculo.id })}
                className={[
                  "w-full rounded-md border p-2 text-left transition",
                  seleccionado
                    ? "border-orange-500 bg-orange-950/25 ring-1 ring-orange-500/30"
                    : "border-slate-800 bg-[#11161d] hover:border-orange-500/60",
                ].join(" ")}
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-mono text-xs font-semibold text-white">{vehiculo.id}</p>
                    <p className="mt-0.5 text-[10px] text-slate-500">
                      {nombreTipo(vehiculo.tipo)} · {nombreEstado(vehiculo.estado)}
                    </p>
                  </div>
                  <span className={`rounded-full px-2 py-0.5 text-[9px] font-semibold ${claseNivel(nivel)}`}>
                    {vehiculo.cargaActual}/{capacidad || "—"} · {ocupacion.toFixed(0)}%
                  </span>
                </div>

                {vehiculo.motivoDetencion && (
                  <p className="mt-1 text-[10px] text-amber-300">
                    {nombreMotivo(vehiculo.motivoDetencion)}
                    {vehiculo.detenidoHasta ? ` hasta ${formatearHora(vehiculo.detenidoHasta)}` : ""}
                  </p>
                )}

                <div className="mt-1.5 grid grid-cols-2 gap-2 text-[10px] text-slate-400">
                  <span>{ruta ? `Ruta ${ruta.id}` : "Sin ruta vigente"}</span>
                  <span className="text-right">{entregas} entregas · {paquetes} paq.</span>
                  <span>Salida {ruta ? formatearHora(ruta.salida) : "—"}</span>
                  <span className="text-right">Llegada {ruta?.llegadaFinal ? formatearHora(ruta.llegadaFinal) : "—"}</span>
                </div>
              </button>
            </li>
          );
        })}
      </ul>
    </Card>
  );
}

function compararVehiculos(
  a: { vehiculo: MonitoreoResponseDto["vehiculos"][number]; ocupacion: number; ruta?: RutaResponseDto },
  b: { vehiculo: MonitoreoResponseDto["vehiculos"][number]; ocupacion: number; ruta?: RutaResponseDto },
  orden: OrdenVehiculos,
) {
  switch (orden) {
    case "OCUPACION_ASC":
      return a.ocupacion - b.ocupacion || a.vehiculo.id.localeCompare(b.vehiculo.id);
    case "OCUPACION_DESC":
      return b.ocupacion - a.ocupacion || a.vehiculo.id.localeCompare(b.vehiculo.id);
    case "SALIDA_ASC":
      return fechaOrden(a.ruta?.salida) - fechaOrden(b.ruta?.salida) || a.vehiculo.id.localeCompare(b.vehiculo.id);
    case "SALIDA_DESC":
      return fechaOrdenDesc(a.ruta?.salida) - fechaOrdenDesc(b.ruta?.salida) || a.vehiculo.id.localeCompare(b.vehiculo.id);
    case "LLEGADA_ASC":
      return fechaOrden(a.ruta?.llegadaFinal) - fechaOrden(b.ruta?.llegadaFinal) || a.vehiculo.id.localeCompare(b.vehiculo.id);
    case "LLEGADA_DESC":
      return fechaOrdenDesc(a.ruta?.llegadaFinal) - fechaOrdenDesc(b.ruta?.llegadaFinal) || a.vehiculo.id.localeCompare(b.vehiculo.id);
    case "CODIGO":
      return a.vehiculo.id.localeCompare(b.vehiculo.id, undefined, { numeric: true });
  }
}

function fechaOrden(valor: string | null | undefined) {
  if (!valor) return Number.POSITIVE_INFINITY;
  const fecha = new Date(valor).getTime();
  return Number.isFinite(fecha) ? fecha : Number.POSITIVE_INFINITY;
}

function fechaOrdenDesc(valor: string | null | undefined) {
  if (!valor) return Number.POSITIVE_INFINITY;
  const fecha = new Date(valor).getTime();
  return Number.isFinite(fecha) ? -fecha : Number.POSITIVE_INFINITY;
}

function nivelOcupacion(
  porcentaje: number,
  carga: number,
  verdeHasta: number,
  ambarHasta: number,
) {
  if (carga === 0) return "VACIO" as const;
  if (porcentaje <= verdeHasta) return "VERDE" as const;
  if (porcentaje <= ambarHasta) return "AMBAR" as const;
  return "ROJO" as const;
}

function claseNivel(nivel: ReturnType<typeof nivelOcupacion>) {
  switch (nivel) {
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

function nombreTipo(tipo: MonitoreoResponseDto["vehiculos"][number]["tipo"]) {
  switch (tipo) {
    case "CAR":
      return "Auto";
    case "MOTORCYCLE":
      return "Moto";
    case "BICYCLE":
      return "Bicicleta";
  }
}

function nombreEstado(estado: MonitoreoResponseDto["vehiculos"][number]["estado"]) {
  switch (estado) {
    case "AVAILABLE":
      return "Disponible";
    case "IN_ROUTE":
      return "En ruta";
    case "UNAVAILABLE":
      return "No disponible";
  }
}

function nombreMotivo(motivo: NonNullable<MonitoreoResponseDto["vehiculos"][number]["motivoDetencion"]>) {
  switch (motivo) {
    case "AVERIA":
      return "Avería";
    case "MANTENIMIENTO":
      return "Mantenimiento";
    case "REFRIGERIO":
      return "Refrigerio";
  }
}

function formatearHora(valor: string) {
  return new Intl.DateTimeFormat("es-PE", {
    timeZone: "America/Lima",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(new Date(valor));
}
