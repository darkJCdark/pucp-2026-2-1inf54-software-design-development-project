export interface ApiErrorDetalle {
  campo?: string;
  mensaje?: string;
}

export interface ApiErrorResponse {
  timestamp?: string;
  status?: number;
  error?: string;
  mensaje?: string;
  ruta?: string;
  detalles?: ApiErrorDetalle[];
}

export class ApiError extends Error {
  readonly status: number;
  readonly detalles: ApiErrorDetalle[];

  constructor(
    status: number,
    message: string,
    detalles: ApiErrorDetalle[] = [],
  ) {
    super(message);

    this.name =
      "ApiError";

    this.status =
      status;

    this.detalles =
      detalles;
  }
}

async function construirApiError(
  response: Response,
): Promise<ApiError> {
  const texto =
    await response
      .text()
      .catch(() => "");

  if (texto) {
    try {
      const body =
        JSON.parse(
          texto,
        ) as ApiErrorResponse;

      return new ApiError(
        response.status,

        body.mensaje ??
          body.error ??
          `Error HTTP ${response.status}`,

        body.detalles ??
          [],
      );
    } catch {
      return new ApiError(
        response.status,

        texto.length > 300
          ? `${texto.slice(0, 300)}...`
          : texto,
      );
    }
  }

  return new ApiError(
    response.status,
    `Error HTTP ${response.status}`,
  );
}

function prepararHeaders(
  options: RequestInit,
) {
  const headers =
    new Headers(
      options.headers,
    );

  if (
    options.body !==
      undefined &&
    !(
      options.body instanceof
      FormData
    ) &&
    !headers.has(
      "Content-Type",
    )
  ) {
    headers.set(
      "Content-Type",
      "application/json",
    );
  }

  /*
   * El frontend espera JSON de los
   * endpoints REST.
   */
  if (
    !headers.has(
      "Accept",
    )
  ) {
    headers.set(
      "Accept",
      "application/json",
    );
  }

  return headers;
}

export async function apiResponse(
  url: string,
  options: RequestInit = {},
): Promise<Response> {

  const response =
    await fetch(
      url,
      {
        ...options,

        headers:
          prepararHeaders(
            options,
          ),

        /*
         * Evita reutilizar respuestas
         * antiguas mientras cambia el
         * backend durante desarrollo.
         */
        cache:
          options.cache ??
          "no-store",
      },
    );

  if (
    !response.ok
  ) {
    throw await construirApiError(
      response,
    );
  }

  return response;
}

export async function apiRequest<T>(
  url: string,
  options: RequestInit = {},
): Promise<T> {

  const response =
    await apiResponse(
      url,
      options,
    );

  /*
   * DELETE u otras operaciones pueden
   * responder correctamente sin cuerpo.
   */
  if (
    response.status ===
    204
  ) {
    return undefined as T;
  }

  /*
   * No confiamos exclusivamente en
   * Content-Type.
   *
   * Leemos el cuerpo y tratamos de
   * interpretarlo como JSON.
   */
  const texto =
    await response.text();

  if (
    !texto.trim()
  ) {
    throw new Error(
      `La respuesta de ${url} llegó vacía (HTTP ${response.status}).`,
    );
  }

  try {
    return JSON.parse(
      texto,
    ) as T;
  } catch {
    const contentType =
      response.headers.get(
        "content-type",
      );

    const muestra =
      texto.length >
      300
        ? `${texto.slice(
            0,
            300,
          )}...`
        : texto;

    throw new Error(
      [
        `La respuesta de ${url} no contiene JSON válido.`,
        `HTTP: ${response.status}`,
        `Content-Type: ${contentType ?? "sin header"}`,
        `Respuesta: ${muestra}`,
      ].join("\n"),
    );
  }
}

export async function apiBlobRequest(
  url: string,
  options: RequestInit = {},
): Promise<{
  blob: Blob;
  nombreArchivo:
    string | null;
}> {

  const response =
    await apiResponse(
      url,
      options,
    );

  const disposition =
    response.headers.get(
      "content-disposition",
    );

  const match =
    disposition?.match(
      /filename\*?=(?:UTF-8''|")?([^";]+)/i,
    );

  return {
    blob:
      await response.blob(),

    nombreArchivo:
      match
        ? decodeURIComponent(
            match[1].replace(
              /"/g,
              "",
            ),
          )
        : null,
  };
}