import { describe, expect, it } from "vitest";

import {
  ESCALA_ENFOQUE,
  ESCALA_MAXIMA,
  ESCALA_MINIMA,
  calcularVistaAjustada,
  calcularVistaCentrada,
  interpolarVista,
  limitarEscala,
  margenesPaneles,
  pantallaAMapa,
  suavizar,
} from "./vistaMapa";

const SIN_MARGENES = { izquierda: 0, derecha: 0 };

describe("limitarEscala", () => {
  it("respeta los límites del mapa", () => {
    expect(limitarEscala(10)).toBe(ESCALA_MAXIMA);
    expect(limitarEscala(0.1)).toBe(ESCALA_MINIMA);
    expect(limitarEscala(2)).toBe(2);
  });

  it("devuelve 1 ante valores no finitos", () => {
    expect(limitarEscala(Number.NaN)).toBe(1);
    expect(limitarEscala(Number.POSITIVE_INFINITY)).toBe(1);
  });
});

describe("margenesPaneles", () => {
  it("usa 430 px a la izquierda en pantallas anchas", () => {
    const m = margenesPaneles(1920, false);
    expect(m.izquierda).toBe(30 + 430);
    expect(m.derecha).toBe(0);
  });

  it("usa 32vw a la izquierda y 30vw a la derecha en pantallas angostas", () => {
    const m = margenesPaneles(1000, true);
    expect(m.izquierda).toBeCloseTo(30 + 320);
    expect(m.derecha).toBeCloseTo(30 + 300);
  });

  it("respeta los anchos mínimos en pantallas pequeñas", () => {
    const m = margenesPaneles(800, true);
    expect(m.izquierda).toBe(30 + 300);
    expect(m.derecha).toBe(30 + 260);
  });

  it("descuenta el detalle de 370 px solo si está abierto", () => {
    expect(margenesPaneles(1920, true).derecha).toBe(30 + 370);
  });
});

describe("calcularVistaCentrada", () => {
  it("deja el punto en el centro del Stage sin paneles", () => {
    const punto = { x: 100, y: 50 };
    const vista = calcularVistaCentrada(punto, 800, 600, SIN_MARGENES, 1);

    expect(vista.escala).toBe(ESCALA_ENFOQUE);
    expect(punto.x * vista.escala + vista.x).toBeCloseTo(400);
    expect(punto.y * vista.escala + vista.y).toBeCloseTo(300);
  });

  it("centra en el área libre entre paneles", () => {
    const punto = { x: 300, y: 200 };
    const margenes = { izquierda: 460, derecha: 400 };
    const vista = calcularVistaCentrada(punto, 1560, 900, margenes, 1);

    // Área libre: 460..1160 -> centro 810
    expect(punto.x * vista.escala + vista.x).toBeCloseTo(810);
    expect(punto.y * vista.escala + vista.y).toBeCloseTo(450);
  });

  it("usa el centro total si los paneles dejan muy poco espacio", () => {
    const punto = { x: 10, y: 10 };
    const margenes = { izquierda: 300, derecha: 300 };
    const vista = calcularVistaCentrada(punto, 700, 400, margenes, 1);

    expect(punto.x * vista.escala + vista.x).toBeCloseTo(350);
  });

  it("no aleja si el usuario ya tiene más zoom", () => {
    const vista = calcularVistaCentrada({ x: 0, y: 0 }, 800, 600, SIN_MARGENES, 3);
    expect(vista.escala).toBe(3);
  });

  it("no supera el zoom máximo", () => {
    const vista = calcularVistaCentrada({ x: 0, y: 0 }, 800, 600, SIN_MARGENES, 1, 9);
    expect(vista.escala).toBe(ESCALA_MAXIMA);
  });
});

describe("pantallaAMapa", () => {
  it("es la inversa de la transformación de la vista", () => {
    const vista = { escala: 2, x: -150, y: 40 };
    const mapa = { x: 123, y: 45 };
    const pantalla = {
      x: mapa.x * vista.escala + vista.x,
      y: mapa.y * vista.escala + vista.y,
    };

    const resultado = pantallaAMapa(pantalla, vista);
    expect(resultado.x).toBeCloseTo(mapa.x);
    expect(resultado.y).toBeCloseTo(mapa.y);
  });
});

describe("suavizar e interpolarVista", () => {
  it("suavizar empieza en 0, termina en 1 y acota fuera de rango", () => {
    expect(suavizar(0)).toBe(0);
    expect(suavizar(1)).toBe(1);
    expect(suavizar(-1)).toBe(0);
    expect(suavizar(2)).toBe(1);
    expect(suavizar(0.5)).toBeGreaterThan(0.5);
  });

  it("interpolarVista devuelve los extremos en t=0 y t=1", () => {
    const desde = { escala: 1, x: 0, y: 0 };
    const hasta = { escala: 2.5, x: -300, y: -200 };

    expect(interpolarVista(desde, hasta, 0)).toEqual(desde);
    expect(interpolarVista(desde, hasta, 1)).toEqual(hasta);
  });

  it("interpolarVista avanza de forma monótona", () => {
    const desde = { escala: 1, x: 0, y: 0 };
    const hasta = { escala: 2.5, x: -300, y: -200 };
    const a = interpolarVista(desde, hasta, 0.25);
    const b = interpolarVista(desde, hasta, 0.75);

    expect(a.escala).toBeLessThan(b.escala);
    expect(a.x).toBeGreaterThan(b.x);
  });
});

describe("calcularVistaAjustada", () => {
  const rect = { x: 100, y: 50, ancho: 700, alto: 500 };

  it("centra el mapa en el área libre entre paneles y bajo la barra superior", () => {
    const margenes = { izquierda: 460, derecha: 0 };
    const vista = calcularVistaAjustada(rect, 1460, 900, margenes, 64, 16);

    const centroRectX = (rect.x + rect.ancho / 2) * vista.escala + vista.x;
    const centroRectY = (rect.y + rect.alto / 2) * vista.escala + vista.y;

    // Área libre: x 460..1460 (centro 960); y 64..884 (centro 474)
    expect(centroRectX).toBeCloseTo(960);
    expect(centroRectY).toBeCloseTo(474);
  });

  it("elige la escala que hace caber el mapa por el lado más restrictivo", () => {
    const vista = calcularVistaAjustada(rect, 1000, 600, { izquierda: 0, derecha: 0 }, 0, 0);
    // min(1000/700, 600/500) = 1.2
    expect(vista.escala).toBeCloseTo(1.2);
  });

  it("no baja del zoom mínimo aunque el mapa no quepa", () => {
    const vista = calcularVistaAjustada(rect, 300, 200, { izquierda: 0, derecha: 0 }, 0, 0);
    expect(vista.escala).toBe(ESCALA_MINIMA);
  });

  it("ignora los paneles si dejan muy poco espacio", () => {
    const margenes = { izquierda: 400, derecha: 400 };
    const vista = calcularVistaAjustada(rect, 900, 600, margenes, 0, 0);
    const centroRectX = (rect.x + rect.ancho / 2) * vista.escala + vista.x;
    expect(centroRectX).toBeCloseTo(450);
  });
});
