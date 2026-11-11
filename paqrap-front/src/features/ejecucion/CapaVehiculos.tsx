import {
  memo,
  useMemo,
} from "react";

import type {
  KonvaEventObject,
} from "konva/lib/Node";

import {
  Arrow,
  Circle,
  Group,
  Text,
} from "react-konva";

import type {
  TipoVehiculoResponseDto,
} from "@/api/flota.dto";

import {
  claveGrupoVehiculo,
  debeAgruparVehiculos,
} from "./agrupamientoVehiculos";

import type {
  RutaMapa,
  TipoVehiculo,
  VehiculoMapa,
} from "./ejecucion.types";

import type {
  SeguimientoRuta,
} from "./seguimientoRuta";

import type {
  NivelDetalleMapa,
} from "./mapaVisual.types";

type NivelSemaforo =
  | "TODOS"
  | "VACIO"
  | "VERDE"
  | "AMBAR"
  | "ROJO";

export interface VehiculoVisual {
  vehiculo:
    VehiculoMapa;

  ruta?:
    RutaMapa;

  seguimiento?:
    SeguimientoRuta;

  x: number;
  y: number;

  enMovimiento:
    boolean;

  progresoTramo:
    number;

  direccionGrados:
    number | null;
}

interface CapaVehiculosProps {
  vehiculosVisuales:
    VehiculoVisual[];

  tiposFlota:
    TipoVehiculoResponseDto[];

  vehiculoSeleccionadoId:
    string | null;

  nivelDetalle:
    NivelDetalleMapa;

  mostrarAutos:
    boolean;

  mostrarMotos:
    boolean;

  mostrarBicicletas:
    boolean;

  filtroCarga:
    NivelSemaforo;

  verdeHasta:
    number;

  ambarHasta:
    number;

  coloresVehiculo:
    Record<
      TipoVehiculo,
      string
    >;

  convertirX:
    (valor: number) => number;

  convertirY:
    (valor: number) => number;

  onSeleccionar:
    (id: string) => void;

  onTooltip: (
    event:
      KonvaEventObject<MouseEvent>,
    titulo: string,
    lineas: string[],
  ) => void;

  onLeave:
    () => void;
}

function CapaVehiculosBase({
  vehiculosVisuales,
  tiposFlota,
  vehiculoSeleccionadoId,
  nivelDetalle,
  mostrarAutos,
  mostrarMotos,
  mostrarBicicletas,
  filtroCarga,
  verdeHasta,
  ambarHasta,
  coloresVehiculo,
  convertirX,
  convertirY,
  onSeleccionar,
  onTooltip,
  onLeave,
}: CapaVehiculosProps) {
  const capacidadPorTipo =
    useMemo(
      () =>
        new Map<
          TipoVehiculo,
          number
        >(
          tiposFlota.map(
            (tipo) => [
              adaptarTipoVehiculo(
                tipo.tipo,
              ),
              tipo.capacidadPaquetes,
            ],
          ),
        ),
      [tiposFlota],
    );

  const gruposVehiculos =
    useMemo(() => {
      const grupos =
        new Map<
          string,
          VehiculoVisual[]
        >();

      for (
        const visual of
        vehiculosVisuales
      ) {
        const seleccionado =
          visual.vehiculo.id ===
          vehiculoSeleccionadoId;

        const clave =
          claveGrupoVehiculo(
            visual.vehiculo.id,
            visual.x,
            visual.y,
            visual.enMovimiento,
            seleccionado,
          );

        const grupo =
          grupos.get(
            clave,
          ) ?? [];

        grupo.push(
          visual,
        );

        grupos.set(
          clave,
          grupo,
        );
      }

      return [
        ...grupos.values(),
      ];
    }, [
      vehiculoSeleccionadoId,
      vehiculosVisuales,
    ]);

  return (
    <>
      {gruposVehiculos.map(
        (grupo) => {
          const visibles =
            grupo.filter(
              (item) => {
                if (
                  !tipoVehiculoVisible(
                    item.vehiculo.tipo,
                    mostrarAutos,
                    mostrarMotos,
                    mostrarBicicletas,
                  )
                ) {
                  return false;
                }

                const capacidad =
                  capacidadPorTipo.get(
                    item.vehiculo.tipo,
                  ) ?? 0;

                const nivel =
                  nivelSemaforoCarga(
                    item.vehiculo
                      .cargaActual,
                    capacidad,
                    verdeHasta,
                    ambarHasta,
                  );

                return (
                  filtroCarga ===
                    "TODOS" ||
                  filtroCarga ===
                    nivel
                );
              },
            );

          if (
            visibles.length ===
            0
          ) {
            return null;
          }

          const contieneSeleccionado =
            visibles.some(
              (item) =>
                item.vehiculo.id ===
                vehiculoSeleccionadoId,
            );

          if (
            debeAgruparVehiculos(
              visibles.length,
              contieneSeleccionado,
            )
          ) {
            let autos = 0;
            let motos = 0;
            let bicicletas = 0;
            let cargaTotal = 0;
            let capacidadTotal = 0;

            for (
              const item of
              visibles
            ) {
              switch (
                item.vehiculo.tipo
              ) {
                case "AUTO":
                  autos += 1;
                  break;

                case "MOTO":
                  motos += 1;
                  break;

                case "BICICLETA":
                  bicicletas += 1;
                  break;
              }

              cargaTotal +=
                item.vehiculo
                  .cargaActual;

              capacidadTotal +=
                capacidadPorTipo.get(
                  item.vehiculo.tipo,
                ) ?? 0;
            }

            return (
              <GrupoVehiculosMarker
                key={`grupo-${visibles[0].x}-${visibles[0].y}`}
                cantidad={
                  visibles.length
                }
                autos={autos}
                motos={motos}
                bicicletas={
                  bicicletas
                }
                cargaTotal={
                  cargaTotal
                }
                capacidadTotal={
                  capacidadTotal
                }
                posicionNegocioX={
                  visibles[0].x
                }
                posicionNegocioY={
                  visibles[0].y
                }
                x={
                  convertirX(
                    visibles[0].x,
                  )
                }
                y={
                  convertirY(
                    visibles[0].y,
                  )
                }
                nivelDetalle={
                  nivelDetalle
                }
                onTooltip={
                  onTooltip
                }
                onLeave={
                  onLeave
                }
              />
            );
          }

          return visibles.map(
            (
              item,
              index,
            ) => {
              const angulo =
                (
                  Math.PI *
                  2 *
                  index
                ) /
                Math.max(
                  visibles.length,
                  1,
                );

              const seleccionado =
                item.vehiculo.id ===
                vehiculoSeleccionadoId;

              const radio =
                visibles.length ===
                  1 ||
                item.enMovimiento ||
                seleccionado
                  ? 0
                  : 14;

              const capacidad =
                capacidadPorTipo.get(
                  item.vehiculo.tipo,
                ) ?? 0;

              return (
                <VehiculoMarker
                  key={
                    item.vehiculo.id
                  }
                  vehiculo={
                    item.vehiculo
                  }
                  enMovimiento={
                    item.enMovimiento
                  }
                  seleccionado={
                    seleccionado
                  }
                  atenuado={
                    vehiculoSeleccionadoId !==
                      null &&
                    !seleccionado
                  }
                  nivelDetalle={
                    nivelDetalle
                  }
                  posicionVisualX={
                    item.x
                  }
                  posicionVisualY={
                    item.y
                  }
                  progresoTramo={
                    item.progresoTramo
                  }
                  capacidad={
                    capacidad
                  }
                  direccionGrados={
                    item.direccionGrados
                  }
                  nivelCarga={
                    nivelSemaforoCarga(
                      item.vehiculo
                        .cargaActual,
                      capacidad,
                      verdeHasta,
                      ambarHasta,
                    )
                  }
                  colorTipo={
                    coloresVehiculo[
                      item.vehiculo
                        .tipo
                    ]
                  }
                  x={
                    convertirX(
                      item.x,
                    ) +
                    Math.cos(
                      angulo,
                    ) *
                      radio
                  }
                  y={
                    convertirY(
                      item.y,
                    ) +
                    Math.sin(
                      angulo,
                    ) *
                      radio
                  }
                  onSeleccionarVehiculo={
                    onSeleccionar
                  }
                  onTooltip={
                    onTooltip
                  }
                  onLeave={
                    onLeave
                  }
                />
              );
            },
          );
        },
      )}
    </>
  );
}

export const CapaVehiculos =
  memo(
    CapaVehiculosBase,
  );

interface VehiculoMarkerProps {
  vehiculo:
    VehiculoMapa;

  enMovimiento:
    boolean;

  seleccionado:
    boolean;

  atenuado:
    boolean;

  nivelDetalle:
    NivelDetalleMapa;

  posicionVisualX:
    number;

  posicionVisualY:
    number;

  progresoTramo:
    number;

  capacidad:
    number;

  nivelCarga:
    Exclude<
      NivelSemaforo,
      "TODOS"
    >;

  direccionGrados:
    number | null;

  colorTipo:
    string;

  x: number;
  y: number;

  onSeleccionarVehiculo:
    (id: string) => void;

  onTooltip: (
    event:
      KonvaEventObject<MouseEvent>,
    titulo: string,
    lineas: string[],
  ) => void;

  onLeave:
    () => void;
}

function VehiculoMarkerBase({
  vehiculo,
  seleccionado,
  atenuado,
  nivelDetalle,
  posicionVisualX,
  posicionVisualY,
  progresoTramo,
  capacidad,
  nivelCarga,
  direccionGrados,
  colorTipo,
  x,
  y,
  onSeleccionarVehiculo,
  onTooltip,
  onLeave,
}: VehiculoMarkerProps) {
  const color =
    vehiculo.estado ===
    "NO_DISPONIBLE"
      ? "#64748b"
      : colorSemaforo(
          nivelCarga,
        );

  const porcentajeCarga =
    capacidad > 0
      ? Math.min(
          100,
          Math.max(
            0,
            (
              vehiculo.cargaActual /
              capacidad
            ) *
              100,
          ),
        )
      : 0;

  const letra =
    vehiculo.tipo ===
    "AUTO"
      ? "A"
      : vehiculo.tipo ===
          "MOTO"
        ? "M"
        : "B";

  /*
   * LOD.
   *
   * Un vehículo seleccionado conserva más
   * información aunque el zoom sea bajo.
   */
  const mostrarDireccion =
    seleccionado ||
    nivelDetalle !== "BAJO";

  const mostrarId =
    seleccionado ||
    nivelDetalle !== "BAJO";

  const mostrarCarga =
    seleccionado ||
    nivelDetalle === "ALTO";

  const usarSombra =
    seleccionado ||
    nivelDetalle === "ALTO";

  const lineas = [
    `Tipo: ${vehiculo.tipo}`,
    `Estado: ${vehiculo.estado}`,
    `Posición confirmada: (${vehiculo.x}, ${vehiculo.y})`,
    `Posición visual: (${posicionVisualX.toFixed(2)}, ${posicionVisualY.toFixed(2)})`,
    `Avance de tramo: ${(progresoTramo * 100).toFixed(0)}%`,
    `Carga: ${vehiculo.cargaActual}/${capacidad || "—"} (${porcentajeCarga.toFixed(0)}%)`,
    `Semáforo de carga: ${nombreNivel(
      nivelCarga,
    )}`,
    vehiculo.motivoDetencion
      ? `Motivo de detención: ${nombreMotivoDetencion(
          vehiculo.motivoDetencion,
        )}`
      : "Sin detención activa",
    vehiculo.detenidoHasta
      ? `Disponible nuevamente: ${formatearFecha(
          vehiculo.detenidoHasta,
        )}`
      : "",
    vehiculo.averiadoHasta
      ? `Averiado hasta: ${formatearFecha(
          vehiculo.averiadoHasta,
        )}`
      : "Sin avería activa",
  ];

  return (
    <Group
      x={x}
      y={y}
      opacity={
        atenuado
          ? 0.25
          : 1
      }
      onClick={() =>
        onSeleccionarVehiculo(
          vehiculo.id,
        )
      }
      onTap={() =>
        onSeleccionarVehiculo(
          vehiculo.id,
        )
      }
      onMouseEnter={(
        event,
      ) =>
        onTooltip(
          event,
          vehiculo.id,
          lineas,
        )
      }
      onMouseMove={(
        event,
      ) =>
        onTooltip(
          event,
          vehiculo.id,
          lineas,
        )
      }
      onMouseLeave={
        onLeave
      }
    >
      {seleccionado && (
        <Circle
          radius={15}
          stroke={color}
          strokeWidth={2}
          opacity={0.35}
          perfectDrawEnabled={
            false
          }
        />
      )}

      {mostrarDireccion &&
        direccionGrados !==
          null &&
        vehiculo.estado ===
          "EN_RUTA" && (
          <Arrow
            points={[
              0,
              0,
              17,
              0,
            ]}
            rotation={
              direccionGrados
            }
            stroke={
              colorTipo
            }
            fill={
              colorTipo
            }
            strokeWidth={2}
            pointerLength={5}
            pointerWidth={5}
            opacity={0.9}
            listening={false}
            perfectDrawEnabled={
              false
            }
          />
        )}

      <Circle
        radius={10}
        fill="#ffffff"
        stroke={color}
        strokeWidth={
          seleccionado
            ? 3.5
            : 2.5
        }
        shadowColor="#0f172a"
        shadowBlur={
          usarSombra
            ? seleccionado
              ? 8
              : 4
            : 0
        }
        shadowOpacity={
          usarSombra
            ? 0.3
            : 0
        }
        shadowForStrokeEnabled={
          usarSombra
        }
        perfectDrawEnabled={
          false
        }
      />

      {/*
       * La letra de tipo se conserva siempre
       * porque cuesta poco y permite distinguir
       * AUTO/MOTO/BICI.
       */}
      <Text
        x={-7}
        y={-6}
        width={14}
        align="center"
        text={letra}
        fontSize={11}
        fontStyle="bold"
        fill={
          colorTipo
        }
        listening={false}
        perfectDrawEnabled={
          false
        }
      />

      {mostrarId && (
        <Text
          x={-28}
          y={13}
          width={56}
          align="center"
          text={
            vehiculo.id
          }
          fontSize={8}
          fontStyle="bold"
          fill="#0f172a"
          listening={false}
          perfectDrawEnabled={
            false
          }
        />
      )}

      {mostrarCarga && (
        <Text
          x={-30}
          y={23}
          width={60}
          align="center"
          text={`${vehiculo.cargaActual}/${capacidad || "—"}`}
          fontSize={7}
          fill="#475569"
          listening={false}
          perfectDrawEnabled={
            false
          }
        />
      )}

      {/*
       * Avería / mantenimiento / refrigerio
       * siempre se conserva porque es información
       * operacional importante.
       */}
      {vehiculo.motivoDetencion && (
        <Group
          x={9}
          y={-13}
          listening={false}
        >
          <Circle
            radius={7}
            fill={
              colorMotivoDetencion(
                vehiculo.motivoDetencion,
              )
            }
            stroke="#ffffff"
            strokeWidth={1}
            perfectDrawEnabled={
              false
            }
          />

          <Text
            x={-6}
            y={-5}
            width={12}
            align="center"
            text={
              simboloMotivoDetencion(
                vehiculo.motivoDetencion,
              )
            }
            fontSize={8}
            fontStyle="bold"
            fill="#ffffff"
            listening={false}
            perfectDrawEnabled={
              false
            }
          />
        </Group>
      )}
    </Group>
  );
}

function vehiculoMarkerIgual(
  anterior:
    Readonly<VehiculoMarkerProps>,

  siguiente:
    Readonly<VehiculoMarkerProps>,
) {
  /*
   * Los que se mueven deben continuar
   * actualizándose a 10 FPS.
   */
  if (
    anterior.enMovimiento ||
    siguiente.enMovimiento
  ) {
    return false;
  }

  return (
    anterior.vehiculo ===
      siguiente.vehiculo &&
    anterior.seleccionado ===
      siguiente.seleccionado &&
    anterior.atenuado ===
      siguiente.atenuado &&
    anterior.nivelDetalle ===
      siguiente.nivelDetalle &&
    anterior.posicionVisualX ===
      siguiente.posicionVisualX &&
    anterior.posicionVisualY ===
      siguiente.posicionVisualY &&
    anterior.progresoTramo ===
      siguiente.progresoTramo &&
    anterior.capacidad ===
      siguiente.capacidad &&
    anterior.nivelCarga ===
      siguiente.nivelCarga &&
    anterior.direccionGrados ===
      siguiente.direccionGrados &&
    anterior.colorTipo ===
      siguiente.colorTipo &&
    anterior.x ===
      siguiente.x &&
    anterior.y ===
      siguiente.y &&
    anterior.onSeleccionarVehiculo ===
      siguiente.onSeleccionarVehiculo &&
    anterior.onTooltip ===
      siguiente.onTooltip &&
    anterior.onLeave ===
      siguiente.onLeave
  );
}

const VehiculoMarker =
  memo(
    VehiculoMarkerBase,
    vehiculoMarkerIgual,
  );

interface GrupoVehiculosMarkerProps {
  cantidad:
    number;

  autos:
    number;

  motos:
    number;

  bicicletas:
    number;

  cargaTotal:
    number;

  capacidadTotal:
    number;

  posicionNegocioX:
    number;

  posicionNegocioY:
    number;

  x: number;
  y: number;

  nivelDetalle:
    NivelDetalleMapa;

  onTooltip: (
    event:
      KonvaEventObject<MouseEvent>,
    titulo: string,
    lineas: string[],
  ) => void;

  onLeave:
    () => void;
}

function GrupoVehiculosMarkerBase({
  cantidad,
  autos,
  motos,
  bicicletas,
  cargaTotal,
  capacidadTotal,
  posicionNegocioX,
  posicionNegocioY,
  x,
  y,
  nivelDetalle,
  onTooltip,
  onLeave,
}: GrupoVehiculosMarkerProps) {
  const porcentajeCarga =
    capacidadTotal > 0
      ? (
          cargaTotal /
          capacidadTotal
        ) *
        100
      : 0;

  const nivel =
    nivelSemaforoCarga(
      cargaTotal,
      capacidadTotal,
      60,
      85,
    );

  const color =
    colorSemaforo(
      nivel,
    );

  const mostrarPorcentaje =
    nivelDetalle !== "BAJO";

  const usarSombra =
    nivelDetalle === "ALTO";

  const lineas = [
    `Autos: ${autos}`,
    `Motos: ${motos}`,
    `Bicicletas: ${bicicletas}`,
    `Carga: ${cargaTotal}/${capacidadTotal || "—"} (${porcentajeCarga.toFixed(0)}%)`,
    `Coordenadas: (${posicionNegocioX}, ${posicionNegocioY})`,
  ];

  return (
    <Group
      x={x}
      y={y}
      onMouseEnter={(
        event,
      ) =>
        onTooltip(
          event,
          `${cantidad} vehículos`,
          lineas,
        )
      }
      onMouseMove={(
        event,
      ) =>
        onTooltip(
          event,
          `${cantidad} vehículos`,
          lineas,
        )
      }
      onMouseLeave={
        onLeave
      }
    >
      <Circle
        radius={16}
        fill="#0f172a"
        stroke={color}
        strokeWidth={2.5}
        shadowColor="#0f172a"
        shadowBlur={
          usarSombra
            ? 5
            : 0
        }
        shadowOpacity={
          usarSombra
            ? 0.3
            : 0
        }
        shadowForStrokeEnabled={
          usarSombra
        }
        perfectDrawEnabled={
          false
        }
      />

      <Text
        x={-14}
        y={-6}
        width={28}
        align="center"
        text={String(
          cantidad,
        )}
        fontSize={11}
        fontStyle="bold"
        fill="#ffffff"
        listening={false}
        perfectDrawEnabled={
          false
        }
      />

      {mostrarPorcentaje && (
        <Text
          x={-18}
          y={18}
          width={36}
          align="center"
          text={`${porcentajeCarga.toFixed(0)}%`}
          fontSize={7}
          fontStyle="bold"
          fill="#334155"
          listening={false}
          perfectDrawEnabled={
            false
          }
        />
      )}
    </Group>
  );
}

const GrupoVehiculosMarker =
  memo(
    GrupoVehiculosMarkerBase,
  );

function adaptarTipoVehiculo(
  tipo:
    TipoVehiculoResponseDto["tipo"],
): TipoVehiculo {
  switch (tipo) {
    case "CAR":
      return "AUTO";

    case "MOTORCYCLE":
      return "MOTO";

    case "BICYCLE":
      return "BICICLETA";
  }
}

function tipoVehiculoVisible(
  tipo:
    TipoVehiculo,

  mostrarAutos:
    boolean,

  mostrarMotos:
    boolean,

  mostrarBicicletas:
    boolean,
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

function nivelSemaforoCarga(
  carga: number,
  capacidad: number,
  verdeHasta: number,
  ambarHasta: number,
): Exclude<
  NivelSemaforo,
  "TODOS"
> {
  const porcentaje =
    capacidad > 0
      ? (
          carga /
          capacidad
        ) *
        100
      : 0;

  if (carga <= 0) {
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

function nombreMotivoDetencion(
  motivo:
    VehiculoMapa["motivoDetencion"],
) {
  switch (motivo) {
    case "AVERIA":
      return "Avería";

    case "MANTENIMIENTO":
      return "Mantenimiento";

    case "REFRIGERIO":
      return "Refrigerio";

    default:
      return "Detención";
  }
}

function simboloMotivoDetencion(
  motivo:
    NonNullable<
      VehiculoMapa["motivoDetencion"]
    >,
) {
  switch (motivo) {
    case "AVERIA":
      return "!";

    case "MANTENIMIENTO":
      return "M";

    case "REFRIGERIO":
      return "☕";
  }
}

function colorMotivoDetencion(
  motivo:
    NonNullable<
      VehiculoMapa["motivoDetencion"]
    >,
) {
  switch (motivo) {
    case "AVERIA":
      return "#dc2626";

    case "MANTENIMIENTO":
      return "#64748b";

    case "REFRIGERIO":
      return "#d97706";
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
    new Date(valor),
  );
}