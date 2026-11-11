import {
  memo,
} from "react";

import type {
  KonvaEventObject,
} from "konva/lib/Node";

import {
  Circle,
  Group,
  Line,
} from "react-konva";

import type {
  BloqueoMonitoreoDto,
} from "@/api/monitoreo.dto";

interface CapaBloqueosProps {
  bloqueos:
    BloqueoMonitoreoDto[];

  visible:
    boolean;

  convertirX:
    (valor: number) => number;

  convertirY:
    (valor: number) => number;

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
 * Capa de bloqueos viales.
 *
 * No depende del reloj animado.
 *
 * Solo necesita volver a renderizarse cuando:
 * - cambian los bloqueos;
 * - cambia la geometría del mapa;
 * - cambia su visibilidad.
 */
function CapaBloqueosBase({
  bloqueos,
  visible,
  convertirX,
  convertirY,
  onTooltip,
  onLeave,
}: CapaBloqueosProps) {
  if (!visible) {
    return null;
  }

  return (
    <>
      {bloqueos.map(
        (bloqueo) => {
          const puntos =
            bloqueo.nodos.flatMap(
              (nodo) => [
                convertirX(
                  nodo.x,
                ),

                convertirY(
                  nodo.y,
                ),
              ],
            );

          const titulo =
            `Bloqueo #${bloqueo.id}`;

          const lineas = [
            `${bloqueo.nodos.length} nodos`,

            `Inicio: ${formatearFecha(
              bloqueo.inicio,
            )}`,

            `Fin: ${formatearFecha(
              bloqueo.fin,
            )}`,
          ];

          return (
            <Group
              key={`bloqueo-${bloqueo.id}`}

              onMouseEnter={(
                event,
              ) =>
                onTooltip(
                  event,
                  titulo,
                  lineas,
                )
              }

              onMouseMove={(
                event,
              ) =>
                onTooltip(
                  event,
                  titulo,
                  lineas,
                )
              }

              onMouseLeave={
                onLeave
              }
            >
              <Line
                points={
                  puntos
                }

                stroke="#dc2626"

                strokeWidth={
                  7
                }

                opacity={
                  0.65
                }

                lineCap="round"

                lineJoin="round"
              />

              {bloqueo.nodos.map(
                (
                  nodo,
                  index,
                ) => (
                  <Circle
                    key={`${bloqueo.id}-${index}`}

                    x={
                      convertirX(
                        nodo.x,
                      )
                    }

                    y={
                      convertirY(
                        nodo.y,
                      )
                    }

                    radius={
                      3.2
                    }

                    fill="#991b1b"
                  />
                ),
              )}
            </Group>
          );
        },
      )}
    </>
  );
}

export const CapaBloqueos =
  memo(
    CapaBloqueosBase,
  );

function formatearFecha(
  valor: string,
) {
  return new Intl.DateTimeFormat(
    "es-PE",
    {
      timeZone:
        "America/Lima",

      day:
        "2-digit",

      month:
        "2-digit",

      hour:
        "2-digit",

      minute:
        "2-digit",

      hour12:
        false,
    },
  ).format(
    new Date(
      valor,
    ),
  );
}