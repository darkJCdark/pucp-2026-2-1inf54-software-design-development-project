import { VentanaFlotante } from "@/components/ui/VentanaFlotante";

import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Coffee,
  PackagePlus,
  RefreshCcw,
  Route,
} from "lucide-react";

import type {
  EventoEjecucion,
  TipoEventoEjecucion,
} from "@/hooks/useEventosEjecucion";

/*
 * Actividad reciente: ítem del acordeón de la columna izquierda
 * (antes flotaba sobre el mapa). Usa VentanaFlotante para tener
 * la misma cabecera que las demás ventanas.
 */
export function FeedEventos({
  eventos,
  minimizada,
  onCambiarMinimizada,
}: {
  eventos: EventoEjecucion[];
  minimizada?: boolean;
  onCambiarMinimizada?: (minimizada: boolean) => void;
}) {
  return (
    <VentanaFlotante
      titulo="Actividad reciente"
      icono={<Activity className="size-4 text-sky-400" />}
      resumen={
        eventos.length === 0
          ? "Sin eventos recientes · en vivo"
          : `${eventos.length} evento${eventos.length === 1 ? "" : "s"} en memoria · en vivo`
      }
      minimizada={minimizada}
      onCambiarMinimizada={onCambiarMinimizada}
      inicialmenteMinimizada
      className="w-full shrink-0"
    >
          {eventos.length === 0 ? (
            <p className="px-3 py-3 text-[11px] text-slate-500">
              Las entregas, salidas y
              replanificaciones aparecerán aquí.
            </p>
          ) : (
            eventos.map(
              (evento) => (
                <div
                  key={evento.id}
                  className="
                    flex
                    gap-2.5
                    border-b
                    border-slate-800/80
                    px-3
                    py-2
                    last:border-b-0
                  "
                >
                  <div className="mt-0.5">
                    <IconoEvento
                      tipo={
                        evento.tipo
                      }
                    />
                  </div>

                  <div className="min-w-0 flex-1">
                    <div className="flex items-start justify-between gap-2">
                      <p className="truncate text-[11px] font-medium text-slate-200">
                        {
                          evento.titulo
                        }
                      </p>

                      <span className="shrink-0 text-[9px] tabular-nums text-slate-500">
                        {formatearHora(
                          evento.instante,
                        )}
                      </span>
                    </div>

                    {evento.detalle && (
                      <p className="mt-0.5 truncate text-[9px] text-slate-500">
                        {
                          evento.detalle
                        }
                      </p>
                    )}
                  </div>
                </div>
              ),
            )
          )}
    </VentanaFlotante>
  );
}

function IconoEvento({
  tipo,
}: {
  tipo: TipoEventoEjecucion;
}) {
  const clases =
    "size-3.5";

  switch (tipo) {
    case "PEDIDO":
      return (
        <PackagePlus
          className={`${clases} text-violet-400`}
        />
      );

    case "ENTREGA":
      return (
        <CheckCircle2
          className={`${clases} text-green-400`}
        />
      );

    case "RUTA":
      return (
        <Route
          className={`${clases} text-sky-400`}
        />
      );

    case "DETENCION":
      return (
        <Coffee
          className={`${clases} text-amber-300`}
        />
      );

    case "REPLANIFICACION":
      return (
        <RefreshCcw
          className={`${clases} text-orange-400`}
        />
      );

    case "AVERIA":
    case "BLOQUEO":
      return (
        <AlertTriangle
          className={`${clases} text-red-400`}
        />
      );
  }
}

function formatearHora(
  valor: string,
) {
  return new Intl.DateTimeFormat(
    "es-PE",
    {
      timeZone:
        "America/Lima",

      hour:
        "2-digit",

      minute:
        "2-digit",

      second:
        "2-digit",

      hour12:
        false,
    },
  ).format(
    new Date(valor),
  );
}