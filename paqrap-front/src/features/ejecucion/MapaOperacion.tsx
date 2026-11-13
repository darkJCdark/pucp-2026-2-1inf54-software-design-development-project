import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";

import {
  ChevronDown,
  Clock3,
  Maximize2,
  Navigation,
  Package,
  RotateCcw,
  SlidersHorizontal,
  X,
  ZoomIn,
  ZoomOut,
} from "lucide-react";

import type {
  KonvaEventObject,
} from "konva/lib/Node";

import {
  Layer,
  Stage,
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
  useUmbralesEjecucion,
} from "@/context/ConfiguracionContext";

import {
  useElementSize,
} from "@/hooks/useElementSize";


import type {
  AlmacenMapa,
  PedidoOperacion,
  RutaMapa,
  VehiculoMapa,
} from "./ejecucion.types";

import {
  obtenerTramosTemporizados,
} from "./geometriaRutas";

import {
  nombreParada,
  obtenerSeguimientoRuta,
} from "./seguimientoRuta";

import {
  CapaReticula,
} from "./CapaReticula";

import {
  CapaBloqueos,
} from "./CapaBloqueos";

import {
  CapaAlmacenes,
} from "./CapaAlmacenes";

import {
  CapaAverias,
} from "./CapaAverias";

import type {
  VehiculoVisual,
} from "./CapaVehiculos";

import {
  CapaPedidos,
} from "./CapaPedidos";

import {
  CapaDinamicaMapa,
} from "./CapaDinamicaMapa";


import {
  usePanZoomCompositor,
} from "@/hooks/usePanZoomCompositor";

import {
  VentanaFlotante,
} from "@/components/ui/VentanaFlotante";

import {
  DURACION_ENFOQUE_MS,
  calcularVistaAjustada,
  calcularVistaCentrada,
  margenesPaneles,
  pantallaAMapa,
} from "./vistaMapa";


const ANCHO_MAPA = 70;
const ALTO_MAPA = 50;

const PADDING = 34;



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

  /**
   * Botones de acción de la página (registrar pedido/avería, resultado).
   * Se dibujan en la misma barra superior derecha que el zoom.
   */
  accionesExtra?: ReactNode;
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
  accionesExtra,
}: MapaOperacionProps) {
  const {
    ref: contenedorRef,
    width,
    height,
  } =
    useElementSize<HTMLElement>();

  const {
    umbrales: configuracion,
  } = useUmbralesEjecucion();

  const [
    vista,
    setVista,
  ] =
    useState<VistaMapa>({
      escala: 1,
      x: 0,
      y: 0,
    });

  const confirmarVista =
    useCallback(
      (
        nuevaVista:
          VistaMapa,
      ) => {
        setVista(
          nuevaVista,
        );
      },
      [],
    );

  const {
    stageRef,
    iniciarPan,
    aplicarZoom:
    aplicarZoomCompositor,
    animarHacia,
  } =
    usePanZoomCompositor({
      vista,

      onConfirmar:
        confirmarVista,
    });


  const arrastrandoMapa = false;

  const [
    tooltip,
    setTooltip,
  ] =
    useState<
      TooltipMapa | null
    >(null);

  const ultimoTooltipMsRef =
    useRef(0);

  const ultimoTooltipTituloRef =
    useRef<string | null>(
      null,
    );

  const INTERVALO_TOOLTIP_MS =
    80;

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
    filtrosMinimizados,
    setFiltrosMinimizados,
  ] =
    useState(true);

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

  /* Cantidad de filtros que ocultan algo (se muestra en el botón). */
  const filtrosActivos =
    Object.values(filtros).filter(
      (visible) => !visible,
    ).length +
    (filtroCargaVehiculos !== "TODOS" ? 1 : 0) +
    (filtroStockAlmacenes !== "TODOS" ? 1 : 0);



  /*
   * El servidor es la fuente
   * verdadera del reloj.
   *
   * Este hook solamente suaviza
   * visualmente el tiempo entre
   * respuestas del polling.
   */

  const instanteServidorMs =
    useMemo(
      () => {
        const valor =
          new Date(
            instanteSimulado,
          ).getTime();

        return Number.isFinite(
          valor,
        )
          ? valor
          : Date.now();
      },
      [
        instanteSimulado,
      ],
    );



  /*
 * Los vehículos necesitan animación frecuente,
 * pero el estado visual de vencimiento de los
 * pedidos no.
 *
 * Redondeamos al minuto simulado para evitar
 * recalcular todos los pedidos en cada frame.
 */
  const instantePedidosMs =
    Math.floor(
      instanteServidorMs /
      60_000,
    ) *
    60_000;

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

/*
 * =========================================================
 * VIEWPORT VISIBLE
 * =========================================================
 *
 * Se calcula DESPUÉS de geometria.
 */
const viewportMapa =
  useMemo(
    () => {
      const escalaVista =
        Math.max(
          vista.escala,
          0.0001,
        );

      const izquierdaPx =
        -vista.x /
        escalaVista;

      const derechaPx =
        (width -
          vista.x) /
        escalaVista;

      const arribaPx =
        -vista.y /
        escalaVista;

      const abajoPx =
        (height -
          vista.y) /
        escalaVista;

      const convertirPixelAX =
        (
          pixel: number,
        ) =>
          (
            pixel -
            geometria.origenX
          ) /
          geometria.escalaBase;

      const convertirPixelAY =
        (
          pixel: number,
        ) =>
          ALTO_MAPA -
          (
            pixel -
            geometria.origenY
          ) /
            geometria.escalaBase;

      const margen =
        2;

      return {
        minX:
          convertirPixelAX(
            izquierdaPx,
          ) -
          margen,

        maxX:
          convertirPixelAX(
            derechaPx,
          ) +
          margen,

        minY:
          convertirPixelAY(
            abajoPx,
          ) -
          margen,

        maxY:
          convertirPixelAY(
            arribaPx,
          ) +
          margen,
      };
    },
    [
      geometria,
      height,
      vista.escala,
      vista.x,
      vista.y,
      width,
    ],
  );

  const viewportPixeles =
  useMemo(
    () => {
      const escala =
        Math.max(
          vista.escala,
          0.0001,
        );

      /*
       * Coordenadas visibles dentro del
       * sistema local del Stage.
       *
       * convertirTrazoAPixeles usa exactamente
       * este mismo sistema.
       */
      const margen =
        100 /
        escala;

      return {
        minX:
          -vista.x /
            escala -
          margen,

        maxX:
          (width -
            vista.x) /
            escala +
          margen,

        minY:
          -vista.y /
            escala -
          margen,

        maxY:
          (height -
            vista.y) /
            escala +
          margen,
      };
    },
    [
      height,
      vista.escala,
      vista.x,
      vista.y,
      width,
    ],
  );

const rutasSinGeometria =
  useMemo(
    () =>
      rutas.filter(
        (ruta) =>
          ruta.distanciaKm >
            0 &&
          obtenerTramosTemporizados(
            ruta,
          ).length ===
            0,
      ).length,
    [
      rutas,
    ],
  );

  /*
   * Posición visual animada
   * para cada vehículo.
   */


  const visualSeleccionado =
    useMemo<
      VehiculoVisual | null
    >(
      () => {
        if (
          !vehiculoSeleccionadoId
        ) {
          return null;
        }

        const vehiculo =
          vehiculos.find(
            (item) =>
              item.id ===
              vehiculoSeleccionadoId,
          );

        if (!vehiculo) {
          return null;
        }

        const ruta =
          rutas.find(
            (item) =>
              item.vehiculoId ===
              vehiculo.id,
          );

        if (!ruta) {
          return {
            vehiculo,

            x:
              vehiculo.x,

            y:
              vehiculo.y,

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
            instanteServidorMs,
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
      [
        instanteServidorMs,
        rutas,
        vehiculoSeleccionadoId,
        vehiculos,
      ],
    );

  /*
   * Agrupamos vehículos estacionados
   * en el mismo punto para evitar
   * 37 iconos exactamente superpuestos.
   */


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

  const mostrarTooltip =
    useCallback(
      (
        event:
          KonvaEventObject<MouseEvent>,

        titulo:
          string,

        lineas:
          string[],
      ) => {
        /*
         * Durante el pan no hacemos ningún
         * trabajo de tooltip.
         */
        if (
          arrastrandoMapa
        ) {
          return;
        }

        const rect =
          contenedorRef.current
            ?.getBoundingClientRect();

        if (!rect) {
          return;
        }

        const ahora =
          performance.now();

        const mismoElemento =
          ultimoTooltipTituloRef
            .current ===
          titulo;

        /*
         * No actualizamos React por cada píxel
         * recorrido por el mouse.
         */
        if (
          mismoElemento &&
          ahora -
          ultimoTooltipMsRef.current <
          INTERVALO_TOOLTIP_MS
        ) {
          return;
        }

        ultimoTooltipMsRef.current =
          ahora;

        ultimoTooltipTituloRef.current =
          titulo;

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
      },
      [
        arrastrandoMapa,
        contenedorRef,
      ],
    );

  const ocultarTooltip =
    useCallback(
      () => {
        ultimoTooltipTituloRef.current =
          null;

        setTooltip(
          null,
        );
      },
      [],
    );
  /*
   * Enfoque automático al seleccionar un vehículo (como un punto en
   * Google Maps): acerca a ESCALA_ENFOQUE y lo deja en el centro del
   * área libre entre paneles, con una animación corta.
   *
   * Posición: la del nodo Konva dibujado (posición animada real).
   * Si el vehículo está oculto por un filtro o agrupado, se usa la
   * posición que reporta el backend.
   */
  const ultimoCentradoRef =
    useRef<string | null>(
      null,
    );

  const centrarEnVehiculo =
    useCallback(
      (id: string) => {
        const stage =
          stageRef.current;

        if (
          !stage ||
          width <= 0 ||
          height <= 0
        ) {
          return;
        }

        const vistaStage = {
          escala:
            stage.scaleX(),
          x: stage.x(),
          y: stage.y(),
        };

        let punto:
          | { x: number; y: number }
          | null = null;

        const nodo =
          stage.findOne(
            `#vehiculo-${id}`,
          );

        if (nodo) {
          punto =
            pantallaAMapa(
              nodo.getAbsolutePosition(),
              vistaStage,
            );
        } else {
          const vehiculo =
            vehiculos.find(
              (item) =>
                item.id === id,
            );

          if (vehiculo) {
            punto = {
              x: geometria.x(
                vehiculo.x,
              ),
              y: geometria.y(
                vehiculo.y,
              ),
            };
          }
        }

        if (!punto) {
          return;
        }

        ultimoCentradoRef.current =
          id;

        animarHacia(
          calcularVistaCentrada(
            punto,
            width,
            height,
            margenesPaneles(
              window.innerWidth,
              true,
            ),
            vistaStage.escala,
          ),
          DURACION_ENFOQUE_MS,
        );
      },
      [
        animarHacia,
        geometria,
        height,
        stageRef,
        vehiculos,
        width,
      ],
    );

  /*
   * Selección desde fuera del mapa (PanelListasOperacion).
   * El clic en el mapa ya centra en seleccionarVehiculo; el ref
   * evita repetir la animación cuando la selección vuelve como prop.
   */
  useEffect(() => {
    if (!seleccionVehiculoId) {
      ultimoCentradoRef.current =
        null;
      return;
    }

    if (
      ultimoCentradoRef.current ===
      seleccionVehiculoId
    ) {
      return;
    }

    centrarEnVehiculo(
      seleccionVehiculoId,
    );
  }, [
    centrarEnVehiculo,
    seleccionVehiculoId,
  ]);

  const seleccionarVehiculo =
    useCallback(
      (id: string) => {
        setVehiculoSeleccionadoId(
          id,
        );

        centrarEnVehiculo(
          id,
        );

        onSeleccionarVehiculo?.(
          id,
        );
      },
      [
        centrarEnVehiculo,
        onSeleccionarVehiculo,
      ],
    );

  const seleccionarPedido =
    useCallback(
      (
        id: string,
      ) => {
        onSeleccionarPedido?.(
          id,
        );
      },
      [
        onSeleccionarPedido,
      ],
    );



 function manejarRueda(
  event:
    KonvaEventObject<WheelEvent>,
) {
  event.evt.preventDefault();

  const rect =
    contenedorRef.current
      ?.getBoundingClientRect();

  if (!rect) {
    return;
  }

  aplicarZoomCompositor(
    event.evt.clientX -
      rect.left,

    event.evt.clientY -
      rect.top,

    event.evt.deltaY,
  );
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

  /*
   * "Ajustar mapa": encuadra el mapa completo en el área libre entre
   * la columna izquierda, el detalle (si está abierto) y la barra superior.
   */
  function ajustarMapa() {
    if (
      width <= 0 ||
      height <= 0
    ) {
      return;
    }

    const hayDetalle =
      Boolean(
        seleccionVehiculoId ||
          seleccionPedidoId ||
          seleccionAlmacenId,
      );

    animarHacia(
      calcularVistaAjustada(
        {
          x: geometria.origenX,
          y: geometria.origenY,
          ancho: geometria.ancho,
          alto: geometria.alto,
        },
        width,
        height,
        margenesPaneles(
          window.innerWidth,
          hayDetalle,
        ),
      ),
      DURACION_ENFOQUE_MS,
    );
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



            onMouseDown={(
              event,
            ) => {
              if (
                event.evt.button !==
                0
              ) {
                return;
              }

              iniciarPan(
                event.evt.clientX,
                event.evt.clientY,
              );

              setTooltip(
                null,
              );

              ultimoTooltipTituloRef.current =
                null;

              if (
                event.target ===
                event.target.getStage()
              ) {
                setVehiculoSeleccionadoId(
                  null,
                );
              }
            }}
            ref={
              stageRef
            }


          >
            <CapaReticula
              geometria={
                geometria
              }
              anchoMapa={
                ANCHO_MAPA
              }
              altoMapa={
                ALTO_MAPA
              }
            />

            {/* BLOQUEOS */}
            <Layer
              name="pan-pesado"

              listening={
                !arrastrandoMapa
              }

              visible={
                !arrastrandoMapa
              }
            >
              <CapaBloqueos
                bloqueos={
                  bloqueos
                }

                visible={
                  filtros.BLOQUEOS
                }

                convertirX={
                  geometria.x
                }

                convertirY={
                  geometria.y
                }

                onTooltip={
                  mostrarTooltip
                }

                onLeave={
                  ocultarTooltip
                }
              />
            </Layer>

            {/* CAPAS DINÁMICAS */}
            <CapaDinamicaMapa
              vehiculos={
                vehiculos
              }

              rutas={
                rutas
              }

              viewportPixeles={
  viewportPixeles
}


              tiposFlota={
                tiposFlota
              }

              instanteSimulado={
                instanteSimulado
              }

              factorAceleracion={
                factorAceleracion
              }

              estadoEscenario={
                estadoEscenario
              }

              vehiculoSeleccionadoId={
                vehiculoSeleccionadoId
              }

              seleccionPedidoId={
                seleccionPedidoId
              }

              mostrarRutas={
                filtros.RUTAS
              }

              mostrarAutos={
                filtros.AUTOS
              }

              mostrarMotos={
                filtros.MOTOS
              }

              mostrarBicicletas={
                filtros.BICICLETAS
              }

              filtroCarga={
                filtroCargaVehiculos
              }

              verdeHasta={
                configuracion
                  .vehiculoVerdeHasta
              }

              ambarHasta={
                configuracion
                  .vehiculoAmbarHasta
              }

              convertirX={
                geometria.x
              }

              convertirY={
                geometria.y
              }

              onSeleccionarVehiculo={
                seleccionarVehiculo
              }

              onTooltip={
                mostrarTooltip
              }

              onLeave={
                ocultarTooltip
              }

              modoPan={
                arrastrandoMapa
              }

              capaIntermedia={
                <Layer
                  name="pan-pesado"
                  listening={
                    !arrastrandoMapa
                  }
                  visible={
                    !arrastrandoMapa
                  }
                >
                  <CapaPedidos
                    pedidos={
                      pedidos
                    }

                    visible={
                      filtros.PEDIDOS
                    }

                    seleccionPedidoId={
                      seleccionPedidoId
                    }

                    instanteReferenciaMs={
                      instantePedidosMs
                    }

                    convertirX={
                      geometria.x
                    }

                    convertirY={
                      geometria.y
                    }

                    onSeleccionar={
                      seleccionarPedido
                    }

                    onTooltip={
                      mostrarTooltip
                    }

                    onLeave={
                      ocultarTooltip
                    }
                    viewport={
  viewportMapa
}

                  />

                  <CapaAlmacenes
                    almacenes={
                      almacenes
                    }

                    filtroStock={
                      filtroStockAlmacenes
                    }

                    verdeHasta={
                      configuracion
                        .almacenVerdeHasta
                    }

                    ambarHasta={
                      configuracion
                        .almacenAmbarHasta
                    }

                    seleccionAlmacenId={
                      seleccionAlmacenId
                    }

                    convertirX={
                      geometria.x
                    }

                    convertirY={
                      geometria.y
                    }

                    onSeleccionar={
                      onSeleccionarAlmacen
                    }

                    onTooltip={
                      mostrarTooltip
                    }

                    onLeave={
                      ocultarTooltip
                    }
                  />
                </Layer>
              }
            />

            {/* AVERÍAS */}
            <Layer
              name="pan-pesado"
              listening={
                !arrastrandoMapa
              }
              visible={
                !arrastrandoMapa
              }
            >
              <CapaAverias
                averias={
                  averias
                }

                visible={
                  filtros.AVERIAS
                }

                convertirX={
                  geometria.x
                }

                convertirY={
                  geometria.y
                }

                onTooltip={
                  mostrarTooltip
                }

                onLeave={
                  ocultarTooltip
                }
              />
            </Layer>
          </Stage>
        )}



      {/*
        * FILTROS: un solo botón que abre un panel desplegable.
        * Se alinea con el ancho responsivo de la columna izquierda.
        */}
      <div className="absolute left-[calc(min(430px,_32vw)_+_40px)] top-3 z-30">

        <button
          type="button"
          onClick={() =>
            setFiltrosMinimizados(
              (actual) => !actual,
            )
          }
          aria-expanded={
            !filtrosMinimizados
          }
          title={
            filtrosMinimizados
              ? "Mostrar filtros"
              : "Ocultar filtros"
          }
          className="flex items-center gap-2 rounded-xl border border-slate-700 bg-[#11161d]/97 px-4 py-2.5 text-sm font-semibold text-white shadow-2xl hover:bg-slate-800"
        >
          <SlidersHorizontal className="size-4 text-orange-400" />
          Filtros
          {filtrosActivos > 0 && (
            <span className="rounded-full bg-orange-500 px-1.5 text-[10px] font-bold text-white">
              {filtrosActivos}
            </span>
          )}
          <ChevronDown
            className={[
              "size-4 text-slate-500 transition-transform duration-150",
              filtrosMinimizados
                ? ""
                : "rotate-180",
            ].join(" ")}
          />
        </button>

        {!filtrosMinimizados && (
        <div className="mt-2 w-[min(340px,calc(100vw-2rem))] rounded-xl border border-slate-700 bg-[#11161d]/97 p-3 shadow-2xl animate-in fade-in slide-in-from-top-1 duration-150">
        <p className="mb-2 text-[10px] font-semibold uppercase text-slate-500">
          Mostrar en el mapa
        </p>
        <div className="flex flex-wrap gap-2">
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

        <p className="mb-2 mt-3 text-[10px] font-semibold uppercase text-slate-500">
          Semáforo
        </p>
        <div className="flex flex-wrap gap-2">
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
            <option className="text-black" value="TODOS">Todos</option>
            <option className="text-black" value="VACIO">Sin carga</option>
            <option className="text-black" value="VERDE">Verde</option>
            <option className="text-black" value="AMBAR">Ámbar</option>
            <option className="text-black" value="ROJO">Rojo</option>
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
            <option className="text-black" value="TODOS">Todos</option>
            <option className="text-black" value="VACIO">Vacío</option>
            <option className="text-black" value="VERDE">Verde</option>
            <option className="text-black" value="AMBAR">Ámbar</option>
            <option className="text-black" value="ROJO">Rojo</option>
          </select>
        </label>
        </div>
        </div>
        )}
      </div>

      {/* ACCIONES + ZOOM: una sola barra arriba a la derecha */}
      <div className="absolute right-3 top-3 z-40 flex items-start gap-2">
      {accionesExtra}
      <div className="flex gap-1 rounded-xl border border-slate-700 bg-[#11161d]/97 p-1 shadow-2xl">

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

        <BotonMapa
          titulo="Ajustar mapa al área libre"
          onClick={
            ajustarMapa
          }
        >
          <Maximize2 className="size-4" />
        </BotonMapa>
      </div>
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

      {/* LEYENDA (minimizable) */}
      <VentanaFlotante
        titulo="LEYENDA"
        inicialmenteMinimizada
        className="absolute bottom-5 right-5 z-20 w-[min(220px,40vw)] text-xs text-slate-300"
        classNameCuerpo="p-4 pt-3"
      >

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
          color="#EC4899"
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
          color="#0891B2"
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
      </VentanaFlotante>

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
              ? `${nombreMotivoDetencion(visual.vehiculo.motivoDetencion)}${visual.vehiculo.detenidoHasta
                ? ` hasta ${formatearHora(visual.vehiculo.detenidoHasta)}`
                : ""
              }`
              : seguimiento.enRefrigerio
                ? `En refrigerio hasta ${seguimiento.refrigerioHasta
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

                    {parada.tipo ===
                      "TRASVASE"
                      ? `(${parada.x}, ${parada.y}) · Llegada ${
                        parada.llegada
                          ? formatearHora(
                            parada.llegada,
                          )
                          : "Sin horario"
                      } · Fin ${
                        parada.fin
                          ? formatearHora(
                            parada.fin,
                          )
                          : "Sin horario"
                      } · ${parada.paquetes} paq.`
                      : parada.llegada
                        ? formatearHora(
                          parada.llegada,
                        )
                        : "Sin ETA"}

                    {parada.tipo !==
                      "TRASVASE" &&
                    parada.paquetes >
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
