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

  constructor(status: number, message: string, detalles: ApiErrorDetalle[] = []) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.detalles = detalles;
  }
}

async function construirApiError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as ApiErrorResponse;

    return new ApiError(
      response.status,
      body.mensaje ?? body.error ?? `Error HTTP ${response.status}`,
      body.detalles ?? [],
    );
  } catch {
    return new ApiError(response.status, `Error HTTP ${response.status}`);
  }
}

function prepararHeaders(options: RequestInit) {
  const headers = new Headers(options.headers);

  if (
    options.body !== undefined &&
    !(options.body instanceof FormData) &&
    !headers.has("Content-Type")
  ) {
    headers.set("Content-Type", "application/json");
  }

  return headers;
}

export async function apiResponse(
  url: string,
  options: RequestInit = {},
): Promise<Response> {
  const response = await fetch(url, {
    ...options,
    headers: prepararHeaders(options),
  });

  if (!response.ok) {
    throw await construirApiError(response);
  }

  return response;
}

export async function apiRequest<T>(
  url: string,
  options: RequestInit = {},
): Promise<T> {
  const response = await apiResponse(url, options);

  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get("content-type");

  if (!contentType?.includes("application/json")) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export async function apiBlobRequest(
  url: string,
  options: RequestInit = {},
): Promise<{ blob: Blob; nombreArchivo: string | null }> {
  const response = await apiResponse(url, options);
  const disposition = response.headers.get("content-disposition");
  const match = disposition?.match(
  /filename\*?=(?:UTF-8''|")?([^";]+)/i,
);

  return {
    blob: await response.blob(),
    nombreArchivo: match ? decodeURIComponent(match[1].replace(/"/g, "")) : null,
  };
}
