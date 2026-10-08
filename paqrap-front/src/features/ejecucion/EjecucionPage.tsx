import {
  useState,
} from "react";

import {
  MOCK_ALMACENES,
  MOCK_INDICADORES,
  MOCK_PEDIDOS,
  MOCK_VEHICULOS,
} from "./ejecucion.mock";

import type {
  PedidoOperacion,
} from "./ejecucion.types";

import { MapaOperacion } from "./MapaOperacion";
import { PanelIndicadores } from "./PanelIndicadores";
import { RegistrarPedidoForm } from "./RegistrarPedidoForm";

export default function EjecucionPage() {
  const [
    pedidos,
    setPedidos,
  ] =
    useState<
      PedidoOperacion[]
    >(MOCK_PEDIDOS);

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

  const handleRegistrarPedido = (
    pedido: PedidoOperacion,
  ) => {
    setPedidos(
      (actuales) => [
        ...actuales,
        pedido,
      ],
    );

    setMensaje(
      `${pedido.id} registrado. En la integración final se enviará al backend para replanificación con Simulated Annealing.`,
    );

    setMostrarFormulario(
      false,
    );
  };

  return (
    <div className="grid h-screen grid-cols-[minmax(0,1fr)_330px] overflow-hidden bg-[#0d1117]">
      <div className="relative min-w-0">
        <MapaOperacion
          pedidos={pedidos}
          almacenes={
            MOCK_ALMACENES
          }
          vehiculos={
            MOCK_VEHICULOS
          }
        />

        {/* RELOJ */}
        <div className="absolute left-5 top-5 z-40 rounded-lg border border-slate-700 bg-[#11161d] p-4 text-white shadow-lg">
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
            siguienteNumero={
              pedidos.length +
              1
            }
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
                Simulated
                Annealing
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
              label="Pedidos visibles"
              value={String(
                pedidos.length,
              )}
            />

            <Dato
              label="Pendientes"
              value={String(
                pedidos.filter(
                  (pedido) =>
                    pedido.estado ===
                    "PENDIENTE",
                ).length,
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
          MOCK_ALMACENES
        }
        vehiculos={
          MOCK_VEHICULOS
        }
      />
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
      onClick={onClick}
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