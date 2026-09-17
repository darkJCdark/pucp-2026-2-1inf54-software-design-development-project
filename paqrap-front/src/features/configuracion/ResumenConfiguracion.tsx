import { Card } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";

import { useConfiguracion } from "@/context/ConfiguracionContext";

export function ResumenConfiguracion() {
  const {
    configuracion,
    archivoPedidos,
  } = useConfiguracion();

  return (
    <Card className="h-fit border-slate-800 bg-[#151b23] p-4">
      <h2 className="mb-5 font-semibold">
        Resumen de configuración
      </h2>

      <Fila
        label="Escenario"
        value={configuracion.escenario}
      />

      <Fila
        label="Archivo"
        value={archivoPedidos?.name ?? "—"}
      />

      <Fila
        label="Algoritmo"
        value={
          configuracion.compararAlgoritmos
            ? "GRASP + SA"
            : configuracion.algoritmo
        }
      />

      <Fila
        label="Semilla"
        value={String(configuracion.semilla)}
      />

      <Separator className="my-4 bg-slate-800" />

      <p className="mb-2 text-xs uppercase text-slate-500">
        Flota
      </p>

      <Fila
        label="Autos"
        value={`${configuracion.autosCantidad} ud · ${configuracion.autosVelocidad} km/h`}
      />

      <Fila
        label="Motos"
        value={`${configuracion.motosCantidad} ud · ${configuracion.motosVelocidad} km/h`}
      />

      <Fila
        label="Bicicletas"
        value={`${configuracion.bicicletasCantidad} ud · ${configuracion.bicicletasVelocidad} km/h`}
      />
    </Card>
  );
}

interface FilaProps {
  label: string;
  value: string;
}

function Fila({
  label,
  value,
}: FilaProps) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-slate-800 py-2 text-xs">
      <span className="uppercase text-slate-500">
        {label}
      </span>

      <span className="text-right font-medium text-slate-200">
        {value}
      </span>
    </div>
  );
}