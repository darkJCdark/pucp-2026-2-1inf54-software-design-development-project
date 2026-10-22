import { Bike, Car, Gauge } from "lucide-react";

import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";

import { useConfiguracion } from "@/context/ConfiguracionContext";
import { CampoConfiguracion } from "./CampoConfiguracion";

export function FlotaConfiguracion() {
  const {
    configuracion,
    actualizarConfiguracion,
  } = useConfiguracion();

  return (
    <Card className="border-slate-800 bg-[#151b23] p-4">
      <div className="mb-5">
        <h2 className="font-semibold">
          Flota de transporte
        </h2>
        <p className="mt-1 text-xs text-slate-500">
          La cantidad se obtiene de la base de datos. La velocidad se guarda en el backend al iniciar.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-3">
        <ColumnaFlota
          icon={<Car className="size-4" />}
          titulo="Autos"
          cantidad={configuracion.autosCantidad}
          velocidad={configuracion.autosVelocidad}
          onVelocidad={(velocidad) =>
            actualizarConfiguracion({
              autosVelocidad: velocidad,
            })
          }
        />

        <ColumnaFlota
          icon={<Gauge className="size-4" />}
          titulo="Motos"
          cantidad={configuracion.motosCantidad}
          velocidad={configuracion.motosVelocidad}
          onVelocidad={(velocidad) =>
            actualizarConfiguracion({
              motosVelocidad: velocidad,
            })
          }
        />

        <ColumnaFlota
          icon={<Bike className="size-4" />}
          titulo="Bicicletas"
          cantidad={configuracion.bicicletasCantidad}
          velocidad={configuracion.bicicletasVelocidad}
          onVelocidad={(velocidad) =>
            actualizarConfiguracion({
              bicicletasVelocidad: velocidad,
            })
          }
        />
      </div>
    </Card>
  );
}

interface ColumnaFlotaProps {
  icon: React.ReactNode;
  titulo: string;
  cantidad: number;
  velocidad: number;
  onVelocidad: (velocidad: number) => void;
}

function ColumnaFlota({
  icon,
  titulo,
  cantidad,
  velocidad,
  onVelocidad,
}: ColumnaFlotaProps) {
  return (
    <div>
      <div className="mb-4 flex items-center gap-2 border-b border-slate-700 pb-2 text-xs font-semibold uppercase text-orange-500">
        {icon}
        {titulo}
      </div>

      <CampoConfiguracion label="Cantidad de unidades">
        <Input
          type="number"
          value={cantidad}
          disabled
          title="La cantidad de unidades proviene de la base de datos"
        />
      </CampoConfiguracion>

      <div className="mt-4">
        <CampoConfiguracion label="Velocidad promedio (km/h)">
          <Input
            type="number"
            min="1"
            step="0.1"
            value={velocidad}
            onChange={(event) =>
              onVelocidad(
                Number(event.target.value),
              )
            }
          />
        </CampoConfiguracion>
      </div>
    </div>
  );
}
