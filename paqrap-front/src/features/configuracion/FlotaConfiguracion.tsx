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
      <h2 className="mb-5 font-semibold">
        Flota de transporte
      </h2>

      <div className="grid gap-6 md:grid-cols-3">
        <ColumnaFlota
          icon={<Car className="size-4" />}
          titulo="Autos"
          cantidad={configuracion.autosCantidad}
          velocidad={configuracion.autosVelocidad}
          onCantidad={(cantidad) =>
            actualizarConfiguracion({
              autosCantidad: cantidad,
            })
          }
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
          onCantidad={(cantidad) =>
            actualizarConfiguracion({
              motosCantidad: cantidad,
            })
          }
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
          velocidad={
            configuracion.bicicletasVelocidad
          }
          onCantidad={(cantidad) =>
            actualizarConfiguracion({
              bicicletasCantidad: cantidad,
            })
          }
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
  onCantidad: (cantidad: number) => void;
  onVelocidad: (velocidad: number) => void;
}

function ColumnaFlota({
  icon,
  titulo,
  cantidad,
  velocidad,
  onCantidad,
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
          min="1"
          value={cantidad}
          onChange={(event) =>
            onCantidad(Number(event.target.value))
          }
        />
      </CampoConfiguracion>

      <div className="mt-4">
        <CampoConfiguracion label="Velocidad promedio (km/h)">
          <Input
            type="number"
            min="1"
            value={velocidad}
            onChange={(event) =>
              onVelocidad(Number(event.target.value))
            }
          />
        </CampoConfiguracion>
      </div>
    </div>
  );
}