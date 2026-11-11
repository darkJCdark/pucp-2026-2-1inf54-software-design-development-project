import {
  memo,
} from "react";

import type {
  KonvaEventObject,
} from "konva/lib/Node";

import {
  Group,
  Rect,
  Text,
} from "react-konva";

import type {
  AlmacenMapa,
} from "./ejecucion.types";

type NivelSemaforo =
  | "TODOS"
  | "VACIO"
  | "VERDE"
  | "AMBAR"
  | "ROJO";

interface CapaAlmacenesProps {
  almacenes:
    AlmacenMapa[];

  filtroStock:
    NivelSemaforo;

  verdeHasta:
    number;

  ambarHasta:
    number;

  seleccionAlmacenId:
    string | null;

  convertirX:
    (valor: number) => number;

  convertirY:
    (valor: number) => number;

  onSeleccionar?:
    (id: string) => void;

  onTooltip: (
    event:
      KonvaEventObject<MouseEvent>,
    titulo:
      string,
    lineas:
      string[],
  ) => void;

  onLeave:
    () => void;
}

/**
 * Capa estática de almacenes.
 *
 * No depende del reloj animado.
 * Solo cambia cuando cambian:
 * - almacenes;
 * - filtros;
 * - selección;
 * - geometría;
 * - umbrales.
 */
function CapaAlmacenesBase({
  almacenes,
  filtroStock,
  verdeHasta,
  ambarHasta,
  seleccionAlmacenId,
  convertirX,
  convertirY,
  onSeleccionar,
  onTooltip,
  onLeave,
}: CapaAlmacenesProps) {
  return (
    <>
      {almacenes.map(
        (almacen) => {
          const central =
            almacen.tipo ===
            "CENTRAL";

          const porcentaje =
            porcentajeAlmacen(
              almacen,
            );

          const nivel =
            nivelSemaforoStock(
              porcentaje,
              verdeHasta,
              ambarHasta,
            );

          if (
            !central &&
            filtroStock !==
              "TODOS" &&
            filtroStock !==
              nivel
          ) {
            return null;
          }

          const color =
            central
              ? "#2563eb"
              : colorSemaforo(
                  nivel,
                );

          const seleccionado =
            seleccionAlmacenId ===
            almacen.id;

          const textoStock =
            central
              ? "Stock ilimitado"
              : `${almacen.stock ?? 0}/${almacen.capacidad ?? 0} · ${porcentaje.toFixed(0)}%`;

          const lineas = [
            `ID: ${almacen.id}`,

            `Coordenadas: (${almacen.x}, ${almacen.y})`,

            textoStock,

            central
              ? "Tipo: Central"
              : `Semáforo: ${nombreNivel(
                  nivel,
                )}`,
          ];

          return (
            <Group
              key={
                almacen.id
              }

              x={
                convertirX(
                  almacen.x,
                )
              }

              y={
                convertirY(
                  almacen.y,
                )
              }

              onClick={() =>
                onSeleccionar?.(
                  almacen.id,
                )
              }

              onTap={() =>
                onSeleccionar?.(
                  almacen.id,
                )
              }

              onMouseEnter={(
                event,
              ) =>
                onTooltip(
                  event,

                  `Almacén ${almacen.nombre}`,

                  lineas,
                )
              }

              onMouseMove={(
                event,
              ) =>
                onTooltip(
                  event,

                  `Almacén ${almacen.nombre}`,

                  lineas,
                )
              }

              onMouseLeave={
                onLeave
              }
            >
              {seleccionado && (
                <Rect
                  x={-24}
                  y={-24}

                  width={
                    48
                  }

                  height={
                    48
                  }

                  cornerRadius={
                    10
                  }

                  stroke="#f97316"

                  strokeWidth={
                    3
                  }

                  opacity={
                    0.8
                  }
                />
              )}

              <Rect
                x={-19}
                y={-19}

                width={
                  38
                }

                height={
                  38
                }

                cornerRadius={
                  7
                }

                fill={
                  central
                    ? "#1e3a8a"
                    : `${color}22`
                }

                stroke={
                  color
                }

                strokeWidth={
                  2.5
                }

                shadowColor="#0f172a"

                shadowBlur={
                  5
                }

                shadowOpacity={
                  0.25
                }
              />

              <Text
                x={-19}
                y={-6}

                width={
                  38
                }

                align="center"

                text={
                  central
                    ? "C"
                    : "ALM"
                }

                fontSize={
                  central
                    ? 19
                    : 11
                }

                fontStyle="bold"

                fill={
                  central
                    ? "#ffffff"
                    : color
                }
              />

              <Text
                x={-48}
                y={25}

                width={
                  96
                }

                align="center"

                text={
                  almacen.nombre
                }

                fontSize={
                  12
                }

                fontStyle="bold"

                fill="#0f172a"
              />

              <Text
                x={-58}
                y={40}

                width={
                  116
                }

                align="center"

                text={
                  textoStock
                }

                fontSize={
                  9
                }

                fill="#475569"
              />
            </Group>
          );
        },
      )}
    </>
  );
}

export const CapaAlmacenes =
  memo(
    CapaAlmacenesBase,
  );

function porcentajeAlmacen(
  almacen:
    AlmacenMapa,
) {
  if (
    almacen.tipo ===
      "CENTRAL" ||
    !almacen.capacidad ||
    almacen.capacidad <=
      0
  ) {
    return 100;
  }

  return Math.min(
    100,

    Math.max(
      0,

      (
        (almacen.stock ??
          0) /
        almacen.capacidad
      ) *
        100,
    ),
  );
}

function nivelSemaforoStock(
  porcentaje:
    number,

  verdeHasta:
    number,

  ambarHasta:
    number,
): Exclude<
  NivelSemaforo,
  "TODOS"
> {
  if (
    porcentaje <= 0
  ) {
    return "VACIO";
  }

  if (
    porcentaje <=
    verdeHasta
  ) {
    return "VERDE";
  }

  if (
    porcentaje <=
    ambarHasta
  ) {
    return "AMBAR";
  }

  return "ROJO";
}

function colorSemaforo(
  nivel:
    Exclude<
      NivelSemaforo,
      "TODOS"
    >,
) {
  switch (nivel) {
    case "VACIO":
      return "#64748b";

    case "VERDE":
      return "#16a34a";

    case "AMBAR":
      return "#d97706";

    case "ROJO":
      return "#dc2626";
  }
}

function nombreNivel(
  nivel:
    Exclude<
      NivelSemaforo,
      "TODOS"
    >,
) {
  switch (nivel) {
    case "VACIO":
      return "Vacío";

    case "VERDE":
      return "Verde";

    case "AMBAR":
      return "Ámbar";

    case "ROJO":
      return "Rojo";
  }
}