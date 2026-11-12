import {
  memo,
  useMemo,
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

import type {
  NivelDetalleMapa,
  ViewportPixeles,
} from "./mapaVisual.types";

type NivelSemaforo =
  | "TODOS"
  | "VACIO"
  | "VERDE"
  | "AMBAR"
  | "ROJO";

const FPS_RUTAS =
  4;

const INTERVALO_RUTAS_REAL_MS =
  1000 /
  FPS_RUTAS;

interface CapaDinamicaMapaProps {
  vehiculos:
    VehiculoMapa[];

  rutas:
    RutaMapa[];

  viewportPixeles:
    ViewportPixeles;

  /*
   * Lo dejamos opcional para que el archivo
   * también compile si temporalmente olvidas
   * pasar la prop desde MapaOperacion.
   */
  nivelDetalle?:
    NivelDetalleMapa;

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
    titulo: string,
    lineas: string[],
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
      "#EC4899",
    MOTO:
      "#22c55e",
    BICICLETA:
      "#f59e0b",
  };

function CapaDinamicaMapaBase({
  vehiculos,
  rutas,
  viewportPixeles,
  nivelDetalle = "MEDIO",
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
  const instanteAnimadoMs =
    useRelojSimuladoAnimado(
      instanteSimulado,
      factorAceleracion,
      estadoEscenario,
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
      [rutas],
    );

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

  /*
   * Antes se congelaban estos instantes con refs leídos durante el
   * render (error react-hooks/refs). No hace falta: con modoPan activo
   * useRelojSimuladoAnimado recibe activo=false y deja de actualizar
   * instanteAnimadoMs, así que el valor ya queda congelado.
   */
  const instanteVehiculosMs =
    instanteAnimadoMs;

  const instanteRutasMs =
    instanteRutasCalculadoMs;

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
      <Layer
        listening={
          !modoPan
        }
      >
        <CapaRutas
          rutas={rutas}
          vehiculosVisuales={
            vehiculosVisualesRutas
          }
          viewportPixeles={
            viewportPixeles
          }
          nivelDetalle={
            nivelDetalle
          }
          visible={
            mostrarRutas
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

      {capaIntermedia}

      <Layer
        listening={
          !modoPan
        }
      >
        <CapaVehiculos
          vehiculosVisuales={
            vehiculosVisuales
          }
          nivelDetalle={
            nivelDetalle
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
