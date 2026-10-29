import {
  useMemo,
  useState,
} from "react";

import {
  Clock3,
  Navigation,
  Package,
  RotateCcw,
  X,
  ZoomIn,
  ZoomOut,
} from "lucide-react";

import type {
  KonvaEventObject,
} from "konva/lib/Node";

import {
  Arrow,
  Circle,
  Group,
  Layer,
  Line,
  Rect,
  Stage,
  Text,
} from "react-konva";

import type {
  AveriaResponseDto,
} from "@/api/incidencias.dto";

import type {
  EstadoEscenarioBackend,
} from "@/api/escenarios.dto";

import type {
  BloqueoMonitoreoDto,
} from "@/api/monitoreo.dto";

import {
  useElementSize,
} from "@/hooks/useElementSize";

import {
  useRelojSimuladoAnimado,
} from "@/hooks/useRelojSimuladoAnimado";

import type {
  AlmacenMapa,
  ParadaMapa,
  PedidoOperacion,
  RutaMapa,
  TipoVehiculo,
  VehiculoMapa,
} from "./ejecucion.types";

import {
  horizontalPrimeroParaVehiculo,
} from "./movimientoVehiculos";

import {
  nombreParada,
  obtenerSeguimientoRuta,
  type SeguimientoRuta,
} from "./seguimientoRuta";

const ANCHO_MAPA = 70;
const ALTO_MAPA = 50;

const PADDING = 34;

const COLORES_VEHICULO:
  Record<
    TipoVehiculo,
    string
  > = {
  AUTO: "#3b82f6",
  MOTO: "#22c55e",
  BICICLETA: "#f59e0b",
};

type FiltroMapa =
  | "AUTOS"
  | "MOTOS"
  | "BICICLETAS"
  | "PEDIDOS"
  | "RUTAS"
  | "BLOQUEOS"
  | "AVERIAS";

interface TooltipMapa {
  x: number;
  y: number;
  titulo: string;
  lineas: string[];
}

interface VistaMapa {
  escala: number;
  x: number;
  y: number;
}

interface MapaOperacionProps {
  pedidos:
    PedidoOperacion[];

  almacenes:
    AlmacenMapa[];

  vehiculos:
    VehiculoMapa[];

  rutas:
    RutaMapa[];

  bloqueos:
    BloqueoMonitoreoDto[];

  averias:
    AveriaResponseDto[];

  instanteSimulado:
    string;

  factorAceleracion:
    number;

  estadoEscenario:
    EstadoEscenarioBackend;

  onSeleccionarVehiculo?: (id: string) => void;
  onSeleccionarPedido?: (id: string) => void;
  onSeleccionarAlmacen?: (id: string) => void;
}

interface VehiculoVisual {
  vehiculo:
    VehiculoMapa;

  ruta?: RutaMapa;

  seguimiento?:
    SeguimientoRuta;

  x: number;
  y: number;

  enMovimiento:
    boolean;

  progresoTramo:
    number;
}

export function MapaOperacion({
  pedidos,
  almacenes,
  vehiculos,
  rutas,
  bloqueos,
  averias,
  instanteSimulado,
  factorAceleracion,
  estadoEscenario,
  onSeleccionarVehiculo,
  onSeleccionarPedido,
  onSeleccionarAlmacen,
}: MapaOperacionProps) {
  const {
    ref: contenedorRef,
    width,
    height,
  } =
    useElementSize<HTMLElement>();

  const [
    vista,
    setVista,
  ] =
    useState<VistaMapa>({
      escala: 1,
      x: 0,
      y: 0,
    });

  const [
    tooltip,
    setTooltip,
  ] =
    useState<
      TooltipMapa | null
    >(null);

  const [
    vehiculoSeleccionadoId,
    setVehiculoSeleccionadoId,
  ] =
    useState<
      string | null
    >(null);

  const [
    filtros,
    setFiltros,
  ] =
    useState<
      Record<
        FiltroMapa,
        boolean
      >
    >({
      AUTOS: true,
      MOTOS: true,
      BICICLETAS: true,
      PEDIDOS: true,
      RUTAS: true,
      BLOQUEOS: true,
      AVERIAS: true,
    });

  /*
   * El servidor es la fuente
   * verdadera del reloj.
   *
   * Este hook solamente suaviza
   * visualmente el tiempo entre
   * respuestas del polling.
   */
  const instanteAnimadoMs =
    useRelojSimuladoAnimado(
      instanteSimulado,
      factorAceleracion,
      estadoEscenario,
    );

  /*
   * Conversión coordenadas de negocio
   * 70 x 50 a pixels de Konva.
   */
  const geometria =
    useMemo(() => {
      const anchoDisponible =
        Math.max(
          1,
          width -
            PADDING * 2,
        );

      const altoDisponible =
        Math.max(
          1,
          height -
            PADDING * 2,
        );

      const escalaBase =
        Math.min(
          anchoDisponible /
            ANCHO_MAPA,

          altoDisponible /
            ALTO_MAPA,
        );

      const ancho =
        ANCHO_MAPA *
        escalaBase;

      const alto =
        ALTO_MAPA *
        escalaBase;

      const origenX =
        (width - ancho) /
        2;

      const origenY =
        (height - alto) /
        2;

      return {
        escalaBase,

        ancho,
        alto,

        origenX,
        origenY,

        x: (
          valor: number,
        ) =>
          origenX +
          valor *
            escalaBase,

        y: (
          valor: number,
        ) =>
          origenY +
          (ALTO_MAPA -
            valor) *
            escalaBase,
      };
    }, [
      height,
      width,
    ]);

  const rutasPorVehiculo =
    useMemo(() => {
      return new Map(
        rutas.map(
          (ruta) => [
            ruta.vehiculoId,
            ruta,
          ],
        ),
      );
    }, [rutas]);

  /*
   * Posición visual animada
   * para cada vehículo.
   */
  const vehiculosVisuales =
    useMemo<
      VehiculoVisual[]
    >(() => {
      return vehiculos.map(
        (vehiculo) => {
          const ruta =
            rutasPorVehiculo.get(
              vehiculo.id,
            );

          if (!ruta) {
            return {
              vehiculo,

              x: vehiculo.x,
              y: vehiculo.y,

              enMovimiento:
                false,

              progresoTramo:
                0,
            };
          }

          const seguimiento =
            obtenerSeguimientoRuta(
              ruta,
              vehiculo,
              instanteAnimadoMs,
            );

          return {
            vehiculo,
            ruta,
            seguimiento,

            x:
              seguimiento
                .posicionVisual
                .x,

            y:
              seguimiento
                .posicionVisual
                .y,

            enMovimiento:
              seguimiento
                .enMovimiento,

            progresoTramo:
              seguimiento
                .progresoTramo,
          };
        },
      );
    }, [
      instanteAnimadoMs,
      rutasPorVehiculo,
      vehiculos,
    ]);

  const visualSeleccionado =
    useMemo(
      () =>
        vehiculosVisuales.find(
          (item) =>
            item.vehiculo
              .id ===
            vehiculoSeleccionadoId,
        ) ?? null,

      [
        vehiculoSeleccionadoId,
        vehiculosVisuales,
      ],
    );

  /*
   * Agrupamos vehículos estacionados
   * en el mismo punto para evitar
   * 37 iconos exactamente superpuestos.
   */
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
          visual.vehiculo
            .id ===
          vehiculoSeleccionadoId;

        const clave =
          visual.enMovimiento ||
          seleccionado
            ? `individual-${visual.vehiculo.id}`
            : `fijo-${visual.x.toFixed(
                3,
              )},${visual.y.toFixed(
                3,
              )}`;

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

  function alternarFiltro(
    filtro:
      FiltroMapa,
  ) {
    setFiltros(
      (actuales) => ({
        ...actuales,

        [filtro]:
          !actuales[
            filtro
          ],
      }),
    );
  }

  function mostrarTooltip(
    event:
      KonvaEventObject<MouseEvent>,

    titulo: string,

    lineas: string[],
  ) {
    const rect =
      contenedorRef.current
        ?.getBoundingClientRect();

    if (!rect) {
      return;
    }

    setTooltip({
      x:
        event.evt.clientX -
        rect.left +
        12,

      y:
        event.evt.clientY -
        rect.top +
        12,

      titulo,
      lineas,
    });
  }

  function ocultarTooltip() {
    setTooltip(null);
  }

  function manejarRueda(
    event:
      KonvaEventObject<WheelEvent>,
  ) {
    event.evt.preventDefault();

    const stage =
      event.target.getStage();

    const puntero =
      stage
        ?.getPointerPosition();

    if (!puntero) {
      return;
    }

    const escalaAnterior =
      vista.escala;

    const puntoMapa = {
      x:
        (puntero.x -
          vista.x) /
        escalaAnterior,

      y:
        (puntero.y -
          vista.y) /
        escalaAnterior,
    };

    const factor =
      event.evt.deltaY >
      0
        ? 0.9
        : 1.1;

    const nuevaEscala =
      Math.min(
        3.5,
        Math.max(
          0.75,

          escalaAnterior *
            factor,
        ),
      );

    setVista({
      escala:
        nuevaEscala,

      x:
        puntero.x -
        puntoMapa.x *
          nuevaEscala,

      y:
        puntero.y -
        puntoMapa.y *
          nuevaEscala,
    });
  }

  function aplicarZoom(
    factor: number,
  ) {
    const centro = {
      x:
        width / 2,

      y:
        height / 2,
    };

    const puntoMapa = {
      x:
        (centro.x -
          vista.x) /
        vista.escala,

      y:
        (centro.y -
          vista.y) /
        vista.escala,
    };

    const nuevaEscala =
      Math.min(
        3.5,
        Math.max(
          0.75,

          vista.escala *
            factor,
        ),
      );

    setVista({
      escala:
        nuevaEscala,

      x:
        centro.x -
        puntoMapa.x *
          nuevaEscala,

      y:
        centro.y -
        puntoMapa.y *
          nuevaEscala,
    });
  }

  function reiniciarVista() {
    setVista({
      escala: 1,
      x: 0,
      y: 0,
    });
  }

  return (
    <section
      ref={
        contenedorRef
      }
      className="relative h-full min-h-[720px] overflow-hidden bg-slate-100"
    >
      {width > 0 &&
        height > 0 && (
          <Stage
            width={width}
            height={height}
            draggable

            x={vista.x}
            y={vista.y}

            scaleX={
              vista.escala
            }

            scaleY={
              vista.escala
            }

            onWheel={
              manejarRueda
            }

            onDragEnd={(
              event,
            ) =>
              setVista(
                (actual) => ({
                  ...actual,

                  x:
                    event
                      .target
                      .x(),

                  y:
                    event
                      .target
                      .y(),
                }),
              )
            }

            onMouseDown={(
              event,
            ) => {
              if (
                event.target ===
                event.target.getStage()
              ) {
                setVehiculoSeleccionadoId(
                  null,
                );
              }
            }}
          >
            {/* RETÍCULA */}
            <Layer
              listening={
                false
              }
            >
              <Rect
                x={
                  geometria
                    .origenX
                }
                y={
                  geometria
                    .origenY
                }
                width={
                  geometria
                    .ancho
                }
                height={
                  geometria
                    .alto
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
                    ANCHO_MAPA +
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
                        ALTO_MAPA,
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
                    ALTO_MAPA +
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
                        ANCHO_MAPA,
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

            <Layer>
              {/* BLOQUEOS */}
              {filtros.BLOQUEOS &&
                bloqueos.map(
                  (
                    bloqueo,
                  ) => {
                    const puntos =
                      bloqueo.nodos.flatMap(
                        (
                          nodo,
                        ) => [
                          geometria.x(
                            nodo.x,
                          ),

                          geometria.y(
                            nodo.y,
                          ),
                        ],
                      );

                    return (
                      <Group
                        key={`bloqueo-${bloqueo.id}`}

                        onMouseEnter={(
                          event,
                        ) =>
                          mostrarTooltip(
                            event,

                            `Bloqueo #${bloqueo.id}`,

                            [
                              `${bloqueo.nodos.length} nodos`,

                              `Inicio: ${formatearFecha(
                                bloqueo.inicio,
                              )}`,

                              `Fin: ${formatearFecha(
                                bloqueo.fin,
                              )}`,
                            ],
                          )
                        }

                        onMouseMove={(
                          event,
                        ) =>
                          mostrarTooltip(
                            event,

                            `Bloqueo #${bloqueo.id}`,

                            [
                              `${bloqueo.nodos.length} nodos`,

                              `Inicio: ${formatearFecha(
                                bloqueo.inicio,
                              )}`,

                              `Fin: ${formatearFecha(
                                bloqueo.fin,
                              )}`,
                            ],
                          )
                        }

                        onMouseLeave={
                          ocultarTooltip
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
                              x={geometria.x(
                                nodo.x,
                              )}
                              y={geometria.y(
                                nodo.y,
                              )}
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

              {/* RUTAS */}
              {filtros.RUTAS &&
                rutas.map(
                  (ruta) => {
                    const visual =
                      vehiculosVisuales.find(
                        (
                          item,
                        ) =>
                          item
                            .vehiculo
                            .id ===
                          ruta.vehiculoId,
                      );

                    if (
                      !visual
                    ) {
                      return null;
                    }

                    const color =
                      COLORES_VEHICULO[
                        ruta
                          .tipoVehiculo
                      ];

                    const seleccionada =
                      ruta.vehiculoId ===
                      vehiculoSeleccionadoId;

                    const haySeleccion =
                      vehiculoSeleccionadoId !==
                      null;

                    /*
                     * Para rutas no seleccionadas
                     * mostramos únicamente el
                     * futuro pendiente.
                     */
                    if (
                      !seleccionada
                    ) {
                      const pendientes =
                        obtenerParadasPendientes(
                          ruta,
                          instanteAnimadoMs,
                        );

                      const puntos =
                        construirPuntosOrtogonales(
                          [
                            {
                              x:
                                visual.x,

                              y:
                                visual.y,
                            },

                            ...pendientes.map(
                              (
                                parada,
                              ) => ({
                                x:
                                  parada.x,

                                y:
                                  parada.y,
                              }),
                            ),
                          ],

                          geometria.x,
                          geometria.y,

                          horizontalPrimeroParaVehiculo(
                            ruta.vehiculoId,
                          ),
                        );

                      if (
                        puntos.length <
                        4
                      ) {
                        return null;
                      }

                      return (
                        <Arrow
                          key={
                            ruta.id
                          }
                          points={
                            puntos
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
                            setVehiculoSeleccionadoId(
                              ruta.vehiculoId,
                            )
                          }
                          onTap={() =>
                            setVehiculoSeleccionadoId(
                              ruta.vehiculoId,
                            )
                          }
                        />
                      );
                    }

                    const seguimiento =
                      visual.seguimiento;

                    if (
                      !seguimiento
                    ) {
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
                          vehiculo={
                            visual.vehiculo
                          }
                          seguimiento={
                            seguimiento
                          }
                          instanteMs={
                            instanteAnimadoMs
                          }
                          color={
                            color
                          }
                          convertirX={
                            geometria.x
                          }
                          convertirY={
                            geometria.y
                          }
                        />
                      </Group>
                    );
                  },
                )}

              {/* PEDIDOS */}
              {filtros.PEDIDOS &&
                pedidos.map(
                  (pedido) => {
                    const color =
                      colorPedido(
                        pedido,
                        instanteAnimadoMs,
                      );

                    return (
                      <Circle
                        key={
                          pedido.id
                        }
                        x={geometria.x(
                          pedido.x,
                        )}
                        y={geometria.y(
                          pedido.y,
                        )}
                        radius={
                          5.5
                        }
                        fill={
                          color
                        }
                        stroke="#ffffff"
                        strokeWidth={
                          1
                        }
                        opacity={
                          pedido.estado ===
                          "ENTREGADO"
                            ? 0.75
                            : 0.95
                        }
                        onClick={() => onSeleccionarPedido?.(pedido.id)}
                        onTap={() => onSeleccionarPedido?.(pedido.id)}
                        onMouseEnter={(
                          event,
                        ) =>
                          mostrarTooltip(
                            event,

                            pedido.id,

                            [
                              `Cliente: ${pedido.clienteId}`,

                              `Coordenadas: (${pedido.x}, ${pedido.y})`,

                              `Paquetes: ${pedido.cantidad}`,

                              `Plazo: ${pedido.plazoHoras} h`,

                              `Estado: ${pedido.estado}`,

                              `Vence: ${formatearFecha(
                                pedido.vence,
                              )}`,
                            ],
                          )
                        }
                        onMouseMove={(
                          event,
                        ) =>
                          mostrarTooltip(
                            event,

                            pedido.id,

                            [
                              `Cliente: ${pedido.clienteId}`,

                              `Coordenadas: (${pedido.x}, ${pedido.y})`,

                              `Paquetes: ${pedido.cantidad}`,

                              `Plazo: ${pedido.plazoHoras} h`,

                              `Estado: ${pedido.estado}`,

                              `Vence: ${formatearFecha(
                                pedido.vence,
                              )}`,
                            ],
                          )
                        }
                        onMouseLeave={
                          ocultarTooltip
                        }
                      />
                    );
                  },
                )}

              {/* ALMACENES */}
              {almacenes.map(
                (almacen) => {
                  const central =
                    almacen.tipo ===
                    "CENTRAL";

                  return (
                    <Group
                      key={
                        almacen.id
                      }
                      x={geometria.x(
                        almacen.x,
                      )}
                      y={geometria.y(
                        almacen.y,
                      )}
                      onClick={() => onSeleccionarAlmacen?.(almacen.id)}
                      onTap={() => onSeleccionarAlmacen?.(almacen.id)}
                      onMouseEnter={(
                        event,
                      ) =>
                        mostrarTooltip(
                          event,

                          `Almacén ${almacen.nombre}`,

                          [
                            `ID: ${almacen.id}`,

                            `Coordenadas: (${almacen.x}, ${almacen.y})`,

                            central
                              ? "Inventario ilimitado"
                              : `Stock: ${almacen.stock ?? 0} / ${almacen.capacidad ?? 1000}`,
                          ],
                        )
                      }
                      onMouseMove={(
                        event,
                      ) =>
                        mostrarTooltip(
                          event,

                          `Almacén ${almacen.nombre}`,

                          [
                            `ID: ${almacen.id}`,

                            `Coordenadas: (${almacen.x}, ${almacen.y})`,

                            central
                              ? "Inventario ilimitado"
                              : `Stock: ${almacen.stock ?? 0} / ${almacen.capacidad ?? 1000}`,
                          ],
                        )
                      }
                      onMouseLeave={
                        ocultarTooltip
                      }
                    >
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
                            : "#ffffff"
                        }
                        stroke={
                          central
                            ? "#2563eb"
                            : "#22c55e"
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
                            : "#16a34a"
                        }
                      />

                      <Text
                        x={-46}
                        y={25}
                        width={
                          92
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
                    </Group>
                  );
                },
              )}

              {/* VEHÍCULOS */}
              {gruposVehiculos.map(
                (grupo) => {
                  const visibles =
                    grupo.filter(
                      (
                        item,
                      ) =>
                        filtroVehiculoActivo(
                          item
                            .vehiculo
                            .tipo,

                          filtros,
                        ),
                    );

                  if (
                    visibles.length ===
                    0
                  ) {
                    return null;
                  }

                  const contieneSeleccionado =
                    visibles.some(
                      (
                        item,
                      ) =>
                        item
                          .vehiculo
                          .id ===
                        vehiculoSeleccionadoId,
                    );

                  if (
                    visibles.length >
                      6 &&
                    !contieneSeleccionado
                  ) {
                    return (
                      <GrupoVehiculosMarker
                        key={`grupo-${visibles[0].x}-${visibles[0].y}`}
                        vehiculos={visibles.map(
                          (
                            item,
                          ) =>
                            item.vehiculo,
                        )}
                        x={geometria.x(
                          visibles[0].x,
                        )}
                        y={geometria.y(
                          visibles[0].y,
                        )}
                        onTooltip={
                          mostrarTooltip
                        }
                        onLeave={
                          ocultarTooltip
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
                        (Math.PI *
                          2 *
                          index) /
                        Math.max(
                          visibles.length,
                          1,
                        );

                      const seleccionado =
                        item
                          .vehiculo
                          .id ===
                        vehiculoSeleccionadoId;

                      const radio =
                        visibles.length ===
                          1 ||
                        item.enMovimiento ||
                        seleccionado
                          ? 0
                          : 14;

                      return (
                        <VehiculoMarker
                          key={
                            item
                              .vehiculo
                              .id
                          }
                          vehiculo={
                            item.vehiculo
                          }
                          seleccionado={
                            seleccionado
                          }
                          atenuado={
                            vehiculoSeleccionadoId !==
                              null &&
                            !seleccionado
                          }
                          posicionVisual={{
                            x:
                              item.x,

                            y:
                              item.y,
                          }}
                          progresoTramo={
                            item.progresoTramo
                          }
                          x={
                            geometria.x(
                              item.x,
                            ) +
                            Math.cos(
                              angulo,
                            ) *
                              radio
                          }
                          y={
                            geometria.y(
                              item.y,
                            ) +
                            Math.sin(
                              angulo,
                            ) *
                              radio
                          }
                          onSeleccionar={() => {
                            setVehiculoSeleccionadoId(item.vehiculo.id);
                            onSeleccionarVehiculo?.(item.vehiculo.id);
                          }}
                          onTooltip={
                            mostrarTooltip
                          }
                          onLeave={
                            ocultarTooltip
                          }
                        />
                      );
                    },
                  );
                },
              )}

              {/* AVERÍAS */}
              {filtros.AVERIAS &&
                averias.map(
                  (averia) => (
                    <Group
                      key={`averia-${averia.id}`}
                      x={geometria.x(
                        averia.x,
                      )}
                      y={geometria.y(
                        averia.y,
                      )}
                      onMouseEnter={(
                        event,
                      ) =>
                        mostrarTooltip(
                          event,

                          `Avería ${averia.tipo}`,

                          [
                            `Vehículo: ${averia.vehiculoId}`,

                            `Coordenadas: (${averia.x}, ${averia.y})`,

                            `Hasta: ${formatearFecha(
                              averia.indisponibleHasta,
                            )}`,
                          ],
                        )
                      }
                      onMouseMove={(
                        event,
                      ) =>
                        mostrarTooltip(
                          event,

                          `Avería ${averia.tipo}`,

                          [
                            `Vehículo: ${averia.vehiculoId}`,

                            `Coordenadas: (${averia.x}, ${averia.y})`,

                            `Hasta: ${formatearFecha(
                              averia.indisponibleHasta,
                            )}`,
                          ],
                        )
                      }
                      onMouseLeave={
                        ocultarTooltip
                      }
                    >
                      <Circle
                        radius={
                          10
                        }
                        fill="#fee2e2"
                        stroke="#dc2626"
                        strokeWidth={
                          2
                        }
                      />

                      <Line
                        points={[
                          -4,
                          -4,
                          4,
                          4,
                        ]}
                        stroke="#dc2626"
                        strokeWidth={
                          2.5
                        }
                      />

                      <Line
                        points={[
                          4,
                          -4,
                          -4,
                          4,
                        ]}
                        stroke="#dc2626"
                        strokeWidth={
                          2.5
                        }
                      />
                    </Group>
                  ),
                )}
            </Layer>
          </Stage>
        )}

      {/* FILTROS */}
      <div className="absolute left-[340px] top-3 z-20 flex max-w-[calc(100%-520px)] flex-wrap gap-2">

        {(
          Object.keys(
            filtros,
          ) as FiltroMapa[]
        ).map(
          (filtro) => (
            <button
              type="button"
              key={
                filtro
              }
              onClick={() =>
                alternarFiltro(
                  filtro,
                )
              }
              className={[
                "rounded-full px-3 py-1 text-[10px] font-semibold shadow-sm transition",

                filtros[
                  filtro
                ]
                  ? "bg-orange-500 text-white"
                  : "bg-slate-800/90 text-slate-400",

              ].join(" ")}
            >
              {
                filtro
              }
            </button>
          ),
        )}
      </div>

      {/* ZOOM */}
      <div className="absolute right-3 top-3 z-20 flex gap-1 rounded-lg border border-slate-700 bg-[#11161d]/95 p-1 shadow-lg">

        <BotonMapa
          titulo="Alejar"
          onClick={() =>
            aplicarZoom(
              0.85,
            )
          }
        >
          <ZoomOut className="size-4" />
        </BotonMapa>

        <BotonMapa
          titulo="Acercar"
          onClick={() =>
            aplicarZoom(
              1.15,
            )
          }
        >
          <ZoomIn className="size-4" />
        </BotonMapa>

        <BotonMapa
          titulo="Restablecer vista"
          onClick={
            reiniciarVista
          }
        >
          <RotateCcw className="size-4" />
        </BotonMapa>
      </div>

      {/* DETALLE DEL VEHÍCULO */}
      {visualSeleccionado
        ?.ruta &&
        visualSeleccionado
          .seguimiento && (
          <PanelVehiculoSeleccionado
            visual={
              visualSeleccionado
            }
            onCerrar={() =>
              setVehiculoSeleccionadoId(
                null,
              )
            }
          />
        )}

      {/* LEYENDA */}
      <div className="absolute bottom-5 right-5 z-20 rounded-md bg-[#11161d]/95 p-4 text-xs text-slate-300 shadow-xl">

        <p className="mb-3 font-semibold text-white">
          LEYENDA
        </p>

        <LeyendaLinea
          tipo="REALIZADO"
          texto="Recorrido realizado"
        />

        <LeyendaLinea
          tipo="ACTUAL"
          texto="Tramo actual"
        />

        <LeyendaLinea
          tipo="FUTURO"
          texto="Ruta pendiente"
        />

        <div className="my-2 border-t border-slate-700" />

        <Leyenda
          color="#3b82f6"
          texto="Automóvil"
        />

        <Leyenda
          color="#22c55e"
          texto="Motocicleta"
        />

        <Leyenda
          color="#f59e0b"
          texto="Bicicleta"
        />

        <Leyenda
          color="#8b5cf6"
          texto="Pedido pendiente"
        />

        <Leyenda
          color="#2563eb"
          texto="Pedido asignado"
        />

        <Leyenda
          color="#f97316"
          texto="Pedido en tránsito"
        />

        <Leyenda
          color="#16a34a"
          texto="Pedido entregado"
        />

        <Leyenda
          color="#dc2626"
          texto="Pedido vencido / incidencia"
        />
      </div>

      {/* TOOLTIP */}
      {tooltip && (
        <div
          className="pointer-events-none absolute z-50 min-w-44 rounded-md border border-slate-700 bg-[#11161d] px-3 py-2 text-left shadow-xl"
          style={{
            left:
              Math.min(
                tooltip.x,

                Math.max(
                  8,
                  width -
                    230,
                ),
              ),

            top:
              Math.min(
                tooltip.y,

                Math.max(
                  8,
                  height -
                    150,
                ),
              ),
          }}
        >
          <p className="mb-1 text-xs font-semibold text-white">
            {
              tooltip.titulo
            }
          </p>

          {tooltip.lineas.map(
            (linea) => (
              <p
                key={
                  linea
                }
                className="whitespace-nowrap text-[11px] text-slate-400"
              >
                {
                  linea
                }
              </p>
            ),
          )}
        </div>
      )}
    </section>
  );
}

/*
 * =========================================================
 * RUTA SELECCIONADA
 * =========================================================
 */

function RutaSeleccionada({
  ruta,
  vehiculo,
  seguimiento,
  instanteMs,
  color,
  convertirX,
  convertirY,
}: {
  ruta: RutaMapa;

  vehiculo:
    VehiculoMapa;

  seguimiento:
    SeguimientoRuta;

  instanteMs:
    number;

  color:
    string;

  convertirX:
    (
      valor: number,
    ) => number;

  convertirY:
    (
      valor: number,
    ) => number;
}) {
  const horizontalPrimero =
    horizontalPrimeroParaVehiculo(
      ruta.vehiculoId,
    );

  const siguiente =
    seguimiento
      .siguienteParada;

  const completadas =
    seguimiento.paradas
      .filter(
        (item) =>
          item.estado ===
          "COMPLETADA",
      )
      .map(
        (item) =>
          item.parada,
      );

  const futuras =
    seguimiento.paradas
      .filter(
        (item) =>
          item.estado ===
          "FUTURA",
      )
      .map(
        (item) =>
          item.parada,
      );

  /*
   * Parte histórica.
   */
  const historial =
    construirPuntosOrtogonales(
      completadas.map(
        (parada) => ({
          x:
            parada.x,

          y:
            parada.y,
        }),
      ),

      convertirX,
      convertirY,

      horizontalPrimero,
    );

  /*
   * Parte del tramo ya recorrida
   * hasta la posición visual actual.
   */
  const recorridoActual =
    construirPuntosOrtogonales(
      [
        {
          x:
            vehiculo.x,

          y:
            vehiculo.y,
        },

        seguimiento
          .posicionVisual,
      ],

      convertirX,
      convertirY,

      horizontalPrimero,
    );

  /*
   * Desde posición visual
   * hasta la siguiente parada.
   */
  const tramoActual =
    siguiente
      ? construirPuntosOrtogonales(
          [
            seguimiento
              .posicionVisual,

            {
              x:
                siguiente.x,

              y:
                siguiente.y,
            },
          ],

          convertirX,
          convertirY,

          horizontalPrimero,
        )
      : [];

  /*
   * Futuro después de la
   * próxima parada.
   */
  const rutaFutura =
    siguiente
      ? construirPuntosOrtogonales(
          [
            {
              x:
                siguiente.x,

              y:
                siguiente.y,
            },

            ...futuras
              .filter(
                (parada) =>
                  parada.orden !==
                  siguiente.orden,
              )
              .map(
                (parada) => ({
                  x:
                    parada.x,

                  y:
                    parada.y,
                }),
              ),
          ],

          convertirX,
          convertirY,

          horizontalPrimero,
        )
      : [];

  return (
    <>
      {/* RECORRIDO HISTÓRICO */}
      {historial.length >=
        4 && (
        <Line
          points={
            historial
          }
          stroke={
            color
          }
          strokeWidth={
            3
          }
          opacity={
            0.3
          }
          lineCap="round"
          lineJoin="round"
        />
      )}

      {/* PARTE YA RECORRIDA DEL TRAMO ACTUAL */}
      {recorridoActual.length >=
          4 &&
        seguimiento.enMovimiento && (
          <Line
            points={
              recorridoActual
            }
            stroke={
              color
            }
            strokeWidth={
              4
            }
            opacity={
              0.55
            }
            lineCap="round"
            lineJoin="round"
          />
        )}

      {/* TRAMO ACTUAL */}
      {tramoActual.length >=
        4 && (
        <Arrow
          points={
            tramoActual
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
      )}

      {/* RUTA FUTURA */}
      {rutaFutura.length >=
        4 && (
        <Arrow
          points={
            rutaFutura
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
            x={convertirX(
              parada.x,
            )}
            y={convertirY(
              parada.y,
            )}
            instanteMs={
              instanteMs
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

  x: number;
  y: number;

  instanteMs:
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
        radius={8}
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
        width={14}
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

/*
 * =========================================================
 * VEHÍCULO
 * =========================================================
 */

function VehiculoMarker({
  vehiculo,
  seleccionado,
  atenuado,
  x,
  y,
  posicionVisual,
  progresoTramo,
  onSeleccionar,
  onTooltip,
  onLeave,
}: {
  vehiculo:
    VehiculoMapa;

  seleccionado:
    boolean;

  atenuado:
    boolean;

  posicionVisual: {
    x: number;
    y: number;
  };

  progresoTramo:
    number;

  x: number;
  y: number;

  onSeleccionar:
    () => void;

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
}) {
  const color =
    vehiculo.estado ===
    "NO_DISPONIBLE"
      ? "#64748b"
      : COLORES_VEHICULO[
          vehiculo.tipo
        ];

  const letra =
    vehiculo.tipo ===
    "AUTO"
      ? "A"
      : vehiculo.tipo ===
          "MOTO"
        ? "M"
        : "B";

  const lineas = [
    `Tipo: ${vehiculo.tipo}`,

    `Estado: ${vehiculo.estado}`,

    `Posición confirmada: (${vehiculo.x}, ${vehiculo.y})`,

    `Posición visual: (${posicionVisual.x.toFixed(
      2,
    )}, ${posicionVisual.y.toFixed(
      2,
    )})`,

    `Avance de tramo: ${(
      progresoTramo *
      100
    ).toFixed(0)}%`,

    `Carga: ${vehiculo.cargaActual}`,

    vehiculo.motivoDetencion
      ? `Motivo de detención: ${nombreMotivoDetencion(vehiculo.motivoDetencion)}`
      : "Sin detención activa",

    vehiculo.detenidoHasta
      ? `Disponible nuevamente: ${formatearFecha(vehiculo.detenidoHasta)}`
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
      onClick={
        onSeleccionar
      }
      onTap={
        onSeleccionar
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
          radius={
            15
          }
          stroke={
            color
          }
          strokeWidth={
            2
          }
          opacity={
            0.35
          }
        />
      )}

      <Circle
        radius={
          10
        }
        fill="#ffffff"
        stroke={
          color
        }
        strokeWidth={
          seleccionado
            ? 3.5
            : 2.5
        }
        shadowColor="#0f172a"
        shadowBlur={
          seleccionado
            ? 8
            : 4
        }
        shadowOpacity={
          0.3
        }
      />

      <Text
        x={-7}
        y={-6}
        width={
          14
        }
        align="center"
        text={
          letra
        }
        fontSize={
          11
        }
        fontStyle="bold"
        fill={
          color
        }
      />

      <Text
        x={-28}
        y={13}
        width={56}
        align="center"
        text={vehiculo.id}
        fontSize={8}
        fontStyle="bold"
        fill="#0f172a"
      />

      {vehiculo.motivoDetencion && (
        <Group x={9} y={-13}>
          <Circle radius={7} fill={colorMotivoDetencion(vehiculo.motivoDetencion)} stroke="#ffffff" strokeWidth={1} />
          <Text
            x={-6}
            y={-5}
            width={12}
            align="center"
            text={simboloMotivoDetencion(vehiculo.motivoDetencion)}
            fontSize={8}
            fontStyle="bold"
            fill="#ffffff"
          />
        </Group>
      )}
    </Group>
  );
}

/*
 * =========================================================
 * GRUPO DE VEHÍCULOS
 * =========================================================
 */

function GrupoVehiculosMarker({
  vehiculos,
  x,
  y,
  onTooltip,
  onLeave,
}: {
  vehiculos:
    VehiculoMapa[];

  x: number;
  y: number;

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
}) {
  const autos =
    vehiculos.filter(
      (vehiculo) =>
        vehiculo.tipo ===
        "AUTO",
    ).length;

  const motos =
    vehiculos.filter(
      (vehiculo) =>
        vehiculo.tipo ===
        "MOTO",
    ).length;

  const bicicletas =
    vehiculos.filter(
      (vehiculo) =>
        vehiculo.tipo ===
        "BICICLETA",
    ).length;

  return (
    <Group
      x={x}
      y={y}
      onMouseEnter={(
        event,
      ) =>
        onTooltip(
          event,

          `${vehiculos.length} vehículos`,

          [
            `Autos: ${autos}`,

            `Motos: ${motos}`,

            `Bicicletas: ${bicicletas}`,

            `Coordenadas: (${vehiculos[0].x}, ${vehiculos[0].y})`,
          ],
        )
      }
      onMouseMove={(
        event,
      ) =>
        onTooltip(
          event,

          `${vehiculos.length} vehículos`,

          [
            `Autos: ${autos}`,

            `Motos: ${motos}`,

            `Bicicletas: ${bicicletas}`,

            `Coordenadas: (${vehiculos[0].x}, ${vehiculos[0].y})`,
          ],
        )
      }
      onMouseLeave={
        onLeave
      }
    >
      <Circle
        radius={
          16
        }
        fill="#0f172a"
        stroke="#f97316"
        strokeWidth={
          2.5
        }
        shadowColor="#0f172a"
        shadowBlur={
          5
        }
        shadowOpacity={
          0.3
        }
      />

      <Text
        x={-14}
        y={-6}
        width={
          28
        }
        align="center"
        text={String(
          vehiculos.length,
        )}
        fontSize={
          11
        }
        fontStyle="bold"
        fill="#ffffff"
      />
    </Group>
  );
}

/*
 * =========================================================
 * PANEL VEHÍCULO SELECCIONADO
 * =========================================================
 */

function PanelVehiculoSeleccionado({
  visual,
  onCerrar,
}: {
  visual:
    VehiculoVisual;

  onCerrar:
    () => void;
}) {
  const ruta =
    visual.ruta;

  const seguimiento =
    visual.seguimiento;

  if (
    !ruta ||
    !seguimiento
  ) {
    return null;
  }

  const siguiente =
    seguimiento
      .siguienteParada;

  const servicio =
    seguimiento
      .paradaEnServicio;

  return (
    <div className="absolute right-5 top-16 z-30 w-[330px] rounded-lg border border-slate-700 bg-[#11161d]/95 p-4 text-white shadow-2xl">

      <div className="flex items-start justify-between gap-3">

        <div>
          <div className="flex items-center gap-2">

            <Navigation className="size-4 text-sky-400" />

            <h3 className="font-semibold">
              {
                visual
                  .vehiculo
                  .id
              }
            </h3>

            <span className="rounded-full bg-sky-500/15 px-2 py-0.5 text-[10px] font-semibold text-sky-400">
              {
                visual
                  .vehiculo
                  .tipo
              }
            </span>
          </div>

          <p className="mt-1 text-xs text-slate-500">
            {visual.vehiculo.motivoDetencion
              ? `${nombreMotivoDetencion(visual.vehiculo.motivoDetencion)}${
                  visual.vehiculo.detenidoHasta
                    ? ` hasta ${formatearHora(visual.vehiculo.detenidoHasta)}`
                    : ""
                }`
              : seguimiento.enRefrigerio
                ? `En refrigerio hasta ${
                    seguimiento.refrigerioHasta
                      ? formatearHora(seguimiento.refrigerioHasta)
                      : "—"
                  }`
                : servicio
                  ? `Atendiendo ${nombreParada(servicio)}`
                  : siguiente
                    ? `En tránsito hacia ${nombreParada(siguiente)}`
                    : "Ruta finalizando"}
          </p>
        </div>

        <button
          type="button"
          onClick={
            onCerrar
          }
          className="rounded p-1 text-slate-500 hover:bg-slate-800 hover:text-white"
        >
          <X className="size-4" />
        </button>
      </div>

      <div className="mt-4 grid grid-cols-2 gap-2">

        <DatoDetalle
          icono={
            <Package className="size-3.5" />
          }
          label="Carga"
          valor={String(
            visual
              .vehiculo
              .cargaActual,
          )}
        />

        <DatoDetalle
          icono={
            <Clock3 className="size-3.5" />
          }
          label="ETA"
          valor={
            seguimiento.eta
              ? formatearHora(
                  seguimiento.eta,
                )
              : "—"
          }
        />
      </div>

      <div className="mt-4">

        <div className="mb-1 flex justify-between text-[10px] text-slate-500">

          <span>
            Progreso de ruta
          </span>

          <span>
            {(
              seguimiento
                .progresoRuta *
              100
            ).toFixed(
              0,
            )}
            %
          </span>
        </div>

        <div className="h-1.5 overflow-hidden rounded-full bg-slate-800">
          <div
            className="h-full rounded-full bg-sky-500 transition-[width]"
            style={{
              width:
                `${seguimiento.progresoRuta * 100}%`,
            }}
          />
        </div>
      </div>

      {seguimiento
        .enMovimiento && (
        <div className="mt-3">

          <div className="mb-1 flex justify-between text-[10px] text-slate-500">

            <span>
              Tramo actual
            </span>

            <span>
              {(
                seguimiento
                  .progresoTramo *
                100
              ).toFixed(
                0,
              )}
              %
            </span>
          </div>

          <div className="h-1 overflow-hidden rounded-full bg-slate-800">
            <div
              className="h-full bg-orange-500"
              style={{
                width:
                  `${seguimiento.progresoTramo * 100}%`,
              }}
            />
          </div>
        </div>
      )}

      <div className="mt-4 border-t border-slate-800 pt-3">

        <div className="mb-2 flex items-center justify-between">

          <p className="text-[10px] font-semibold uppercase text-slate-500">
            Itinerario
          </p>

          <span className="text-[10px] text-slate-500">
            {ruta.distanciaKm.toFixed(
              1,
            )}{" "}
            km · S/{" "}
            {ruta.costo.toFixed(
              1,
            )}
          </span>
        </div>

        <div className="max-h-44 space-y-1 overflow-y-auto pr-1">

          {seguimiento.paradas.map(
            ({
              parada,
              estado,
            }) => (
              <div
                key={`detalle-${parada.orden}`}
                className="flex items-center gap-2 rounded-md bg-slate-800/60 px-2 py-1.5"
              >
                <span
                  className={[
                    "flex size-5 shrink-0 items-center justify-center rounded-full text-[9px] font-bold",

                    estado ===
                    "COMPLETADA"
                      ? "bg-green-500/15 text-green-400"

                      : estado ===
                        "REFRIGERIO"
                        ? "bg-amber-500/15 text-amber-300"

                        : estado ===
                          "EN_SERVICIO"
                        ? "bg-orange-500/15 text-orange-400"

                        : estado ===
                          "SIGUIENTE"
                          ? "bg-sky-500/15 text-sky-400"

                          : "bg-slate-700 text-slate-400",

                  ].join(
                    " ",
                  )}
                >
                  {estado ===
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

                        : parada.orden}
                </span>

                <div className="min-w-0 flex-1">

                  <p className="truncate text-[11px] text-slate-200">
                    {nombreParada(
                      parada,
                    )}
                  </p>

                  <p className="text-[9px] text-slate-500">

                    {parada.llegada
                      ? formatearHora(
                          parada.llegada,
                        )
                      : "Sin ETA"}

                    {parada.paquetes >
                    0
                      ? ` · ${parada.paquetes} paq.`
                      : ""}
                  </p>
                </div>
              </div>
            ),
          )}
        </div>
      </div>
    </div>
  );
}

/*
 * =========================================================
 * COMPONENTES AUXILIARES
 * =========================================================
 */

function DatoDetalle({
  icono,
  label,
  valor,
}: {
  icono:
    React.ReactNode;

  label:
    string;

  valor:
    string;
}) {
  return (
    <div className="rounded-md bg-slate-800/70 p-2">

      <div className="flex items-center gap-1 text-[9px] uppercase text-slate-500">
        {icono}
        {label}
      </div>

      <p className="mt-1 text-sm font-semibold text-slate-100">
        {valor}
      </p>
    </div>
  );
}

function BotonMapa({
  titulo,
  onClick,
  children,
}: {
  titulo:
    string;

  onClick:
    () => void;

  children:
    React.ReactNode;
}) {
  return (
    <button
      type="button"
      title={
        titulo
      }
      onClick={
        onClick
      }
      className="rounded-md p-2 text-slate-300 hover:bg-slate-700 hover:text-white"
    >
      {children}
    </button>
  );
}

function Leyenda({
  color,
  texto,
}: {
  color:
    string;

  texto:
    string;
}) {
  return (
    <div className="mb-1.5 flex items-center gap-2">

      <span
        className="size-2 rounded-full"
        style={{
          backgroundColor:
            color,
        }}
      />

      <span>
        {texto}
      </span>
    </div>
  );
}

function LeyendaLinea({
  tipo,
  texto,
}: {
  tipo:
    | "REALIZADO"
    | "ACTUAL"
    | "FUTURO";

  texto:
    string;
}) {
  return (
    <div className="mb-1.5 flex items-center gap-2">

      <span
        className={[
          "block w-7 border-t-2",

          tipo ===
          "REALIZADO"
            ? "border-slate-400 opacity-60"

            : tipo ===
              "ACTUAL"
              ? "border-orange-400 border-t-[4px]"

              : "border-orange-400 border-dashed",

        ].join(
          " ",
        )}
      />

      <span>
        {texto}
      </span>
    </div>
  );
}

function filtroVehiculoActivo(
  tipo:
    TipoVehiculo,

  filtros:
    Record<
      FiltroMapa,
      boolean
    >,
) {
  switch (tipo) {
    case "AUTO":
      return filtros.AUTOS;

    case "MOTO":
      return filtros.MOTOS;

    case "BICICLETA":
      return filtros.BICICLETAS;
  }
}

function colorPedido(
  pedido:
    PedidoOperacion,

  instanteMs:
    number,
) {
  if (
    pedido.estado !==
      "ENTREGADO" &&
    new Date(
      pedido.vence,
    ).getTime() <
      instanteMs
  ) {
    return "#dc2626";
  }

  switch (
    pedido.estado
  ) {
    case "ENTREGADO":
      return "#16a34a";

    case "EN_TRANSITO":
      return "#f97316";

    case "ASIGNADO":
      return "#2563eb";

    case "PENDIENTE":
      return "#8b5cf6";
  }
}

function obtenerParadasPendientes(
  ruta:
    RutaMapa,

  instanteMs:
    number,
) {
  return ruta.paradas.filter(
    (parada) => {
      const referencia =
        parada.fin ??
        parada.llegada;

      if (
        !referencia
      ) {
        return true;
      }

      return (
        new Date(
          referencia,
        ).getTime() >=
        instanteMs
      );
    },
  );
}

function construirPuntosOrtogonales(
  puntos: Array<{
    x: number;
    y: number;
  }>,

  convertirX:
    (
      valor: number,
    ) => number,

  convertirY:
    (
      valor: number,
    ) => number,

  horizontalPrimero:
    boolean,
) {
  if (
    puntos.length ===
    0
  ) {
    return [];
  }

  const resultado = [
    convertirX(
      puntos[0].x,
    ),

    convertirY(
      puntos[0].y,
    ),
  ];

  for (
    let index = 1;
    index <
    puntos.length;
    index += 1
  ) {
    const anterior =
      puntos[
        index - 1
      ];

    const actual =
      puntos[index];

    if (
      horizontalPrimero
    ) {
      resultado.push(
        convertirX(
          actual.x,
        ),

        convertirY(
          anterior.y,
        ),
      );
    } else {
      resultado.push(
        convertirX(
          anterior.x,
        ),

        convertirY(
          actual.y,
        ),
      );
    }

    resultado.push(
      convertirX(
        actual.x,
      ),

      convertirY(
        actual.y,
      ),
    );
  }

  return resultado;
}

function nombreMotivoDetencion(motivo: VehiculoMapa["motivoDetencion"]) {
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

function simboloMotivoDetencion(motivo: NonNullable<VehiculoMapa["motivoDetencion"]>) {
  switch (motivo) {
    case "AVERIA":
      return "!";
    case "MANTENIMIENTO":
      return "M";
    case "REFRIGERIO":
      return "☕";
  }
}

function colorMotivoDetencion(motivo: NonNullable<VehiculoMapa["motivoDetencion"]>) {
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
  valor:
    string,
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

function formatearHora(
  valor:
    string,
) {
  return new Intl.DateTimeFormat(
    "es-PE",
    {
      timeZone:
        "America/Lima",

      hour:
        "2-digit",

      minute:
        "2-digit",

      second:
        "2-digit",

      hour12:
        false,
    },
  ).format(
    new Date(valor),
  );
}