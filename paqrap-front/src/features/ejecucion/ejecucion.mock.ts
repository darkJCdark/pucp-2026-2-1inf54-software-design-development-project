import type {
  AlmacenMapa,
  IndicadoresOperacion,
  PedidoOperacion,
  VehiculoMapa,
} from "./ejecucion.types";

export const MOCK_ALMACENES: AlmacenMapa[] = [
  {
    id: "ALM-000",
    nombre: "Central",
    tipo: "CENTRAL",
    x: 27,
    y: 14,
  },
  {
    id: "ALM-001",
    nombre: "Nor-Oeste",
    tipo: "INTERMEDIO",
    x: 12,
    y: 38,
    stock: 798,
  },
  {
    id: "ALM-002",
    nombre: "Este",
    tipo: "INTERMEDIO",
    x: 57,
    y: 27,
    stock: 627,
  },
];

export const MOCK_VEHICULOS: VehiculoMapa[] = [
  {
    id: "AUT-001",
    tipo: "AUTO",
    estado: "EN_RUTA",
    x: 29,
    y: 20,
  },
  {
    id: "AUT-002",
    tipo: "AUTO",
    estado: "DISPONIBLE",
    x: 27,
    y: 14,
  },
  {
    id: "MOT-001",
    tipo: "MOTO",
    estado: "EN_RUTA",
    x: 16,
    y: 21,
  },
  {
    id: "MOT-002",
    tipo: "MOTO",
    estado: "EN_RUTA",
    x: 33,
    y: 27,
  },
  {
    id: "BIC-001",
    tipo: "BICICLETA",
    estado: "EN_RUTA",
    x: 21,
    y: 34,
  },
  {
    id: "BIC-002",
    tipo: "BICICLETA",
    estado: "DISPONIBLE",
    x: 27,
    y: 14,
  },
];

export const MOCK_PEDIDOS: PedidoOperacion[] = [
  {
    id: "PED-001",
    x: 17,
    y: 30,
    cantidad: 3,
    plazoHoras: 8,
    estado: "PENDIENTE",
  },
  {
    id: "PED-002",
    x: 31,
    y: 35,
    cantidad: 5,
    plazoHoras: 12,
    estado: "ASIGNADO",
  },
  {
    id: "PED-003",
    x: 40,
    y: 29,
    cantidad: 2,
    plazoHoras: 18,
    estado: "EN_TRANSITO",
  },
  {
    id: "PED-004",
    x: 53,
    y: 34,
    cantidad: 4,
    plazoHoras: 36,
    estado: "PENDIENTE",
  },
];

export const MOCK_INDICADORES: IndicadoresOperacion =
  {
    costoAcumulado: 1417,
    distanciaRecorrida: 139,
    entregasCompletadas: 138,
    pedidosRiesgo: 1,
  };