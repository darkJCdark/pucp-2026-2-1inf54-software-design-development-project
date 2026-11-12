import { useState, type ReactNode } from "react";
import { ChevronDown } from "lucide-react";

import { cn } from "@/lib/utils";

/*
 * Ventana flotante minimizable para los paneles que se dibujan
 * sobre el mapa de operación.
 *
 * - Cabecera uniforme: ícono, título y dato clave (resumen).
 * - Minimizada: solo se ve la cabecera.
 * - Abierta: el cuerpo ocupa como máximo 45% del alto de la pantalla,
 *   hace scroll por dentro y aparece con una transición de 150 ms.
 * - El estado vive en memoria (no se recuerda al recargar).
 * - Puede usarse controlada (minimizada + onCambiarMinimizada) o no.
 */
interface VentanaFlotanteProps {
  titulo: ReactNode;
  /** Ícono de la cabecera (lucide, size-4). */
  icono?: ReactNode;
  /** Dato clave visible en la cabecera (por ejemplo, "En ejecución"). */
  resumen?: ReactNode;
  /** Botones extra en la cabecera, fuera del botón de minimizar. */
  acciones?: ReactNode;
  minimizada?: boolean;
  onCambiarMinimizada?: (minimizada: boolean) => void;
  inicialmenteMinimizada?: boolean;
  className?: string;
  classNameCuerpo?: string;
  children: ReactNode;
}

export function VentanaFlotante({
  titulo,
  icono,
  resumen,
  acciones,
  minimizada: minimizadaControlada,
  onCambiarMinimizada,
  inicialmenteMinimizada = false,
  className,
  classNameCuerpo,
  children,
}: VentanaFlotanteProps) {
  const [minimizadaInterna, setMinimizadaInterna] =
    useState(inicialmenteMinimizada);

  const minimizada =
    minimizadaControlada ?? minimizadaInterna;

  function alternar() {
    const siguiente = !minimizada;

    if (minimizadaControlada === undefined) {
      setMinimizadaInterna(siguiente);
    }

    onCambiarMinimizada?.(siguiente);
  }

  return (
    <section
      className={cn(
        "overflow-hidden rounded-xl border border-slate-700 bg-[#11161d]/97 text-white shadow-2xl",
        className,
      )}
    >
      <div className="flex items-center gap-1">
        <button
          type="button"
          onClick={alternar}
          aria-expanded={!minimizada}
          title={minimizada ? "Abrir" : "Minimizar"}
          className="flex min-w-0 flex-1 items-center justify-between gap-3 px-4 py-3 text-left hover:bg-slate-800/60"
        >
          <span className="flex min-w-0 items-center gap-2">
            {icono && (
              <span className="shrink-0 text-orange-400">
                {icono}
              </span>
            )}

            <span className="min-w-0">
              <span className="block truncate text-sm font-semibold">
                {titulo}
              </span>

              {resumen && (
                <span className="block truncate text-[10px] text-slate-500">
                  {resumen}
                </span>
              )}
            </span>
          </span>

          <ChevronDown
            className={cn(
              "size-4 shrink-0 text-slate-500 transition-transform duration-150",
              !minimizada && "rotate-180",
            )}
          />
        </button>

        {acciones && (
          <div className="flex shrink-0 items-center gap-1 pr-2">
            {acciones}
          </div>
        )}
      </div>

      {!minimizada && (
        <div
          className={cn(
            "max-h-[45vh] overflow-y-auto border-t border-slate-800 animate-in fade-in slide-in-from-top-1 duration-150",
            classNameCuerpo,
          )}
        >
          {children}
        </div>
      )}
    </section>
  );
}
