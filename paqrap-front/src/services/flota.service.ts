import type {
  ActualizarTipoVehiculoRequestDto,
  TipoVehiculoBackend,
  TipoVehiculoResponseDto,
  VehiculoResponseDto,
} from "@/api/flota.dto";

import {
  apiRequest,
} from "@/services/api";

/**
 * Algunos contratos antiguos devolvían:
 *
 * {
 *   contenido: [...]
 * }
 *
 * mientras que el backend actual devuelve
 * directamente:
 *
 * [...]
 *
 * Estas funciones admiten ambos formatos para
 * evitar incompatibilidades después del merge.
 */
function normalizarLista<T>(
  respuesta: unknown,
  nombre: string,
): T[] {
  if (
    Array.isArray(
      respuesta,
    )
  ) {
    return respuesta as T[];
  }

  if (
    respuesta &&
    typeof respuesta === "object" &&
    "contenido" in respuesta
  ) {
    const contenido =
      (
        respuesta as {
          contenido?: unknown;
        }
      ).contenido;

    if (
      Array.isArray(
        contenido,
      )
    ) {
      return contenido as T[];
    }
  }

  throw new Error(
    `La respuesta de ${nombre} no tiene el formato esperado.`,
  );
}

export const flotaService = {

  async listarVehiculos():
    Promise<VehiculoResponseDto[]> {

    const respuesta =
      await apiRequest<unknown>(
        "/api/flota/vehiculos",
      );

    return normalizarLista<VehiculoResponseDto>(
      respuesta,
      "/api/flota/vehiculos",
    );
  },

  obtenerVehiculo(
    id: string,
  ): Promise<VehiculoResponseDto> {

    return apiRequest<VehiculoResponseDto>(
      `/api/flota/vehiculos/${encodeURIComponent(
        id,
      )}`,
    );
  },

  /**
   * NOTA:
   *
   * El frontend conserva estos métodos porque
   * la configuración permite cambiar cantidades.
   *
   * El backend actual debe exponer POST/DELETE
   * para que estas operaciones funcionen.
   *
   * Con la flota inicial:
   *
   * 10 autos
   * 15 motos
   * 12 bicicletas
   *
   * estos métodos no se ejecutan.
   */
  altaVehiculo(
    tipo: TipoVehiculoBackend,
  ): Promise<VehiculoResponseDto> {

    return apiRequest<VehiculoResponseDto>(
      "/api/flota/vehiculos",
      {
        method: "POST",

        body:
          JSON.stringify({
            tipo,
          }),
      },
    );
  },

  /** Solo AVAILABLE o UNAVAILABLE: IN_ROUTE lo asigna la simulación. */
  cambiarEstadoVehiculo(
    id: string,
    estado: "AVAILABLE" | "UNAVAILABLE",
  ): Promise<VehiculoResponseDto> {

    return apiRequest<VehiculoResponseDto>(
      `/api/flota/vehiculos/${encodeURIComponent(
        id,
      )}/estado`,
      {
        method: "PATCH",

        body:
          JSON.stringify({
            estado,
          }),
      },
    );
  },

  /**
   * Si la unidad ya tiene historial (rutas, averías o mantenimientos), el backend no la borra: la deja UNAVAILABLE
   * (baja lógica).
   */
  bajaVehiculo(
    id: string,
  ): Promise<void> {

    return apiRequest<void>(
      `/api/flota/vehiculos/${encodeURIComponent(
        id,
      )}`,
      {
        method: "DELETE",
      },
    );
  },

  async listarTipos():
    Promise<TipoVehiculoResponseDto[]> {

    const respuesta =
      await apiRequest<unknown>(
        "/api/flota/tipos",
      );

    return normalizarLista<TipoVehiculoResponseDto>(
      respuesta,
      "/api/flota/tipos",
    );
  },

  obtenerTipo(
    tipo: string,
  ): Promise<TipoVehiculoResponseDto> {

    return apiRequest<TipoVehiculoResponseDto>(
      `/api/flota/tipos/${encodeURIComponent(
        tipo,
      )}`,
    );
  },

  actualizarTipo(
    tipo: TipoVehiculoBackend,

    request:
      ActualizarTipoVehiculoRequestDto,
  ): Promise<TipoVehiculoResponseDto> {

    return apiRequest<TipoVehiculoResponseDto>(
      `/api/flota/tipos/${tipo}`,
      {
        method: "PUT",

        body:
          JSON.stringify(
            request,
          ),
      },
    );
  },
};