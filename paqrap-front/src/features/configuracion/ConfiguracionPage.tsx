import {
  useEffect,
  useState,
} from "react";

import {
  useNavigate,
} from "react-router-dom";

import {
  CircleStop,
  ExternalLink,
  Loader2,
  TriangleAlert,
} from "lucide-react";

import {
  Button,
} from "@/components/ui/button";

import {
  useConfiguracion,
} from "@/context/ConfiguracionContext";

import type {
  EscenarioResponseDto,
  TipoEscenarioBackend,
} from "@/api/escenarios.dto";

import {
  escenariosService,
} from "@/services/escenarios.service";

import {
  pedidosService,
} from "@/services/pedidos.service";

import {
  ApiError,
} from "@/services/api";

import {
  OrigenDatos,
} from "./OrigenDatos";

import {
  ParametrosOperacion,
} from "./ParametrosOperacion";

import {
  FlotaConfiguracion,
} from "./FlotaConfiguracion";

import {
  SemaforoConfiguracion,
} from "./SemaforoConfiguracion";

import {
  ResumenConfiguracion,
} from "./ResumenConfiguracion";

export default function ConfiguracionPage() {
  const navigate =
    useNavigate();

  const {
    configuracion,
    archivoPedidos,
  } = useConfiguracion();

  const [
    iniciando,
    setIniciando,
  ] = useState(false);

  const [
    error,
    setError,
  ] = useState<
    string | null
  >(null);

  const [
    mensaje,
    setMensaje,
  ] = useState<
    string | null
  >(null);

  const [
    escenarioActivo,
    setEscenarioActivo,
  ] =
    useState<
      EscenarioResponseDto | null
    >(null);

  const [
    verificandoActivo,
    setVerificandoActivo,
  ] = useState(true);

  const [
    deteniendo,
    setDeteniendo,
  ] = useState(false);

  const necesitaArchivo =
    configuracion.escenario !==
    "DIA_A_DIA";

  useEffect(() => {
    void cargarEscenarioActivo();
  }, []);

  async function buscarEscenarioActivo():
    Promise<
      EscenarioResponseDto | null
    > {
    const pagina =
      await escenariosService.listar(
        0,
        100,
      );

    return (
      pagina.contenido.find(
        (escenario) =>
          escenario.estado ===
            "RUNNING" ||
          escenario.estado ===
            "PAUSED",
      ) ?? null
    );
  }

  async function cargarEscenarioActivo() {
    try {
      setVerificandoActivo(
        true,
      );

      const activo =
        await buscarEscenarioActivo();

      setEscenarioActivo(
        activo,
      );
    } catch (error) {
      console.error(
        "No se pudo consultar el escenario activo:",
        error,
      );
    } finally {
      setVerificandoActivo(
        false,
      );
    }
  }

  async function handleDetenerActivo() {
    if (!escenarioActivo) {
      return;
    }

    try {
      setDeteniendo(true);
      setError(null);
      setMensaje(null);

      const id =
        escenarioActivo.id;

      await escenariosService.detener(
        id,
      );

      setEscenarioActivo(
        null,
      );

      setMensaje(
        `La ejecución #${id} fue detenida correctamente.`,
      );
    } catch (error) {
      manejarError(
        error,
        "No fue posible detener la simulación activa.",
      );
    } finally {
      setDeteniendo(false);
    }
  }

  function handleIrAEjecucion() {
    if (!escenarioActivo) {
      return;
    }

    navigate(
      `/ejecucion/${escenarioActivo.id}`,
    );
  }

  function validarConfiguracion():
    string | null {
    /*
     * Día a Día no requiere
     * archivo ni fecha.
     */
    if (
      configuracion.escenario ===
      "DIA_A_DIA"
    ) {
      return null;
    }

    if (!archivoPedidos) {
      return "Selecciona el archivo de pedidos antes de iniciar.";
    }

    const match =
      /^ventas\.(\d{4})(\d{2})\.txt$/i.exec(
        archivoPedidos.name,
      );

    if (!match) {
      return "El archivo debe tener el nombre ventas.AAAAMM.txt.";
    }

    if (
      !configuracion.inicioSimulado
    ) {
      return "Selecciona la fecha y hora inicial de la simulación.";
    }

    /*
     * Evitamos el error más frecuente:
     *
     * archivo de enero
     * +
     * simulación iniciada en octubre.
     */
    const anioArchivo =
      Number(match[1]);

    const mesArchivo =
      Number(match[2]);

    const fecha =
      new Date(
        `${normalizarFechaLocal(
          configuracion.inicioSimulado,
        )}-05:00`,
      );

    if (
      Number.isNaN(
        fecha.getTime(),
      )
    ) {
      return "La fecha inicial no es válida.";
    }

    /*
     * Convertimos nuevamente a hora de
     * Lima para verificar el periodo.
     */
    const partes =
      new Intl.DateTimeFormat(
        "en-CA",
        {
          timeZone:
            "America/Lima",

          year: "numeric",
          month: "2-digit",
        },
      ).formatToParts(
        fecha,
      );

    const anioInicio =
      Number(
        partes.find(
          (parte) =>
            parte.type ===
            "year",
        )?.value,
      );

    const mesInicio =
      Number(
        partes.find(
          (parte) =>
            parte.type ===
            "month",
        )?.value,
      );

    if (
      anioInicio !==
        anioArchivo ||
      mesInicio !==
        mesArchivo
    ) {
      return `La simulación debe comenzar dentro del periodo del archivo: ${anioArchivo}-${String(
        mesArchivo,
      ).padStart(2, "0")}.`;
    }

    return null;
  }

  async function handleIniciarSimulacion() {
    const errorConfiguracion =
      validarConfiguracion();

    if (errorConfiguracion) {
      setError(
        errorConfiguracion,
      );

      return;
    }

    try {
      setIniciando(true);
      setError(null);
      setMensaje(null);

      /*
       * Revisión final para evitar
       * escenarios CREATED huérfanos.
       */
      const activo =
        await buscarEscenarioActivo();

      if (activo) {
        setEscenarioActivo(
          activo,
        );

        setError(
          "Ya existe una simulación activa.",
        );

        return;
      }

      /*
       * Para 5D y Colapso cargamos
       * primero los datos históricos.
       *
       * El backend evita duplicarlos
       * si el mismo archivo ya fue
       * cargado anteriormente.
       */
      if (
        necesitaArchivo &&
        archivoPedidos
      ) {
        const carga =
          await pedidosService.cargarArchivo(
            archivoPedidos,
          );

        console.log(
          "Carga de pedidos:",
          carga,
        );
      }

      const tipo =
        convertirEscenario(
          configuracion.escenario,
        );

      const request =
        tipo === "DAY_TO_DAY"
          ? {
              tipo,
            }
          : {
              tipo,

              /*
               * El archivo se interpreta
               * en America/Lima.
               *
               * Forzamos -05:00 para que
               * la simulación use la misma
               * zona horaria independientemente
               * del navegador.
               */
              inicioSimulado:
                convertirHoraLimaAIso(
                  configuracion.inicioSimulado,
                ),
            };

      const creado =
        await escenariosService.crear(
          request,
        );

      const iniciado =
        await escenariosService.iniciar(
          creado.id,
        );

      navigate(
        `/ejecucion/${iniciado.id}`,
      );
    } catch (error) {
      console.error(
        "No se pudo iniciar la simulación:",
        error,
      );

      if (
        error instanceof ApiError &&
        error.status === 409
      ) {
        try {
          const activo =
            await buscarEscenarioActivo();

          setEscenarioActivo(
            activo,
          );
        } catch {
          // Conservamos el error original.
        }
      }

      manejarError(
        error,
        "No fue posible iniciar la simulación.",
      );
    } finally {
      setIniciando(false);
    }
  }

  function manejarError(
    error: unknown,
    fallback: string,
  ) {
    if (
      error instanceof ApiError
    ) {
      setError(
        error.message,
      );

      return;
    }

    if (
      error instanceof Error
    ) {
      setError(
        error.message,
      );

      return;
    }

    setError(
      fallback,
    );
  }

  const puedeIniciar =
    !iniciando &&
    !verificandoActivo &&
    !escenarioActivo &&
    validarConfiguracion() ===
      null;

  return (
    <div className="flex min-h-full flex-col">
      <div className="grid flex-1 gap-3 p-4 xl:grid-cols-[250px_minmax(0,1fr)_270px]">
        <OrigenDatos />

        <section className="space-y-3">
          <ParametrosOperacion />

          <FlotaConfiguracion />

          <SemaforoConfiguracion />
        </section>

        <ResumenConfiguracion />
      </div>

      {verificandoActivo && (
        <div className="mx-4 mb-3 flex items-center gap-2 rounded-md border border-slate-700 bg-[#151b23] px-4 py-3 text-sm text-slate-300">
          <Loader2 className="size-4 animate-spin" />

          Verificando si existe una simulación activa...
        </div>
      )}

      {escenarioActivo && (
        <div className="mx-4 mb-3 rounded-lg border border-amber-700/70 bg-amber-950/20 p-4">
          <div className="flex flex-col justify-between gap-4 md:flex-row md:items-center">
            <div className="flex items-start gap-3">
              <TriangleAlert className="mt-0.5 size-5 shrink-0 text-amber-400" />

              <div>
                <p className="font-semibold text-amber-300">
                  Ya existe una simulación activa
                </p>

                <p className="mt-1 text-sm text-slate-400">
                  Ejecución #
                  {
                    escenarioActivo.id
                  }
                  {" · "}
                  {nombreTipoEscenario(
                    escenarioActivo.tipo,
                  )}
                  {" · "}
                  {nombreEstado(
                    escenarioActivo.estado,
                  )}
                </p>

                <p className="mt-1 text-xs text-slate-500">
                  Puedes volver a la
                  ejecución actual o
                  detenerla antes de
                  iniciar otra.
                </p>
              </div>
            </div>

            <div className="flex shrink-0 gap-2">
              <button
                type="button"
                onClick={
                  handleIrAEjecucion
                }
                className="flex items-center gap-2 rounded-md border border-slate-600 bg-slate-800 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
              >
                <ExternalLink className="size-4" />

                Ir a ejecución
              </button>

              <button
                type="button"
                disabled={
                  deteniendo
                }
                onClick={() =>
                  void handleDetenerActivo()
                }
                className="flex items-center gap-2 rounded-md bg-red-700 px-4 py-2 text-sm font-semibold text-white hover:bg-red-600 disabled:opacity-50"
              >
                {deteniendo ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <CircleStop className="size-4" />
                )}

                {deteniendo
                  ? "Deteniendo..."
                  : "Detener simulación"}
              </button>
            </div>
          </div>
        </div>
      )}

      {mensaje && (
        <div className="mx-4 mb-3 rounded-md border border-green-900 bg-green-950/30 px-4 py-3 text-sm text-green-400">
          {mensaje}
        </div>
      )}

      {error && (
        <div className="mx-4 mb-3 rounded-md border border-red-900 bg-red-950/40 px-4 py-3 text-sm text-red-400">
          {error}
        </div>
      )}

      <div className="sticky bottom-0 flex items-center justify-between border-t border-slate-800 bg-[#0d1117] px-4 py-3">
        <div className="text-xs text-slate-500">
          {escenarioActivo
            ? "Existe una ejecución activa."
            : "Listo para iniciar una ejecución."}
        </div>

        <Button
          type="button"
          disabled={
            !puedeIniciar
          }
          onClick={() =>
            void handleIniciarSimulacion()
          }
          className="bg-[#ff6b35] text-white hover:bg-[#ff7b4d]"
        >
          {iniciando ? (
            <>
              <Loader2 className="mr-2 size-4 animate-spin" />

              Iniciando...
            </>
          ) : (
            "Iniciar simulación"
          )}
        </Button>
      </div>
    </div>
  );
}

function convertirEscenario(
  escenario:
    | "DIA_A_DIA"
    | "SIMULACION_5D"
    | "COLAPSO",
): TipoEscenarioBackend {
  switch (escenario) {
    case "DIA_A_DIA":
      return "DAY_TO_DAY";

    case "SIMULACION_5D":
      return "FIVE_DAY";

    case "COLAPSO":
      return "COLLAPSE";
  }
}

function nombreTipoEscenario(
  tipo: TipoEscenarioBackend,
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
  estado: EscenarioResponseDto["estado"],
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

/**
 * datetime-local normalmente tiene:
 *
 * YYYY-MM-DDTHH:mm
 *
 * Agregamos segundos si faltan.
 */
function normalizarFechaLocal(
  valor: string,
) {
  return valor.length === 16
    ? `${valor}:00`
    : valor;
}

/**
 * Los archivos históricos de PaqRap
 * se interpretan en hora de Lima.
 *
 * Perú utiliza UTC-5 sin DST.
 */
function convertirHoraLimaAIso(
  valor: string,
) {
  const normalizado =
    normalizarFechaLocal(
      valor,
    );

  const fecha =
    new Date(
      `${normalizado}-05:00`,
    );

  if (
    Number.isNaN(
      fecha.getTime(),
    )
  ) {
    throw new Error(
      "La fecha inicial de la simulación no es válida.",
    );
  }

  return fecha.toISOString();
}