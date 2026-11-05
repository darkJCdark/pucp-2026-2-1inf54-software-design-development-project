import type { ParadaResponseDto, RutaResponseDto } from "@/api/planificacion.dto";

/**
 * Distancias y tiempos de una unidad sobre su ruta vigente, en un instante.
 * Origen = salida de la ruta; destino = siguiente parada por atender.
 */
export interface DistanciasVehiculo {
  kmAlDestino: number;
  minutosAlDestino: number;
  kmDesdeOrigen: number;
  minutosDesdeOrigen: number;
  kmRestantes: number;
  /** LLEGANDO: viaja hacia la parada. SALIENDO: está en ella (servicio o refrigerio) y aún no sale. */
  fase: "LLEGANDO" | "SALIENDO";
  /** Hora de llegada a la parada o, si ya está en ella, de salida. */
  horaFase: string | null;
}

function ms(valor: string | null | undefined) {
  return valor ? new Date(valor).getTime() : Number.NaN;
}

function arriboMs(parada: ParadaResponseDto) {
  return ms(parada.inicioRefrigerio ?? parada.llegada);
}

/** Calles del tramo hacia la parada: el camino real o, sin él, la distancia en la cuadrícula. */
function kmTramo(parada: ParadaResponseDto, anterior: { x: number; y: number } | null) {
  if (parada.camino && parada.camino.length > 0) {
    return parada.camino.length - 1;
  }
  return anterior ? Math.abs(parada.x - anterior.x) + Math.abs(parada.y - anterior.y) : 0;
}

/** Null si la ruta no tiene horarios, aún no sale o ya terminó: no hay distancias que informar. */
export function calcularDistancias(
  ruta: RutaResponseDto | undefined,
  instanteMs: number,
): DistanciasVehiculo | null {
  const salida = ms(ruta?.salida);
  if (!ruta || !Number.isFinite(salida) || instanteMs < salida) {
    return null;
  }

  const paradas = [...ruta.paradas].sort((a, b) => a.orden - b.orden);
  const siguiente = paradas.findIndex((parada) => {
    const fin = ms(parada.fin ?? parada.llegada);
    return Number.isFinite(fin) && fin > instanteMs;
  });
  if (siguiente < 0) {
    return null;
  }

  const tramos = paradas.map((parada, indice) =>
    kmTramo(parada, indice > 0 ? paradas[indice - 1] : null),
  );
  const parada = paradas[siguiente];
  const anterior = siguiente > 0 ? paradas[siguiente - 1] : null;
  const inicioTramo = anterior ? ms(anterior.fin ?? anterior.llegada) : salida;
  const finTramo = arriboMs(parada);
  const progreso =
    instanteMs >= finTramo || finTramo <= inicioTramo
      ? 1
      : Math.max(0, (instanteMs - inicioTramo) / (finTramo - inicioTramo));

  const kmAnteriores = tramos.slice(0, siguiente).reduce((total, km) => total + km, 0);
  const kmAlDestino = tramos[siguiente] * (1 - progreso);
  const kmPosteriores = tramos.slice(siguiente + 1).reduce((total, km) => total + km, 0);

  return {
    kmAlDestino,
    minutosAlDestino: Math.max(0, finTramo - instanteMs) / 60_000,
    kmDesdeOrigen: kmAnteriores + tramos[siguiente] * progreso,
    minutosDesdeOrigen: (instanteMs - salida) / 60_000,
    kmRestantes: kmAlDestino + kmPosteriores,
    fase: instanteMs >= finTramo ? "SALIENDO" : "LLEGANDO",
    horaFase: instanteMs >= finTramo ? parada.fin : parada.inicioRefrigerio ?? parada.llegada,
  };
}
