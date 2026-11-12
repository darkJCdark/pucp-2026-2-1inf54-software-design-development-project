import {
  useEffect,
  useState,
} from "react";

import {
  ChevronDown,
  Package,
  PanelLeftOpen,
  Truck,
  Warehouse,
} from "lucide-react";

import type {
  TipoVehiculoResponseDto,
} from "@/api/flota.dto";

import type {
  MonitoreoResponseDto,
} from "@/api/monitoreo.dto";

import type {
  PlanResponseDto,
} from "@/api/planificacion.dto";

import type {
  PedidoOperacion,
} from "./ejecucion.types";

import type {
  SeleccionDetalleOperacion,
} from "./DetalleOperacionPanel";

import {
  ListaAlmacenes,
} from "./ListaAlmacenes";

import {
  ListaDistanciasVehiculos,
} from "./ListaDistanciasVehiculos";

import {
  ListaPedidosEjecucion,
  type SeleccionLista,
} from "./ListaPedidosEjecucion";

import {
  ListaVehiculos,
} from "./ListaVehiculos";

type PestanaPanel =
  | "VEHICULOS"
  | "PEDIDOS"
  | "ALMACENES";

interface PanelListasOperacionProps {
  monitoreo:
    MonitoreoResponseDto;

  ejecucionId:
    number;

  tiposFlota:
    TipoVehiculoResponseDto[];

  plan:
    PlanResponseDto | null;

  /**
   * Pedidos ya cargados por EjecucionPage.
   */
  pedidos:
    PedidoOperacion[];

  seleccion:
    SeleccionDetalleOperacion | null;

  onSeleccionar: (
    seleccion:
      SeleccionLista,
  ) => void;

  /**
   * Control opcional desde EjecucionPage (acordeón de la columna
   * izquierda). Sin estas props el panel maneja su propio estado.
   */
  abierto?: boolean;
  onCambiarAbierto?: (abierto: boolean) => void;
}

export function PanelListasOperacion({
  monitoreo,
  ejecucionId,
  tiposFlota,
  plan,
  pedidos,
  seleccion,
  onSeleccionar,
  abierto: abiertoControlado,
  onCambiarAbierto,
}: PanelListasOperacionProps) {
  const [
    abiertoInterno,
    setAbiertoInterno,
  ] =
    useState(false);

  const abierto =
    abiertoControlado ??
    abiertoInterno;

  function cambiarAbierto(
    siguiente: boolean,
  ) {
    if (
      abiertoControlado ===
      undefined
    ) {
      setAbiertoInterno(
        siguiente,
      );
    }

    onCambiarAbierto?.(
      siguiente,
    );
  }

  const [
    pestana,
    setPestana,
  ] =
    useState<PestanaPanel>(
      "VEHICULOS",
    );

  /*
   * Una selección solo cambia la pestaña. Ya no abre el panel:
   * el detalle se muestra a la derecha y así no se apilan ventanas.
   */
  useEffect(() => {
    if (!seleccion) {
      return;
    }

    setPestana(
      seleccion.tipo ===
        "VEHICULO"
        ? "VEHICULOS"
        : seleccion.tipo ===
            "PEDIDO"
          ? "PEDIDOS"
          : "ALMACENES",
    );
  }, [
    seleccion,
  ]);

  return (
    <aside className="w-full shrink-0 overflow-hidden rounded-xl border border-slate-700 bg-[#11161d]/97 text-white shadow-2xl">
      <button
        type="button"
        onClick={() =>
          cambiarAbierto(
            !abierto,
          )
        }
        className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left hover:bg-slate-800/60"
        aria-expanded={
          abierto
        }
      >
        <div className="flex items-center gap-2">
          <PanelLeftOpen className="size-4 text-orange-400" />

          <div>
            <p className="text-sm font-semibold">
              Panel de operación
            </p>

            <p className="text-[10px] text-slate-500">
              {
                monitoreo
                  .vehiculos
                  .length
              }{" "}
              unidades ·{" "}
              {
                monitoreo
                  .pedidos
                  .llegados
              }{" "}
              pedidos ·{" "}
              {
                monitoreo
                  .almacenes
                  .length
              }{" "}
              almacenes
            </p>
          </div>
        </div>

        <ChevronDown
          className={[
            "size-4 text-slate-500 transition-transform",

            abierto
              ? "rotate-180"
              : "",
          ].join(" ")}
        />
      </button>

      {abierto && (
        <div className="border-t border-slate-800 animate-in fade-in slide-in-from-top-1 duration-150">
          <div className="grid grid-cols-3 gap-1 p-2">
            <Pestana
              activa={
                pestana ===
                "VEHICULOS"
              }
              icono={
                <Truck className="size-3.5" />
              }
              texto="Vehículos"
              onClick={() =>
                setPestana(
                  "VEHICULOS",
                )
              }
            />

            <Pestana
              activa={
                pestana ===
                "PEDIDOS"
              }
              icono={
                <Package className="size-3.5" />
              }
              texto="Pedidos"
              onClick={() =>
                setPestana(
                  "PEDIDOS",
                )
              }
            />

            <Pestana
              activa={
                pestana ===
                "ALMACENES"
              }
              icono={
                <Warehouse className="size-3.5" />
              }
              texto="Almacenes"
              onClick={() =>
                setPestana(
                  "ALMACENES",
                )
              }
            />
          </div>

          <div className="max-h-[calc(45vh-3rem)] space-y-2 overflow-y-auto p-2 pt-0">
            {pestana ===
              "VEHICULOS" && (
              <>
                <ListaVehiculos
                  monitoreo={
                    monitoreo
                  }
                  ejecucionId={
                    ejecucionId
                  }
                  tiposFlota={
                    tiposFlota
                  }
                  plan={
                    plan
                  }
                  seleccionId={
                    seleccion?.tipo ===
                      "VEHICULO"
                      ? seleccion.id
                      : null
                  }
                  onSeleccionar={
                    onSeleccionar
                  }
                />

                <ListaDistanciasVehiculos
                  monitoreo={
                    monitoreo
                  }
                  plan={
                    plan
                  }
                  seleccionId={
                    seleccion?.tipo ===
                      "VEHICULO"
                      ? seleccion.id
                      : null
                  }
                  onSeleccionar={
                    onSeleccionar
                  }
                />
              </>
            )}

            {pestana ===
              "PEDIDOS" && (
              <ListaPedidosEjecucion
                pedidos={
                  pedidos
                }
                plan={
                  plan
                }
                seleccionId={
                  seleccion?.tipo ===
                    "PEDIDO"
                    ? seleccion.id
                    : null
                }
                onSeleccionar={
                  onSeleccionar
                }
              />
            )}

            {pestana ===
              "ALMACENES" && (
              <ListaAlmacenes
                monitoreo={
                  monitoreo
                }
                ejecucionId={
                  ejecucionId
                }
                plan={
                  plan
                }
                seleccionId={
                  seleccion?.tipo ===
                    "ALMACEN"
                    ? seleccion.id
                    : null
                }
                onSeleccionar={
                  onSeleccionar
                }
              />
            )}
          </div>
        </div>
      )}
    </aside>
  );
}

function Pestana({
  activa,
  icono,
  texto,
  onClick,
}: {
  activa:
    boolean;

  icono:
    React.ReactNode;

  texto:
    string;

  onClick:
    () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={[
        "flex items-center justify-center gap-1 rounded-md px-2 py-2 text-[11px] font-medium",

        activa
          ? "bg-orange-500 text-white"
          : "bg-slate-800 text-slate-300 hover:bg-slate-700",
      ].join(" ")}
    >
      {icono}
      {texto}
    </button>
  );
}