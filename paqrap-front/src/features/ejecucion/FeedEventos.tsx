import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  PackagePlus,
  RefreshCcw,
  Route,
  Coffee,
} from "lucide-react";

import type {
  EventoEjecucion,
  TipoEventoEjecucion,
} from "@/hooks/useEventosEjecucion";

export function FeedEventos({
  eventos,
}: {
  eventos:
    EventoEjecucion[];
}) {
  return (
    <div className="absolute bottom-5 left-[450px] z-40 hidden w-[370px] overflow-hidden rounded-lg border border-slate-700 bg-[#11161d]/95 text-white shadow-xl xl:block">
      <div className="flex items-center justify-between border-b border-slate-800 px-4 py-3">

        <div className="flex items-center gap-2">
          <Activity className="size-4 text-sky-400" />

          <p className="text-sm font-semibold">
            Actividad reciente
          </p>
        </div>

        <span className="text-[10px] uppercase text-slate-500">
          en vivo
        </span>
      </div>

      <div className="max-h-52 overflow-y-auto">

        {eventos.length ===
        0 ? (
          <p className="px-4 py-4 text-xs text-slate-500">
            Las entregas,
            salidas y
            replanificaciones
            aparecerán aquí.
          </p>
        ) : (
          eventos.map(
            (evento) => (
              <div
                key={evento.id}
                className="flex gap-3 border-b border-slate-800/80 px-4 py-2.5 last:border-b-0"
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
                    <p className="truncate text-xs font-medium text-slate-200">
                      {
                        evento.titulo
                      }
                    </p>

                    <span className="shrink-0 text-[10px] text-slate-500">
                      {formatearHora(
                        evento.instante,
                      )}
                    </span>
                  </div>

                  {evento.detalle && (
                    <p className="mt-0.5 truncate text-[10px] text-slate-500">
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
      </div>
    </div>
  );
}

function IconoEvento({
  tipo,
}: {
  tipo:
    TipoEventoEjecucion;
}) {
  const clases =
    "size-4";

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

      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",

      hour12: false,
    },
  ).format(
    new Date(valor),
  );
}