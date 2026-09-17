import type {
  PedidoAlgoritmo,
  VehiculoAlgoritmo,
} from "./algoritmo.types";

export const PEDIDOS_PRUEBA: PedidoAlgoritmo[] = [
  {
    id: "PED-001",
    x: 29,
    y: 18,
    cantidad: 4,
    plazoHoras: 4,
  },
  {
    id: "PED-002",
    x: 32,
    y: 20,
    cantidad: 6,
    plazoHoras: 8,
  },
  {
    id: "PED-003",
    x: 20,
    y: 22,
    cantidad: 3,
    plazoHoras: 12,
  },
  {
    id: "PED-004",
    x: 15,
    y: 30,
    cantidad: 4,
    plazoHoras: 18,
  },
  {
    id: "PED-005",
    x: 40,
    y: 15,
    cantidad: 5,
    plazoHoras: 36,
  },
];

export const VEHICULOS_PRUEBA: VehiculoAlgoritmo[] = [
  {
    id: "AUT-001",
    tipo: "AUTO",
    capacidad: 24,
    velocidad: 40,
    costoKm: 8,
  },
  {
    id: "MOT-001",
    tipo: "MOTO",
    capacidad: 8,
    velocidad: 25,
    costoKm: 6,
  },
  {
    id: "BIC-001",
    tipo: "BICICLETA",
    capacidad: 4,
    velocidad: 12,
    costoKm: 3,
  },
];