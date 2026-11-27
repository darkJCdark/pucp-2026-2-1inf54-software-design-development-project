import { useEffect, type ReactNode } from "react";
import { AlertTriangle, CheckCircle2, ChevronDown, Menu } from "lucide-react";

import { cn } from "@/lib/utils";

import {
  resumenAlertas,
  teclaCierraPanel,
} from "./barraEstadoMapa.logica";

export type TonoEstado =
  | "activo"
  | "pausado"
  | "inicial"
  | "terminado"
  | "error";

interface BarraEstadoMapaProps {
  /** Ejemplo: "Simulación #30". */
  titulo: string;
  /** Ejemplo: "En ejecución". */
  estadoTexto: string;
  estadoTono: TonoEstado;
  pendientes: number;
  vencidos: number;
  entregados: number;
  alertas: number;
  colapso: boolean;
  /** Franja de tiempos (siempre visible). */
  tiempos: ReactNode;
  abierto: boolean;
  onCambiarAbierto: (abierto: boolean) => void;
  /** Abre el panel directamente en las alertas. */
  onAbrirAlertas: () => void;
  /** Las tarjetas desplegables (acordeón). */
  children: ReactNode;
}

const COLOR_PUNTO: Record<TonoEstado, string> = {
  activo: "bg-green-500",
  pausado: "bg-amber-400",
  inicial: "bg-sky-400",
  terminado: "bg-slate-400",
  error: "bg-red-500",
};

/*
 * Barra superior izquierda del mapa.
 *
 * Fila 1: botón "Panel", escenario con su estado y aviso de alertas.
 * Fila 2: contadores con palabras completas y franja de tiempos.
 * Debajo: las tarjetas desplegables, encima del mapa. El mapa no se mueve.
 *
 * Las tarjetas se ocultan sin desmontarse para conservar filtros y
 * orden de las listas al plegar.
 */
export function BarraEstadoMapa({
  titulo,
  estadoTexto,
  estadoTono,
  pendientes,
  vencidos,
  entregados,
  alertas,
  colapso,
  tiempos,
  abierto,
  onCambiarAbierto,
  onAbrirAlertas,
  children,
}: BarraEstadoMapaProps) {
  useEffect(() => {
    if (!abierto) {
      return;
    }

    function alPulsar(evento: KeyboardEvent) {
      if (teclaCierraPanel(evento.key, abierto)) {
        onCambiarAbierto(false);
      }
    }

    document.addEventListener("keydown", alPulsar);

    return () => document.removeEventListener("keydown", alPulsar);
  }, [abierto, onCambiarAbierto]);

  const resumen = resumenAlertas(alertas, colapso);

  return (
    <div className="pointer-events-none absolute left-5 top-3 z-50 flex flex-col items-start gap-2">
      <div className="pointer-events-auto flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={() => onCambiarAbierto(!abierto)}
          aria-expanded={abierto}
          title={abierto ? "Cerrar el panel" : "Abrir el panel"}
          className={cn(
            "flex h-10 items-center gap-2 rounded-xl border bg-[#11161d]/97 px-3.5 text-sm font-semibold text-white shadow-2xl hover:bg-slate-800",
            abierto ? "border-orange-500" : "border-slate-700",
          )}
        >
          <Menu className="size-4 text-orange-400" />
          Panel
          <ChevronDown
            className={cn(
              "size-4 text-slate-400 transition-transform duration-150",
              abierto && "rotate-180",
            )}
          />
        </button>

        <div className="flex h-10 items-center gap-2 rounded-xl border border-slate-700 bg-[#11161d]/97 px-3.5 text-sm shadow-2xl">
          <span className={cn("size-2.5 rounded-full", COLOR_PUNTO[estadoTono])} />
          <span className="font-semibold text-white">{titulo}</span>
          <span className="text-slate-400">— {estadoTexto}</span>
        </div>

        <button
          type="button"
          onClick={onAbrirAlertas}
          title="Ver alertas"
          className={cn(
            "flex h-10 items-center gap-2 rounded-xl border bg-[#11161d]/97 px-3.5 text-sm font-semibold shadow-2xl hover:bg-slate-800",
            resumen.tono === "ok" && "border-slate-700 text-green-400",
            resumen.tono === "aviso" && "border-amber-500 text-amber-300",
            resumen.tono === "colapso" && "border-red-500 text-red-300",
          )}
        >
          {resumen.tono === "ok" ? (
            <CheckCircle2 className="size-4" />
          ) : (
            <AlertTriangle className="size-4" />
          )}
          {resumen.texto}
        </button>
      </div>

      <div className="pointer-events-auto flex flex-wrap items-stretch gap-2">
        <Contador
          valor={pendientes}
          etiqueta="Pendientes"
          ayuda="Pedidos pendientes de entrega"
        />
        <Contador
          valor={vencidos}
          etiqueta="Vencidos"
          ayuda="Pedidos vencidos sin entregar"
          alerta={vencidos > 0}
        />
        <Contador
          valor={entregados}
          etiqueta="Entregados"
          ayuda="Pedidos ya entregados"
        />
        {tiempos}
      </div>

      <div
        className={cn(
          "pointer-events-auto max-h-[calc(100vh-10rem)] w-[min(430px,calc(100vw-2.5rem))] overflow-y-auto pr-1",
          !abierto && "hidden",
        )}
      >
        {children}
      </div>
    </div>
  );
}

function Contador({
  valor,
  etiqueta,
  ayuda,
  alerta = false,
}: {
  valor: number;
  etiqueta: string;
  ayuda: string;
  alerta?: boolean;
}) {
  return (
    <div
      title={ayuda}
      className="flex items-baseline gap-1.5 rounded-xl border border-slate-700 bg-[#11161d]/97 px-3.5 py-1.5 shadow-2xl"
    >
      <span
        className={cn(
          "text-xl font-bold leading-none tabular-nums",
          alerta ? "text-red-400" : "text-white",
        )}
      >
        {valor}
      </span>
      <span className="text-xs text-slate-400">{etiqueta}</span>
    </div>
  );
}
