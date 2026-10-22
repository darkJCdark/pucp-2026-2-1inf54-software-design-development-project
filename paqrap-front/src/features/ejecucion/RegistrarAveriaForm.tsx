import {
  useMemo,
  useState,
  type FormEvent,
} from "react";

import type {
  RegistrarAveriaRequestDto,
  TipoAveriaBackend,
} from "@/api/incidencias.dto";

import type {
  VehiculoMapa,
} from "./ejecucion.types";

interface RegistrarAveriaFormProps {
  vehiculos: VehiculoMapa[];
  instante: string;
  ejecucionId: number;

  onRegistrar: (
    request: RegistrarAveriaRequestDto,
  ) => Promise<void>;

  onCerrar: () => void;
}

export function RegistrarAveriaForm({
  vehiculos,
  instante,
  ejecucionId,
  onRegistrar,
  onCerrar,
}: RegistrarAveriaFormProps) {
  const ordenados = useMemo(
    () =>
      [...vehiculos].sort((a, b) =>
        a.id.localeCompare(b.id),
      ),
    [vehiculos],
  );

  const [vehiculoId, setVehiculoId] =
    useState(ordenados[0]?.id ?? "");

  const [tipo, setTipo] =
    useState<TipoAveriaBackend>(
      "MINOR",
    );

  const [enviando, setEnviando] =
    useState(false);

  const [error, setError] =
    useState<string | null>(null);

  async function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();
    setError(null);

    const vehiculo =
      vehiculos.find(
        (item) =>
          item.id === vehiculoId,
      );

    if (!vehiculo) {
      setError(
        "Selecciona un vehículo válido.",
      );
      return;
    }

    try {
      setEnviando(true);

      await onRegistrar({
        vehiculoId,
        tipo,
        ocurridaEn: instante,
        x: vehiculo.x,
        y: vehiculo.y,
        ejecucionId,
      });
    } catch (error) {
      setError(
        error instanceof Error
          ? error.message
          : "No fue posible registrar la avería.",
      );
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="absolute right-5 top-16 z-50 w-80 rounded-lg border border-slate-700 bg-[#11161d] p-5 text-white shadow-xl">
      <div className="mb-4">
        <h2 className="font-semibold">
          Registrar avería
        </h2>

        <p className="mt-1 text-xs text-slate-400">
          La incidencia se registrará en el instante simulado actual y disparará una replanificación.
        </p>
      </div>

      <form
        onSubmit={handleSubmit}
        className="space-y-4"
      >
        <div>
          <label className="mb-1 block text-xs text-slate-400">
            Vehículo
          </label>

          <select
            value={vehiculoId}
            onChange={(event) =>
              setVehiculoId(
                event.target.value,
              )
            }
            className="w-full rounded-md border border-slate-700 bg-[#202833] px-3 py-2 text-sm text-white outline-none focus:border-orange-500"
          >
            {ordenados.map(
              (vehiculo) => (
                <option
                  key={vehiculo.id}
                  value={vehiculo.id}
                >
                  {vehiculo.id} · {vehiculo.tipo} · {vehiculo.estado}
                </option>
              ),
            )}
          </select>
        </div>

        <div>
          <label className="mb-1 block text-xs text-slate-400">
            Tipo de avería
          </label>

          <select
            value={tipo}
            onChange={(event) =>
              setTipo(
                event.target.value as TipoAveriaBackend,
              )
            }
            className="w-full rounded-md border border-slate-700 bg-[#202833] px-3 py-2 text-sm text-white outline-none focus:border-orange-500"
          >
            <option value="MINOR">
              Menor · 2 horas
            </option>

            <option value="INTERMEDIATE">
              Intermedia
            </option>

            <option value="MAJOR">
              Mayor
            </option>
          </select>
        </div>

        {error && (
          <div className="rounded-md border border-red-900 bg-red-950/40 px-3 py-2 text-xs text-red-400">
            {error}
          </div>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={onCerrar}
            disabled={enviando}
            className="rounded-md border border-slate-700 px-4 py-2 text-sm text-slate-300 hover:bg-slate-800 disabled:opacity-50"
          >
            Cancelar
          </button>

          <button
            type="submit"
            disabled={
              enviando ||
              !vehiculoId
            }
            className="rounded-md bg-red-700 px-4 py-2 text-sm font-semibold text-white hover:bg-red-600 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {enviando
              ? "Registrando..."
              : "Registrar avería"}
          </button>
        </div>
      </form>
    </div>
  );
}
