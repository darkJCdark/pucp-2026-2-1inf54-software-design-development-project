import {
  BrainCircuit,
  CalendarClock,
  Clock3,
  Dices,
  SlidersHorizontal,
} from "lucide-react";

import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { Switch } from "@/components/ui/switch";
import { useConfiguracion } from "@/context/ConfiguracionContext";

import { CampoConfiguracion } from "./CampoConfiguracion";
import type { Escenario } from "./configuracion.types";

export function ParametrosOperacion() {
  const { configuracion, actualizarConfiguracion } = useConfiguracion();

  const necesitaInicio = configuracion.escenario !== "DIA_A_DIA";

  function cambiarEscenario(escenario: Escenario) {
    actualizarConfiguracion({ escenario });
  }

  return (
    <Card className="border-slate-800 bg-[#151b23] p-4">
      <h2 className="mb-5 font-semibold text-slate-100">
        Parámetros de la operación
      </h2>

      <p className="mb-2 text-xs font-semibold uppercase text-slate-400">
        Escenario de operación
      </p>

      <div className="mb-5 grid grid-cols-3 overflow-hidden rounded-md border border-slate-700">
        <BotonEscenario
          label="Operación día a día"
          active={configuracion.escenario === "DIA_A_DIA"}
          onClick={() => cambiarEscenario("DIA_A_DIA")}
        />
        <BotonEscenario
          label="Simulación de periodo (5D)"
          active={configuracion.escenario === "SIMULACION_5D"}
          onClick={() => cambiarEscenario("SIMULACION_5D")}
        />
        <BotonEscenario
          label="Simulación de colapso logístico"
          active={configuracion.escenario === "COLAPSO"}
          onClick={() => cambiarEscenario("COLAPSO")}
        />
      </div>

      <Separator className="mb-5 bg-slate-800" />

      <div className="grid gap-4 md:grid-cols-2">
        <CampoConfiguracion label="Duración / condición de término">
          <div className="relative">
            <Clock3 className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-500" />
            <Input
              disabled
              value={obtenerDuracion(configuracion.escenario)}
              className="bg-slate-800/70 pl-9"
            />
          </div>
        </CampoConfiguracion>

        <CampoConfiguracion label="Planificador">
          <div className="flex h-10 items-center gap-2 rounded-md border border-slate-700 bg-slate-800/70 px-3 text-sm text-slate-200">
            <BrainCircuit className="size-4 text-sky-400" />
            Simulated Annealing
          </div>
          <p className="mt-1 text-[11px] text-slate-500">
            SA es el único motor integrado en la arquitectura objetivo.
          </p>
        </CampoConfiguracion>

        {necesitaInicio && (
          <CampoConfiguracion label="Inicio de la simulación">
            <div className="relative">
              <CalendarClock className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-500" />
              <input
                type="datetime-local"
                value={configuracion.inicioSimulado}
                onChange={(event) =>
                  actualizarConfiguracion({ inicioSimulado: event.target.value })
                }
                className="h-10 w-full rounded-md border border-slate-700 bg-slate-800/70 pl-9 pr-3 text-sm text-slate-200 outline-none focus:border-orange-500"
              />
            </div>
            <p className="mt-1 text-[11px] text-slate-500">
              Debe corresponder al mes del archivo de ventas.
            </p>
          </CampoConfiguracion>
        )}
      </div>

      <Separator className="my-5 bg-slate-800" />

      <div className="flex items-start justify-between gap-4 rounded-lg border border-slate-700 bg-[#11161d] p-4">
        <div className="flex gap-3">
          <SlidersHorizontal className="mt-0.5 size-5 text-orange-400" />
          <div>
            <p className="text-sm font-semibold text-slate-100">
              Configuración avanzada de SA
            </p>
            <p className="mt-1 text-xs leading-5 text-slate-500">
              Permite repetir una corrida con la misma semilla y ajustar los parámetros del algoritmo.
            </p>
          </div>
        </div>

        <Switch
          checked={configuracion.usarConfiguracionSa}
          onCheckedChange={(checked) =>
            actualizarConfiguracion({ usarConfiguracionSa: checked })
          }
          aria-label="Activar configuración avanzada de SA"
        />
      </div>

      {configuracion.usarConfiguracionSa && (
        <div className="mt-4 grid gap-4 rounded-lg border border-slate-800 bg-[#11161d] p-4 md:grid-cols-2 xl:grid-cols-5">
          <CampoConfiguracion label="Semilla">
            <div className="relative">
              <Dices className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-500" />
              <Input
                type="number"
                value={configuracion.saSemilla}
                placeholder="Aleatoria"
                onChange={(event) =>
                  actualizarConfiguracion({ saSemilla: event.target.value })
                }
                className="pl-9"
              />
            </div>
          </CampoConfiguracion>

          <CampoConfiguracion label="Temperatura inicial">
            <Input
              type="number"
              min="0.000001"
              step="1"
              value={configuracion.saTemperaturaInicial}
              onChange={(event) =>
                actualizarConfiguracion({
                  saTemperaturaInicial: Number(event.target.value),
                })
              }
            />
          </CampoConfiguracion>

          <CampoConfiguracion label="Temperatura mínima">
            <Input
              type="number"
              min="0.000001"
              step="0.1"
              value={configuracion.saTemperaturaMinima}
              onChange={(event) =>
                actualizarConfiguracion({
                  saTemperaturaMinima: Number(event.target.value),
                })
              }
            />
          </CampoConfiguracion>

          <CampoConfiguracion label="Factor de enfriamiento">
            <Input
              type="number"
              min="0.8"
              max="0.99"
              step="0.01"
              value={configuracion.saFactorEnfriamiento}
              onChange={(event) =>
                actualizarConfiguracion({
                  saFactorEnfriamiento: Number(event.target.value),
                })
              }
            />
          </CampoConfiguracion>

          <CampoConfiguracion label="Iteraciones / temperatura">
            <Input
              type="number"
              min="1"
              step="1"
              value={configuracion.saIteracionesPorTemperatura}
              onChange={(event) =>
                actualizarConfiguracion({
                  saIteracionesPorTemperatura: Number(event.target.value),
                })
              }
            />
          </CampoConfiguracion>

          <p className="md:col-span-2 xl:col-span-5 text-[11px] text-slate-500">
            Valores por defecto del backend: T0=1000, Tmin=1, factor=0.95 e iteraciones=50. Tmin debe ser menor que T0 y el factor debe estar entre 0.80 y 0.99.
          </p>
        </div>
      )}
    </Card>
  );
}

interface BotonEscenarioProps {
  label: string;
  active: boolean;
  onClick: () => void;
}

function BotonEscenario({ label, active, onClick }: BotonEscenarioProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={[
        "min-h-16 px-3 py-2 text-xs font-medium transition-colors",
        active
          ? "bg-orange-500 text-white"
          : "bg-[#11161d] text-slate-400 hover:bg-slate-800 hover:text-white",
      ].join(" ")}
    >
      {label}
    </button>
  );
}

function obtenerDuracion(escenario: Escenario) {
  switch (escenario) {
    case "DIA_A_DIA":
      return "Hasta que el operador la detenga";
    case "SIMULACION_5D":
      return "5 días simulados";
    case "COLAPSO":
      return "Hasta detectar colapso logístico";
  }
}
