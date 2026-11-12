import {
    memo,
    useMemo,
} from "react";

import type {
    KonvaEventObject,
} from "konva/lib/Node";

import {
    Circle,
    Shape,
} from "react-konva";

import type {
    PedidoOperacion,
} from "./ejecucion.types";

/*
 * =========================================================
 * CONFIGURACIÓN
 * =========================================================
 */

/*
 * Radio visual original de los pedidos.
 */
const RADIO_PEDIDO = 5.5;

/*
 * Radio un poco mayor para facilitar click/hover.
 *
 * No cambia el tamaño visual.
 * Solo el área interactiva.
 */
const RADIO_HIT = 9;

/*
 * Tamaño de celda del índice espacial.
 *
 * Evita recorrer miles de pedidos cada vez
 * que queremos identificar el pedido bajo
 * el cursor.
 */
const TAMANO_CELDA_PX = 24;

const ANCHO_MAPA = 70;
const ALTO_MAPA = 50;

const FORMATEADOR_FECHA =
    new Intl.DateTimeFormat(
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
    );

type CategoriaPedido =
    | "VENCIDO"
    | "PENDIENTE"
    | "ASIGNADO"
    | "EN_TRANSITO"
    | "ENTREGADO";

interface EstiloPedido {
    fill:
    string;

    opacity:
    number;
}

const ESTILOS_PEDIDO:
    Record<
        CategoriaPedido,
        EstiloPedido
    > = {
    VENCIDO: {
        fill:
            "#dc2626",

        opacity:
            0.95,
    },

    PENDIENTE: {
        fill:
            "#8b5cf6",

        opacity:
            0.95,
    },

    ASIGNADO: {
        fill:
            "#2563eb",

        opacity:
            0.95,
    },

    EN_TRANSITO: {
        fill:
            "#f97316",

        opacity:
            0.95,
    },

    ENTREGADO: {
        fill:
            "#0891B2",

        opacity:
            0.75,
    },
};

/*
 * Orden de pintura.
 *
 * Vencidos quedan al final para ser más
 * visibles cuando existen puntos superpuestos.
 */
const ORDEN_CATEGORIAS:
    CategoriaPedido[] = [
        "ENTREGADO",
        "PENDIENTE",
        "ASIGNADO",
        "EN_TRANSITO",
        "VENCIDO",
    ];

/*
 * =========================================================
 * TIPOS
 * =========================================================
 */

interface CapaPedidosProps {
    pedidos:
    PedidoOperacion[];

    visible:
    boolean;

    seleccionPedidoId:
    string | null;

    instanteReferenciaMs:
    number;

    viewport: {
        minX:
        number;

        maxX:
        number;

        minY:
        number;

        maxY:
        number;
    };

    convertirX:
    (
        valor:
            number,
    ) => number;

    convertirY:
    (
        valor:
            number,
    ) => number;

    onSeleccionar?:
    (
        id:
            string,
    ) => void;

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

interface PedidoBase {
    pedido:
    PedidoOperacion;

    /*
     * Coordenadas ya convertidas a píxeles.
     *
     * No volvemos a llamar convertirX/Y durante
     * cada dibujo.
     */
    px:
    number;

    py:
    number;

    vencimientoMs:
    number;

    lineasTooltip:
    string[];
}

interface PedidoVisual
    extends PedidoBase {
    categoria:
    CategoriaPedido;
}

interface LotePedidos {
    categoria:
    CategoriaPedido;

    pedidos:
    PedidoVisual[];

    /*
     * Índice espacial:
     *
     * "celdaX:celdaY"
     *     ↓
     * pedidos próximos.
     */
    indice:
    Map<
        string,
        PedidoVisual[]
    >;
}

/*
 * =========================================================
 * COMPONENTE
 * =========================================================
 */

function CapaPedidosBase({
    pedidos,
    visible,
    seleccionPedidoId,
    instanteReferenciaMs,
    viewport,
    convertirX,
    convertirY,
    onSeleccionar,
    onTooltip,
    onLeave,
}: CapaPedidosProps) {
    /*
     * =======================================================
     * PREPROCESAMIENTO ESTÁTICO
     * =======================================================
     *
     * Antes:
     *
     * cada Circle hacía conversiones, Date,
     * strings de tooltip, etc.
     *
     * Ahora lo calculamos una sola vez cuando
     * cambia la colección de pedidos.
     */
    const pedidosBase =
        useMemo<
            PedidoBase[]
        >(
            () =>
                pedidos.map(
                    (
                        pedido,
                    ) => ({
                        pedido,

                        px:
                            convertirX(
                                pedido.x,
                            ),

                        py:
                            convertirY(
                                pedido.y,
                            ),

                        vencimientoMs:
                            new Date(
                                pedido.vence,
                            ).getTime(),

                        lineasTooltip: [
                            `Cliente: ${pedido.clienteId}`,

                            `Coordenadas: (${pedido.x}, ${pedido.y})`,

                            `Paquetes: ${pedido.cantidad}`,

                            `Plazo: ${pedido.plazoHoras} h`,

                            `Estado: ${pedido.estado}`,

                            `Vence: ${FORMATEADOR_FECHA.format(
                                new Date(
                                    pedido.vence,
                                ),
                            )}`,
                        ],
                    }),
                ),
            [
                pedidos,
                convertirX,
                convertirY,
            ],
        );

    /*
     * =======================================================
     * CLASIFICACIÓN VISUAL
     * =======================================================
     *
     * Solo cambia cuando:
     *
     * - cambia un pedido;
     * - cambia el instante utilizado para vencimientos.
     */
    const pedidosVisuales =
        useMemo<
            PedidoVisual[]
        >(
            () =>
                pedidosBase.map(
                    (
                        base,
                    ) => ({
                        ...base,

                        categoria:
                            obtenerCategoriaPedido(
                                base.pedido,
                                base.vencimientoMs,
                                instanteReferenciaMs,
                            ),
                    }),
                ),
            [
                pedidosBase,
                instanteReferenciaMs,
            ],
        );

    /*
 * =========================================================
 * VIEWPORT CULLING
 * =========================================================
 *
 * Solo conservamos pedidos que están dentro del
 * área actualmente visible del Stage.
 *
 * Este cálculo ocurre cuando cambia:
 * - la vista;
 * - el zoom;
 * - los pedidos.
 *
 * No ocurre en cada frame de vehículos.
 */
    const pedidosVisibles =
  useMemo(
    () => {
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

      /*
       * Fail-safe:
       *
       * Si por cualquier razón el cálculo
       * de viewport falla, NO dejamos vacío
       * el mapa. Dibujamos todos los pedidos.
       */
      if (
        !viewportValido
      ) {
        return pedidosVisuales;
      }

      return pedidosVisuales.filter(
        (
          pedido,
        ) =>
          pedido.pedido.x >=
            viewport.minX &&
          pedido.pedido.x <=
            viewport.maxX &&
          pedido.pedido.y >=
            viewport.minY &&
          pedido.pedido.y <=
            viewport.maxY,
      );
    },
    [
      pedidosVisuales,
      viewport.maxX,
      viewport.maxY,
      viewport.minX,
      viewport.minY,
    ],
  );


    /*
     * =======================================================
     * BATCH
     * =======================================================
     *
     * En vez de:
     *
     * 5000 pedidos
     * → 5000 Circle
     *
     * generamos como máximo:
     *
     * 5 Shape
     *
     * una por categoría/color.
     */
    const lotes =
        useMemo<
            LotePedidos[]
        >(
            () => {
                const agrupados =
                    new Map<
                        CategoriaPedido,
                        PedidoVisual[]
                    >();

                for (
                    const categoria of
                    ORDEN_CATEGORIAS
                ) {
                    agrupados.set(
                        categoria,
                        [],
                    );
                }

                for (
                    const pedido of
                    pedidosVisibles
                ) {
                    agrupados
                        .get(
                            pedido.categoria,
                        )
                        ?.push(
                            pedido,
                        );
                }

                return ORDEN_CATEGORIAS.map(
                    (
                        categoria,
                    ) => {
                        const items =
                            agrupados.get(
                                categoria,
                            ) ?? [];

                        return {
                            categoria,

                            pedidos:
                                items,

                            indice:
                                construirIndiceEspacial(
                                    items,
                                ),
                        };
                    },
                ).filter(
                    (
                        lote,
                    ) =>
                        lote.pedidos.length >
                        0,
                );
            },
            [
                pedidosVisibles,
            ],
        );

    /*
     * Pedido seleccionado.
     *
     * Sigue siendo un Circle independiente para
     * conservar exactamente el resaltado naranja.
     */
    const pedidoSeleccionado =
        useMemo(
            () =>
                seleccionPedidoId
                    ? pedidosVisibles.find(
                        (
                            pedido,
                        ) =>
                            pedido.pedido
                                .id ===
                            seleccionPedidoId,
                    ) ??
                    null
                    : null,
            [
                pedidosVisibles,
                seleccionPedidoId,
            ],
        );

    /*
     * Dimensiones aproximadas del área lógica.
     *
     * Shape necesita un área conocida aunque
     * el dibujo real lo hagamos manualmente.
     */
    const anchoShape =
        Math.max(
            convertirX(
                0,
            ),

            convertirX(
                ANCHO_MAPA,
            ),
        ) +
        20;

    const altoShape =
        Math.max(
            convertirY(
                0,
            ),

            convertirY(
                ALTO_MAPA,
            ),
        ) +
        20;

    if (!visible) {
        return null;
    }

    return (
        <>
            {lotes.map(
                (
                    lote,
                ) => {
                    const estilo =
                        ESTILOS_PEDIDO[
                        lote.categoria
                        ];

                    return (
                        <Shape
                            key={
                                lote.categoria
                            }

                            width={
                                anchoShape
                            }

                            height={
                                altoShape
                            }

                            fill={
                                estilo.fill
                            }

                            stroke="#ffffff"

                            strokeWidth={
                                1
                            }

                            opacity={
                                estilo.opacity
                            }

                            /*
                             * Evita algunos buffers auxiliares
                             * de Konva que no aportan en puntos
                             * tan pequeños.
                             */
                            perfectDrawEnabled={
                                false
                            }

                            shadowForStrokeEnabled={
                                false
                            }

                            /*
                             * =================================
                             * DIBUJO VISUAL
                             * =================================
                             *
                             * Todos los pedidos de este color
                             * se pintan en una sola operación
                             * de Shape.
                             */
                            sceneFunc={(
                                context,
                                shape,
                            ) => {
                                context.beginPath();

                                for (
                                    const pedido of
                                    lote.pedidos
                                ) {
                                    /*
                                     * moveTo evita que Canvas conecte
                                     * un círculo con el siguiente.
                                     */
                                    context.moveTo(
                                        pedido.px +
                                        RADIO_PEDIDO,

                                        pedido.py,
                                    );

                                    context.arc(
                                        pedido.px,
                                        pedido.py,
                                        RADIO_PEDIDO,
                                        0,
                                        Math.PI *
                                        2,
                                        false,
                                    );
                                }

                                context.fillStrokeShape(
                                    shape,
                                );
                            }}

                            /*
                             * =================================
                             * HIT CANVAS
                             * =================================
                             *
                             * Seguimos teniendo hover/click,
                             * pero sin un nodo Konva por pedido.
                             *
                             * Dibujamos una zona invisible algo
                             * mayor alrededor de cada pedido.
                             */
                            hitFunc={(
                                context,
                                shape,
                            ) => {
                                context.beginPath();

                                for (
                                    const pedido of
                                    lote.pedidos
                                ) {
                                    context.moveTo(
                                        pedido.px +
                                        RADIO_HIT,

                                        pedido.py,
                                    );

                                    context.arc(
                                        pedido.px,
                                        pedido.py,
                                        RADIO_HIT,
                                        0,
                                        Math.PI *
                                        2,
                                        false,
                                    );
                                }

                                context.fillStrokeShape(
                                    shape,
                                );
                            }}

                            onClick={(
                                event,
                            ) => {
                                const pedido =
                                    obtenerPedidoDesdeEvento(
                                        event,
                                        lote,
                                    );

                                if (!pedido) {
                                    return;
                                }

                                onSeleccionar?.(
                                    pedido.pedido.id,
                                );
                            }}

                            onTap={(
                                event,
                            ) => {
                                const pedido =
                                    obtenerPedidoDesdeEvento(
                                        event,
                                        lote,
                                    );

                                if (!pedido) {
                                    return;
                                }

                                onSeleccionar?.(
                                    pedido.pedido.id,
                                );
                            }}

                            onMouseEnter={(
                                event,
                            ) => {
                                const pedido =
                                    obtenerPedidoDesdeEvento(
                                        event,
                                        lote,
                                    );

                                if (!pedido) {
                                    return;
                                }

                                onTooltip(
                                    event,

                                    pedido.pedido.id,

                                    pedido
                                        .lineasTooltip,
                                );
                            }}

                            onMouseMove={(
                                event,
                            ) => {
                                const pedido =
                                    obtenerPedidoDesdeEvento(
                                        event,
                                        lote,
                                    );

                                if (!pedido) {
                                    return;
                                }

                                onTooltip(
                                    event,

                                    pedido.pedido.id,

                                    pedido
                                        .lineasTooltip,
                                );
                            }}

                            onMouseLeave={
                                onLeave
                            }
                        />
                    );
                },
            )}

            {/*
       * ===================================================
       * PEDIDO SELECCIONADO
       * ===================================================
       *
       * Único Circle individual normalmente presente.
       *
       * listening=false para que los eventos continúen
       * llegando al Shape batch que está debajo.
       */}
            {pedidoSeleccionado && (
                <Circle
                    x={
                        pedidoSeleccionado
                            .px
                    }

                    y={
                        pedidoSeleccionado
                            .py
                    }

                    radius={
                        8
                    }

                    fill={
                        ESTILOS_PEDIDO[
                            pedidoSeleccionado
                                .categoria
                        ].fill
                    }

                    opacity={
                        ESTILOS_PEDIDO[
                            pedidoSeleccionado
                                .categoria
                        ].opacity
                    }

                    stroke="#f97316"

                    strokeWidth={
                        3
                    }

                    listening={
                        false
                    }

                    perfectDrawEnabled={
                        false
                    }
                />
            )}
        </>
    );
}

export const CapaPedidos =
    memo(
        CapaPedidosBase,
    );

/*
 * =========================================================
 * ÍNDICE ESPACIAL
 * =========================================================
 */

function claveCelda(
    x:
        number,

    y:
        number,
) {
    const celdaX =
        Math.floor(
            x /
            TAMANO_CELDA_PX,
        );

    const celdaY =
        Math.floor(
            y /
            TAMANO_CELDA_PX,
        );

    return `${celdaX}:${celdaY}`;
}

function construirIndiceEspacial(
    pedidos:
        PedidoVisual[],
) {
    const indice =
        new Map<
            string,
            PedidoVisual[]
        >();

    for (
        const pedido of
        pedidos
    ) {
        const clave =
            claveCelda(
                pedido.px,
                pedido.py,
            );

        const celda =
            indice.get(
                clave,
            );

        if (celda) {
            celda.push(
                pedido,
            );
        } else {
            indice.set(
                clave,
                [
                    pedido,
                ],
            );
        }
    }

    return indice;
}

/*
 * Busca únicamente en la celda actual
 * y las ocho vecinas.
 *
 * No recorremos los 5000 pedidos.
 */
function buscarPedidoCercano(
    lote:
        LotePedidos,

    x:
        number,

    y:
        number,
) {
    const celdaX =
        Math.floor(
            x /
            TAMANO_CELDA_PX,
        );

    const celdaY =
        Math.floor(
            y /
            TAMANO_CELDA_PX,
        );

    const radioCuadrado =
        RADIO_HIT *
        RADIO_HIT;

    let mejor:
        PedidoVisual | null =
        null;

    let mejorDistancia =
        radioCuadrado;

    /*
     * Recorremos en orden inverso para aproximar
     * el comportamiento visual de Canvas cuando
     * existen varios pedidos exactamente encima.
     */
    for (
        let offsetX = -1;
        offsetX <= 1;
        offsetX += 1
    ) {
        for (
            let offsetY = -1;
            offsetY <= 1;
            offsetY += 1
        ) {
            const clave =
                `${celdaX + offsetX}:${celdaY + offsetY}`;

            const candidatos =
                lote.indice.get(
                    clave,
                );

            if (!candidatos) {
                continue;
            }

            for (
                let i =
                    candidatos.length -
                    1;
                i >= 0;
                i -= 1
            ) {
                const candidato =
                    candidatos[i];

                const dx =
                    candidato.px -
                    x;

                const dy =
                    candidato.py -
                    y;

                const distancia =
                    dx * dx +
                    dy * dy;

                if (
                    distancia <=
                    mejorDistancia
                ) {
                    mejor =
                        candidato;

                    mejorDistancia =
                        distancia;
                }
            }
        }
    }

    return mejor;
}

/*
 * =========================================================
 * EVENTOS
 * =========================================================
 */

function obtenerPedidoDesdeEvento(
    event:
        KonvaEventObject<Event>,

    lote:
        LotePedidos,
) {
    const stage =
        event.target.getStage();

    const layer =
        event.target.getLayer();

    const puntero =
        stage
            ?.getPointerPosition();

    if (
        !stage ||
        !layer ||
        !puntero
    ) {
        return null;
    }

    /*
     * getPointerPosition devuelve coordenadas
     * absolutas del Stage.
     *
     * Las convertimos a coordenadas locales de
     * la Layer teniendo en cuenta pan y zoom.
     */
    const transformacion =
        layer
            .getAbsoluteTransform()
            .copy()
            .invert();

    const puntoLocal =
        transformacion.point(
            puntero,
        );

    return buscarPedidoCercano(
        lote,
        puntoLocal.x,
        puntoLocal.y,
    );
}

/*
 * =========================================================
 * ESTADO VISUAL
 * =========================================================
 */

function obtenerCategoriaPedido(
    pedido:
        PedidoOperacion,

    vencimientoMs:
        number,

    instanteMs:
        number,
): CategoriaPedido {
    if (
        pedido.estado !==
        "ENTREGADO" &&
        Number.isFinite(
            vencimientoMs,
        ) &&
        vencimientoMs <
        instanteMs
    ) {
        return "VENCIDO";
    }

    switch (
    pedido.estado
    ) {
        case "ENTREGADO":
            return "ENTREGADO";

        case "EN_TRANSITO":
            return "EN_TRANSITO";

        case "ASIGNADO":
            return "ASIGNADO";

        case "PENDIENTE":
            return "PENDIENTE";
    }
}
