import type {
  PlanResponseDto,
  RutaResponseDto,
} from "@/api/planificacion.dto";

import type {
  RutaMapa,
  TipoVehiculo,
} from "@/features/ejecucion/ejecucion.types";

function adaptarTipoVehiculo(
  tipo: RutaResponseDto["tipoVehiculo"],
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

export function adaptarRuta(
  dto: RutaResponseDto,
): RutaMapa {
  return {
    id: dto.id,
    vehiculoId: dto.vehiculoId,
    tipoVehiculo:
      adaptarTipoVehiculo(
        dto.tipoVehiculo,
      ),
    salida: dto.salida,
    llegadaFinal: dto.llegadaFinal,
    distanciaKm: dto.distanciaKm,
    costo: dto.costo,
    paradas: dto.paradas.map((parada) => ({
      ...parada,
      inicioRefrigerio: parada.inicioRefrigerio ?? null,
    })),
    almacenInicialId: dto.almacenInicialId ?? null,
    planPersistedId: dto.planPersistedId ?? null,
    routePersistedId: dto.routePersistedId ?? null,
  };
}

export function adaptarRutas(
  plan: PlanResponseDto | null,
): RutaMapa[] {
  return plan?.rutas.map(adaptarRuta) ?? [];
}
