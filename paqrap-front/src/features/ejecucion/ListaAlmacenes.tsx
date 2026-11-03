import { useState } from "react";

import type { MonitoreoResponseDto } from "@/api/monitoreo.dto";
import { Card } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { useConfiguracion } from "@/context/ConfiguracionContext";

const ALMACENES_ORDENADOS = [
  "CENTRAL",
  "NORTHWEST",
  "EAST",
] as const;

const NOMBRES_ALMACEN: Record<
  (typeof ALMACENES_ORDENADOS)[number],
  string
> = {
  CENTRAL: "Central",
  NORTHWEST: "Nor-Oeste",
  EAST: "Este",
};

interface ListaAlmacenesProps {
  monitoreo: MonitoreoResponseDto;
  ejecucionId: number;
  onSeleccionar: (sel: {
    tipo: "VEHICULO" | "PEDIDO" | "ALMACEN";
    id: string;
  }) => void;
}

export function ListaAlmacenes({
  monitoreo,
  ejecucionId,
  onSeleccionar,
}: ListaAlmacenesProps) {
  const [busqueda, setBusqueda] = useState("");
  const { configuracion } = useConfiguracion();
  const termino = busqueda.trim().toLocaleLowerCase("es-PE");
  const almacenes = ALMACENES_ORDENADOS.flatMap((id) => {
    const almacen = monitoreo.almacenes.find(
      (item) => item.id === id,
    );

    return almacen ? [{ ...almacen, nombre: NOMBRES_ALMACEN[id] }] : [];
  }).filter((almacen) =>
    almacen.nombre.toLocaleLowerCase("es-PE").includes(termino),
  );
  const inputId = `busqueda-almacenes-${ejecucionId}`;

  return (
    <Card className="border-slate-800 bg-[#151b23] p-4">
      <div className="mb-4 flex items-center justify-between gap-3">
        <div>
          <h3 className="font-semibold">Almacenes</h3>
          <p className="mt-1 text-xs text-slate-500">
            Inventario disponible por almacén
          </p>
        </div>
      </div>

      <label htmlFor={inputId} className="sr-only">
        Buscar almacén
      </label>
      <input
        id={inputId}
        type="search"
        value={busqueda}
        onChange={(event) => setBusqueda(event.target.value)}
        placeholder="Buscar almacén"
        className="mb-3 w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white outline-none placeholder:text-slate-500 focus:border-orange-500"
      />

      <div className="space-y-2">
        {almacenes.map((almacen) => {
          const esCentralIlimitado =
            almacen.id === "CENTRAL" &&
            almacen.stock === null &&
            almacen.capacidad === null;
          const porcentaje =
            almacen.capacidad !== null && almacen.capacidad > 0
              ? (almacen.stock ?? 0) / almacen.capacidad * 100
              : null;
          const semaforo = obtenerSemaforo(
            porcentaje,
            configuracion.almacenVerdeHasta,
            configuracion.almacenAmbarHasta,
          );

          return (
            <button
              key={almacen.id}
              type="button"
              onClick={() =>
                onSeleccionar({
                  tipo: "ALMACEN",
                  id: almacen.id,
                })
              }
              className="w-full rounded-md p-3 text-left transition hover:bg-slate-800/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-orange-500"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-medium text-white">
                    {almacen.nombre}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    {esCentralIlimitado
                      ? "Ilimitado"
                      : `Stock actual: ${almacen.stock ?? "Sin dato"}`}
                  </p>
                </div>

                {semaforo && (
                  <span
                    className={`rounded-full px-2 py-1 text-[10px] font-medium ${semaforo.clase}`}
                  >
                    {semaforo.etiqueta}
                  </span>
                )}
              </div>

              {porcentaje !== null && (
                <>
                  <Progress
                    value={Math.min(Math.max(porcentaje, 0), 100)}
                    className="mt-3 h-2"
                  />
                  <div className="mt-1 flex justify-between text-[11px] text-slate-500">
                    <span>{porcentaje.toFixed(1)}% de stock</span>
                    <span>Capacidad: {almacen.capacidad}</span>
                  </div>
                </>
              )}
            </button>
          );
        })}

        {almacenes.length === 0 && (
          <p className="py-4 text-center text-sm text-slate-500">
            No se encontraron almacenes.
          </p>
        )}
      </div>
    </Card>
  );
}

function obtenerSemaforo(
  porcentaje: number | null,
  verdeHasta: number,
  ambarHasta: number,
) {
  if (porcentaje === null) return null;

  if (porcentaje >= ambarHasta) {
    return {
      etiqueta: "Verde",
      clase: "bg-green-500 text-black",
    };
  }

  if (porcentaje >= verdeHasta) {
    return {
      etiqueta: "Ámbar",
      clase: "bg-amber-500 text-black",
    };
  }

  return {
    etiqueta: "Rojo",
    clase: "bg-red-500 text-white",
  };
}
