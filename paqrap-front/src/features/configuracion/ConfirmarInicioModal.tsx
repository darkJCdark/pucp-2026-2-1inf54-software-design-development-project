import { BrainCircuit, Boxes, CalendarClock, FileText, Route } from "lucide-react";

import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import type { ConfiguracionPlanificador } from "./configuracion.types";

interface ConfirmarInicioModalProps {
  abierto: boolean;
  configuracion: ConfiguracionPlanificador;
  archivoPedidos: File | null;
  archivoBloqueos: File | null;
  confirmando: boolean;
  onConfirmar: () => void;
  onCancelar: () => void;
}

export function ConfirmarInicioModal({
  abierto,
  configuracion,
  archivoPedidos,
  archivoBloqueos,
  confirmando,
  onConfirmar,
  onCancelar,
}: ConfirmarInicioModalProps) {
  return (
    <ConfirmDialog
      abierto={abierto}
      titulo="Confirmar inicio de simulación"
      descripcion="Revisa los datos antes de cargar archivos, configurar la flota y crear el escenario."
      textoConfirmar="Cargar e iniciar"
      confirmando={confirmando}
      onConfirmar={onConfirmar}
      onCancelar={onCancelar}
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <ResumenDato
          icono={<Route className="size-4" />}
          label="Escenario"
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
          label="Archivo de pedidos"
          value={archivoPedidos?.name ?? "Registro en línea"}
        />
        <ResumenDato
          icono={<FileText className="size-4" />}
          label="Archivo de bloqueos"
          value={archivoBloqueos?.name ?? "Sin archivo"}
        />
        <ResumenDato
          icono={<Boxes className="size-4" />}
          label="Flota"
          value={`${configuracion.autosCantidad} autos · ${configuracion.motosCantidad} motos · ${configuracion.bicicletasCantidad} bicicletas`}
        />
        <ResumenDato
          icono={<BrainCircuit className="size-4" />}
          label="Simulated Annealing"
          value={
            configuracion.usarConfiguracionSa
              ? `Semilla ${configuracion.saSemilla || "aleatoria"} · T0 ${configuracion.saTemperaturaInicial}`
              : "Parámetros por defecto"
          }
        />
      </div>

      <div className="mt-4 rounded-md border border-amber-800/60 bg-amber-950/25 p-3 text-xs leading-5 text-amber-200">
        Solo puede existir una simulación activa. Si se redujo la cantidad de unidades, el backend puede rechazar la baja de vehículos con historial.
      </div>
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
