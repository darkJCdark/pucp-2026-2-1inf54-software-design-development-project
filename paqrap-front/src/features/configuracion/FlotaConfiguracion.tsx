import { Bike, Car, Gauge, Minus, Plus } from "lucide-react";

import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useConfiguracion } from "@/context/ConfiguracionContext";

import { CampoConfiguracion } from "./CampoConfiguracion";

export function FlotaConfiguracion() {
  const { configuracion, actualizarConfiguracion } = useConfiguracion();

  return (
    <Card className="border-slate-800 bg-[#151b23] p-4">
      <div className="mb-5">
        <h2 className="font-semibold">Flota de transporte</h2>
        <p className="mt-1 text-xs leading-5 text-slate-500">
          La cantidad y la velocidad se aplican antes de crear el escenario. La baja de una unidad puede ser rechazada si tiene historial o existe una ejecución activa.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-3">
        <ColumnaFlota
          icon={<Car className="size-4" />}
          titulo="Autos"
          cantidad={configuracion.autosCantidad}
          velocidad={configuracion.autosVelocidad}
          onCantidad={(cantidad) => actualizarConfiguracion({ autosCantidad: cantidad })}
          onVelocidad={(velocidad) => actualizarConfiguracion({ autosVelocidad: velocidad })}
        />

        <ColumnaFlota
          icon={<Gauge className="size-4" />}
          titulo="Motos"
          cantidad={configuracion.motosCantidad}
          velocidad={configuracion.motosVelocidad}
          onCantidad={(cantidad) => actualizarConfiguracion({ motosCantidad: cantidad })}
          onVelocidad={(velocidad) => actualizarConfiguracion({ motosVelocidad: velocidad })}
        />

        <ColumnaFlota
          icon={<Bike className="size-4" />}
          titulo="Bicicletas"
          cantidad={configuracion.bicicletasCantidad}
          velocidad={configuracion.bicicletasVelocidad}
          onCantidad={(cantidad) =>
            actualizarConfiguracion({ bicicletasCantidad: cantidad })
          }
          onVelocidad={(velocidad) =>
            actualizarConfiguracion({ bicicletasVelocidad: velocidad })
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
  function cambiarCantidad(delta: number) {
    onCantidad(Math.min(99, Math.max(1, cantidad + delta)));
  }

  return (
    <div>
      <div className="mb-4 flex items-center gap-2 border-b border-slate-700 pb-2 text-xs font-semibold uppercase text-orange-500">
        {icon}
        {titulo}
      </div>

      <CampoConfiguracion label="Cantidad de unidades">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => cambiarCantidad(-1)}
            className="flex size-10 items-center justify-center rounded-md border border-slate-700 text-slate-300 hover:bg-slate-800"
            aria-label={`Reducir ${titulo}`}
          >
            <Minus className="size-4" />
          </button>

          <Input
            type="number"
            min="1"
            max="99"
            value={cantidad}
            onChange={(event) =>
              onCantidad(Math.min(99, Math.max(1, Number(event.target.value) || 1)))
            }
            className="text-center"
          />

          <button
            type="button"
            onClick={() => cambiarCantidad(1)}
            className="flex size-10 items-center justify-center rounded-md border border-slate-700 text-slate-300 hover:bg-slate-800"
            aria-label={`Aumentar ${titulo}`}
          >
            <Plus className="size-4" />
          </button>
        </div>
      </CampoConfiguracion>

      <div className="mt-4">
        <CampoConfiguracion label="Velocidad promedio (km/h)">
          <Input
            type="number"
            min="0.1"
            step="0.1"
            value={velocidad}
            onChange={(event) => onVelocidad(Number(event.target.value))}
          />
        </CampoConfiguracion>
      </div>
    </div>
  );
}
