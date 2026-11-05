import { useEffect, useState } from "react";
import { Clock3, Timer } from "lucide-react";

import type { EscenarioResponseDto } from "@/api/escenarios.dto";
import { useRelojSimuladoAnimado } from "@/hooks/useRelojSimuladoAnimado";

interface BloqueTiemposProps {
  escenario: EscenarioResponseDto;
  instante: string;
}

export function BloqueTiempos({
  escenario,
  instante,
}: BloqueTiemposProps) {
  const instanteSimuladoMs =
    useRelojSimuladoAnimado(
      instante,
      escenario.factorAceleracion,
      escenario.estado,
    );

  const [ahoraRealMs, setAhoraRealMs] =
    useState(() => Date.now());

  useEffect(() => {
    const intervalo =
      window.setInterval(() => {
        setAhoraRealMs(Date.now());
      }, 1000);

    return () =>
      window.clearInterval(intervalo);
  }, []);

  const finRealMs =
    fechaMs(escenario.finReal);

  const referenciaReal =
    Number.isFinite(finRealMs)
      ? finRealMs
      : ahoraRealMs;

  const inicioRealMs =
    fechaMs(escenario.inicioReal);

  const inicioSimuladoMs =
    fechaMs(escenario.inicioSimulado);

  const transcurridoReal =
    Number.isFinite(inicioRealMs)
      ? Math.max(
          0,
          referenciaReal - inicioRealMs,
        )
      : 0;

  const transcurridoSimulado =
    Number.isFinite(inicioSimuladoMs)
      ? Math.max(
          0,
          instanteSimuladoMs -
            inicioSimuladoMs,
        )
      : 0;

  return (
    <div className="overflow-hidden rounded-md border border-slate-800 bg-slate-900/45">
      <FilaTiempo
        icono={
          <Timer className="size-3.5 text-sky-400" />
        }
        label="Simulado"
        momento={formatearFechaHora(
          instanteSimuladoMs,
        )}
        transcurrido={formatearDuracionCompacta(
          transcurridoSimulado,
        )}
      />

      <div className="border-t border-slate-800" />

      <FilaTiempo
        icono={
          <Clock3 className="size-3.5 text-slate-400" />
        }
        label="Real"
        momento={formatearFechaHora(
          referenciaReal,
        )}
        transcurrido={formatearDuracionCompacta(
          transcurridoReal,
        )}
      />
    </div>
  );
}

function FilaTiempo({
  icono,
  label,
  momento,
  transcurrido,
}: {
  icono: React.ReactNode;
  label: string;
  momento: string;
  transcurrido: string;
}) {
  return (
    <div className="grid grid-cols-[72px_minmax(0,1fr)_auto] items-center gap-2 px-2.5 py-1.5">
      <div className="flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wide text-slate-500">
        {icono}
        <span>{label}</span>
      </div>

      <span className="truncate text-[11px] font-medium tabular-nums text-slate-200">
        {momento}
      </span>

      <span
        className="rounded bg-slate-800/90 px-1.5 py-0.5 text-[10px] font-semibold tabular-nums text-slate-300"
        title="Tiempo transcurrido"
      >
        +{transcurrido}
      </span>
    </div>
  );
}

function fechaMs(
  valor: string | null | undefined,
) {
  if (!valor) {
    return Number.NaN;
  }

  return new Date(valor).getTime();
}

function formatearFechaHora(
  valorMs: number,
) {
  if (!Number.isFinite(valorMs)) {
    return "—";
  }

  return new Intl.DateTimeFormat(
    "es-PE",
    {
      timeZone: "America/Lima",
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false,
    },
  ).format(new Date(valorMs));
}

function formatearDuracionCompacta(
  valorMs: number,
) {
  const totalSegundos = Math.max(
    0,
    Math.floor(valorMs / 1000),
  );

  const dias = Math.floor(
    totalSegundos / 86_400,
  );

  const horas = Math.floor(
    (totalSegundos % 86_400) / 3600,
  );

  const minutos = Math.floor(
    (totalSegundos % 3600) / 60,
  );

  const segundos =
    totalSegundos % 60;

  if (dias > 0) {
    return `${dias}d ${String(
      horas,
    ).padStart(2, "0")}h ${String(
      minutos,
    ).padStart(2, "0")}m`;
  }

  if (horas > 0) {
    return `${String(
      horas,
    ).padStart(2, "0")}h ${String(
      minutos,
    ).padStart(2, "0")}m`;
  }

  return `${String(
    minutos,
  ).padStart(2, "0")}m ${String(
    segundos,
  ).padStart(2, "0")}s`;
}