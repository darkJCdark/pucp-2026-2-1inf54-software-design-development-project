export function duracionMenorValida(minutos: number) {
  return Number.isInteger(minutos) && minutos > 0 && minutos <= 2_147_483_647;
}
