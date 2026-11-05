import { useState } from "react";

import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  ChevronDown,
  Coffee,
  PackagePlus,
  RefreshCcw,
  Route,
} from "lucide-react";

import type {
  EventoEjecucion,
  TipoEventoEjecucion,
} from "@/hooks/useEventosEjecucion";

export function FeedEventos({
  eventos,
}: {
  eventos: EventoEjecucion[];
}) {
  const [abierto, setAbierto] =
    useState(false);

  return (
    <div
      className="
        absolute
        bottom-[72px]
        left-[450px]
        z-40
        hidden
        w-[340px]
        overflow-hidden
        rounded-lg
        border
        border-slate-700
        bg-[#11161d]/95
        text-white
        shadow-xl
        xl:block
      "
    >
      <button
        type="button"
        onClick={() =>
          setAbierto(
            (actual) => !actual,
          )
        }
        className="
          flex
          w-full
          items-center
          justify-between
          gap-3
          px-3
          py-2.5
          text-left
          hover:bg-slate-800/55
        "
        aria-expanded={abierto}
      >
        <div className="flex min-w-0 items-center gap-2">
          <Activity className="size-4 shrink-0 text-sky-400" />

          <div className="min-w-0">
            <p className="text-xs font-semibold">
              Actividad reciente
            </p>

            <p className="truncate text-[10px] text-slate-500">
              {eventos.length === 0
                ? "Sin eventos recientes"
                : `${eventos.length} evento${
                    eventos.length === 1
                      ? ""
                      : "s"
                  } en memoria`}
            </p>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <span className="text-[9px] uppercase text-slate-500">
            en vivo
          </span>

          <ChevronDown
            className={[
              "size-4 text-slate-500 transition-transform",
              abierto
                ? "rotate-180"
                : "",
            ].join(" ")}
          />
        </div>
      </button>

      {abierto && (
        <div
          className="
            max-h-44
            overflow-y-auto
            border-t
            border-slate-800
          "
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
        </div>
      )}
    </div>
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