import {
  memo,
  useMemo,
} from "react";

import {
  Arrow,
  Circle,
  Group,
  Line,
  Text,
} from "react-konva";

import type {
  ParadaMapa,
  RutaMapa,
  TipoVehiculo,
} from "./ejecucion.types";

import type {
  VehiculoVisual,
} from "./CapaVehiculos";

import {
  convertirTrazoAPixeles,
  obtenerTrazosRuta,
} from "./geometriaRutas";

import type {
  SeguimientoRuta,
} from "./seguimientoRuta";

interface CapaRutasProps {
  rutas:
    RutaMapa[];

  vehiculosVisuales:
    VehiculoVisual[];

  visible:
    boolean;

  vehiculoSeleccionadoId:
    string | null;

  seleccionPedidoId:
    string | null;

  instanteMs:
    number;

  convertirX:
    (valor: number) => number;

  convertirY:
    (valor: number) => number;

  coloresVehiculo:
    Record<
      TipoVehiculo,
      string
    >;

  onSeleccionarVehiculo:
    (id: string) => void;
}

function CapaRutasBase({
  rutas,
  vehiculosVisuales,
  visible,
  vehiculoSeleccionadoId,
  seleccionPedidoId,
  instanteMs,
  convertirX,
  convertirY,
  coloresVehiculo,
  onSeleccionarVehiculo,
}: CapaRutasProps) {
  /*
   * Antes MapaOperacion hacía:
   *
   * rutas.map(...)
   *   -> vehiculosVisuales.find(...)
   *
   * para cada ruta.
   *
   * Ahora hacemos una única indexación O(n).
   */
  const visualPorVehiculo =
    useMemo(
      () =>
        new Map(
          vehiculosVisuales.map(
            (visual) => [
              visual.vehiculo.id,
              visual,
            ],
          ),
        ),
      [
        vehiculosVisuales,
      ],
    );

  /*
   * Rutas asociadas al pedido seleccionado.
   * Esta lógica también deja de vivir en
   * MapaOperacion.
   */
  const rutasSeleccionadasPorPedido =
    useMemo(
      () =>
        new Set(
          seleccionPedidoId
            ? rutas
                .filter(
                  (ruta) =>
                    ruta.paradas.some(
                      (parada) =>
                        parada.pedidoId ===
                        seleccionPedidoId,
                    ),
                )
                .map(
                  (ruta) =>
                    ruta.id,
                )
            : [],
        ),
      [
        rutas,
        seleccionPedidoId,
      ],
    );

  if (!visible) {
    return null;
  }

  const haySeleccion =
    vehiculoSeleccionadoId !==
      null ||
    seleccionPedidoId !==
      null;

  return (
    <>
      {rutas.map(
        (ruta) => {
          const visual =
            visualPorVehiculo.get(
              ruta.vehiculoId,
            );

          if (!visual) {
            return null;
          }

          const color =
            coloresVehiculo[
              ruta.tipoVehiculo
            ];

          const seleccionada =
            ruta.vehiculoId ===
              vehiculoSeleccionadoId ||
            rutasSeleccionadasPorPedido.has(
              ruta.id,
            );

          /*
           * Para rutas no seleccionadas
           * mostramos únicamente el
           * recorrido pendiente.
           */
          if (!seleccionada) {
            const trazos =
              obtenerTrazosRuta(
                ruta,
                instanteMs,
              ).pendientes;

            return (
              <Group
                key={
                  ruta.id
                }
              >
                {trazos.map(
                  (
                    trazo,
                    indice,
                  ) => (
                    <Arrow
                      key={`${ruta.id}-pendiente-${indice}`}

                      points={
                        convertirTrazoAPixeles(
                          trazo,
                          convertirX,
                          convertirY,
                        )
                      }

                      stroke={
                        color
                      }

                      fill={
                        color
                      }

                      strokeWidth={
                        2.5
                      }

                      opacity={
                        haySeleccion
                          ? 0.12
                          : 0.55
                      }

                      dash={[
                        9,
                        6,
                      ]}

                      lineCap="round"

                      lineJoin="round"

                      pointerLength={
                        6
                      }

                      pointerWidth={
                        6
                      }

                      hitStrokeWidth={
                        18
                      }

                      onClick={() =>
                        onSeleccionarVehiculo(
                          ruta.vehiculoId,
                        )
                      }

                      onTap={() =>
                        onSeleccionarVehiculo(
                          ruta.vehiculoId,
                        )
                      }
                    />
                  ),
                )}
              </Group>
            );
          }

          const seguimiento =
            visual.seguimiento;

          if (!seguimiento) {
            return null;
          }

          return (
            <Group
              key={
                ruta.id
              }
            >
              <RutaSeleccionada
                ruta={
                  ruta
                }

                seguimiento={
                  seguimiento
                }

                instanteMs={
                  instanteMs
                }

                color={
                  color
                }

                convertirX={
                  convertirX
                }

                convertirY={
                  convertirY
                }
              />
            </Group>
          );
        },
      )}
    </>
  );
}

export const CapaRutas =
  memo(
    CapaRutasBase,
  );

/*
 * =========================================================
 * RUTA SELECCIONADA
 * =========================================================
 */

function RutaSeleccionada({
  ruta,
  seguimiento,
  instanteMs,
  color,
  convertirX,
  convertirY,
}: {
  ruta:
    RutaMapa;

  seguimiento:
    SeguimientoRuta;

  instanteMs:
    number;

  color:
    string;

  convertirX:
    (valor: number) => number;

  convertirY:
    (valor: number) => number;
}) {
  const trazos =
    obtenerTrazosRuta(
      ruta,
      instanteMs,
      seguimiento.siguienteParada
        ?.orden,
    );

  return (
    <>
      {/* Recorrido ya realizado */}
      {trazos.recorridos.map(
        (
          trazo,
          indice,
        ) => (
          <Line
            key={`recorrido-${indice}`}

            points={
              convertirTrazoAPixeles(
                trazo,
                convertirX,
                convertirY,
              )
            }

            stroke={
              color
            }

            strokeWidth={
              3
            }

            opacity={
              0.4
            }

            lineCap="round"

            lineJoin="round"
          />
        ),
      )}

      {/* Tramo actual */}
      {trazos.actuales.map(
        (
          trazo,
          indice,
        ) => (
          <Arrow
            key={`actual-${indice}`}

            points={
              convertirTrazoAPixeles(
                trazo,
                convertirX,
                convertirY,
              )
            }

            stroke={
              color
            }

            fill={
              color
            }

            strokeWidth={
              6
            }

            opacity={
              1
            }

            lineCap="round"

            lineJoin="round"

            pointerLength={
              9
            }

            pointerWidth={
              9
            }

            shadowColor={
              color
            }

            shadowBlur={
              7
            }

            shadowOpacity={
              0.35
            }
          />
        ),
      )}

      {/* Ruta futura */}
      {trazos.futuros.map(
        (
          trazo,
          indice,
        ) => (
          <Arrow
            key={`futuro-${indice}`}

            points={
              convertirTrazoAPixeles(
                trazo,
                convertirX,
                convertirY,
              )
            }

            stroke={
              color
            }

            fill={
              color
            }

            strokeWidth={
              3
            }

            opacity={
              0.65
            }

            dash={[
              10,
              6,
            ]}

            lineCap="round"

            lineJoin="round"

            pointerLength={
              7
            }

            pointerWidth={
              7
            }
          />
        ),
      )}

      {/* PARADAS */}
      {seguimiento.paradas.map(
        ({
          parada,
          estado,
        }) => (
          <ParadaMarker
            key={`${ruta.id}-parada-${parada.orden}`}

            parada={
              parada
            }

            estado={
              estado
            }

            x={
              convertirX(
                parada.x,
              )
            }

            y={
              convertirY(
                parada.y,
              )
            }
          />
        ),
      )}
    </>
  );
}

/*
 * =========================================================
 * PARADA
 * =========================================================
 */

function ParadaMarker({
  parada,
  estado,
  x,
  y,
}: {
  parada:
    ParadaMapa;

  estado:
    | "COMPLETADA"
    | "REFRIGERIO"
    | "EN_SERVICIO"
    | "SIGUIENTE"
    | "FUTURA";

  x:
    number;

  y:
    number;
}) {
  const color =
    estado ===
    "COMPLETADA"
      ? "#16a34a"
      : estado ===
          "REFRIGERIO"
        ? "#f59e0b"
        : estado ===
            "EN_SERVICIO"
          ? "#f97316"
          : estado ===
              "SIGUIENTE"
            ? "#0ea5e9"
            : "#64748b";

  const texto =
    estado ===
    "COMPLETADA"
      ? "✓"
      : estado ===
          "REFRIGERIO"
        ? "☕"
        : estado ===
            "EN_SERVICIO"
          ? "●"
          : estado ===
              "SIGUIENTE"
            ? "▶"
            : String(
                parada.orden,
              );

  return (
    <Group
      x={x}
      y={y}
    >
      <Circle
        radius={
          8
        }

        fill="#ffffff"

        stroke={
          color
        }

        strokeWidth={
          2
        }

        opacity={
          estado ===
          "FUTURA"
            ? 0.7
            : 1
        }
      />

      <Text
        x={-7}
        y={-5}

        width={
          14
        }

        align="center"

        text={
          texto
        }

        fontSize={
          9
        }

        fontStyle="bold"

        fill={
          color
        }
      />
    </Group>
  );
}