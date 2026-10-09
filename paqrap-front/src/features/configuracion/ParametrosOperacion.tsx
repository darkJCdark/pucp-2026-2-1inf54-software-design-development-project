import {
  BrainCircuit,
  CalendarClock,
  Clock3,
} from "lucide-react";

import {
  Card,
} from "@/components/ui/card";

import {
  Input,
} from "@/components/ui/input";

import {
  Separator,
} from "@/components/ui/separator";

import {
  useConfiguracion,
} from "@/context/ConfiguracionContext";

import type {
  Escenario,
} from "./configuracion.types";

import {
  CampoConfiguracion,
} from "./CampoConfiguracion";

export function ParametrosOperacion() {
  const {
    configuracion,
    actualizarConfiguracion,
  } = useConfiguracion();

  function cambiarEscenario(
    escenario: Escenario,
  ) {
    actualizarConfiguracion({
      escenario,
    });
  }

  const necesitaInicio =
    configuracion.escenario !==
    "DIA_A_DIA";

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
          active={
            configuracion.escenario ===
            "DIA_A_DIA"
          }
          onClick={() =>
            cambiarEscenario(
              "DIA_A_DIA",
            )
          }
        />

        <BotonEscenario
          label="Simulación de periodo (5D)"
          active={
            configuracion.escenario ===
            "SIMULACION_5D"
          }
          onClick={() =>
            cambiarEscenario(
              "SIMULACION_5D",
            )
          }
        />

        <BotonEscenario
          label="Simulación de colapso logístico"
          active={
            configuracion.escenario ===
            "COLAPSO"
          }
          onClick={() =>
            cambiarEscenario(
              "COLAPSO",
            )
          }
        />
      </div>

      <Separator className="mb-5 bg-slate-800" />

      <div className="grid gap-4 md:grid-cols-2">
        <CampoConfiguracion label="Duración / condición de término">
          <div className="relative">
            <Clock3 className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-500" />

            <Input
              disabled
              value={obtenerDuracion(
                configuracion.escenario,
              )}
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
            Algoritmo seleccionado
            para el planificador.
          </p>
        </CampoConfiguracion>

        {necesitaInicio && (
          <CampoConfiguracion label="Inicio de la simulación">
            <div className="relative">
              <CalendarClock className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-500" />

              <input
                type="datetime-local"
                value={
                  configuracion.inicioSimulado
                }
                onChange={(
                  event,
                ) =>
                  actualizarConfiguracion(
                    {
                      inicioSimulado:
                        event
                          .target
                          .value,
                    },
                  )
                }
                className="h-10 w-full rounded-md border border-slate-700 bg-slate-800/70 pl-9 pr-3 text-sm text-slate-200 outline-none focus:border-orange-500"
              />
            </div>

            <p className="mt-1 text-[11px] text-slate-500">
              Fecha y hora desde la
              que se consumirán los
              datos simulados.
            </p>
          </CampoConfiguracion>
        )}
      </div>
    </Card>
  );
}

interface BotonEscenarioProps {
  label: string;
  active: boolean;

  onClick: () => void;
}

function BotonEscenario({
  label,
  active,
  onClick,
}: BotonEscenarioProps) {
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

function obtenerDuracion(
  escenario: Escenario,
) {
  switch (escenario) {
    case "DIA_A_DIA":
      return "Operación continua / tiempo real";

    case "SIMULACION_5D":
      return "5 días simulados";

    case "COLAPSO":
      return "Hasta condición de colapso";
  }
}