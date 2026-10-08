import type {
  EstadoPedidoBackend,
  PedidoResponseDto,
} from "@/api/pedidos.dto";

import type {
  EstadoPedido,
  PedidoOperacion,
} from "@/features/ejecucion/ejecucion.types";

function adaptarEstadoPedido(
  estado: EstadoPedidoBackend,
): EstadoPedido {
  switch (estado) {
    case "REGISTERED":
      return "PENDIENTE";

    case "ASSIGNED":
      return "ASIGNADO";

    case "IN_TRANSIT":
      return "EN_TRANSITO";

    case "DELIVERED":
      return "ENTREGADO";
  }
}

export function adaptarPedido(
  dto: PedidoResponseDto,
): PedidoOperacion {
  return {
    id: dto.id,

    x: dto.x,
    y: dto.y,

    cantidad: dto.paquetes,

    plazoHoras:
      dto.horasPrometidas,

    estado:
      adaptarEstadoPedido(
        dto.estado,
      ),
  };
}

export function adaptarPedidos(
  dtos: PedidoResponseDto[],
): PedidoOperacion[] {
  return dtos.map(
    adaptarPedido,
  );
}