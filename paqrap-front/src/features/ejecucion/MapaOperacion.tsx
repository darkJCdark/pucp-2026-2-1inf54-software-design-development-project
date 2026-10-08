import {
  Bike,
  Car,
  House,
  Navigation,
} from "lucide-react";

import type {
  AlmacenMapa,
  PedidoOperacion,
  VehiculoMapa,
} from "./ejecucion.types";

const ANCHO_MAPA = 70;
const ALTO_MAPA = 50;

function posicionX(
  x: number,
) {
  return `${(x / ANCHO_MAPA) * 100}%`;
}

function posicionY(
  y: number,
) {
  return `${
    100 -
    (y / ALTO_MAPA) * 100
  }%`;
}

interface MapaOperacionProps {
  pedidos: PedidoOperacion[];
  almacenes: AlmacenMapa[];
  vehiculos: VehiculoMapa[];
}

export function MapaOperacion({
  pedidos,
  almacenes,
  vehiculos,
}: MapaOperacionProps) {
  return (
    <section className="relative h-full min-h-[720px] overflow-hidden bg-white">
      <div
        className="absolute inset-0"
        style={{
          backgroundImage: `
            linear-gradient(to right, #cbd5e1 1px, transparent 1px),
            linear-gradient(to bottom, #cbd5e1 1px, transparent 1px)
          `,
          backgroundSize:
            "24px 24px",
        }}
      />

      <div className="absolute left-3 top-3 z-20 flex gap-2">
        {[
          "AUTOS",
          "MOTOS",
          "BICICLETAS",
          "PEDIDOS",
        ].map((filtro) => (
          <button
            type="button"
            key={filtro}
            className="rounded-full bg-orange-500 px-3 py-1 text-[11px] font-semibold text-white"
          >
            {filtro}
          </button>
        ))}
      </div>

      {pedidos.map(
        (pedido) => (
          <PedidoMarker
            key={pedido.id}
            pedido={pedido}
          />
        ),
      )}

      {almacenes.map(
        (almacen) => (
          <AlmacenMarker
            key={almacen.id}
            almacen={almacen}
          />
        ),
      )}

      {vehiculos.map(
        (vehiculo) => (
          <VehiculoMarker
            key={vehiculo.id}
            vehiculo={vehiculo}
          />
        ),
      )}

      <div className="absolute bottom-5 right-5 z-30 rounded-md bg-[#11161d] p-4 text-xs text-slate-300 shadow-xl">
        <p className="mb-3 font-semibold text-white">
          LEYENDA
        </p>

        <Leyenda
          color="bg-blue-800"
          texto="Almacén central"
        />

        <Leyenda
          color="bg-green-500"
          texto="Almacén intermedio"
        />

        <Leyenda
          color="bg-blue-500"
          texto="Automóvil"
        />

        <Leyenda
          color="bg-green-500"
          texto="Motocicleta"
        />

        <Leyenda
          color="bg-amber-500"
          texto="Bicicleta"
        />

        <Leyenda
          color="bg-violet-500"
          texto="Pedido"
        />
      </div>
    </section>
  );
}

function PedidoMarker({
  pedido,
}: {
  pedido: PedidoOperacion;
}) {
  const color =
    pedido.estado === "ENTREGADO"
      ? "bg-slate-400"
      : pedido.estado ===
          "EN_TRANSITO"
        ? "bg-sky-500"
        : pedido.estado ===
            "ASIGNADO"
          ? "bg-amber-500"
          : "bg-violet-500";

  return (
    <div
      className={`group absolute z-10 size-3 -translate-x-1/2 -translate-y-1/2 cursor-pointer rounded-full ${color}`}
      style={{
        left: posicionX(
          pedido.x,
        ),
        top: posicionY(
          pedido.y,
        ),
      }}
    >
      <TooltipMapa
        titulo={pedido.id}
        lineas={[
          `Coordenadas: (${pedido.x}, ${pedido.y})`,
          `Cantidad: ${pedido.cantidad}`,
          `Plazo: ${pedido.plazoHoras} h`,
          `Estado: ${pedido.estado}`,
        ]}
      />
    </div>
  );
}

function AlmacenMarker({
  almacen,
}: {
  almacen: AlmacenMapa;
}) {
  return (
    <div
      className="group absolute z-30 -translate-x-1/2 -translate-y-1/2 cursor-pointer text-center"
      style={{
        left: posicionX(
          almacen.x,
        ),
        top: posicionY(
          almacen.y,
        ),
      }}
    >
      <TooltipMapa
        titulo={`Almacén ${almacen.nombre}`}
        lineas={[
          `ID: ${almacen.id}`,
          `Coordenadas: (${almacen.x}, ${almacen.y})`,
          almacen.tipo ===
          "CENTRAL"
            ? "Inventario: central"
            : `Stock: ${
                almacen.stock ??
                0
              } / 1000`,
        ]}
      />

      <div
        className={
          almacen.tipo ===
          "CENTRAL"
            ? "mx-auto flex size-11 items-center justify-center rounded-md border-2 border-blue-700 bg-blue-900 text-white shadow-md"
            : "mx-auto flex size-11 items-center justify-center rounded-md border-2 border-green-500 bg-white text-xs font-bold text-green-600 shadow-md"
        }
      >
        {almacen.tipo ===
        "CENTRAL" ? (
          <House className="size-7" />
        ) : (
          "ALM"
        )}
      </div>

      <span className="mt-1 block whitespace-nowrap text-sm font-semibold text-slate-900">
        {almacen.nombre}
      </span>
    </div>
  );
}

function VehiculoMarker({
  vehiculo,
}: {
  vehiculo: VehiculoMapa;
}) {
  const color =
    vehiculo.estado ===
    "NO_DISPONIBLE"
      ? "border-slate-500 text-slate-500"
      : vehiculo.tipo ===
          "AUTO"
        ? "border-blue-500 text-blue-500"
        : vehiculo.tipo ===
            "MOTO"
          ? "border-green-500 text-green-500"
          : "border-amber-500 text-amber-500";

  const Icono =
    vehiculo.tipo === "AUTO"
      ? Car
      : vehiculo.tipo ===
          "MOTO"
        ? Navigation
        : Bike;

  return (
    <div
      className="group absolute z-20 flex size-8 -translate-x-1/2 -translate-y-1/2 cursor-pointer items-center justify-center"
      style={{
        left: posicionX(
          vehiculo.x,
        ),
        top: posicionY(
          vehiculo.y,
        ),
      }}
    >
      <TooltipMapa
        titulo={vehiculo.id}
        lineas={[
          `Tipo: ${vehiculo.tipo}`,
          `Coordenadas: (${vehiculo.x}, ${vehiculo.y})`,
          `Estado: ${vehiculo.estado}`,
        ]}
      />

      <div
        className={`flex size-8 items-center justify-center rounded-full border-2 bg-white ${color}`}
      >
        <Icono className="size-5" />
      </div>
    </div>
  );
}

interface TooltipMapaProps {
  titulo: string;
  lineas: string[];
}

function TooltipMapa({
  titulo,
  lineas,
}: TooltipMapaProps) {
  return (
    <div className="pointer-events-none absolute bottom-full left-1/2 z-50 mb-2 hidden min-w-36 -translate-x-1/2 rounded-md border border-slate-700 bg-[#11161d] px-3 py-2 text-left shadow-lg group-hover:block">
      <p className="mb-1 text-xs font-semibold text-white">
        {titulo}
      </p>

      {lineas.map(
        (linea) => (
          <p
            key={linea}
            className="whitespace-nowrap text-[11px] text-slate-400"
          >
            {linea}
          </p>
        ),
      )}
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
      <span
        className={`size-2 rounded-full ${color}`}
      />

      <span>{texto}</span>
    </div>
  );
}