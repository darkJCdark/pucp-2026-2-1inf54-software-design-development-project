import { useState } from "react";

import {
  PEDIDOS_PRUEBA,
  VEHICULOS_PRUEBA,
} from "@/features/algoritmos/algoritmo.mock";

import type {
  PedidoAlgoritmo,
} from "@/features/algoritmos/algoritmo.types";

import {
  asignarPedidoTemporal,
} from "./simuladorTemporal";

import type {
  AsignacionTemporal,
  PedidoTemporal,
  VehiculoTemporal,
} from "./simulacion.types";

import { MapaOperacion } from "./MapaOperacion";
import { PanelIndicadores } from "./PanelIndicadores";
import { RegistrarPedidoForm } from "./RegistrarPedidoForm";

const VEHICULOS_INICIALES: VehiculoTemporal[] =
  VEHICULOS_PRUEBA.map((vehiculo) => ({
    ...vehiculo,
    disponibleDesdeMin: 0,
  }));

export default function EjecucionPage() {
  const [
    pedidos,
    setPedidos,
  ] = useState<PedidoAlgoritmo[]>(
    PEDIDOS_PRUEBA,
  );

  const [
    vehiculos,
    setVehiculos,
  ] = useState<VehiculoTemporal[]>(
    VEHICULOS_INICIALES,
  );

  const [
    tiempoActualMin,
    setTiempoActualMin,
  ] = useState(0);

  const [
    mostrarFormulario,
    setMostrarFormulario,
  ] = useState(false);

  const [
    mensaje,
    setMensaje,
  ] = useState(
    "Todavía no se ha registrado un nuevo pedido.",
  );

  const [
    asignaciones,
    setAsignaciones,
  ] = useState<AsignacionTemporal[]>([]);

  const handleRegistrarPedido = (
    pedidoBase: PedidoAlgoritmo,
  ) => {
    /*
     * El pedido se registra en el instante
     * actual de la simulación.
     */
    const pedido: PedidoTemporal = {
      ...pedidoBase,
      creadoEnMin: tiempoActualMin,
    };

    /*
     * Lo agregamos a la lista para
     * mostrarlo también en el mapa.
     */
    setPedidos((actuales) => [
      ...actuales,
      pedido,
    ]);

    /*
     * Intentamos asignarlo usando el estado
     * actual de los vehículos.
     */
    const resultado =
      asignarPedidoTemporal(
        pedido,
        vehiculos,
        tiempoActualMin,
      );

    /*
     * No existe una asignación factible.
     */
    if (!resultado.asignacion) {
      setMensaje(
        `${pedido.id}: ${
          resultado.motivo ??
          "No pudo ser asignado."
        }`,
      );

      setMostrarFormulario(false);

      return;
    }

    /*
     * Guardamos la nueva disponibilidad
     * de los vehículos.
     */
    setVehiculos(
      resultado.vehiculosActualizados,
    );

    /*
     * Guardamos la asignación realizada.
     */
    setAsignaciones((actuales) => [
      ...actuales,
      resultado.asignacion!,
    ]);

    setMensaje(
      `${pedido.id} asignado a ${
        resultado.asignacion.vehiculoId
      }. Llega ${formatearTiempo(
        resultado.asignacion
          .llegadaPedidoMin,
      )} y vuelve al Central ${formatearTiempo(
        resultado.asignacion
          .retornoCentralMin,
      )}.`,
    );

    setMostrarFormulario(false);
  };

  return (
    <div className="grid h-screen grid-cols-[minmax(0,1fr)_330px] overflow-hidden bg-[#0d1117]">
      <div className="relative min-w-0">
        <MapaOperacion
          pedidos={pedidos}
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
            <button
              type="button"
              onClick={() =>
                setTiempoActualMin(
                  (actual) =>
                    actual + 10,
                )
              }
              className="rounded bg-slate-700 px-3 py-1 text-xs hover:bg-slate-600"
            >
              +10 min
            </button>

            <button
              type="button"
              onClick={() =>
                setTiempoActualMin(
                  (actual) =>
                    actual + 30,
                )
              }
              className="rounded bg-slate-700 px-3 py-1 text-xs hover:bg-slate-600"
            >
              +30 min
            </button>

            <button
              type="button"
              onClick={() =>
                setTiempoActualMin(
                  (actual) =>
                    actual + 60,
                )
              }
              className="rounded bg-slate-700 px-3 py-1 text-xs hover:bg-slate-600"
            >
              +1 h
            </button>
          </div>
        </div>

        {/* BOTÓN REGISTRAR */}
        <button
          type="button"
          onClick={() =>
            setMostrarFormulario(true)
          }
          className="absolute right-5 top-5 z-40 rounded-md bg-orange-500 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-orange-600"
        >
          + Registrar pedido
        </button>

        {/* FORMULARIO */}
        {mostrarFormulario && (
          <RegistrarPedidoForm
            siguienteNumero={
              pedidos.length + 1
            }
            onRegistrar={
              handleRegistrarPedido
            }
            onCerrar={() =>
              setMostrarFormulario(false)
            }
          />
        )}

        {/* ESTADO DE VEHÍCULOS */}
        <div className="absolute bottom-5 left-5 z-40 w-96 rounded-lg border border-slate-700 bg-[#11161d]/95 p-4 text-white shadow-xl">
          <h3 className="font-semibold">
            Estado de vehículos
          </h3>

          <div className="mt-3 space-y-2">
            {vehiculos.map(
              (vehiculo) => {
                const ocupado =
                  vehiculo.disponibleDesdeMin >
                  tiempoActualMin;

                return (
                  <div
                    key={vehiculo.id}
                    className="flex items-center justify-between rounded-md bg-[#1b222c] p-3"
                  >
                    <div>
                      <p className="text-sm font-semibold">
                        {vehiculo.id}
                      </p>

                      <p className="text-xs text-slate-500">
                        {vehiculo.tipo}
                      </p>
                    </div>

                    <div className="text-right">
                      <p
                        className={
                          ocupado
                            ? "text-xs font-semibold text-orange-400"
                            : "text-xs font-semibold text-green-400"
                        }
                      >
                        {ocupado
                          ? "EN RUTA"
                          : "DISPONIBLE"}
                      </p>

                      {ocupado && (
                        <p className="text-[11px] text-slate-500">
                          vuelve{" "}
                          {formatearTiempo(
                            vehiculo.disponibleDesdeMin,
                          )}
                        </p>
                      )}
                    </div>
                  </div>
                );
              },
            )}
          </div>

          {/* MENSAJE DE LA ÚLTIMA ASIGNACIÓN */}
          <p className="mt-3 rounded-md bg-slate-800 p-2 text-xs text-slate-300">
            {mensaje}
          </p>

          {/* HISTORIAL */}
          {asignaciones.length > 0 && (
            <div className="mt-4 border-t border-slate-700 pt-3">
              <p className="mb-2 text-xs font-semibold text-slate-400">
                Últimas asignaciones
              </p>

              <div className="space-y-2">
                {asignaciones
                  .slice(-3)
                  .reverse()
                  .map(
                    (
                      asignacion,
                      index,
                    ) => (
                      <div
                        key={`${asignacion.pedidoId}-${index}`}
                        className="text-xs"
                      >
                        <span className="text-white">
                          {
                            asignacion.pedidoId
                          }
                        </span>

                        <span className="text-slate-500">
                          {" "}
                          →{" "}
                        </span>

                        <span className="text-orange-400">
                          {
                            asignacion.vehiculoId
                          }
                        </span>

                        <p className="text-[11px] text-slate-500">
                          llegada{" "}
                          {formatearTiempo(
                            asignacion.llegadaPedidoMin,
                          )}
                          {" · "}
                          regreso{" "}
                          {formatearTiempo(
                            asignacion.retornoCentralMin,
                          )}
                        </p>
                      </div>
                    ),
                  )}
              </div>
            </div>
          )}
        </div>
      </div>

      <PanelIndicadores />
    </div>
  );
}

function formatearTiempo(
  minutosTotales: number,
) {
  const total = Math.round(
    minutosTotales,
  );

  const horas = Math.floor(
    total / 60,
  );

  const minutos = total % 60;

  return `${String(horas).padStart(
    2,
    "0",
  )}:${String(minutos).padStart(
    2,
    "0",
  )}`;
}