/*
 * Cálculos puros de la cámara del mapa de operación.
 *
 * Se usan para centrar el mapa sobre un vehículo seleccionado
 * (clic en el mapa o en la lista) con una animación corta.
 * No dependen de React ni de Konva para poder probarse con vitest.
 */

export interface Vista {
  escala: number;
  x: number;
  y: number;
}

export interface Punto {
  x: number;
  y: number;
}

/** Límites de zoom que ya usa el mapa (rueda y botones). */
export const ESCALA_MINIMA = 0.75;
export const ESCALA_MAXIMA = 3.5;

/** Zoom al que se acerca el mapa al seleccionar un vehículo. */
export const ESCALA_ENFOQUE = 2.5;

/** Duración de la animación de enfoque. */
export const DURACION_ENFOQUE_MS = 350;

/** Ancho mínimo libre para usar el centro del área visible. */
const ANCHO_LIBRE_MINIMO = 200;

export interface Margenes {
  izquierda: number;
  derecha: number;
}

export function limitarEscala(
  escala: number,
): number {
  if (!Number.isFinite(escala)) {
    return 1;
  }

  return Math.min(
    ESCALA_MAXIMA,
    Math.max(ESCALA_MINIMA, escala),
  );
}

/*
 * Ancho que ocupan los paneles flotantes sobre el mapa.
 * Debe coincidir con las clases de EjecucionPage y DetalleOperacionPanel:
 * columna izquierda left-5 + w-[min(430px,32vw)] min-w-[300px];
 * detalle right-5 + w-[min(370px,30vw)] min-w-[260px].
 */
export function margenesPaneles(
  anchoVentana: number,
  hayDetalleAbierto: boolean,
): Margenes {
  const separacion = 20 + 10;

  return {
    izquierda:
      separacion +
      Math.max(
        300,
        Math.min(430, anchoVentana * 0.32),
      ),

    derecha: hayDetalleAbierto
      ? separacion +
        Math.max(
          260,
          Math.min(370, anchoVentana * 0.3),
        )
      : 0,
  };
}

/*
 * Vista que deja `punto` (coordenadas del mapa antes del zoom,
 * las mismas que usa Konva dentro del Stage) en el centro del
 * área no tapada por los paneles.
 *
 * Nunca aleja: si el usuario ya está más cerca que ESCALA_ENFOQUE,
 * se conserva su zoom.
 */
export function calcularVistaCentrada(
  punto: Punto,
  ancho: number,
  alto: number,
  margenes: Margenes,
  escalaActual: number,
  escalaObjetivo: number = ESCALA_ENFOQUE,
): Vista {
  const escala =
    limitarEscala(
      Math.max(
        escalaActual,
        escalaObjetivo,
      ),
    );

  const libre =
    ancho -
    margenes.izquierda -
    margenes.derecha;

  const centroX =
    libre >= ANCHO_LIBRE_MINIMO
      ? margenes.izquierda + libre / 2
      : ancho / 2;

  const centroY = alto / 2;

  return {
    escala,
    x: centroX - punto.x * escala,
    y: centroY - punto.y * escala,
  };
}

export interface Rectangulo {
  x: number;
  y: number;
  ancho: number;
  alto: number;
}

/** Alto reservado arriba para la barra de filtros y acciones. */
export const MARGEN_SUPERIOR_AJUSTE = 64;

/** Alto reservado abajo (leyenda minimizada). */
export const MARGEN_INFERIOR_AJUSTE = 16;

/*
 * Vista que encuadra `rect` (el mapa en coordenadas del Stage a escala 1)
 * dentro del área libre entre paneles, centrado. Usada por "Ajustar mapa".
 * Respeta los mismos límites de zoom que la rueda y los botones.
 */
export function calcularVistaAjustada(
  rect: Rectangulo,
  ancho: number,
  alto: number,
  margenes: Margenes,
  margenSuperior: number = MARGEN_SUPERIOR_AJUSTE,
  margenInferior: number = MARGEN_INFERIOR_AJUSTE,
): Vista {
  const libreConPaneles =
    ancho -
    margenes.izquierda -
    margenes.derecha;

  const usarMargenes =
    libreConPaneles >= ANCHO_LIBRE_MINIMO;

  const izquierda =
    usarMargenes ? margenes.izquierda : 0;

  const libreAncho =
    usarMargenes ? libreConPaneles : ancho;

  const libreAlto =
    Math.max(
      1,
      alto - margenSuperior - margenInferior,
    );

  const escala =
    limitarEscala(
      Math.min(
        libreAncho / Math.max(1, rect.ancho),
        libreAlto / Math.max(1, rect.alto),
      ),
    );

  const centroX = izquierda + libreAncho / 2;
  const centroY = margenSuperior + libreAlto / 2;

  return {
    escala,
    x: centroX - (rect.x + rect.ancho / 2) * escala,
    y: centroY - (rect.y + rect.alto / 2) * escala,
  };
}

/** Pasa de coordenadas de pantalla del Stage a coordenadas del mapa. */
export function pantallaAMapa(
  pantalla: Punto,
  vista: Vista,
): Punto {
  return {
    x: (pantalla.x - vista.x) / vista.escala,
    y: (pantalla.y - vista.y) / vista.escala,
  };
}

/** Curva ease-out cúbica: arranca rápido y frena al llegar. */
export function suavizar(
  t: number,
): number {
  const acotado =
    Math.min(1, Math.max(0, t));

  return 1 - Math.pow(1 - acotado, 3);
}

/*
 * Vista intermedia de la animación (t entre 0 y 1).
 * Interpola escala y desplazamiento con la curva suavizada.
 */
export function interpolarVista(
  desde: Vista,
  hasta: Vista,
  t: number,
): Vista {
  const k = suavizar(t);

  return {
    escala:
      desde.escala +
      (hasta.escala - desde.escala) * k,
    x: desde.x + (hasta.x - desde.x) * k,
    y: desde.y + (hasta.y - desde.y) * k,
  };
}
