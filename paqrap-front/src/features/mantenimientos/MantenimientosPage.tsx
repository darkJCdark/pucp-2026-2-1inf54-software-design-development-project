import {
  useEffect,
  useMemo,
  useState,
  type FormEvent,
} from "react";

import {
  CalendarPlus,
  Loader2,
  RefreshCcw,
  Trash2,
} from "lucide-react";

import type {
  MantenimientoResponseDto,
} from "@/api/mantenimientos.dto";

import type {
  VehiculoResponseDto,
} from "@/api/flota.dto";

import {
  ApiError,
} from "@/services/api";

import {
  flotaService,
} from "@/services/flota.service";

import {
  mantenimientosService,
} from "@/services/mantenimientos.service";

export default function MantenimientosPage() {
  const [vehiculos, setVehiculos] =
    useState<VehiculoResponseDto[]>([]);

  const [mantenimientos, setMantenimientos] =
    useState<MantenimientoResponseDto[]>([]);

  const [vehiculoId, setVehiculoId] =
    useState("");

  const [fecha, setFecha] =
    useState(fechaHoy());

  const [cargando, setCargando] =
    useState(true);

  const [guardando, setGuardando] =
    useState(false);

  const [error, setError] =
    useState<string | null>(null);

  useEffect(() => {
    void cargar();
  }, []);

  async function cargar() {
    try {
      setCargando(true);
      setError(null);

      const [respuestaVehiculos, respuestaMantenimientos] =
        await Promise.all([
          flotaService.listarVehiculos(),
          mantenimientosService.listar(),
        ]);

      setVehiculos(
        [...respuestaVehiculos].sort((a, b) =>
          a.id.localeCompare(b.id),
        ),
      );

      setMantenimientos(
        [...respuestaMantenimientos].sort(
          (a, b) =>
            a.fecha.localeCompare(b.fecha) ||
            a.vehiculoId.localeCompare(
              b.vehiculoId,
            ),
        ),
      );

      if (
        !vehiculoId &&
        respuestaVehiculos.length > 0
      ) {
        setVehiculoId(
          respuestaVehiculos[0].id,
        );
      }
    } catch (error) {
      setError(
        mensajeError(
          error,
          "No fue posible cargar los mantenimientos.",
        ),
      );
    } finally {
      setCargando(false);
    }
  }

  async function programar(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();

    if (!vehiculoId || !fecha) {
      setError(
        "Selecciona un vehículo y una fecha.",
      );
      return;
    }

    try {
      setGuardando(true);
      setError(null);

      await mantenimientosService.programar({
        vehiculoId,
        fecha,
      });

      await cargar();
    } catch (error) {
      setError(
        mensajeError(
          error,
          "No fue posible programar el mantenimiento.",
        ),
      );
    } finally {
      setGuardando(false);
    }
  }

  async function cancelar(
    mantenimiento: MantenimientoResponseDto,
  ) {
    try {
      setGuardando(true);
      setError(null);

      await mantenimientosService.cancelar(
        mantenimiento.vehiculoId,
        mantenimiento.fecha,
      );

      await cargar();
    } catch (error) {
      setError(
        mensajeError(
          error,
          "No fue posible cancelar el mantenimiento.",
        ),
      );
    } finally {
      setGuardando(false);
    }
  }

  const tipoPorVehiculo = useMemo(
    () =>
      new Map(
        vehiculos.map((vehiculo) => [
          vehiculo.id,
          vehiculo.tipo,
        ]),
      ),
    [vehiculos],
  );

  return (
    <div className="min-h-screen bg-[#0d1117] p-6 text-white">
      <div className="mb-6 flex items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">
            Mantenimientos
          </h1>
          <p className="mt-1 text-sm text-slate-400">
            Programación de indisponibilidad preventiva por vehículo.
          </p>
        </div>

        <button
          type="button"
          onClick={() => void cargar()}
          className="rounded-md border border-slate-700 p-2 text-slate-300 hover:bg-slate-800"
          title="Actualizar"
        >
          <RefreshCcw className="size-5" />
        </button>
      </div>

      {error && (
        <div className="mb-4 rounded-md border border-red-900 bg-red-950/30 px-4 py-3 text-sm text-red-400">
          {error}
        </div>
      )}

      <div className="grid gap-4 xl:grid-cols-[340px_minmax(0,1fr)]">
        <form
          onSubmit={programar}
          className="h-fit rounded-lg border border-slate-800 bg-[#11161d] p-4"
        >
          <div className="mb-4 flex items-center gap-2 font-semibold">
            <CalendarPlus className="size-5 text-orange-400" />
            Programar mantenimiento
          </div>

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
            className="mb-4 h-10 w-full rounded-md border border-slate-700 bg-[#202833] px-3 text-sm outline-none focus:border-orange-500"
          >
            {vehiculos.map((vehiculo) => (
              <option
                key={vehiculo.id}
                value={vehiculo.id}
              >
                {vehiculo.id} · {nombreTipo(vehiculo.tipo)}
              </option>
            ))}
          </select>

          <label className="mb-1 block text-xs text-slate-400">
            Fecha
          </label>

          <input
            type="date"
            value={fecha}
            onChange={(event) =>
              setFecha(event.target.value)
            }
            className="h-10 w-full rounded-md border border-slate-700 bg-[#202833] px-3 text-sm outline-none focus:border-orange-500"
          />

          <button
            type="submit"
            disabled={guardando || !vehiculoId}
            className="mt-4 flex w-full items-center justify-center gap-2 rounded-md bg-orange-500 px-4 py-2 text-sm font-semibold hover:bg-orange-600 disabled:opacity-50"
          >
            {guardando && (
              <Loader2 className="size-4 animate-spin" />
            )}
            Programar
          </button>
        </form>

        <section className="overflow-hidden rounded-lg border border-slate-800 bg-[#11161d]">
          {cargando ? (
            <div className="flex h-72 items-center justify-center text-slate-400">
              <Loader2 className="mr-2 size-5 animate-spin" />
              Cargando...
            </div>
          ) : mantenimientos.length === 0 ? (
            <div className="p-10 text-center text-slate-500">
              No existen mantenimientos programados.
            </div>
          ) : (
            <div className="overflow-auto">
              <table className="w-full min-w-[620px] text-left text-sm">
                <thead className="bg-[#151b23] text-xs uppercase text-slate-500">
                  <tr>
                    <th className="px-4 py-3">Fecha</th>
                    <th className="px-4 py-3">Vehículo</th>
                    <th className="px-4 py-3">Tipo</th>
                    <th className="px-4 py-3 text-right">Acciones</th>
                  </tr>
                </thead>

                <tbody>
                  {mantenimientos.map((mantenimiento) => (
                    <tr
                      key={`${mantenimiento.vehiculoId}-${mantenimiento.fecha}`}
                      className="border-t border-slate-800"
                    >
                      <td className="px-4 py-3 text-slate-300">
                        {mantenimiento.fecha}
                      </td>
                      <td className="px-4 py-3 font-medium">
                        {mantenimiento.vehiculoId}
                      </td>
                      <td className="px-4 py-3 text-slate-400">
                        {nombreTipo(
                          tipoPorVehiculo.get(
                            mantenimiento.vehiculoId,
                          ),
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <button
                          type="button"
                          disabled={guardando}
                          onClick={() =>
                            void cancelar(
                              mantenimiento,
                            )
                          }
                          className="rounded-md p-2 text-red-400 hover:bg-red-950/40 disabled:opacity-50"
                          title="Cancelar mantenimiento"
                        >
                          <Trash2 className="size-4" />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}

function fechaHoy() {
  return new Intl.DateTimeFormat(
    "en-CA",
    {
      timeZone: "America/Lima",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    },
  ).format(new Date());
}

function nombreTipo(
  tipo:
    | VehiculoResponseDto["tipo"]
    | undefined,
) {
  switch (tipo) {
    case "CAR":
      return "Auto";
    case "MOTORCYCLE":
      return "Moto";
    case "BICYCLE":
      return "Bicicleta";
    default:
      return "—";
  }
}

function mensajeError(
  error: unknown,
  fallback: string,
) {
  if (error instanceof ApiError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return fallback;
}
