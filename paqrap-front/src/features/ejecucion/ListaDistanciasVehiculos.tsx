import { useMemo, useState } from "react";
import { Route } from "lucide-react";

import type { MonitoreoResponseDto } from "@/api/monitoreo.dto";
import type { PlanResponseDto } from "@/api/planificacion.dto";
import { Card } from "@/components/ui/card";

import { calcularDistancias } from "./distanciasVehiculo";
import type { SeleccionLista } from "./ListaPedidosEjecucion";

interface ListaDistanciasVehiculosProps {
  monitoreo: MonitoreoResponseDto;
  plan: PlanResponseDto | null;
  seleccionId?: string | null;
  onSeleccionar: (seleccion: SeleccionLista) => void;
}

type Criterio = "minutosAlDestino" | "minutosDesdeOrigen" | "kmAlDestino" | "kmDesdeOrigen" | "kmRestantes";

/** Rangos del filtro de distancia (AutoEVAL E11); el extremo superior no se incluye. */
const RANGOS_KM: Array<{ etiqueta: string; desde: number; hasta: number }> = [
  { etiqueta: "[0, 10[ km", desde: 0, hasta: 10 },
  { etiqueta: "[10, 20[ km", desde: 10, hasta: 20 },
  { etiqueta: "[20, 40[ km", desde: 20, hasta: 40 },
  { etiqueta: "[40, 200[ km", desde: 40, hasta: 200 },
];

function formatearHora(valor: string | null) {
  return valor
    ? new Intl.DateTimeFormat("es-PE", {
        timeZone: "America/Lima",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
      }).format(new Date(valor))
    : "—";
}

const CRITERIOS: Array<{ valor: Criterio; etiqueta: string; unidad: string }> = [
  { valor: "minutosAlDestino", etiqueta: "Minutos al destino", unidad: "min" },
  { valor: "minutosDesdeOrigen", etiqueta: "Minutos desde el origen", unidad: "min" },
  { valor: "kmAlDestino", etiqueta: "Km al destino", unidad: "km" },
  { valor: "kmDesdeOrigen", etiqueta: "Km desde el origen (recorridos)", unidad: "km" },
  { valor: "kmRestantes", etiqueta: "Km restantes de la ruta", unidad: "km" },
];

function numero(texto: string) {
  const valor = Number(texto);
  return texto.trim() === "" || !Number.isFinite(valor) ? null : valor;
}

/** Unidades en ruta: búsqueda por rango y orden según su distancia o tiempo a origen y destino. */
export function ListaDistanciasVehiculos({
  monitoreo,
  plan,
  seleccionId,
  onSeleccionar,
}: ListaDistanciasVehiculosProps) {
  const [criterio, setCriterio] = useState<Criterio>("minutosAlDestino");
  const [desde, setDesde] = useState("");
  const [hasta, setHasta] = useState("");
  const [descendente, setDescendente] = useState(false);
  const [rango, setRango] = useState("");

  const unidad = CRITERIOS.find((item) => item.valor === criterio)?.unidad ?? "";

  const filas = useMemo(() => {
    const instanteMs = new Date(monitoreo.instante).getTime();
    const minimo = numero(desde);
    const maximo = numero(hasta);
    const rutas = new Map((plan?.rutas ?? []).map((ruta) => [ruta.vehiculoId, ruta]));

    return monitoreo.vehiculos
      .flatMap((vehiculo) => {
        const distancias = calcularDistancias(rutas.get(vehiculo.id), instanteMs);
        return distancias ? [{ id: vehiculo.id, distancias }] : [];
      })
      .filter(({ distancias }) => {
        const valor = distancias[criterio];
        const rangoKm = RANGOS_KM[Number(rango)];
        return (minimo === null || valor >= minimo) && (maximo === null || valor <= maximo)
          && (rango === "" || (distancias.kmAlDestino >= rangoKm.desde && distancias.kmAlDestino < rangoKm.hasta));
      })
      .sort((a, b) =>
        (descendente ? -1 : 1) * (a.distancias[criterio] - b.distancias[criterio])
          || a.id.localeCompare(b.id),
      );
  }, [criterio, desde, descendente, hasta, monitoreo.instante, monitoreo.vehiculos, plan, rango]);

  const claseCampo =
    "rounded-md border border-slate-700 bg-[#11161d] px-2 py-1.5 text-xs text-slate-200";

  return (
    <Card className="border-slate-800 bg-[#151b23] p-4">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Route className="size-4 text-sky-400" />
          <h3 className="font-semibold">Unidades por distancia</h3>
        </div>
        <span className="text-[11px] text-slate-500">{filas.length}</span>
      </div>

      <div className="mb-3 grid grid-cols-[minmax(0,1fr)_64px_64px_36px] gap-2">
        <select
          value={criterio}
          onChange={(event) => setCriterio(event.target.value as Criterio)}
          aria-label="Criterio de distancia"
          className={claseCampo}
        >
          {CRITERIOS.map((item) => (
            <option key={item.valor} value={item.valor}>{item.etiqueta}</option>
          ))}
        </select>
        <input
          type="number"
          min={0}
          value={desde}
          onChange={(event) => setDesde(event.target.value)}
          placeholder="Desde"
          aria-label={`Desde (${unidad})`}
          className={claseCampo}
        />
        <input
          type="number"
          min={0}
          value={hasta}
          onChange={(event) => setHasta(event.target.value)}
          placeholder="Hasta"
          aria-label={`Hasta (${unidad})`}
          className={claseCampo}
        />
        <button
          type="button"
          onClick={() => setDescendente((actual) => !actual)}
          aria-label={descendente ? "Orden descendente" : "Orden ascendente"}
          className={claseCampo}
        >
          {descendente ? "↓" : "↑"}
        </button>
      </div>

      <select
        value={rango}
        onChange={(event) => setRango(event.target.value)}
        aria-label="Filtrar por distancia al destino"
        className={`${claseCampo} mb-3 w-full`}
      >
        <option value="">Distancia al destino: todas</option>
        {RANGOS_KM.map((item, indice) => (
          <option key={item.etiqueta} value={indice}>{item.etiqueta}</option>
        ))}
      </select>

      <ul className="max-h-[300px] space-y-1 overflow-y-auto pr-1">
        {filas.length === 0 && (
          <li className="py-3 text-center text-xs text-slate-500">
            No hay unidades en ruta dentro de ese rango.
          </li>
        )}
        {filas.map(({ id, distancias }) => (
          <li key={id}>
            <button
              type="button"
              onClick={() => onSeleccionar({ tipo: "VEHICULO", id })}
              className={[
                "w-full rounded-md border p-2 text-left transition",
                seleccionId === id
                  ? "border-orange-500 bg-orange-950/25 ring-1 ring-orange-500/30"
                  : "border-slate-800 bg-[#11161d] hover:border-orange-500/60",
              ].join(" ")}
            >
              <p className="font-mono text-xs font-semibold text-white">{id}</p>
              <div className="mt-1 grid grid-cols-2 gap-x-2 text-[10px] text-slate-400">
                <span>Destino: {distancias.kmAlDestino.toFixed(1)} km · {distancias.minutosAlDestino.toFixed(0)} min</span>
                <span className="text-right">Origen: {distancias.kmDesdeOrigen.toFixed(1)} km · {distancias.minutosDesdeOrigen.toFixed(0)} min</span>
                <span>Restante de ruta: {distancias.kmRestantes.toFixed(1)} km</span>
                <span className="text-right">
                  {distancias.fase === "LLEGANDO" ? "Llega" : "Sale"} {formatearHora(distancias.horaFase)}
                </span>
              </div>
            </button>
          </li>
        ))}
      </ul>
    </Card>
  );
}
