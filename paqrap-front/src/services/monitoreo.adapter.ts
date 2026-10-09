import type {
  AlmacenMonitoreoDto,
  VehiculoMonitoreoDto,
} from "@/api/monitoreo.dto";

import type {
  AlmacenMapa,
  EstadoVehiculo,
  TipoVehiculo,
  VehiculoMapa,
} from "@/features/ejecucion/ejecucion.types";

const NOMBRES_ALMACEN: Record<
  string,
  string
> = {
  CENTRAL: "Central",
  EAST: "Este",
  NORTHWEST: "Nor-Oeste",
};

function adaptarTipoVehiculo(
  tipo: VehiculoMonitoreoDto["tipo"],
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

function adaptarEstadoVehiculo(
  estado: VehiculoMonitoreoDto["estado"],
): EstadoVehiculo {
  switch (estado) {
    case "AVAILABLE":
      return "DISPONIBLE";

    case "IN_ROUTE":
      return "EN_RUTA";

    case "UNAVAILABLE":
      return "NO_DISPONIBLE";
  }
}

export function adaptarVehiculoMonitoreo(
  dto: VehiculoMonitoreoDto,
): VehiculoMapa {
  return {
    id: dto.id,

    tipo: adaptarTipoVehiculo(
      dto.tipo,
    ),

    estado: adaptarEstadoVehiculo(
      dto.estado,
    ),

    x: dto.x,
    y: dto.y,
  };
}

export function adaptarVehiculosMonitoreo(
  dtos: VehiculoMonitoreoDto[],
): VehiculoMapa[] {
  return dtos.map(
    adaptarVehiculoMonitoreo,
  );
}

export function adaptarAlmacenMonitoreo(
  dto: AlmacenMonitoreoDto,
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
      dto.stock ??
      undefined,
  };
}

export function adaptarAlmacenesMonitoreo(
  dtos: AlmacenMonitoreoDto[],
): AlmacenMapa[] {
  return dtos.map(
    adaptarAlmacenMonitoreo,
  );
}