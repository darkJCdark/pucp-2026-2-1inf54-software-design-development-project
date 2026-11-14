import { expect, it } from "vitest";
import { duracionMenorValida } from "./duracionAveria";
it.each([120, 60, 1])("acepta %i minutos", (n) => expect(duracionMenorValida(n)).toBe(true));
it.each([0, -1, 1.5, NaN, Infinity, 2147483648])("rechaza %s", (n) => expect(duracionMenorValida(n)).toBe(false));
