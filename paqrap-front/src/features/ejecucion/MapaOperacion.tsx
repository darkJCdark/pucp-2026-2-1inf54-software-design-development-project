import {
  useEffect,
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

import type {
  TipoVehiculoResponseDto,
} from "@/api/flota.dto";

import {
  useConfiguracion,
} from "@/context/ConfiguracionContext";

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
  convertirTrazoAPixeles,
  obtenerTrazosRuta,
  obtenerTramosTemporizados,
} from "./geometriaRutas";
import { claveGrupoVehiculo, debeAgruparVehiculos } from "./agrupamientoVehiculos";

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

type NivelSemaforo =
  | "TODOS"
  | "VACIO"
  | "VERDE"
  | "AMBAR"
  | "ROJO";

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

  tiposFlota:
    TipoVehiculoResponseDto[];

  seleccionVehiculoId?: string | null;
  seleccionPedidoId?: string | null;
  seleccionAlmacenId?: string | null;

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

  direccionGrados:
    number | null;
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
  tiposFlota,
  seleccionVehiculoId = null,
  seleccionPedidoId = null,
  seleccionAlmacenId = null,
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

  const {
    configuracion,
  } = useConfiguracion();

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

  useEffect(() => {
    setVehiculoSeleccionadoId(seleccionVehiculoId);
  }, [seleccionVehiculoId]);

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

  const [
    filtroCargaVehiculos,
    setFiltroCargaVehiculos,
  ] =
    useState<NivelSemaforo>("TODOS");

  const [
    filtroStockAlmacenes,
    setFiltroStockAlmacenes,
  ] =
    useState<NivelSemaforo>("TODOS");

  useEffect(() => {
    setVehiculoSeleccionadoId(
      seleccionVehiculoId,
    );
  }, [seleccionVehiculoId]);

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

  const capacidadPorTipo =
    useMemo(
      () =>
        new Map(
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

  const rutasSinGeometria = useMemo(
    () => rutas.filter((ruta) => ruta.distanciaKm > 0 && obtenerTramosTemporizados(ruta).length === 0).length,
    [rutas],
  );

  const rutasSeleccionadasPorPedido =
    useMemo(
      () =>
        new Set(
          seleccionPedidoId
            ? rutas
                .filter((ruta) =>
                  ruta.paradas.some(
                    (parada) =>
                      parada.pedidoId === seleccionPedidoId,
                  ),
                )
                .map((ruta) => ruta.id)
            : [],
        ),
      [rutas, seleccionPedidoId],
    );

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

              direccionGrados:
                null,
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

            direccionGrados:
              seguimiento
                .direccionGrados,
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

        const clave = claveGrupoVehiculo(
          visual.vehiculo.id, visual.x, visual.y, visual.enMovimiento, seleccionado,
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
      {rutasSinGeometria > 0 && (
        <div role="status" className="absolute bottom-3 left-3 z-20 max-w-md rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900 shadow">
          {rutasSinGeometria} ruta(s) sin tramos válidos. Se mantiene la posición de monitoreo sin inventar recorridos.
          Comprueba que el backend esté actualizado e inicia una ejecución nueva.
        </div>
      )}
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
                        vehiculoSeleccionadoId ||
                      rutasSeleccionadasPorPedido.has(
                        ruta.id,
                      );

                    const haySeleccion =
                      vehiculoSeleccionadoId !==
                        null ||
                      seleccionPedidoId !==
                        null;

                    /*
                     * Para rutas no seleccionadas
                     * mostramos únicamente el
                     * futuro pendiente.
                     */
                    if (
                      !seleccionada
                    ) {
                      const trazos = obtenerTrazosRuta(ruta, instanteAnimadoMs).pendientes;
                      return (
                        <Group key={ruta.id}>
                          {trazos.map((trazo, indice) => (
                            <Arrow
                              key={`${ruta.id}-pendiente-${indice}`}
                              points={convertirTrazoAPixeles(trazo, geometria.x, geometria.y)}
                              stroke={color}
                              fill={color}
                              strokeWidth={2.5}
                              opacity={haySeleccion ? 0.12 : 0.55}
                              dash={[9, 6]}
                              lineCap="round"
                              lineJoin="round"
                              pointerLength={6}
                              pointerWidth={6}
                              hitStrokeWidth={18}
                              onClick={() => {
                                setVehiculoSeleccionadoId(ruta.vehiculoId);
                                onSeleccionarVehiculo?.(ruta.vehiculoId);
                              }}
                              onTap={() => {
                                setVehiculoSeleccionadoId(ruta.vehiculoId);
                                onSeleccionarVehiculo?.(ruta.vehiculoId);
                              }}
                            />
                          ))}
                        </Group>
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
                          seleccionPedidoId === pedido.id
                            ? 8
                            : 5.5
                        }
                        fill={
                          color
                        }
                        stroke={
                          seleccionPedidoId === pedido.id
                            ? "#f97316"
                            : "#ffffff"
                        }
                        strokeWidth={
                          seleccionPedidoId === pedido.id
                            ? 3
                            : 1
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

                  const porcentaje =
                    porcentajeAlmacen(
                      almacen,
                    );

                  const nivel =
                    nivelSemaforoStock(
                      porcentaje,
                      configuracion.almacenVerdeHasta,
                      configuracion.almacenAmbarHasta,
                    );

                  if (
                    !central &&
                    filtroStockAlmacenes !== "TODOS" &&
                    filtroStockAlmacenes !== nivel
                  ) {
                    return null;
                  }

                  const color =
                    central
                      ? "#2563eb"
                      : colorSemaforo(nivel);

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
                      : `Semáforo: ${nombreNivel(nivel)}`,
                  ];

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
                      onClick={() =>
                        onSeleccionarAlmacen?.(
                          almacen.id,
                        )
                      }
                      onTap={() =>
                        onSeleccionarAlmacen?.(
                          almacen.id,
                        )
                      }
                      onMouseEnter={(
                        event,
                      ) =>
                        mostrarTooltip(
                          event,
                          `Almacén ${almacen.nombre}`,
                          lineas,
                        )
                      }
                      onMouseMove={(
                        event,
                      ) =>
                        mostrarTooltip(
                          event,
                          `Almacén ${almacen.nombre}`,
                          lineas,
                        )
                      }
                      onMouseLeave={
                        ocultarTooltip
                      }
                    >
                      {seleccionado && (
                        <Rect
                          x={-24}
                          y={-24}
                          width={48}
                          height={48}
                          cornerRadius={10}
                          stroke="#f97316"
                          strokeWidth={3}
                          opacity={0.8}
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

              {/* VEHÍCULOS */}
              {gruposVehiculos.map(
                (grupo) => {
                  const visibles =
                    grupo.filter(
                      (
                        item,
                      ) => {
                        if (
                          !filtroVehiculoActivo(
                            item.vehiculo.tipo,
                            filtros,
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
                            item.vehiculo.cargaActual,
                            capacidad,
                            configuracion.vehiculoVerdeHasta,
                            configuracion.vehiculoAmbarHasta,
                          );

                        return (
                          filtroCargaVehiculos === "TODOS" ||
                          filtroCargaVehiculos === nivel
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
                      (
                        item,
                      ) =>
                        item
                          .vehiculo
                          .id ===
                        vehiculoSeleccionadoId,
                    );

                  if (
                    debeAgruparVehiculos(visibles.length, contieneSeleccionado)
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
                        cargaTotal={visibles.reduce(
                          (total, item) =>
                            total + item.vehiculo.cargaActual,
                          0,
                        )}
                        capacidadTotal={visibles.reduce(
                          (total, item) =>
                            total +
                            (capacidadPorTipo.get(
                              item.vehiculo.tipo,
                            ) ?? 0),
                          0,
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
                          capacidad={
                            capacidadPorTipo.get(
                              item.vehiculo.tipo,
                            ) ?? 0
                          }
                          direccionGrados={
                            item.direccionGrados
                          }
                          nivelCarga={
                            nivelSemaforoCarga(
                              item.vehiculo.cargaActual,
                              capacidadPorTipo.get(
                                item.vehiculo.tipo,
                              ) ?? 0,
                              configuracion.vehiculoVerdeHasta,
                              configuracion.vehiculoAmbarHasta,
                            )
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
                  (averia) => {
                    const estilo =
                      estiloAveria(
                        averia.tipo,
                      );

                    return (
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
                            `Avería ${estilo.nombre}`,
                            [
                              `Vehículo: ${averia.vehiculoId}`,
                              `Tipo: ${estilo.nombre}`,
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
                            `Avería ${estilo.nombre}`,
                            [
                              `Vehículo: ${averia.vehiculoId}`,
                              `Tipo: ${estilo.nombre}`,
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
                          radius={12}
                          fill={estilo.fondo}
                          stroke={estilo.color}
                          strokeWidth={3}
                          shadowColor={estilo.color}
                          shadowBlur={6}
                          shadowOpacity={0.35}
                        />

                        <Text
                          x={-10}
                          y={-6}
                          width={20}
                          align="center"
                          text={estilo.codigo}
                          fontSize={10}
                          fontStyle="bold"
                          fill={estilo.color}
                        />
                      </Group>
                    );
                  },
                )}
            </Layer>
          </Stage>
        )}

      {/* FILTROS */}
      <div className="absolute left-[470px] top-3 z-20 flex max-w-[calc(100%-640px)] flex-wrap items-center gap-2">

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

        <label className="flex items-center gap-1 rounded-full bg-slate-800/95 px-2 py-1 text-[10px] text-slate-300 shadow-sm">
          Carga
          <select
            value={filtroCargaVehiculos}
            onChange={(event) =>
              setFiltroCargaVehiculos(
                event.target.value as NivelSemaforo,
              )
            }
            className="bg-transparent font-semibold text-white outline-none"
            aria-label="Filtrar vehículos por semáforo de carga"
          >
            <option value="TODOS">Todos</option>
            <option value="VACIO">Sin carga</option>
            <option value="VERDE">Verde</option>
            <option value="AMBAR">Ámbar</option>
            <option value="ROJO">Rojo</option>
          </select>
        </label>

        <label className="flex items-center gap-1 rounded-full bg-slate-800/95 px-2 py-1 text-[10px] text-slate-300 shadow-sm">
          Stock
          <select
            value={filtroStockAlmacenes}
            onChange={(event) =>
              setFiltroStockAlmacenes(
                event.target.value as NivelSemaforo,
              )
            }
            className="bg-transparent font-semibold text-white outline-none"
            aria-label="Filtrar almacenes por semáforo de stock"
          >
            <option value="TODOS">Todos</option>
            <option value="VACIO">Vacío</option>
            <option value="VERDE">Verde</option>
            <option value="AMBAR">Ámbar</option>
            <option value="ROJO">Rojo</option>
          </select>
        </label>
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
      {!onSeleccionarVehiculo &&
        visualSeleccionado
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

        <div className="my-2 border-t border-slate-700" />

        <p className="mb-1.5 text-[10px] uppercase text-slate-500">
          Semáforo carga / stock
        </p>

        <Leyenda
          color="#64748b"
          texto="Vacío"
        />

        <Leyenda
          color="#16a34a"
          texto="Verde"
        />

        <Leyenda
          color="#d97706"
          texto="Ámbar"
        />

        <Leyenda
          color="#dc2626"
          texto="Rojo"
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
  seguimiento,
  instanteMs,
  color,
  convertirX,
  convertirY,
}: {
  ruta: RutaMapa;
  seguimiento: SeguimientoRuta;
  instanteMs: number;
  color: string;
  convertirX: (valor: number) => number;
  convertirY: (valor: number) => number;
}) {
  const trazos = obtenerTrazosRuta(ruta, instanteMs, seguimiento.siguienteParada?.orden);
  return (
    <>
      {/* Historial completo, incluido el trayecto desde el almacén de salida. */}
      {trazos.recorridos.map((trazo, indice) => (
        <Line
          key={`recorrido-${indice}`}
          points={convertirTrazoAPixeles(trazo, convertirX, convertirY)}
          stroke={color}
          strokeWidth={3}
          opacity={0.4}
          lineCap="round"
          lineJoin="round"
        />
      ))}
      {/* Calles reales pendientes hasta la próxima parada. */}
      {trazos.actuales.map((trazo, indice) => (
        <Arrow
          key={`actual-${indice}`}
          points={convertirTrazoAPixeles(trazo, convertirX, convertirY)}
          stroke={color}
          fill={color}
          strokeWidth={6}
          opacity={1}
          lineCap="round"
          lineJoin="round"
          pointerLength={9}
          pointerWidth={9}
          shadowColor={color}
          shadowBlur={7}
          shadowOpacity={0.35}
        />
      ))}
      {trazos.futuros.map((trazo, indice) => (
        <Arrow
          key={`futuro-${indice}`}
          points={convertirTrazoAPixeles(trazo, convertirX, convertirY)}
          stroke={color}
          fill={color}
          strokeWidth={3}
          opacity={0.65}
          dash={[10, 6]}
          lineCap="round"
          lineJoin="round"
          pointerLength={7}
          pointerWidth={7}
        />
      ))}

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
  capacidad,
  nivelCarga,
  direccionGrados,
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

  capacidad:
    number;

  nivelCarga:
    Exclude<NivelSemaforo, "TODOS">;

  direccionGrados:
    number | null;

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
  const colorTipo =
    COLORES_VEHICULO[
      vehiculo.tipo
    ];

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
            (vehiculo.cargaActual /
              capacidad) *
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

    `Carga: ${vehiculo.cargaActual}/${capacidad || "—"} (${porcentajeCarga.toFixed(0)}%)`,
    `Semáforo de carga: ${nombreNivel(nivelCarga)}`,

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

      {direccionGrados !== null && vehiculo.estado === "EN_RUTA" && (
        <Arrow
          points={[0, 0, 17, 0]}
          rotation={direccionGrados}
          stroke={colorTipo}
          fill={colorTipo}
          strokeWidth={2}
          pointerLength={5}
          pointerWidth={5}
          opacity={0.9}
          listening={false}
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
          colorTipo
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

      <Text
        x={-30}
        y={23}
        width={60}
        align="center"
        text={`${vehiculo.cargaActual}/${capacidad || "—"}`}
        fontSize={7}
        fill="#475569"
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
  cargaTotal,
  capacidadTotal,
  x,
  y,
  onTooltip,
  onLeave,
}: {
  vehiculos:
    VehiculoMapa[];

  cargaTotal:
    number;

  capacidadTotal:
    number;

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

  const porcentajeCarga =
    capacidadTotal > 0
      ? (cargaTotal / capacidadTotal) * 100
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

            `Carga: ${cargaTotal}/${capacidadTotal || "—"} (${porcentajeCarga.toFixed(0)}%)`,

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

            `Carga: ${cargaTotal}/${capacidadTotal || "—"} (${porcentajeCarga.toFixed(0)}%)`,

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

      <Text
        x={-18}
        y={18}
        width={36}
        align="center"
        text={`${porcentajeCarga.toFixed(0)}%`}
        fontSize={7}
        fontStyle="bold"
        fill="#334155"
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

function adaptarTipoVehiculo(
  tipo: TipoVehiculoResponseDto["tipo"],
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

function porcentajeAlmacen(
  almacen: AlmacenMapa,
) {
  if (
    almacen.tipo === "CENTRAL" ||
    !almacen.capacidad ||
    almacen.capacidad <= 0
  ) {
    return 100;
  }

  return Math.min(
    100,
    Math.max(
      0,
      ((almacen.stock ?? 0) /
        almacen.capacidad) *
        100,
    ),
  );
}

function nivelSemaforoStock(
  porcentaje: number,
  verdeDesde: number,
  ambarDesde: number,
): Exclude<NivelSemaforo, "TODOS"> {
  if (porcentaje <= 0) {
    return "VACIO";
  }

  if (porcentaje >= ambarDesde) {
    return "VERDE";
  }

  if (porcentaje >= verdeDesde) {
    return "AMBAR";
  }

  return "ROJO";
}

function nivelSemaforoCarga(
  carga: number,
  capacidad: number,
  verdeHasta: number,
  ambarHasta: number,
): Exclude<NivelSemaforo, "TODOS"> {
  const porcentaje =
    capacidad > 0
      ? (carga / capacidad) * 100
      : 0;

  if (carga <= 0) {
    return "VACIO";
  }

  if (porcentaje <= verdeHasta) {
    return "VERDE";
  }

  if (porcentaje <= ambarHasta) {
    return "AMBAR";
  }

  return "ROJO";
}

function colorSemaforo(
  nivel: Exclude<NivelSemaforo, "TODOS">,
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
  nivel: Exclude<NivelSemaforo, "TODOS">,
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

function estiloAveria(
  tipo: AveriaResponseDto["tipo"],
) {
  switch (tipo) {
    case "MINOR":
      return {
        nombre: "leve",
        codigo: "L",
        color: "#d97706",
        fondo: "#fffbeb",
      };
    case "INTERMEDIATE":
      return {
        nombre: "intermedia",
        codigo: "I",
        color: "#ea580c",
        fondo: "#fff7ed",
      };
    case "MAJOR":
      return {
        nombre: "grave",
        codigo: "G",
        color: "#dc2626",
        fondo: "#fef2f2",
      };
  }
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
