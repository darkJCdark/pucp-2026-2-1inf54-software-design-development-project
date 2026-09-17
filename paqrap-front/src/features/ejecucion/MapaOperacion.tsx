import {
  Bike,
  Car,
  House,
  MapPin,
  Navigation,
} from "lucide-react";

import {
  MOCK_ALMACENES,
  MOCK_PEDIDOS,
  MOCK_VEHICULOS,
} from "./ejecucion.mock";

const ANCHO_MAPA = 70;
const ALTO_MAPA = 50;

function posicionX(x: number) {
  return `${(x / ANCHO_MAPA) * 100}%`;
}

function posicionY(y: number) {
  return `${100 - (y / ALTO_MAPA) * 100}%`;
}

export function MapaOperacion() {
  return (
    <section className="relative h-full min-h-[720px] overflow-hidden bg-white">
      {/* Cuadrícula */}
      <div
        className="absolute inset-0"
        style={{
          backgroundImage: `
            linear-gradient(to right, #cbd5e1 1px, transparent 1px),
            linear-gradient(to bottom, #cbd5e1 1px, transparent 1px)
          `,
          backgroundSize: "24px 24px",
        }}
      />

      {/* Filtros */}
      <div className="absolute left-3 top-3 z-20 flex gap-2">
        {[
          "AUTOS",
          "MOTOS",
          "BICICLETAS",
          "PEDIDOS",
          "BLOQUEADOS",
        ].map((filtro) => (
          <button
            key={filtro}
            className="rounded-full bg-orange-500 px-3 py-1 text-[11px] font-semibold text-white"
          >
            {filtro}
          </button>
        ))}
      </div>

      {/* Pedidos */}
      {MOCK_PEDIDOS.map((pedido) => (
        <div
          key={pedido.id}
          title={pedido.id}
          className="absolute z-10 size-3 -translate-x-1/2 -translate-y-1/2 rounded-full bg-green-500"
          style={{
            left: posicionX(pedido.x),
            top: posicionY(pedido.y),
          }}
        />
      ))}

      {/* Almacenes */}
      {MOCK_ALMACENES.map((almacen) => (
        <div
          key={almacen.id}
          className="absolute z-20 -translate-x-1/2 -translate-y-1/2 text-center"
          style={{
            left: posicionX(almacen.x),
            top: posicionY(almacen.y),
          }}
        >
          <div
            className={
              almacen.tipo === "CENTRAL"
                ? "mx-auto flex size-10 items-center justify-center rounded-md bg-blue-900 text-white"
                : "mx-auto flex size-10 items-center justify-center rounded-md border-2 border-green-500 bg-white text-xs font-bold text-green-600"
            }
          >
            {almacen.tipo === "CENTRAL" ? (
              <House className="size-6" />
            ) : (
              "ALM"
            )}
          </div>

          <span className="mt-1 block text-sm font-medium text-slate-900">
            {almacen.nombre}
          </span>
        </div>
      ))}

      {/* Vehículos */}
      {MOCK_VEHICULOS.map((vehiculo) => (
        <VehiculoMarker
          key={vehiculo.id}
          tipo={vehiculo.tipo}
          averiado={vehiculo.estado === "AVERIADO"}
          x={vehiculo.x}
          y={vehiculo.y}
        />
      ))}

      {/* Leyenda */}
      <div className="absolute bottom-5 right-5 z-30 rounded-md bg-[#11161d] p-4 text-xs text-slate-300 shadow-xl">
        <p className="mb-3 font-semibold text-white">
          LEYENDA
        </p>

        <Leyenda color="bg-blue-800" texto="Almacén central" />
        <Leyenda color="bg-green-500" texto="Almacén intermedio" />
        <Leyenda color="bg-blue-500" texto="Automóvil" />
        <Leyenda color="bg-green-500" texto="Motocicleta" />
        <Leyenda color="bg-amber-500" texto="Bicicleta" />
        <Leyenda color="bg-red-500" texto="Unidad averiada" />
      </div>
    </section>
  );
}

interface VehiculoMarkerProps {
  tipo: "AUTO" | "MOTO" | "BICICLETA";
  averiado: boolean;
  x: number;
  y: number;
}

function VehiculoMarker({
  tipo,
  averiado,
  x,
  y,
}: VehiculoMarkerProps) {
  const color = averiado
    ? "border-red-500 text-red-500"
    : tipo === "AUTO"
      ? "border-blue-500 text-blue-500"
      : tipo === "MOTO"
        ? "border-green-500 text-green-500"
        : "border-amber-500 text-amber-500";

  const Icono =
    tipo === "AUTO"
      ? Car
      : tipo === "MOTO"
        ? Navigation
        : Bike;

  return (
    <div
      className={`absolute z-20 flex size-8 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full border-2 bg-white ${color}`}
      style={{
        left: posicionX(x),
        top: posicionY(y),
      }}
    >
      <Icono className="size-5" />
    </div>
  );
}

interface LeyendaProps {
  color: string;
  texto: string;
}

function Leyenda({
  color,
  texto,
}: LeyendaProps) {
  return (
    <div className="mb-1.5 flex items-center gap-2">
      <span className={`size-2 rounded-full ${color}`} />
      <span>{texto}</span>
    </div>
  );
}