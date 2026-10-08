import {
  useEffect,
  useState,
} from "react";

import {
  CheckCircle2,
  Loader2,
  ServerCrash,
} from "lucide-react";

import {
  MOCK_INDICADORES,
} from "./ejecucion.mock";

import type {
  AlmacenMapa,
  PedidoOperacion,
  VehiculoMapa,
} from "./ejecucion.types";

import type {
  RegistrarPedidoRequestDto,
} from "@/api/pedidos.dto";

import {
  almacenesService,
} from "@/services/almacenes.service";

import {
  adaptarAlmacenes,
} from "@/services/almacenes.adapter";

import {
  flotaService,
} from "@/services/flota.service";

import {
  adaptarVehiculos,
} from "@/services/flota.adapter";

import {
  pedidosService,
} from "@/services/pedidos.service";

import {
  adaptarPedido,
  adaptarPedidos,
} from "@/services/pedidos.adapter";

import {
  ApiError,
} from "@/services/api";

import {
  MapaOperacion,
} from "./MapaOperacion";

import {
  PanelIndicadores,
} from "./PanelIndicadores";

import {
  RegistrarPedidoForm,
} from "./RegistrarPedidoForm";

export default function EjecucionPage() {
  const [
    pedidos,
    setPedidos,
  ] =
    useState<
      PedidoOperacion[]
    >([]);

  const [
    almacenes,
    setAlmacenes,
  ] =
    useState<
      AlmacenMapa[]
    >([]);

  const [
    vehiculos,
    setVehiculos,
  ] =
    useState<
      VehiculoMapa[]
    >([]);

  const [
    cargandoBackend,
    setCargandoBackend,
  ] = useState(true);

  const [
    errorBackend,
    setErrorBackend,
  ] = useState<
    string | null
  >(null);

  const [
    mostrarFormulario,
    setMostrarFormulario,
  ] = useState(false);

  const [
    tiempoActualMin,
    setTiempoActualMin,
  ] = useState(0);

  const [
    mensaje,
    setMensaje,
  ] = useState(
    "Esperando información del planificador.",
  );

  useEffect(() => {
    void cargarDatosBackend();
  }, []);

  async function cargarDatosBackend() {
    try {
      setCargandoBackend(
        true,
      );

      setErrorBackend(
        null,
      );

      const [
        respuestaAlmacenes,
        respuestaVehiculos,
        respuestaPedidos,
      ] = await Promise.all([
        almacenesService.listar(),

        flotaService.listarVehiculos(),

        pedidosService.listar(),
      ]);

      setAlmacenes(
        adaptarAlmacenes(
          respuestaAlmacenes,
        ),
      );

      setVehiculos(
        adaptarVehiculos(
          respuestaVehiculos,
        ),
      );

      setPedidos(
        adaptarPedidos(
          respuestaPedidos.contenido,
        ),
      );

      console.log(
        "Almacenes backend:",
        respuestaAlmacenes,
      );

      console.log(
        "Vehículos backend:",
        respuestaVehiculos,
      );

      console.log(
        "Pedidos backend:",
        respuestaPedidos,
      );
    } catch (error) {
      console.error(
        "Error cargando datos del backend:",
        error,
      );

      if (
        error instanceof
        ApiError
      ) {
        setErrorBackend(
          error.message,
        );
      } else if (
        error instanceof
        Error
      ) {
        setErrorBackend(
          error.message,
        );
      } else {
        setErrorBackend(
          "No fue posible conectarse con el backend.",
        );
      }
    } finally {
      setCargandoBackend(
        false,
      );
    }
  }

  async function handleRegistrarPedido(
    request: RegistrarPedidoRequestDto,
  ) {
    const respuesta =
      await pedidosService.registrar(
        request,
      );

    const nuevoPedido =
      adaptarPedido(
        respuesta,
      );

    setPedidos(
      (actuales) => [
        ...actuales,
        nuevoPedido,
      ],
    );

    setMensaje(
      `${respuesta.id} registrado correctamente.`,
    );

    setMostrarFormulario(
      false,
    );
  }

  return (
    <div className="grid h-screen grid-cols-[minmax(0,1fr)_330px] overflow-hidden bg-[#0d1117]">
      <div className="relative min-w-0">

        <MapaOperacion
          pedidos={
            pedidos
          }
          almacenes={
            almacenes
          }
          vehiculos={
            vehiculos
          }
        />

        <EstadoBackend
          cargando={
            cargandoBackend
          }
          error={
            errorBackend
          }
          cantidadAlmacenes={
            almacenes.length
          }
          cantidadVehiculos={
            vehiculos.length
          }
          cantidadPedidos={
            pedidos.length
          }
          onReintentar={() =>
            void cargarDatosBackend()
          }
        />

        {/* RELOJ */}
        <div className="absolute left-5 top-20 z-40 rounded-lg border border-slate-700 bg-[#11161d] p-4 text-white shadow-lg">
          <p className="text-xs text-slate-400">
            Tiempo simulado
          </p>

          <p className="text-xl font-semibold">
            {formatearTiempo(
              tiempoActualMin,
            )}
          </p>

          <div className="mt-3 flex gap-2">
            <BotonTiempo
              label="+10 min"
              onClick={() =>
                setTiempoActualMin(
                  (actual) =>
                    actual +
                    10,
                )
              }
            />

            <BotonTiempo
              label="+30 min"
              onClick={() =>
                setTiempoActualMin(
                  (actual) =>
                    actual +
                    30,
                )
              }
            />

            <BotonTiempo
              label="+1 h"
              onClick={() =>
                setTiempoActualMin(
                  (actual) =>
                    actual +
                    60,
                )
              }
            />
          </div>
        </div>

        {/* REGISTRAR PEDIDO */}
        <button
          type="button"
          onClick={() =>
            setMostrarFormulario(
              true,
            )
          }
          className="absolute right-5 top-5 z-40 rounded-md bg-orange-500 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-orange-600"
        >
          + Registrar pedido
        </button>

        {mostrarFormulario && (
          <RegistrarPedidoForm
            onRegistrar={
              handleRegistrarPedido
            }
            onCerrar={() =>
              setMostrarFormulario(
                false,
              )
            }
          />
        )}

        {/* PLANIFICADOR */}
        <div className="absolute bottom-5 left-5 z-40 w-96 rounded-lg border border-slate-700 bg-[#11161d]/95 p-4 text-white shadow-xl">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h3 className="font-semibold">
                Planificador
              </h3>

              <p className="mt-1 text-xs text-slate-500">
                Simulated Annealing
              </p>
            </div>

            <span className="rounded-full bg-sky-500/15 px-2 py-1 text-[10px] font-semibold text-sky-400">
              SA
            </span>
          </div>

          <p className="mt-3 rounded-md bg-slate-800 p-2 text-xs text-slate-300">
            {mensaje}
          </p>

          <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
            <Dato
              label="Pedidos"
              value={String(
                pedidos.length,
              )}
            />

            <Dato
              label="Vehículos"
              value={String(
                vehiculos.length,
              )}
            />
          </div>
        </div>
      </div>

      <PanelIndicadores
        indicadores={
          MOCK_INDICADORES
        }
        almacenes={
          almacenes
        }
        vehiculos={
          vehiculos
        }
      />
    </div>
  );
}

interface EstadoBackendProps {
  cargando: boolean;

  error: string | null;

  cantidadAlmacenes: number;
  cantidadVehiculos: number;
  cantidadPedidos: number;

  onReintentar: () => void;
}

function EstadoBackend({
  cargando,
  error,
  cantidadAlmacenes,
  cantidadVehiculos,
  cantidadPedidos,
  onReintentar,
}: EstadoBackendProps) {
  if (cargando) {
    return (
      <div className="absolute left-5 top-5 z-50 flex items-center gap-2 rounded-md border border-slate-700 bg-[#11161d] px-3 py-2 text-xs text-slate-300 shadow">
        <Loader2 className="size-4 animate-spin" />

        Conectando con backend...
      </div>
    );
  }

  if (error) {
    return (
      <div className="absolute left-5 top-5 z-50 flex items-center gap-3 rounded-md border border-red-900 bg-[#11161d] px-3 py-2 text-xs shadow">
        <ServerCrash className="size-4 text-red-400" />

        <div>
          <p className="text-red-400">
            Backend no disponible
          </p>

          <p className="mt-0.5 max-w-64 text-[10px] text-slate-500">
            {error}
          </p>
        </div>

        <button
          type="button"
          onClick={
            onReintentar
          }
          className="rounded bg-slate-700 px-2 py-1 text-[10px] text-white hover:bg-slate-600"
        >
          Reintentar
        </button>
      </div>
    );
  }

  return (
    <div className="absolute left-5 top-5 z-50 flex items-center gap-2 rounded-md border border-green-900 bg-[#11161d] px-3 py-2 text-xs text-green-400 shadow">
      <CheckCircle2 className="size-4" />

      Backend conectado ·{" "}
      {cantidadAlmacenes} almacenes ·{" "}
      {cantidadVehiculos} vehículos ·{" "}
      {cantidadPedidos} pedidos
    </div>
  );
}

function BotonTiempo({
  label,
  onClick,
}: {
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={
        onClick
      }
      className="rounded bg-slate-700 px-3 py-1 text-xs hover:bg-slate-600"
    >
      {label}
    </button>
  );
}

function Dato({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div className="rounded-md bg-[#1b222c] p-2">
      <p className="text-[10px] uppercase text-slate-500">
        {label}
      </p>

      <p className="mt-1 font-semibold text-slate-200">
        {value}
      </p>
    </div>
  );
}

function formatearTiempo(
  minutosTotales: number,
) {
  const total =
    Math.round(
      minutosTotales,
    );

  const horas =
    Math.floor(
      total / 60,
    );

  const minutos =
    total % 60;

  return `${String(
    horas,
  ).padStart(
    2,
    "0",
  )}:${String(
    minutos,
  ).padStart(
    2,
    "0",
  )}`;
}