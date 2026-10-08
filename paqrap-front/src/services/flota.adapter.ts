import type {
  TipoVehiculoBackend,
  VehiculoResponseDto,
  EstadoVehiculoBackend,
} from "@/api/flota.dto";

import type {
  EstadoVehiculo,
  TipoVehiculo,
  VehiculoMapa,
} from "@/features/ejecucion/ejecucion.types";

function adaptarTipoVehiculo(
  tipo: TipoVehiculoBackend,
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
  estado: EstadoVehiculoBackend,
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

export function adaptarVehiculo(
  dto: VehiculoResponseDto,
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

export function adaptarVehiculos(
  dtos: VehiculoResponseDto[],
): VehiculoMapa[] {
  return dtos.map(
    adaptarVehiculo,
  );
}