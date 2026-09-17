import {
    Bike,
    Car,
    House,
    MapPin,
    Navigation,
} from "lucide-react";

import {
    MOCK_ALMACENES,
    MOCK_VEHICULOS,
} from "./ejecucion.mock";

import type { PedidoAlgoritmo } from "@/features/algoritmos/algoritmo.types";

const ANCHO_MAPA = 70;
const ALTO_MAPA = 50;

function posicionX(x: number) {
    return `${(x / ANCHO_MAPA) * 100}%`;
}

function posicionY(y: number) {
    return `${100 - (y / ALTO_MAPA) * 100}%`;
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
        <div
            className="
                        pointer-events-none
                        absolute
                        bottom-full
                        left-1/2
                        z-50
                        mb-2
                        hidden
                        min-w-36
                        -translate-x-1/2
                        rounded-md
                        border
                        border-slate-700
                        bg-[#11161d]
                        px-3
                        py-2
                        text-left
                        shadow-lg
                        group-hover:block
                    "
        >
            <p className="mb-1 text-xs font-semibold text-white">
                {titulo}
            </p>

            {lineas.map((linea) => (
                <p
                    key={linea}
                    className="whitespace-nowrap text-[11px] text-slate-400"
                >
                    {linea}
                </p>
            ))}
        </div>
    );
}

interface MapaOperacionProps {
    pedidos: PedidoAlgoritmo[];
}

export function MapaOperacion({
    pedidos,
}: MapaOperacionProps) {
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
            {pedidos.map((pedido) => (
                <div
                    key={pedido.id}
                    className="
      group
      absolute
      z-10
      size-3
      -translate-x-1/2
      -translate-y-1/2
      cursor-pointer
      rounded-full
      bg-green-500
    "
                    style={{
                        left: posicionX(pedido.x),
                        top: posicionY(pedido.y),
                    }}
                >
                    <TooltipMapa
                        titulo={pedido.id}
                        lineas={[
                            `Coordenadas: (${pedido.x}, ${pedido.y})`,
                            `Cantidad: ${pedido.cantidad}`,
                            `Plazo: ${pedido.plazoHoras} h`,
                        ]}
                    />
                </div>
            ))}

            {/* Almacenes */}
            {MOCK_ALMACENES.map((almacen) => (
                <div
                    key={almacen.id}
                    className="
      group
      absolute
      z-30
      -translate-x-1/2
      -translate-y-1/2
      cursor-pointer
      text-center
    "
                    style={{
                        left: posicionX(almacen.x),
                        top: posicionY(almacen.y),
                    }}
                >
                    <TooltipMapa
                        titulo={`Almacén ${almacen.nombre}`}
                        lineas={[
                            `ID: ${almacen.id}`,
                            `Coordenadas: (${almacen.x}, ${almacen.y})`,
                            almacen.tipo === "CENTRAL"
                                ? "Inventario: permanente"
                                : `Stock: ${almacen.stock ?? 0} / 1000`,
                        ]}
                    />

                    <div
                        className={
                            almacen.tipo === "CENTRAL"
                                ? `
              mx-auto
              flex size-11
              items-center justify-center
              rounded-md
              border-2 border-blue-700
              bg-blue-900
              text-white
              shadow-md
            `
                                : `
              mx-auto
              flex size-11
              items-center justify-center
              rounded-md
              border-2 border-green-500
              bg-white
              text-xs font-bold
              text-green-600
              shadow-md
            `
                        }
                    >
                        {almacen.tipo === "CENTRAL" ? (
                            <House className="size-7" />
                        ) : (
                            "ALM"
                        )}
                    </div>

                    <span className="mt-1 block whitespace-nowrap text-sm font-semibold text-slate-900">
                        {almacen.nombre}
                    </span>
                </div>
            ))}

            {/* Vehículos */}
            {MOCK_VEHICULOS.map((vehiculo) => (
                <VehiculoMarker
                    key={vehiculo.id}
                    id={vehiculo.id}
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
    id: string;
    tipo: "AUTO" | "MOTO" | "BICICLETA";
    averiado: boolean;
    x: number;
    y: number;
}

function VehiculoMarker({
    id,
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
            className="
        group
        absolute
        z-20
        flex
        size-8
        -translate-x-1/2
        -translate-y-1/2
        cursor-pointer
        items-center
        justify-center
      "
            style={{
                left: posicionX(x),
                top: posicionY(y),
            }}
        >
            <TooltipMapa
                titulo={id}
                lineas={[
                    `Tipo: ${tipo}`,
                    `Coordenadas: (${x}, ${y})`,
                    `Estado: ${averiado ? "AVERIADO" : "EN RUTA"}`,
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