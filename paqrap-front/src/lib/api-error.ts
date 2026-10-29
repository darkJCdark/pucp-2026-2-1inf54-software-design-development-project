import { ApiError } from "@/services/api";

export function mensajeDeError(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    const detalles = error.detalles
      .filter((detalle) => detalle.mensaje)
      .map((detalle) =>
        detalle.campo
          ? `${detalle.campo}: ${detalle.mensaje}`
          : String(detalle.mensaje),
      );

    return detalles.length > 0
      ? `${error.message}\n${detalles.join("\n")}`
      : error.message;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return fallback;
}
