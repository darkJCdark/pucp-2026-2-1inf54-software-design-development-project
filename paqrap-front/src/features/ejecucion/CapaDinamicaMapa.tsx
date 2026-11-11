import {
    memo,
    useMemo,
    useRef,
    type ReactNode,
} from "react";

import type {
    KonvaEventObject,
} from "konva/lib/Node";

import {
    Layer,
} from "react-konva";

import type {
    EstadoEscenarioBackend,
} from "@/api/escenarios.dto";

import type {
    TipoVehiculoResponseDto,
} from "@/api/flota.dto";

import {
    useRelojSimuladoAnimado,
} from "@/hooks/useRelojSimuladoAnimado";

import {
    CapaRutas,
} from "./CapaRutas";

import {
    CapaVehiculos,
    type VehiculoVisual,
} from "./CapaVehiculos";

import type {
    RutaMapa,
    TipoVehiculo,
    VehiculoMapa,
} from "./ejecucion.types";

import {
    obtenerSeguimientoRuta,
} from "./seguimientoRuta";

type NivelSemaforo =
    | "TODOS"
    | "VACIO"
    | "VERDE"
    | "AMBAR"
    | "ROJO";

const FPS_RUTAS = 4;

const INTERVALO_RUTAS_REAL_MS =
    1000 / FPS_RUTAS;

interface CapaDinamicaMapaProps {
    vehiculos:
    VehiculoMapa[];

    rutas:
    RutaMapa[];

    tiposFlota:
    TipoVehiculoResponseDto[];

    instanteSimulado:
    string;

    factorAceleracion:
    number;

    estadoEscenario:
    EstadoEscenarioBackend;

    vehiculoSeleccionadoId:
    string | null;

    seleccionPedidoId:
    string | null;

    mostrarRutas:
    boolean;

    mostrarAutos:
    boolean;

    mostrarMotos:
    boolean;

    mostrarBicicletas:
    boolean;

    filtroCarga:
    NivelSemaforo;

    verdeHasta:
    number;

    ambarHasta:
    number;

    convertirX:
    (valor: number) => number;

    convertirY:
    (valor: number) => number;

    onSeleccionarVehiculo:
    (id: string) => void;

    onTooltip: (
        event:
            KonvaEventObject<MouseEvent>,
        titulo:
            string,
        lineas:
            string[],
    ) => void;

    onLeave:
    () => void;

    modoPan:
    boolean;

    capaIntermedia:
    ReactNode;
}

const COLORES_VEHICULO:
    Record<
        TipoVehiculo,
        string
    > = {
    AUTO:
        "#3b82f6",

    MOTO:
        "#22c55e",

    BICICLETA:
        "#f59e0b",
};

function CapaDinamicaMapaBase({
    vehiculos,
    rutas,
    tiposFlota,
    instanteSimulado,
    factorAceleracion,
    estadoEscenario,
    vehiculoSeleccionadoId,
    seleccionPedidoId,
    mostrarRutas,
    mostrarAutos,
    mostrarMotos,
    mostrarBicicletas,
    filtroCarga,
    verdeHasta,
    ambarHasta,
    convertirX,
    convertirY,
    onSeleccionarVehiculo,
    onTooltip,
    onLeave,
    modoPan,
    capaIntermedia,
}: CapaDinamicaMapaProps) {
    /*
     * Reloj principal.
     *
     * La simulación continúa normalmente aunque
     * el usuario esté arrastrando el mapa.
     */
    const instanteAnimadoMs =
        useRelojSimuladoAnimado(
            instanteSimulado,
            factorAceleracion,
            estadoEscenario,

            /*
             * Mientras hacemos pan:
             * cero frames de animación React.
             */
            !modoPan,
        );

    const rutasPorVehiculo =
        useMemo(
            () =>
                new Map(
                    rutas.map(
                        (ruta) => [
                            ruta.vehiculoId,
                            ruta,
                        ],
                    ),
                ),
            [
                rutas,
            ],
        );

    /*
     * =====================================================
     * CONGELACIÓN VISUAL DURANTE PAN
     * =====================================================
     *
     * Mientras el usuario arrastra:
     *
     * - no recalculamos posiciones de vehículos;
     * - no recalculamos seguimiento de rutas;
     * - las rutas se ocultan temporalmente.
     *
     * La simulación del backend NO se pausa.
     */

    const instanteVehiculosRef =
        useRef(
            instanteAnimadoMs,
        );

    if (!modoPan) {
        instanteVehiculosRef.current =
            instanteAnimadoMs;
    }

    const instanteVehiculosMs =
        instanteVehiculosRef.current;

    /*
     * Rutas a aproximadamente 4 FPS.
     */
    const intervaloRutasSimuladoMs =
        Math.max(
            1,
            factorAceleracion *
            INTERVALO_RUTAS_REAL_MS,
        );

    const instanteRutasCalculadoMs =
        Math.floor(
            instanteAnimadoMs /
            intervaloRutasSimuladoMs,
        ) *
        intervaloRutasSimuladoMs;

    const instanteRutasRef =
        useRef(
            instanteRutasCalculadoMs,
        );

    if (!modoPan) {
        instanteRutasRef.current =
            instanteRutasCalculadoMs;
    }

    const instanteRutasMs =
        instanteRutasRef.current;

    /*
     * =====================================================
     * VEHÍCULOS
     * =====================================================
     *
     * Normal:
     *   10 FPS.
     *
     * Durante pan:
     *   valor congelado.
     */
    const vehiculosVisuales =
        useMemo<
            VehiculoVisual[]
        >(
            () =>
                construirVehiculosVisuales(
                    vehiculos,
                    rutasPorVehiculo,
                    instanteVehiculosMs,
                ),
            [
                instanteVehiculosMs,
                rutasPorVehiculo,
                vehiculos,
            ],
        );

    /*
     * =====================================================
     * RUTAS
     * =====================================================
     *
     * Normal:
     *   aproximadamente 4 FPS.
     *
     * Durante pan:
     *   seguimiento congelado.
     */
    const vehiculosVisualesRutas =
        useMemo<
            VehiculoVisual[]
        >(
            () =>
                construirVehiculosVisuales(
                    vehiculos,
                    rutasPorVehiculo,
                    instanteRutasMs,
                ),
            [
                instanteRutasMs,
                rutasPorVehiculo,
                vehiculos,
            ],
        );

    return (
        <>
            {/*
       * ===================================================
       * RUTAS
       * ===================================================
       *
       * Las ocultamos mientras hacemos pan.
       *
       * Esta es la optimización principal:
       * evita pintar potencialmente cientos de Arrow/Line
       * mientras el Stage se está desplazando.
       */}
            <Layer>
                <CapaRutas
                    rutas={
                        rutas
                    }

                    vehiculosVisuales={
                        vehiculosVisualesRutas
                    }

                    visible={
                        mostrarRutas
                    }

                    vehiculoSeleccionadoId={
                        vehiculoSeleccionadoId
                    }

                    seleccionPedidoId={
                        seleccionPedidoId
                    }

                    instanteMs={
                        instanteRutasMs
                    }

                    convertirX={
                        convertirX
                    }

                    convertirY={
                        convertirY
                    }

                    coloresVehiculo={
                        COLORES_VEHICULO
                    }

                    onSeleccionarVehiculo={
                        onSeleccionarVehiculo
                    }
                />
            </Layer>

            {/*
       * Pedidos + almacenes.
       *
       * Permanecen visibles durante el pan,
       * pero sin interacción.
       */}
            {capaIntermedia}

            {/*
       * ===================================================
       * VEHÍCULOS
       * ===================================================
       *
       * Permanecen visibles para mantener referencia
       * espacial, pero quedan congelados durante el pan.
       */}
            <Layer>
                <CapaVehiculos
                    vehiculosVisuales={
                        vehiculosVisuales
                    }

                    tiposFlota={
                        tiposFlota
                    }

                    vehiculoSeleccionadoId={
                        vehiculoSeleccionadoId
                    }

                    mostrarAutos={
                        mostrarAutos
                    }

                    mostrarMotos={
                        mostrarMotos
                    }

                    mostrarBicicletas={
                        mostrarBicicletas
                    }

                    filtroCarga={
                        filtroCarga
                    }

                    verdeHasta={
                        verdeHasta
                    }

                    ambarHasta={
                        ambarHasta
                    }

                    coloresVehiculo={
                        COLORES_VEHICULO
                    }

                    convertirX={
                        convertirX
                    }

                    convertirY={
                        convertirY
                    }

                    onSeleccionar={
                        onSeleccionarVehiculo
                    }

                    onTooltip={
                        onTooltip
                    }

                    onLeave={
                        onLeave
                    }
                />
            </Layer>
        </>
    );
}

export const CapaDinamicaMapa =
    memo(
        CapaDinamicaMapaBase,
    );

function construirVehiculosVisuales(
    vehiculos:
        VehiculoMapa[],

    rutasPorVehiculo:
        Map<
            string,
            RutaMapa
        >,

    instanteMs:
        number,
): VehiculoVisual[] {
    return vehiculos.map(
        (vehiculo) => {
            const ruta =
                rutasPorVehiculo.get(
                    vehiculo.id,
                );

            if (!ruta) {
                return {
                    vehiculo,

                    x:
                        vehiculo.x,

                    y:
                        vehiculo.y,

                    enMovimiento:
                        false,

                    progresoTramo:
                        0,

                    direccionGrados:
                        null,
                };
            }

            const seguimiento =
                obtenerSeguimientoRuta(
                    ruta,
                    vehiculo,
                    instanteMs,
                );

            return {
                vehiculo,
                ruta,
                seguimiento,

                x:
                    seguimiento
                        .posicionVisual
                        .x,

                y:
                    seguimiento
                        .posicionVisual
                        .y,

                enMovimiento:
                    seguimiento
                        .enMovimiento,

                progresoTramo:
                    seguimiento
                        .progresoTramo,

                direccionGrados:
                    seguimiento
                        .direccionGrados,
            };
        },
    );
}