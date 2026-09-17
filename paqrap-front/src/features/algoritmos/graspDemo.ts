import {
  PEDIDOS_PRUEBA,
  VEHICULOS_PRUEBA,
} from "./algoritmo.mock";

import { ejecutarGrasp } from "./grasp";

export function probarGrasp() {
  console.log("Iniciando prueba GRASP...");

  console.log("Pedidos:", PEDIDOS_PRUEBA);
  console.log("Vehículos:", VEHICULOS_PRUEBA);

  const solucion = ejecutarGrasp(
    PEDIDOS_PRUEBA,
    VEHICULOS_PRUEBA,
    {
      alpha: 0.3,
      maxIteraciones: 500,
      semilla: 42,
    },
  );

  console.log("========== GRASP ==========");
  console.log("Factible:", solucion.factible);
  console.log("Costo:", solucion.costoTotal);
  console.log("Distancia:", solucion.distanciaTotal);
  console.log("Solución completa:", solucion);

  return solucion;
}