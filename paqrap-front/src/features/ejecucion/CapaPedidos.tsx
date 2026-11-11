import {
  memo,
  useMemo,
} from "react";

import type {
  KonvaEventObject,
} from "konva/lib/Node";

import {
  Circle,
} from "react-konva";

import type {
  PedidoOperacion,
} from "./ejecucion.types";

interface CapaPedidosProps {
  pedidos:
    PedidoOperacion[];

  visible:
    boolean;

  seleccionPedidoId:
    string | null;

  instanteReferenciaMs:
    number;

  convertirX:
    (valor: number) => number;

  convertirY:
    (valor: number) => number;

  onSeleccionar?:
    (id: string) => void;

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

interface PedidoPreparado {
  pedido:
    PedidoOperacion;

  vencimientoMs:
    number;
}

function CapaPedidosBase({
  pedidos,
  visible,
  seleccionPedidoId,
  instanteReferenciaMs,
  convertirX,
  convertirY,
  onSeleccionar,
  onTooltip,
  onLeave,
}: CapaPedidosProps) {
  /*
   * Parseamos los vencimientos únicamente cuando
   * cambia la colección de pedidos.
   *
   * Evita crear Date repetidamente durante la
   * animación del mapa.
   */
  const pedidosPreparados =
    useMemo<
      PedidoPreparado[]
    >(
      () =>
        pedidos.map(
          (pedido) => ({
            pedido,

            vencimientoMs:
              new Date(
                pedido.vence,
              ).getTime(),
          }),
        ),
      [
        pedidos,
      ],
    );

  if (!visible) {
    return null;
  }

  return (
    <>
      {pedidosPreparados.map(
        ({
          pedido,
          vencimientoMs,
        }) => {
          const seleccionado =
            seleccionPedidoId ===
            pedido.id;

          const color =
            colorPedido(
              pedido,
              vencimientoMs,
              instanteReferenciaMs,
            );

          const lineas = [
            `Cliente: ${pedido.clienteId}`,

            `Coordenadas: (${pedido.x}, ${pedido.y})`,

            `Paquetes: ${pedido.cantidad}`,

            `Plazo: ${pedido.plazoHoras} h`,

            `Estado: ${pedido.estado}`,

            `Vence: ${formatearFecha(
              pedido.vence,
            )}`,
          ];

          return (
            <Circle
              key={
                pedido.id
              }

              x={
                convertirX(
                  pedido.x,
                )
              }

              y={
                convertirY(
                  pedido.y,
                )
              }

              radius={
                seleccionado
                  ? 8
                  : 5.5
              }

              fill={
                color
              }

              stroke={
                seleccionado
                  ? "#f97316"
                  : "#ffffff"
              }

              strokeWidth={
                seleccionado
                  ? 3
                  : 1
              }

              opacity={
                pedido.estado ===
                "ENTREGADO"
                  ? 0.75
                  : 0.95
              }

              onClick={() =>
                onSeleccionar?.(
                  pedido.id,
                )
              }

              onTap={() =>
                onSeleccionar?.(
                  pedido.id,
                )
              }

              onMouseEnter={(
                event,
              ) =>
                onTooltip(
                  event,
                  pedido.id,
                  lineas,
                )
              }

              onMouseMove={(
                event,
              ) =>
                onTooltip(
                  event,
                  pedido.id,
                  lineas,
                )
              }

              onMouseLeave={
                onLeave
              }
            />
          );
        },
      )}
    </>
  );
}

export const CapaPedidos =
  memo(
    CapaPedidosBase,
  );

function colorPedido(
  pedido:
    PedidoOperacion,

  vencimientoMs:
    number,

  instanteMs:
    number,
) {
  if (
    pedido.estado !==
      "ENTREGADO" &&
    vencimientoMs <
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
    new Date(
      valor,
    ),
  );
}