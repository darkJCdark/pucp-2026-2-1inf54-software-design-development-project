import { Boxes, CalendarClock, FileText, Route, Settings2 } from "lucide-react";

import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import type { ConfiguracionPlanificador } from "./configuracion.types";

interface ConfirmarInicioModalProps {
  abierto: boolean;
  enParalelo?: boolean;
  configuracion: ConfiguracionPlanificador;
  archivosBloqueos: File[];
  confirmando: boolean;
  fuentePedidos: string;
  onConfirmar: () => void;
  onCancelar: () => void;
}

export function ConfirmarInicioModal({
  abierto,
  enParalelo = false,
  configuracion,
  archivosBloqueos,
  confirmando,
  fuentePedidos,
  onConfirmar,
  onCancelar,
}: ConfirmarInicioModalProps) {
  const esOperacion = configuracion.escenario === "DIA_A_DIA";
  return (
    <ConfirmDialog
      abierto={abierto}
      titulo={esOperacion ? "Iniciar operación Día a Día" : "Iniciar simulación"}
      descripcion={enParalelo ? "Se iniciará en paralelo, conservando la flota vigente y con controles independientes." : "Revisa los datos y la flota antes de iniciar."}
      textoConfirmar={esOperacion ? "Iniciar operación" : "Iniciar simulación"}
      confirmando={confirmando}
      onConfirmar={onConfirmar}
      onCancelar={onCancelar}
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <ResumenDato
          icono={<Route className="size-4" />}
          label="Modalidad"
          value={nombreEscenario(configuracion.escenario)}
        />
        <ResumenDato
          icono={<CalendarClock className="size-4" />}
          label="Inicio"
          value={
            configuracion.escenario === "DIA_A_DIA"
              ? "Hora actual de Lima"
              : configuracion.inicioSimulado
          }
        />
        <ResumenDato
          icono={<FileText className="size-4" />}
          label="Pedidos"
          value={fuentePedidos}
        />
        <ResumenDato
          icono={<FileText className="size-4" />}
          label="Bloqueos"
          value={archivosBloqueos.map((archivo) => archivo.name).join(" · ") || "Sin bloqueos"}
        />
        <ResumenDato
          icono={<Boxes className="size-4" />}
          label="Flota"
          value={`${configuracion.autosCantidad} autos · ${configuracion.motosCantidad} motos · ${configuracion.bicicletasCantidad} bicicletas`}
        />
        {configuracion.usarConfiguracionSa && (
          <ResumenDato
            icono={<Settings2 className="size-4" />}
            label="Configuración avanzada"
            value={`Semilla ${configuracion.saSemilla || "aleatoria"} · T0 ${configuracion.saTemperaturaInicial}`}
          />
        )}
      </div>

      <div className="mt-4 rounded-lg border border-slate-700 bg-slate-800/40 p-3 text-xs leading-5 text-slate-300">
        {esOperacion ? "Día a Día puede funcionar junto con una simulación 5D o de colapso." : "Puedes mantener una operación Día a Día mientras esta simulación está activa."}
        {enParalelo && " Pausar o detener una no detiene la otra."}
      </div>
      {enParalelo && archivosBloqueos.length > 0 && <p className="mt-3 text-xs leading-5 text-slate-400">Los bloqueos se actualizan por mes. Si la otra ejecución utiliza un mes que vuelves a cargar, también recibirá el contenido actualizado.</p>}
    </ConfirmDialog>
  );
}

function ResumenDato({
  icono,
  label,
  value,
}: {
  icono: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="rounded-lg border border-slate-800 bg-[#151b23] p-3">
      <div className="flex items-center gap-2 text-[10px] font-semibold uppercase text-slate-500">
        {icono}
        {label}
      </div>
      <p className="mt-2 text-sm text-slate-200">{value || "—"}</p>
    </div>
  );
}

function nombreEscenario(escenario: ConfiguracionPlanificador["escenario"]) {
  switch (escenario) {
    case "DIA_A_DIA":
      return "Operación Día a Día";
    case "SIMULACION_5D":
      return "Simulación de periodo (5D)";
    case "COLAPSO":
      return "Simulación de colapso logístico";
  }
}
