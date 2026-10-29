import {
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import {
  AlertTriangle,
  BarChart3,
  CirclePause,
  CirclePlay,
  Loader2,
  OctagonX,
  PackagePlus,
  ServerCrash,
  Wrench,
} from "lucide-react";

import {
  useNavigate,
  useParams,
} from "react-router-dom";

import {
  useEventosEjecucion,
} from "@/hooks/useEventosEjecucion";

import type {
  MonitoreoResponseDto,
} from "@/api/monitoreo.dto";

import type {
  PlanResponseDto,
  ReplanificacionResponseDto,
} from "@/api/planificacion.dto";

import type {
  RegistrarAveriaRequestDto,
} from "@/api/incidencias.dto";

import type {
  RegistrarPedidoRequestDto,
} from "@/api/pedidos.dto";

import type {
  TipoVehiculoBackend,
  TipoVehiculoResponseDto,
} from "@/api/flota.dto";

import {
  ApiError,
} from "@/services/api";

import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import { useToast } from "@/components/feedback/ToastProvider";
import { mensajeDeError } from "@/lib/api-error";

import {
  escenariosService,
} from "@/services/escenarios.service";

import {
  flotaService,
} from "@/services/flota.service";

import {
  incidenciasService,
} from "@/services/incidencias.service";

import {
  adaptarAlmacenesMonitoreo,
  adaptarVehiculosMonitoreo,
} from "@/services/monitoreo.adapter";

import {
  monitoreoService,
} from "@/services/monitoreo.service";

import {
  adaptarPedido,
  adaptarPedidos,
} from "@/services/pedidos.adapter";

import {
  pedidosService,
} from "@/services/pedidos.service";

import {
  adaptarRutas,
} from "@/services/planificacion.adapter";

import {
  planificacionService,
} from "@/services/planificacion.service";

import type {
  AlmacenMapa,
  IndicadoresOperacion,
  PedidoOperacion,
  VehiculoMapa,
} from "./ejecucion.types";

import {
  MapaOperacion,
} from "./MapaOperacion";

import {
  FeedEventos,
} from "./FeedEventos";

import {
  PanelIndicadores,
} from "./PanelIndicadores";

import {
  RegistrarAveriaForm,
} from "./RegistrarAveriaForm";

import {
  RegistrarPedidoForm,
} from "./RegistrarPedidoForm";

import {
  DetalleOperacionPanel,
  type SeleccionDetalleOperacion,
} from "./DetalleOperacionPanel";

import { ResultadoEscenarioModal } from "../ejecuciones/ResultadoEscenarioModal";

export default function EjecucionPage() {
  const {
    ejecucionId:
      ejecucionIdParam,
  } =
    useParams();

  const navigate =
    useNavigate();

  const { mostrarToast } = useToast();

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
    tiposFlota,
    setTiposFlota,
  ] =
    useState<
      TipoVehiculoResponseDto[]
    >([]);

  const [
    plan,
    setPlan,
  ] =
    useState<
      PlanResponseDto | null
    >(null);

  const [
    replanificacion,
    setReplanificacion,
  ] =
    useState<
      ReplanificacionResponseDto | null
    >(null);

  const [
    cargando,
    setCargando,
  ] =
    useState(
      true,
    );

  const [
    error,
    setError,
  ] =
    useState<
      string | null
    >(null);

  const [
    errorPlan,
    setErrorPlan,
  ] =
    useState<
      string | null
    >(null);

  const [
    accionando,
    setAccionando,
  ] =
    useState(
      false,
    );

  const [
    mostrarFormularioPedido,
    setMostrarFormularioPedido,
  ] =
    useState(
      false,
    );

  const [
    mostrarFormularioAveria,
    setMostrarFormularioAveria,
  ] =
    useState(
      false,
    );

  const [seleccionDetalle, setSeleccionDetalle] =
    useState<SeleccionDetalleOperacion | null>(null);

  const [tipoActualizando, setTipoActualizando] =
    useState<TipoVehiculoBackend | null>(null);

  const [confirmarDetencion, setConfirmarDetencion] =
    useState(false);

  const [mostrarResultado, setMostrarResultado] =
    useState(false);

  const estadoEnCursoRef =
    useRef(false);

  const planEnCursoRef =
    useRef(false);

  const ultimoRefrescoPedidosRef =
    useRef(0);

  /*
   * =====================================================
   * POLLING
   * =====================================================
   */

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

      setCargando(
        false,
      );

      return;
    }

    void cargarDatosIniciales();

    /*
     * Monitoreo:
     * reloj, vehículos, almacenes,
     * métricas, incidencias.
     */
    const intervaloEstado =
      window.setInterval(
        () => {
          void cargarEstado(
            false,
          );
        },
        1000,
      );

    /*
     * Plan SA y última
     * replanificación.
     */
    const intervaloPlan =
      window.setInterval(
        () => {
          void cargarPlan();
        },
        2000,
      );

    return () => {
      window.clearInterval(
        intervaloEstado,
      );

      window.clearInterval(
        intervaloPlan,
      );
    };
  }, [
    ejecucionId,
  ]);

  async function cargarDatosIniciales() {
    try {
      setCargando(
        true,
      );

      setError(
        null,
      );

      const [
        tipos,
      ] =
        await Promise.all([
          flotaService.listarTipos(),

          cargarEstado(
            false,
            true,
          ),

          cargarPlan(),
        ]);

      setTiposFlota(
        tipos,
      );
    } catch (
      error
    ) {
      manejarErrorEstado(
        error,
      );
    } finally {
      setCargando(
        false,
      );
    }
  }

  async function cargarEstado(
    mostrarCarga =
      false,

    forzarPedidos =
      false,
  ) {
    if (
      estadoEnCursoRef
        .current
    ) {
      return;
    }

    estadoEnCursoRef.current =
      true;

    try {
      if (
        mostrarCarga
      ) {
        setCargando(
          true,
        );
      }

      const respuesta =
        await monitoreoService.obtener(
          ejecucionId,
        );

      setError(
        null,
      );

      setMonitoreo(
        respuesta,
      );

      setAlmacenes(
        adaptarAlmacenesMonitoreo(
          respuesta.almacenes,
        ),
      );

      setVehiculos(
        adaptarVehiculosMonitoreo(
          respuesta.vehiculos,
        ),
      );

      const ahora =
        Date.now();

      /*
       * Pedidos no necesitan
       * polling cada segundo.
       */
      if (
        forzarPedidos ||
        ahora -
          ultimoRefrescoPedidosRef.current >=
          2000
      ) {
        const respuestaPedidos =
          await pedidosService.listarTodos(
            {
              /*
               * Evita mostrar pedidos
               * históricos de otras
               * simulaciones.
               */
              registradoDesde:
                respuesta
                  .escenario
                  .inicioSimulado,

              registradoHasta:
                respuesta
                  .instante,

              sort:
                "registradoEn,asc",
            },
          );

        setPedidos(
          adaptarPedidos(
            respuestaPedidos,
          ),
        );

        ultimoRefrescoPedidosRef.current =
          ahora;
      }

      setError(
        null,
      );
    } catch (
      error
    ) {
      manejarErrorEstado(
        error,
      );
    } finally {
      estadoEnCursoRef.current =
        false;

      if (
        mostrarCarga
      ) {
        setCargando(
          false,
        );
      }
    }
  }

  async function cargarPlan() {
    if (
      planEnCursoRef
        .current
    ) {
      return;
    }

    planEnCursoRef.current =
      true;

    try {
      const [
        resultadoPlan,
        resultadoReplanificacion,
      ] =
        await Promise.allSettled(
          [
            planificacionService.obtenerPlan(
              ejecucionId,
            ),

            planificacionService.obtenerReplanificacion(
              ejecucionId,
            ),
          ],
        );

      if (
        resultadoPlan.status ===
        "fulfilled"
      ) {
        setPlan(
          resultadoPlan.value,
        );

        setErrorPlan(
          null,
        );
      } else {
        const motivo =
          resultadoPlan.reason;

        if (
          motivo instanceof
            ApiError &&
          motivo.status ===
            404
        ) {
          setPlan(
            null,
          );

          setErrorPlan(
            null,
          );
        } else {
          setErrorPlan(
            mensajeError(
              motivo,

              "No fue posible consultar el plan.",
            ),
          );
        }
      }

      if (
        resultadoReplanificacion.status ===
        "fulfilled"
      ) {
        setReplanificacion(
          resultadoReplanificacion.value,
        );
      } else {
        const motivo =
          resultadoReplanificacion.reason;

        if (
          motivo instanceof
            ApiError &&
          motivo.status ===
            404
        ) {
          setReplanificacion(
            null,
          );
        }
      }
    } finally {
      planEnCursoRef.current =
        false;
    }
  }

  function manejarErrorEstado(
    error:
      unknown,
  ) {
    console.error(
      "Error consultando ejecución:",
      error,
    );

    setError(
      mensajeError(
        error,

        "No fue posible consultar el estado de la ejecución.",
      ),
    );
  }

  /*
   * =====================================================
   * PEDIDOS
   * =====================================================
   */

  async function handleRegistrarPedido(
    request:
      RegistrarPedidoRequestDto,
  ) {
    const respuesta =
      await pedidosService.registrar(
        {
          ...request,

          registradoEn:
            monitoreo
              ?.instante,
        },
      );

    setPedidos(
      (actuales) => [
        ...actuales,

        adaptarPedido(
          respuesta,
        ),
      ],
    );

    setMostrarFormularioPedido(
      false,
    );

    mostrarToast({
      titulo: "Pedido registrado",
      descripcion: `El pedido ${respuesta.id} fue guardado y provocará una replanificación.`,
      variante: "success",
    });

    ultimoRefrescoPedidosRef.current =
      0;

    await cargarEstado(
      false,
      true,
    );

    /*
     * Backend dispara
     * NUEVO_PEDIDO → SA.
     *
     * Esperamos un poco
     * antes de refrescar
     * el plan visual.
     */
    window.setTimeout(
      () => {
        void cargarPlan();
      },
      1200,
    );
  }

  /*
   * =====================================================
   * AVERÍAS
   * =====================================================
   */

  async function handleRegistrarAveria(
    request:
      RegistrarAveriaRequestDto,
  ) {
    await incidenciasService.registrarAveria(
      request,
    );

    setMostrarFormularioAveria(
      false,
    );

    mostrarToast({
      titulo: "Avería registrada",
      descripcion: "El backend calculó la posición real de la unidad y solicitó una replanificación.",
      variante: "warning",
    });

    await cargarEstado(
      false,
      true,
    );

    window.setTimeout(
      () => {
        void cargarPlan();
      },
      1200,
    );
  }

  /*
   * =====================================================
   * CONTROLES DE ESCENARIO
   * =====================================================
   */

  async function ejecutarAccion(
    accion:
      | "PAUSAR"
      | "REANUDAR"
      | "DETENER",
  ) {
    try {
      setAccionando(
        true,
      );

      setError(
        null,
      );

      switch (
        accion
      ) {
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

      if (accion === "DETENER") {
        setConfirmarDetencion(false);
      }

      mostrarToast({
        titulo:
          accion === "PAUSAR"
            ? "Simulación pausada"
            : accion === "REANUDAR"
              ? "Simulación reanudada"
              : "Simulación detenida",
        variante: "success",
      });

      await cargarEstado(
        false,
        true,
      );

      await cargarPlan();
    } catch (
      error
    ) {
      const mensaje = mensajeDeError(
        error,
        "No fue posible cambiar el estado del escenario.",
      );
      setError(mensaje);
      mostrarToast({ titulo: "No se pudo completar la acción", descripcion: mensaje, variante: "error" });
    } finally {
      setAccionando(
        false,
      );
    }
  }

  async function handleActualizarVelocidad(
    tipoVehiculo: TipoVehiculoBackend,
    velocidadKmh: number,
  ) {
    const actual = tiposFlota.find((tipoFlota) => tipoFlota.tipo === tipoVehiculo);
    if (!actual) {
      throw new Error("No se encontró la configuración del tipo de vehículo.");
    }

    try {
      setTipoActualizando(tipoVehiculo);
      const actualizado = await flotaService.actualizarTipo(tipoVehiculo, {
        capacidadPaquetes: actual.capacidadPaquetes,
        velocidadKmh,
        costoPorKm: actual.costoPorKm,
      });

      setTiposFlota((tipos) =>
        tipos.map((tipoFlota) =>
          tipoFlota.tipo === tipoVehiculo ? actualizado : tipoFlota,
        ),
      );

      mostrarToast({
        titulo: "Velocidad actualizada",
        descripcion: `${nombreTipoVehiculo(tipoVehiculo)}: ${velocidadKmh} km/h. El backend replanificará con el nuevo perfil.`,
        variante: "success",
      });

      window.setTimeout(() => void cargarPlan(), 1200);
    } catch (cause) {
      const mensaje = mensajeDeError(cause, "No fue posible actualizar la velocidad.");
      mostrarToast({ titulo: "Velocidad rechazada", descripcion: mensaje, variante: "error" });
      throw cause;
    } finally {
      setTipoActualizando(null);
    }
  }

  /*
   * =====================================================
   * FEED DE EVENTOS
   * =====================================================
   */

  const eventos =
    useEventosEjecucion(
      monitoreo,
      pedidos,
      vehiculos,
      replanificacion,
    );

  /*
   * El mapa necesita TODAS
   * las paradas del plan,
   * incluso las ya visitadas,
   * para diferenciar:
   *
   * realizado / actual / futuro.
   */
  const rutas =
    useMemo(
      () =>
        adaptarRutas(
          plan,
        ),

      [plan],
    );

  /*
   * =====================================================
   * ESTADOS DE CARGA
   * =====================================================
   */

  if (
    cargando
  ) {
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

  if (
    !monitoreo
  ) {
    return null;
  }

  /*
   * =====================================================
   * PERMISOS POR ESCENARIO
   * =====================================================
   */

  const estado =
    monitoreo
      .escenario
      .estado;

  const tipo =
    monitoreo
      .escenario
      .tipo;

  const puedeRegistrarPedido =
    tipo ===
      "DAY_TO_DAY" &&
    estado ===
      "RUNNING";

  const puedeRegistrarAveria =
    estado ===
      "RUNNING" &&
    (
      tipo ===
        "DAY_TO_DAY" ||
      tipo ===
        "FIVE_DAY"
    );

  /*
   * =====================================================
   * INDICADORES REALES DEL BACKEND
   * =====================================================
   */

  const indicadores:
    IndicadoresOperacion =
    {
      costoAcumulado:
        monitoreo
          .ejecucion
          .costo,

      distanciaRecorrida:
        monitoreo
          .ejecucion
          .distanciaKm,

      entregasCompletadas:
        monitoreo
          .ejecucion
          .pedidosEntregados,

      entregasATiempo:
        monitoreo
          .ejecucion
          .entregadosATiempo,

      paquetesEntregados:
        monitoreo
          .ejecucion
          .paquetesEntregados ?? 0,

      pedidosRiesgo:
        monitoreo
          .pedidos
          .vencidosSinEntregar,
    };

  /*
   * =====================================================
   * PANTALLA
   * =====================================================
   */

  return (
    <div className="grid h-screen grid-cols-[minmax(0,1fr)_360px] overflow-hidden bg-[#0d1117]">

      <div className="relative min-w-0">

        {/* MAPA KONVA */}
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
          rutas={
            rutas
          }
          bloqueos={
            monitoreo
              .bloqueosVigentes
          }
          averias={
            monitoreo
              .averiasActivas
          }
          instanteSimulado={
            monitoreo
              .instante
          }
          factorAceleracion={
            monitoreo
              .escenario
              .factorAceleracion
          }
          estadoEscenario={
            monitoreo
              .escenario
              .estado
          }
          onSeleccionarVehiculo={(id) => setSeleccionDetalle({ tipo: "VEHICULO", id })}
          onSeleccionarPedido={(id) => setSeleccionDetalle({ tipo: "PEDIDO", id })}
          onSeleccionarAlmacen={(id) => setSeleccionDetalle({ tipo: "ALMACEN", id })}
        />

        {/* RELOJ + CONTROLES */}
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
            setConfirmarDetencion(true)
          }
        />

        {/* ACCIONES */}
        <div className="absolute right-5 top-16 z-40 flex flex-col gap-2">

          {puedeRegistrarPedido && (
            <button
              type="button"
              onClick={() => {
                setMostrarFormularioAveria(
                  false,
                );

                setMostrarFormularioPedido(
                  true,
                );
              }}
              className="flex items-center justify-center gap-2 rounded-md bg-orange-500 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-orange-600"
            >
              <PackagePlus className="size-4" />

              Registrar pedido
            </button>
          )}

          {puedeRegistrarAveria && (
            <button
              type="button"
              onClick={() => {
                setMostrarFormularioPedido(
                  false,
                );

                setMostrarFormularioAveria(
                  true,
                );
              }}
              className="flex items-center justify-center gap-2 rounded-md bg-red-800 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-red-700"
            >
              <Wrench className="size-4" />

              Registrar avería
            </button>
          )}

          {["STOPPED", "COMPLETED", "COLLAPSED", "FAILED"].includes(estado) && (
            <button
              type="button"
              onClick={() => setMostrarResultado(true)}
              className="flex items-center justify-center gap-2 rounded-md border border-orange-700 bg-[#11161d] px-4 py-2 text-sm font-semibold text-orange-300 shadow hover:bg-orange-950/50"
            >
              <BarChart3 className="size-4" />
              Ver resultado
            </button>
          )}
        </div>

        {/* FORM PEDIDO */}
        {mostrarFormularioPedido &&
          puedeRegistrarPedido && (
            <RegistrarPedidoForm
              onRegistrar={
                handleRegistrarPedido
              }
              onCerrar={() =>
                setMostrarFormularioPedido(
                  false,
                )
              }
            />
          )}

        {/* FORM AVERÍA */}
        {mostrarFormularioAveria &&
          puedeRegistrarAveria && (
            <RegistrarAveriaForm
              vehiculos={
                vehiculos
              }
              ejecucionId={
                ejecucionId
              }
              onRegistrar={
                handleRegistrarAveria
              }
              onCerrar={() =>
                setMostrarFormularioAveria(
                  false,
                )
              }
            />
          )}

        <DetalleOperacionPanel
          seleccion={seleccionDetalle}
          ejecucionId={ejecucionId}
          instante={monitoreo.instante}
          onCerrar={() => setSeleccionDetalle(null)}
        />

        {/* ERROR */}
        {error && (
          <div className="absolute left-5 top-44 z-50 max-w-sm rounded-md border border-red-900 bg-[#11161d] px-3 py-2 text-xs text-red-400 shadow">
            {error}
          </div>
        )}

        {/* COLAPSO */}
        {monitoreo
          .colapso && (
          <div className="absolute left-5 top-44 z-40 max-w-md rounded-md border border-red-700 bg-red-950/90 p-3 text-xs text-red-100 shadow-xl">

            <div className="flex items-center gap-2 font-semibold">
              <AlertTriangle className="size-4" />

              Colapso detectado
            </div>

            <p className="mt-1">
              Pedido{" "}
              {
                monitoreo
                  .colapso
                  .pedidoId
              }{" "}
              ·{" "}
              {nombreCausaColapso(
                monitoreo
                  .colapso
                  .causa,
              )}
            </p>
          </div>
        )}

        {/* FEED DE EVENTOS */}
        <FeedEventos
          eventos={
            eventos
          }
        />

        {/* RESUMEN OPERATIVO */}
        <EstadoOperativo
          monitoreo={
            monitoreo
          }
          rutas={
            rutas.length
          }
          replanificacion={
            replanificacion
          }
        />
      </div>

      {/* PANEL DERECHO */}
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
        tiposFlota={
          tiposFlota
        }
        plan={
          plan
        }
        replanificacion={
          replanificacion
        }
        errorPlan={
          errorPlan
        }
        permitirCambioVelocidad={estado === "RUNNING" || estado === "PAUSED"}
        tipoActualizando={tipoActualizando}
        onActualizarVelocidad={handleActualizarVelocidad}
        onSeleccionarAlmacen={(id) => setSeleccionDetalle({ tipo: "ALMACEN", id })}
      />

      <ConfirmDialog
        abierto={confirmarDetencion}
        titulo="Detener simulación"
        descripcion="El escenario se cerrará y el backend consolidará el resultado final."
        textoConfirmar="Detener"
        variante="danger"
        confirmando={accionando}
        onCancelar={() => setConfirmarDetencion(false)}
        onConfirmar={() => void ejecutarAccion("DETENER")}
      />

      <ResultadoEscenarioModal
        ejecucionId={mostrarResultado ? ejecucionId : null}
        onCerrar={() => setMostrarResultado(false)}
      />
    </div>
  );
}

/*
 * =========================================================
 * PANEL ESCENARIO
 * =========================================================
 */

function PanelEscenario({
  monitoreo,
  accionando,
  onPausar,
  onReanudar,
  onDetener,
}: {
  monitoreo:
    MonitoreoResponseDto;

  accionando:
    boolean;

  onPausar:
    () => void;

  onReanudar:
    () => void;

  onDetener:
    () => void;
}) {
  const estado =
    monitoreo
      .escenario
      .estado;

  return (
    <div className="absolute left-5 top-5 z-50 min-w-80 rounded-lg border border-slate-700 bg-[#11161d]/95 p-4 text-white shadow-xl">

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
              monitoreo
                .instante,
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
        Hora simulada · Lima ·{" "}
        {
          monitoreo
            .escenario
            .factorAceleracion
        }
        ×
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

/*
 * =========================================================
 * ESTADO OPERATIVO
 * =========================================================
 */

function EstadoOperativo({
  monitoreo,
  rutas,
  replanificacion,
}: {
  monitoreo:
    MonitoreoResponseDto;

  rutas:
    number;

  replanificacion:
    ReplanificacionResponseDto | null;
}) {
  return (
    <div className="absolute bottom-5 left-5 z-40 w-[410px] rounded-lg border border-slate-700 bg-[#11161d]/95 p-4 text-white shadow-xl">

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

      <div className="mt-4 grid grid-cols-4 gap-2 text-xs">

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
          label="Entregados"
          value={String(
            monitoreo
              .ejecucion
              .pedidosEntregados,
          )}
        />

        <Dato
          label="Rutas"
          value={String(
            rutas,
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
          label="Replanes"
          value={String(
            replanificacion
              ?.numeroReplanificacion ??
            0,
          )}
        />
      </div>

      <p className="mt-3 rounded-md bg-slate-800 p-2 text-[11px] text-slate-400">
        El mapa combina
        monitoreo, pedidos del
        periodo y el último
        plan vigente de SA.
      </p>
    </div>
  );
}

/*
 * =========================================================
 * BADGE
 * =========================================================
 */

function EstadoBadge({
  estado,
}: {
  estado:
    MonitoreoResponseDto[
      "escenario"
    ][
      "estado"
    ];
}) {
  const estilos =
    estado ===
    "RUNNING"
      ? "bg-green-500/15 text-green-400"

      : estado ===
        "PAUSED"
        ? "bg-amber-500/15 text-amber-400"

        : estado ===
            "COLLAPSED" ||
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

/*
 * =========================================================
 * DATO PEQUEÑO
 * =========================================================
 */

function Dato({
  label,
  value,
}: {
  label:
    string;

  value:
    string;
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

/*
 * =========================================================
 * LABELS
 * =========================================================
 */

function nombreTipoEscenario(
  tipo:
    MonitoreoResponseDto[
      "escenario"
    ][
      "tipo"
    ],
) {
  switch (
    tipo
  ) {
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
    MonitoreoResponseDto[
      "escenario"
    ][
      "estado"
    ],
) {
  switch (
    estado
  ) {
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

function nombreCausaColapso(
  causa:
    NonNullable<
      MonitoreoResponseDto[
        "colapso"
      ]
    >[
      "causa"
    ],
) {
  switch (
    causa
  ) {
    case "PLAZO_VENCIDO":
      return "plazo vencido";

    case "PLAZO_INALCANZABLE":
      return "plazo inalcanzable";
  }
}

/*
 * =========================================================
 * FORMATOS
 * =========================================================
 */

function formatearInstante(
  instante:
    string,
) {
  return new Intl.DateTimeFormat(
    "es-PE",
    {
      timeZone:
        "America/Lima",

      day:
        "2-digit",

      month:
        "2-digit",

      year:
        "numeric",

      hour:
        "2-digit",

      minute:
        "2-digit",

      second:
        "2-digit",

      hour12:
        false,
    },
  ).format(
    new Date(
      instante,
    ),
  );
}

/*
 * =========================================================
 * ERROR
 * =========================================================
 */

function nombreTipoVehiculo(tipo: TipoVehiculoBackend) {
  switch (tipo) {
    case "CAR":
      return "Autos";
    case "MOTORCYCLE":
      return "Motos";
    case "BICYCLE":
      return "Bicicletas";
  }
}

function mensajeError(
  error:
    unknown,

  fallback:
    string,
) {
  if (
    error instanceof
    ApiError
  ) {
    return error.message;
  }

  if (
    error instanceof
    Error
  ) {
    return error.message;
  }

  return fallback;
}