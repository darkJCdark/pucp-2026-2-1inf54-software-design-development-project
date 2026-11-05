import { useEffect, useState } from "react";
import { CalendarClock, Clock3, Timer, Watch } from "lucide-react";

import type { EscenarioResponseDto } from "@/api/escenarios.dto";
import { useRelojSimuladoAnimado } from "@/hooks/useRelojSimuladoAnimado";

interface BloqueTiemposProps {
  escenario: EscenarioResponseDto;
  instante: string;
}

export function BloqueTiempos({ escenario, instante }: BloqueTiemposProps) {
  const instanteSimuladoMs = useRelojSimuladoAnimado(
    instante,
    escenario.factorAceleracion,
    escenario.estado,
  );
  const [ahoraRealMs, setAhoraRealMs] = useState(() => Date.now());

  useEffect(() => {
    const intervalo = window.setInterval(() => setAhoraRealMs(Date.now()), 1000);
    return () => window.clearInterval(intervalo);
  }, []);

  const finRealMs = fechaMs(escenario.finReal);
  const referenciaReal = Number.isFinite(finRealMs) ? finRealMs : ahoraRealMs;
  const inicioRealMs = fechaMs(escenario.inicioReal);
  const inicioSimuladoMs = fechaMs(escenario.inicioSimulado);

  const transcurridoReal = Number.isFinite(inicioRealMs)
    ? Math.max(0, referenciaReal - inicioRealMs)
    : 0;
  const transcurridoSimulado = Number.isFinite(inicioSimuladoMs)
    ? Math.max(0, instanteSimuladoMs - inicioSimuladoMs)
    : 0;

  return (
    <div className="grid grid-cols-2 gap-2">
      <DatoTiempo
        icono={<CalendarClock className="size-3.5" />}
        label="Momento simulado"
        valor={formatearFechaHora(instanteSimuladoMs)}
      />
      <DatoTiempo
        icono={<Timer className="size-3.5" />}
        label="Transcurrido simulado"
        valor={formatearDuracion(transcurridoSimulado)}
      />
      <DatoTiempo
        icono={<Clock3 className="size-3.5" />}
        label="Momento real"
        valor={formatearFechaHora(referenciaReal)}
      />
      <DatoTiempo
        icono={<Watch className="size-3.5" />}
        label="Transcurrido real"
        valor={formatearDuracion(transcurridoReal)}
      />
    </div>
  );
}

function DatoTiempo({
  icono,
  label,
  valor,
}: {
  icono: React.ReactNode;
  label: string;
  valor: string;
}) {
  return (
    <div className="rounded-md bg-slate-800/70 px-2.5 py-2">
      <div className="flex items-center gap-1.5 text-[9px] font-semibold uppercase text-slate-500">
        {icono}
        {label}
      </div>
      <p className="mt-1 whitespace-nowrap text-xs font-semibold text-slate-100">{valor}</p>
    </div>
  );
}

function fechaMs(valor: string | null | undefined) {
  if (!valor) return Number.NaN;
  return new Date(valor).getTime();
}

function formatearFechaHora(valorMs: number) {
  if (!Number.isFinite(valorMs)) return "—";
  return new Intl.DateTimeFormat("es-PE", {
    timeZone: "America/Lima",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(new Date(valorMs));
}

function formatearDuracion(valorMs: number) {
  const totalSegundos = Math.max(0, Math.floor(valorMs / 1000));
  const dias = Math.floor(totalSegundos / 86_400);
  const horas = Math.floor((totalSegundos % 86_400) / 3600);
  const minutos = Math.floor((totalSegundos % 3600) / 60);
  const segundos = totalSegundos % 60;

  return [
    dias > 0 ? `${dias}d` : null,
    `${String(horas).padStart(2, "0")}h`,
    `${String(minutos).padStart(2, "0")}m`,
    `${String(segundos).padStart(2, "0")}s`,
  ]
    .filter(Boolean)
    .join(" ");
}
