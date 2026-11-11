import {
  memo,
} from "react";

import type {
  KonvaEventObject,
} from "konva/lib/Node";

import {
  Circle,
  Group,
  Text,
} from "react-konva";

import type {
  AveriaResponseDto,
} from "@/api/incidencias.dto";

interface CapaAveriasProps {
  averias:
    AveriaResponseDto[];

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

function CapaAveriasBase({
  averias,
  visible,
  convertirX,
  convertirY,
  onTooltip,
  onLeave,
}: CapaAveriasProps) {
  if (!visible) {
    return null;
  }

  return (
    <>
      {averias.map(
        (averia) => {
          const estilo =
            estiloAveria(
              averia.tipo,
            );

          const titulo =
            `Avería ${estilo.nombre}`;

          const lineas = [
            `Vehículo: ${averia.vehiculoId}`,

            `Tipo: ${estilo.nombre}`,

            `Coordenadas: (${averia.x}, ${averia.y})`,

            `Hasta: ${formatearFecha(
              averia.indisponibleHasta,
            )}`,
          ];

          return (
            <Group
              key={`averia-${averia.id}`}

              x={
                convertirX(
                  averia.x,
                )
              }

              y={
                convertirY(
                  averia.y,
                )
              }

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
              <Circle
                radius={
                  12
                }

                fill={
                  estilo.fondo
                }

                stroke={
                  estilo.color
                }

                strokeWidth={
                  3
                }

                shadowColor={
                  estilo.color
                }

                shadowBlur={
                  6
                }

                shadowOpacity={
                  0.35
                }
              />

              <Text
                x={-10}
                y={-6}

                width={
                  20
                }

                align="center"

                text={
                  estilo.codigo
                }

                fontSize={
                  10
                }

                fontStyle="bold"

                fill={
                  estilo.color
                }
              />
            </Group>
          );
        },
      )}
    </>
  );
}

export const CapaAverias =
  memo(
    CapaAveriasBase,
  );

function estiloAveria(
  tipo:
    AveriaResponseDto["tipo"],
) {
  switch (tipo) {
    case "MINOR":
      return {
        nombre:
          "leve",

        codigo:
          "L",

        color:
          "#d97706",

        fondo:
          "#fffbeb",
      };

    case "INTERMEDIATE":
      return {
        nombre:
          "intermedia",

        codigo:
          "I",

        color:
          "#ea580c",

        fondo:
          "#fff7ed",
      };

    case "MAJOR":
      return {
        nombre:
          "grave",

        codigo:
          "G",

        color:
          "#dc2626",

        fondo:
          "#fef2f2",
      };
  }
}

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