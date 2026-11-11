import {
  memo,
} from "react";

import {
  Layer,
  Line,
  Rect,
} from "react-konva";

interface GeometriaReticula {
  ancho: number;
  alto: number;

  origenX: number;
  origenY: number;

  x: (
    valor: number,
  ) => number;

  y: (
    valor: number,
  ) => number;
}

interface CapaReticulaProps {
  geometria:
    GeometriaReticula;

  anchoMapa:
    number;

  altoMapa:
    number;
}

/**
 * Retícula completamente estática del mapa.
 *
 * Solo debe volver a renderizarse cuando cambia
 * la geometría del contenedor, por ejemplo al
 * redimensionar la ventana.
 *
 * No escucha eventos de Konva.
 */
function CapaReticulaBase({
  geometria,
  anchoMapa,
  altoMapa,
}: CapaReticulaProps) {
  return (
    <Layer
      listening={false}
    >
      <Rect
        x={
          geometria.origenX
        }
        y={
          geometria.origenY
        }
        width={
          geometria.ancho
        }
        height={
          geometria.alto
        }
        fill="#ffffff"
        stroke="#64748b"
        strokeWidth={
          1.5
        }
      />

      {Array.from(
        {
          length:
            anchoMapa +
            1,
        },

        (_, x) => (
          <Line
            key={`vertical-${x}`}
            points={[
              geometria.x(
                x,
              ),

              geometria.y(
                0,
              ),

              geometria.x(
                x,
              ),

              geometria.y(
                altoMapa,
              ),
            ]}
            stroke={
              x % 5 ===
              0
                ? "#94a3b8"
                : "#dbe4ef"
            }
            strokeWidth={
              x % 5 ===
              0
                ? 0.8
                : 0.35
            }
          />
        ),
      )}

      {Array.from(
        {
          length:
            altoMapa +
            1,
        },

        (_, y) => (
          <Line
            key={`horizontal-${y}`}
            points={[
              geometria.x(
                0,
              ),

              geometria.y(
                y,
              ),

              geometria.x(
                anchoMapa,
              ),

              geometria.y(
                y,
              ),
            ]}
            stroke={
              y % 5 ===
              0
                ? "#94a3b8"
                : "#dbe4ef"
            }
            strokeWidth={
              y % 5 ===
              0
                ? 0.8
                : 0.35
            }
          />
        ),
      )}
    </Layer>
  );
}

/**
 * La capa queda memoizada.
 *
 * Durante la animación de vehículos,
 * geometria/anchoMapa/altoMapa no cambian,
 * por lo que React no necesita reconstruir
 * sus más de 120 nodos Konva.
 */
export const CapaReticula =
  memo(
    CapaReticulaBase,
  );