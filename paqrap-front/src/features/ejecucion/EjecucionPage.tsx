import {
  useEffect,
  useState,
} from "react";

import {
  CheckCircle2,
  CirclePause,
  CirclePlay,
  Loader2,
  OctagonX,
  ServerCrash,
} from "lucide-react";

import {
  useNavigate,
  useParams,
} from "react-router-dom";

import type {
  MonitoreoResponseDto,
} from "@/api/monitoreo.dto";

import type {
  RegistrarPedidoRequestDto,
} from "@/api/pedidos.dto";

import type {
  AlmacenMapa,
  IndicadoresOperacion,
  PedidoOperacion,
  VehiculoMapa,
} from "./ejecucion.types";

import {
  monitoreoService,
} from "@/services/monitoreo.service";

import {
  escenariosService,
} from "@/services/escenarios.service";

import {
  adaptarAlmacenesMonitoreo,
  adaptarVehiculosMonitoreo,
} from "@/services/monitoreo.adapter";

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
  const {
    ejecucionId:
      ejecucionIdParam,
  } = useParams();

  const navigate =
    useNavigate();

  const ejecucionId =
    Number(
      ejecucionIdParam,
    );

  const [
    monitoreo,
    setMonitoreo,
  ] =
    useState<
      MonitoreoResponseDto | null
    >(null);

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
    cargando,
    setCargando,
  ] = useState(true);

  const [
    error,
    setError,
  ] =
    useState<
      string | null
    >(null);

  const [
    accionando,
    setAccionando,
  ] = useState(false);

  const [
    mostrarFormulario,
    setMostrarFormulario,
  ] = useState(false);

  useEffect(() => {
    if (
      !Number.isInteger(
        ejecucionId,
      ) ||
      ejecucionId <= 0
    ) {
      setError(
        "Identificador de ejecución inválido.",
      );

      setCargando(false);

      return;
    }

    void cargarEstado();

    const interval =
      window.setInterval(
        () => {
          void cargarEstado(
            false,
          );
        },
        1000,
      );

    return () => {
      window.clearInterval(
        interval,
      );
    };
  }, [ejecucionId]);

  async function cargarEstado(
    mostrarCarga = true,
  ) {
    try {
      if (mostrarCarga) {
        setCargando(true);
      }

      setError(null);

      const respuestaMonitoreo =
        await monitoreoService.obtener(
          ejecucionId,
        );

      setMonitoreo(
        respuestaMonitoreo,
      );

      setAlmacenes(
        adaptarAlmacenesMonitoreo(
          respuestaMonitoreo.almacenes,
        ),
      );

      setVehiculos(
        adaptarVehiculosMonitoreo(
          respuestaMonitoreo.vehiculos,
        ),
      );

      /*
       * Monitoreo entrega el resumen
       * de pedidos, pero no sus
       * coordenadas individuales.
       *
       * Consultamos únicamente los
       * pedidos que ya llegaron hasta
       * el instante simulado actual.
       */
      const respuestaPedidos =
        await pedidosService.listar(
          {
            registradoHasta:
              respuestaMonitoreo.instante,
          },
        );

      setPedidos(
        adaptarPedidos(
          respuestaPedidos.contenido,
        ),
      );
    } catch (error) {
      console.error(
        "Error consultando monitoreo:",
        error,
      );

      if (
        error instanceof ApiError
      ) {
        setError(
          error.message,
        );
      } else if (
        error instanceof Error
      ) {
        setError(
          error.message,
        );
      } else {
        setError(
          "No fue posible consultar el estado del escenario.",
        );
      }
    } finally {
      if (mostrarCarga) {
        setCargando(false);
      }
    }
  }

  async function handleRegistrarPedido(
    request: RegistrarPedidoRequestDto,
  ) {
    const requestConInstante: RegistrarPedidoRequestDto =
      {
        ...request,

        /*
         * Día a Día utiliza el instante
         * actual de la simulación.
         */
        registradoEn:
          monitoreo?.instante,
      };

    const respuesta =
      await pedidosService.registrar(
        requestConInstante,
      );

    setPedidos(
      (actuales) => [
        ...actuales,
        adaptarPedido(
          respuesta,
        ),
      ],
    );

    setMostrarFormulario(
      false,
    );

    await cargarEstado(
      false,
    );
  }

  async function ejecutarAccion(
    accion:
      | "PAUSAR"
      | "REANUDAR"
      | "DETENER",
  ) {
    try {
      setAccionando(true);
      setError(null);

      switch (accion) {
        case "PAUSAR":
          await escenariosService.pausar(
            ejecucionId,
          );
          break;

        case "REANUDAR":
          await escenariosService.reanudar(
            ejecucionId,
          );
          break;

        case "DETENER":
          await escenariosService.detener(
            ejecucionId,
          );
          break;
      }

      await cargarEstado(
        false,
      );
    } catch (error) {
      if (
        error instanceof ApiError
      ) {
        setError(
          error.message,
        );
      } else if (
        error instanceof Error
      ) {
        setError(
          error.message,
        );
      } else {
        setError(
          "No fue posible cambiar el estado del escenario.",
        );
      }
    } finally {
      setAccionando(false);
    }
  }

  if (cargando) {
    return (
      <div className="flex h-full min-h-[600px] items-center justify-center bg-[#0d1117] text-slate-300">
        <Loader2 className="mr-3 size-5 animate-spin" />

        Cargando escenario...
      </div>
    );
  }

  if (
    error &&
    !monitoreo
  ) {
    return (
      <div className="flex h-full min-h-[600px] flex-col items-center justify-center bg-[#0d1117] text-white">
        <ServerCrash className="mb-4 size-10 text-red-400" />

        <p className="font-semibold">
          No fue posible cargar el escenario
        </p>

        <p className="mt-2 max-w-md text-center text-sm text-slate-400">
          {error}
        </p>

        <button
          type="button"
          onClick={() =>
            navigate(
              "/simulacion",
            )
          }
          className="mt-5 rounded-md bg-orange-500 px-4 py-2 text-sm font-semibold"
        >
          Volver a configuración
        </button>
      </div>
    );
  }

  const estado =
    monitoreo?.escenario.estado;

  const tipo =
    monitoreo?.escenario.tipo;

  const puedeRegistrarPedido =
    tipo === "DAY_TO_DAY" &&
    estado === "RUNNING";

  const indicadores: IndicadoresOperacion =
    {
      /*
       * Todavía no hay métricas de
       * planificación en Monitoreo.
       * Las integraremos cuando SA
       * publique rutas/resultados.
       */
      costoAcumulado: 0,
      distanciaRecorrida: 0,

      entregasCompletadas:
        pedidos.filter(
          (pedido) =>
            pedido.estado ===
            "ENTREGADO",
        ).length,

      pedidosRiesgo:
        monitoreo?.pedidos
          .vencidosSinEntregar ??
        0,
    };

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

        {/* Estado / reloj */}
        {monitoreo && (
          <PanelEscenario
            monitoreo={
              monitoreo
            }
            accionando={
              accionando
            }
            onPausar={() =>
              void ejecutarAccion(
                "PAUSAR",
              )
            }
            onReanudar={() =>
              void ejecutarAccion(
                "REANUDAR",
              )
            }
            onDetener={() =>
              void ejecutarAccion(
                "DETENER",
              )
            }
          />
        )}

        {/* Error no fatal */}
        {error &&
          monitoreo && (
            <div className="absolute left-5 top-40 z-50 max-w-sm rounded-md border border-red-900 bg-[#11161d] px-3 py-2 text-xs text-red-400 shadow">
              {error}
            </div>
          )}

        {/* Registrar pedido:
            solo Día a Día */}
        {puedeRegistrarPedido && (
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
        )}

        {mostrarFormulario &&
          puedeRegistrarPedido && (
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

        {/* Estado operativo */}
        {monitoreo && (
          <div className="absolute bottom-5 left-5 z-40 w-96 rounded-lg border border-slate-700 bg-[#11161d]/95 p-4 text-white shadow-xl">
            <div className="flex items-center justify-between gap-3">
              <div>
                <h3 className="font-semibold">
                  Estado operativo
                </h3>

                <p className="mt-1 text-xs text-slate-500">
                  Ejecución #
                  {
                    monitoreo
                      .escenario
                      .id
                  }
                </p>
              </div>

              <span className="rounded-full bg-sky-500/15 px-2 py-1 text-[10px] font-semibold text-sky-400">
                {
                  monitoreo
                    .escenario
                    .factorAceleracion
                }
                ×
              </span>
            </div>

            <div className="mt-4 grid grid-cols-3 gap-2 text-xs">
              <Dato
                label="Llegados"
                value={String(
                  monitoreo
                    .pedidos
                    .llegados,
                )}
              />

              <Dato
                label="Pendientes"
                value={String(
                  monitoreo
                    .pedidos
                    .pendientes,
                )}
              />

              <Dato
                label="Vencidos"
                value={String(
                  monitoreo
                    .pedidos
                    .vencidosSinEntregar,
                )}
              />

              <Dato
                label="Bloqueos"
                value={String(
                  monitoreo
                    .bloqueosVigentes
                    .length,
                )}
              />

              <Dato
                label="Averías"
                value={String(
                  monitoreo
                    .averiasActivas
                    .length,
                )}
              />

              <Dato
                label="Vehículos"
                value={String(
                  monitoreo
                    .vehiculos
                    .length,
                )}
              />
            </div>

            <p className="mt-3 rounded-md bg-slate-800 p-2 text-[11px] text-slate-400">
              El reloj, inventario,
              bloqueos y averías se
              obtienen del backend.
            </p>
          </div>
        )}
      </div>

      <PanelIndicadores
        indicadores={
          indicadores
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

interface PanelEscenarioProps {
  monitoreo: MonitoreoResponseDto;

  accionando: boolean;

  onPausar: () => void;
  onReanudar: () => void;
  onDetener: () => void;
}

function PanelEscenario({
  monitoreo,
  accionando,
  onPausar,
  onReanudar,
  onDetener,
}: PanelEscenarioProps) {
  const estado =
    monitoreo.escenario.estado;

  return (
    <div className="absolute left-5 top-5 z-50 min-w-72 rounded-lg border border-slate-700 bg-[#11161d] p-4 text-white shadow-xl">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-[10px] uppercase text-slate-500">
            {nombreTipoEscenario(
              monitoreo
                .escenario
                .tipo,
            )}
          </p>

          <p className="mt-1 text-lg font-semibold">
            {formatearInstante(
              monitoreo.instante,
            )}
          </p>
        </div>

        <EstadoBadge
          estado={
            estado
          }
        />
      </div>

      <p className="mt-1 text-[10px] text-slate-500">
        Hora simulada · Lima
      </p>

      <div className="mt-4 flex gap-2">
        {estado ===
          "RUNNING" && (
          <button
            type="button"
            disabled={
              accionando
            }
            onClick={
              onPausar
            }
            className="flex items-center gap-1 rounded-md bg-slate-700 px-3 py-2 text-xs hover:bg-slate-600 disabled:opacity-50"
          >
            <CirclePause className="size-4" />

            Pausar
          </button>
        )}

        {estado ===
          "PAUSED" && (
          <button
            type="button"
            disabled={
              accionando
            }
            onClick={
              onReanudar
            }
            className="flex items-center gap-1 rounded-md bg-green-700 px-3 py-2 text-xs hover:bg-green-600 disabled:opacity-50"
          >
            <CirclePlay className="size-4" />

            Reanudar
          </button>
        )}

        {(estado ===
          "RUNNING" ||
          estado ===
            "PAUSED") && (
          <button
            type="button"
            disabled={
              accionando
            }
            onClick={
              onDetener
            }
            className="flex items-center gap-1 rounded-md bg-red-900 px-3 py-2 text-xs text-red-100 hover:bg-red-800 disabled:opacity-50"
          >
            <OctagonX className="size-4" />

            Detener
          </button>
        )}
      </div>
    </div>
  );
}

function EstadoBadge({
  estado,
}: {
  estado:
    MonitoreoResponseDto["escenario"]["estado"];
}) {
  const estilos =
    estado === "RUNNING"
      ? "bg-green-500/15 text-green-400"
      : estado === "PAUSED"
        ? "bg-amber-500/15 text-amber-400"
        : estado === "COLLAPSED" ||
            estado ===
              "FAILED"
          ? "bg-red-500/15 text-red-400"
          : "bg-slate-500/15 text-slate-300";

  return (
    <span
      className={`rounded-full px-2 py-1 text-[10px] font-semibold ${estilos}`}
    >
      {nombreEstado(
        estado,
      )}
    </span>
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
      <p className="text-[9px] uppercase text-slate-500">
        {label}
      </p>

      <p className="mt-1 font-semibold text-slate-200">
        {value}
      </p>
    </div>
  );
}

function nombreTipoEscenario(
  tipo:
    MonitoreoResponseDto["escenario"]["tipo"],
) {
  switch (tipo) {
    case "DAY_TO_DAY":
      return "Día a Día";

    case "FIVE_DAY":
      return "Simulación 5D";

    case "COLLAPSE":
      return "Colapso";
  }
}

function nombreEstado(
  estado:
    MonitoreoResponseDto["escenario"]["estado"],
) {
  switch (estado) {
    case "CREATED":
      return "Creado";

    case "RUNNING":
      return "En ejecución";

    case "PAUSED":
      return "Pausado";

    case "STOPPED":
      return "Detenido";

    case "COMPLETED":
      return "Completado";

    case "COLLAPSED":
      return "Colapsado";

    case "FAILED":
      return "Error";
  }
}

function formatearInstante(
  instante: string,
) {
  return new Intl.DateTimeFormat(
    "es-PE",
    {
      timeZone:
        "America/Lima",

      day: "2-digit",
      month: "2-digit",
      year: "numeric",

      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",

      hour12: false,
    },
  ).format(
    new Date(instante),
  );
}