import type {
  AlmacenResponseDto,
} from "@/api/almacenes.dto";

import type {
  AlmacenMapa,
} from "@/features/ejecucion/ejecucion.types";

const NOMBRES_ALMACEN: Record<
  string,
  string
> = {
  CENTRAL: "Central",
  EAST: "Este",
  NORTHWEST: "Nor-Oeste",
};

export function adaptarAlmacen(
  dto: AlmacenResponseDto,
): AlmacenMapa {
  return {
    id: dto.id,

    nombre:
      NOMBRES_ALMACEN[dto.id] ??
      dto.id,

    tipo:
      dto.tipo === "CENTRAL"
        ? "CENTRAL"
        : "INTERMEDIO",

    x: dto.x,
    y: dto.y,

    stock:
      dto.stockInicial ??
      undefined,
  };
}

export function adaptarAlmacenes(
  dtos: AlmacenResponseDto[],
): AlmacenMapa[] {
  return dtos.map(
    adaptarAlmacen,
  );
}