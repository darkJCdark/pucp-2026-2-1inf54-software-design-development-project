import {
  memo,
  useMemo,
  type ComponentProps,
} from "react";

import {
  Circle,
  Group,
  Shape,
  Text,
} from "react-konva";

import {
  convertirTrazoAPixeles,
  obtenerTrazosRuta,
} from "./geometriaRutas";

import type {
  ParadaMapa,
  RutaMapa,
  TipoVehiculo,
} from "./ejecucion.types";

import type {
  VehiculoVisual,
} from "./CapaVehiculos";

import type {
  SeguimientoRuta,
} from "./seguimientoRuta";

import type {
  NivelDetalleMapa,
  ViewportPixeles,
} from "./mapaVisual.types";

type SceneFunc =
  NonNullable<
    ComponentProps<
      typeof Shape
    >["sceneFunc"]
  >;

type ContextoShape =
  Parameters<SceneFunc>[0];

type ShapeKonva =
  Parameters<SceneFunc>[1];

type TrazoPixeles =
  number[];

interface CapaRutasProps {
  rutas: RutaMapa[];

  vehiculosVisuales:
    VehiculoVisual[];

  viewportPixeles:
    ViewportPixeles;

  nivelDetalle:
    NivelDetalleMapa;

  visible: boolean;

  mostrarAutos: boolean;

  mostrarMotos: boolean;

  mostrarBicicletas: boolean;

  vehiculoSeleccionadoId:
    string | null;

  seleccionPedidoId:
    string | null;

  instanteMs: number;

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
  viewportPixeles,
  nivelDetalle,
  visible,
  mostrarAutos,
  mostrarMotos,
  mostrarBicicletas,
  vehiculoSeleccionadoId,
  seleccionPedidoId,
  instanteMs,
  convertirX,
  convertirY,
  coloresVehiculo,
  onSeleccionarVehiculo,
}: CapaRutasProps) {
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
      [vehiculosVisuales],
    );

  const rutasSeleccionadasPorPedido =
    useMemo(
      () => {
        if (!seleccionPedidoId) {
          return new Set<string>();
        }

        return new Set(
          rutas
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
            ),
        );
      },
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

  const dibujarFlechas =
    nivelDetalle !== "BAJO";

  return (
    <>
      {rutas.map((ruta) => {
        const visual =
          visualPorVehiculo.get(
            ruta.vehiculoId,
          );

        if (!visual) {
          return null;
        }

        if (
          !tipoVehiculoVisible(
            visual.vehiculo.tipo,
            mostrarAutos,
            mostrarMotos,
            mostrarBicicletas,
          )
        ) {
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
         * Ruta normal:
         * una sola Shape para todos
         * sus tramos visibles.
         */
        if (!seleccionada) {
          const trazos =
            obtenerTrazosRuta(
              ruta,
              instanteMs,
            ).pendientes;

          const trazosPixeles =
            convertirTrazos(
              trazos,
              convertirX,
              convertirY,
              viewportPixeles,
            );

          if (
            trazosPixeles.length ===
            0
          ) {
            return null;
          }

          return (
            <RutaPendienteBatch
              key={ruta.id}
              trazos={
                trazosPixeles
              }
              color={color}
              atenuada={
                haySeleccion
              }
              dibujarFlechas={
                dibujarFlechas
              }
              onSeleccionar={() =>
                onSeleccionarVehiculo(
                  ruta.vehiculoId,
                )
              }
            />
          );
        }

        const seguimiento =
          visual.seguimiento;

        if (!seguimiento) {
          return null;
        }

        return (
          <RutaSeleccionadaBatch
            key={ruta.id}
            ruta={ruta}
            seguimiento={
              seguimiento
            }
            instanteMs={
              instanteMs
            }
            color={color}
            convertirX={
              convertirX
            }
            convertirY={
              convertirY
            }
            viewportPixeles={
              viewportPixeles
            }
            nivelDetalle={
              nivelDetalle
            }
          />
        );
      })}
    </>
  );
}

function tipoVehiculoVisible(
  tipo: TipoVehiculo,
  mostrarAutos: boolean,
  mostrarMotos: boolean,
  mostrarBicicletas: boolean,
) {
  switch (tipo) {
    case "AUTO":
      return mostrarAutos;

    case "MOTO":
      return mostrarMotos;

    case "BICICLETA":
      return mostrarBicicletas;
  }
}

export const CapaRutas =
  memo(CapaRutasBase);

interface RutaPendienteBatchProps {
  trazos:
    TrazoPixeles[];

  color:
    string;

  atenuada:
    boolean;

  dibujarFlechas:
    boolean;

  onSeleccionar:
    () => void;
}

function RutaPendienteBatch({
  trazos,
  color,
  atenuada,
  dibujarFlechas,
  onSeleccionar,
}: RutaPendienteBatchProps) {
  return (
    <Shape
      stroke={color}
      fill={color}
      strokeWidth={2.5}
      opacity={
        atenuada
          ? 0.12
          : 0.55
      }
      dash={[
        9,
        6,
      ]}
      lineCap="round"
      lineJoin="round"
      hitStrokeWidth={18}
      perfectDrawEnabled={
        false
      }
      shadowForStrokeEnabled={
        false
      }
      sceneFunc={(
        context,
        shape,
      ) => {
        dibujarTrazos(
          context,
          shape,
          trazos,
          dibujarFlechas,
          6,
        );
      }}
      onClick={
        onSeleccionar
      }
      onTap={
        onSeleccionar
      }
    />
  );
}

interface RutaSeleccionadaBatchProps {
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

  viewportPixeles:
    ViewportPixeles;

  nivelDetalle:
    NivelDetalleMapa;
}

function RutaSeleccionadaBatch({
  ruta,
  seguimiento,
  instanteMs,
  color,
  convertirX,
  convertirY,
  viewportPixeles,
  nivelDetalle,
}: RutaSeleccionadaBatchProps) {
  const trazos =
    obtenerTrazosRuta(
      ruta,
      instanteMs,
      seguimiento
        .siguienteParada
        ?.orden,
    );

  const recorridos =
    convertirTrazos(
      trazos.recorridos,
      convertirX,
      convertirY,
      viewportPixeles,
    );

  const actuales =
    convertirTrazos(
      trazos.actuales,
      convertirX,
      convertirY,
      viewportPixeles,
    );

  const futuros =
    convertirTrazos(
      trazos.futuros,
      convertirX,
      convertirY,
      viewportPixeles,
    );

  const mostrarFlechas =
    nivelDetalle !== "BAJO";

  const usarSombra =
    nivelDetalle === "ALTO";

  const paradasVisibles =
    seguimiento.paradas.filter(
      ({ estado }) => {
        if (
          nivelDetalle ===
          "ALTO"
        ) {
          return true;
        }

        if (
          nivelDetalle ===
          "BAJO"
        ) {
          return false;
        }

        return (
          estado ===
            "SIGUIENTE" ||
          estado ===
            "EN_SERVICIO" ||
          estado ===
            "REFRIGERIO"
        );
      },
    );

  return (
    <>
      {/* RECORRIDO REALIZADO */}
      {recorridos.length >
        0 && (
        <Shape
          stroke={color}
          strokeWidth={3}
          opacity={0.4}
          lineCap="round"
          lineJoin="round"
          listening={false}
          perfectDrawEnabled={
            false
          }
          shadowForStrokeEnabled={
            false
          }
          sceneFunc={(
            context,
            shape,
          ) => {
            dibujarTrazos(
              context,
              shape,
              recorridos,
              false,
              0,
            );
          }}
        />
      )}

      {/* TRAMO ACTUAL */}
      {actuales.length >
        0 && (
        <Shape
          stroke={color}
          fill={color}
          strokeWidth={
            nivelDetalle ===
            "BAJO"
              ? 4
              : 6
          }
          opacity={1}
          lineCap="round"
          lineJoin="round"
          shadowColor={
            color
          }
          shadowBlur={
            usarSombra
              ? 7
              : 0
          }
          shadowOpacity={
            usarSombra
              ? 0.35
              : 0
          }
          listening={false}
          perfectDrawEnabled={
            false
          }
          shadowForStrokeEnabled={
            usarSombra
          }
          sceneFunc={(
            context,
            shape,
          ) => {
            dibujarTrazos(
              context,
              shape,
              actuales,
              mostrarFlechas,
              9,
            );
          }}
        />
      )}

      {/* RUTA FUTURA */}
      {futuros.length >
        0 && (
        <Shape
          stroke={color}
          fill={color}
          strokeWidth={
            nivelDetalle ===
            "BAJO"
              ? 2
              : 3
          }
          opacity={0.65}
          dash={[
            10,
            6,
          ]}
          lineCap="round"
          lineJoin="round"
          listening={false}
          perfectDrawEnabled={
            false
          }
          shadowForStrokeEnabled={
            false
          }
          sceneFunc={(
            context,
            shape,
          ) => {
            dibujarTrazos(
              context,
              shape,
              futuros,
              mostrarFlechas,
              7,
            );
          }}
        />
      )}

      {paradasVisibles.map(
        ({
          parada,
          estado,
        }) => (
          <ParadaMarker
            key={`${ruta.id}-parada-${parada.orden}`}
            parada={parada}
            estado={estado}
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

type TrazoRuta =
  Parameters<
    typeof convertirTrazoAPixeles
  >[0];

function convertirTrazos(
  trazos:
    readonly TrazoRuta[],

  convertirX:
    (valor: number) => number,

  convertirY:
    (valor: number) => number,

  viewport:
    ViewportPixeles,
): TrazoPixeles[] {
  const viewportValido =
    Number.isFinite(
      viewport.minX,
    ) &&
    Number.isFinite(
      viewport.maxX,
    ) &&
    Number.isFinite(
      viewport.minY,
    ) &&
    Number.isFinite(
      viewport.maxY,
    ) &&
    viewport.minX <=
      viewport.maxX &&
    viewport.minY <=
      viewport.maxY;

  return trazos
    .map(
      (trazo) =>
        convertirTrazoAPixeles(
          trazo,
          convertirX,
          convertirY,
        ),
    )
    .filter(
      (puntos) => {
        if (
          puntos.length <
          4
        ) {
          return false;
        }

        /*
         * Fail-safe:
         * si el viewport se rompe,
         * conservamos la ruta.
         */
        if (
          !viewportValido
        ) {
          return true;
        }

        return trazoIntersectaViewport(
          puntos,
          viewport,
        );
      },
    );
}

function trazoIntersectaViewport(
  puntos:
    TrazoPixeles,

  viewport:
    ViewportPixeles,
) {
  let minX =
    Infinity;

  let maxX =
    -Infinity;

  let minY =
    Infinity;

  let maxY =
    -Infinity;

  for (
    let i = 0;
    i <
    puntos.length;
    i += 2
  ) {
    const x =
      puntos[i];

    const y =
      puntos[i + 1];

    if (
      !Number.isFinite(x) ||
      !Number.isFinite(y)
    ) {
      continue;
    }

    minX =
      Math.min(
        minX,
        x,
      );

    maxX =
      Math.max(
        maxX,
        x,
      );

    minY =
      Math.min(
        minY,
        y,
      );

    maxY =
      Math.max(
        maxY,
        y,
      );
  }

  if (
    minX === Infinity ||
    minY === Infinity
  ) {
    return false;
  }

  return !(
    maxX <
      viewport.minX ||
    minX >
      viewport.maxX ||
    maxY <
      viewport.minY ||
    minY >
      viewport.maxY
  );
}

function dibujarTrazos(
  context:
    ContextoShape,

  shape:
    ShapeKonva,

  trazos:
    TrazoPixeles[],

  dibujarFlecha:
    boolean,

  tamanoFlecha:
    number,
) {
  /*
   * Líneas abiertas:
   * solamente stroke.
   */
  context.beginPath();

  for (
    const puntos of
    trazos
  ) {
    if (
      puntos.length <
      4
    ) {
      continue;
    }

    context.moveTo(
      puntos[0],
      puntos[1],
    );

    for (
      let i = 2;
      i <
      puntos.length;
      i += 2
    ) {
      context.lineTo(
        puntos[i],
        puntos[
          i + 1
        ],
      );
    }
  }

  context.strokeShape(
    shape,
  );

  if (
    !dibujarFlecha ||
    tamanoFlecha <=
      0
  ) {
    return;
  }

  /*
   * Únicamente las puntas se rellenan.
   */
  context.beginPath();

  for (
    const puntos of
    trazos
  ) {
    agregarFlecha(
      context,
      puntos,
      tamanoFlecha,
    );
  }

  context.fillShape(
    shape,
  );
}

function agregarFlecha(
  context:
    ContextoShape,

  puntos:
    TrazoPixeles,

  tamano:
    number,
) {
  if (
    puntos.length <
      4 ||
    tamano <=
      0
  ) {
    return;
  }

  const ultimo =
    puntos.length -
    2;

  const anterior =
    ultimo -
    2;

  const x2 =
    puntos[ultimo];

  const y2 =
    puntos[
      ultimo + 1
    ];

  const x1 =
    puntos[anterior];

  const y1 =
    puntos[
      anterior + 1
    ];

  const dx =
    x2 - x1;

  const dy =
    y2 - y1;

  const longitud =
    Math.hypot(
      dx,
      dy,
    );

  if (
    longitud <
    0.001
  ) {
    return;
  }

  const ux =
    dx /
    longitud;

  const uy =
    dy /
    longitud;

  const px =
    -uy;

  const py =
    ux;

  const largo =
    tamano;

  const ancho =
    tamano *
    0.65;

  const baseX =
    x2 -
    ux *
      largo;

  const baseY =
    y2 -
    uy *
      largo;

  const izquierdaX =
    baseX +
    px *
      ancho;

  const izquierdaY =
    baseY +
    py *
      ancho;

  const derechaX =
    baseX -
    px *
      ancho;

  const derechaY =
    baseY -
    py *
      ancho;

  context.moveTo(
    x2,
    y2,
  );

  context.lineTo(
    izquierdaX,
    izquierdaY,
  );

  context.lineTo(
    derechaX,
    derechaY,
  );

  context.closePath();
}

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

  x: number;
  y: number;
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

  const esTrasvase =
    parada.tipo ===
    "TRASVASE";

  const texto =
    esTrasvase
      ? "⇄"
      : estado ===
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
      listening={false}
    >
      <Circle
        radius={8}
        fill="#ffffff"
        stroke={color}
        strokeWidth={2}
        opacity={
          estado ===
          "FUTURA"
            ? 0.7
            : 1
        }
        perfectDrawEnabled={
          false
        }
      />

      <Text
        x={
          esTrasvase
            ? -9
            : -7
        }
        y={
          esTrasvase
            ? -7
            : -5
        }
        width={
          esTrasvase
            ? 18
            : 14
        }
        align="center"
        text={texto}
        fontSize={
          esTrasvase
            ? 13
            : 9
        }
        fontStyle="bold"
        fill={color}
        listening={false}
        perfectDrawEnabled={
          false
        }
      />
    </Group>
  );
}
