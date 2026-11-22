import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  ArrowRight,
  FlaskConical,
  LockKeyhole,
  Radio,
  ExternalLink,
  Loader2,
  RefreshCcw,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { useToast } from "@/components/feedback/ToastProvider";
import { useConfiguracion } from "@/context/ConfiguracionContext";
import { useEjecucionesActivas } from "@/context/EjecucionesActivasContext";
import { activaDelGrupo, nombreEstadoEjecucion, nombreTipoEjecucion } from "@/lib/ejecuciones";
import { claveUmbralesEjecucion, guardarUmbrales } from "./semaforo";

import type {
  CrearEscenarioRequestDto,
  TipoEscenarioBackend,
} from "@/api/escenarios.dto";

import type {
  TipoVehiculoBackend,
  VehiculoResponseDto,
} from "@/api/flota.dto";

import type { PeriodoCargadoResponseDto } from "@/api/pedidos.dto";

import { escenariosService } from "@/services/escenarios.service";
import { pedidosService } from "@/services/pedidos.service";
import { bloqueosService } from "@/services/bloqueos.service";
import { flotaService } from "@/services/flota.service";
import { mensajeDeError } from "@/lib/api-error";

import { OrigenDatos } from "./OrigenDatos";
import { ParametrosOperacion } from "./ParametrosOperacion";
import { FlotaConfiguracion } from "./FlotaConfiguracion";
import { SemaforoConfiguracion } from "./SemaforoConfiguracion";
import { AveriaConfiguracion } from "./AveriaConfiguracion";
import { ResumenConfiguracion } from "./ResumenConfiguracion";
import { ConfirmarInicioModal } from "./ConfirmarInicioModal";

import {
  archivoVentasEsperado,
  periodoDesdeArchivoBloqueos,
  periodoDesdeArchivoVentas,
  periodoLegible,
  periodosBloqueosDiaADia,
  periodosRequeridos,
} from "./periodos";

export default function ConfiguracionPage() {
  const navigate = useNavigate();
  const { mostrarToast } = useToast();

  const {
    configuracion,
    actualizarConfiguracion,
    archivosPedidos,
    archivosBloqueos,
  } = useConfiguracion();

  const { activas, operacion, simulacion, cargando: verificandoActivo, error: errorActividad, actualizar: actualizarActivas } = useEjecucionesActivas();
  const esOperacion = configuracion.escenario === "DIA_A_DIA";
  const escenarioActivo = esOperacion ? operacion : simulacion;
  const otraActiva = esOperacion ? simulacion : operacion;
  const flotaBloqueada = activas.length > 0 || verificandoActivo || !!errorActividad;
  const [iniciando, setIniciando] = useState(false);
  const inicioEnCurso = useRef(false);
  const [error, setError] = useState<string | null>(null);
  const [mostrarConfirmacion, setMostrarConfirmacion] = useState(false);
  const [periodosCargados, setPeriodosCargados] = useState<PeriodoCargadoResponseDto[]>([]);
  const [consultandoPeriodos, setConsultandoPeriodos] = useState(true);
  const [configuracionLista, setConfiguracionLista] = useState(false);
  const [recarga, setRecarga] = useState(0);
  const usaDatosHistoricos = !esOperacion;
  const mesesRequeridos = periodosRequeridos(configuracion.escenario, configuracion.inicioSimulado);
  const periodosArchivosPedidos = archivosPedidos.map((archivo) => periodoDesdeArchivoVentas(archivo.name));
  const periodosDisponibles = new Set(periodosCargados.map((item) => item.periodo));
  for (const periodo of periodosArchivosPedidos) {
    if (periodo) periodosDisponibles.add(periodo);
  }
  const mesesFaltantes = mesesRequeridos.filter((periodo) => !periodosDisponibles.has(periodo));
  const claveActivas = activas.map((ejecucion) => ejecucion.id).sort().join(",");

  useEffect(() => {
    let vigente = true;
    setConfiguracionLista(false);
    setConsultandoPeriodos(true);
    async function inicializar() {
      try {
        const [tipos, vehiculos, periodos] = await Promise.all([
          flotaService.listarTipos(), flotaService.listarVehiculos(), pedidosService.periodosCargados(),
        ]);
        if (!vigente) return;
        const porTipo = new Map(tipos.map((tipo) => [tipo.tipo, tipo]));
        actualizarConfiguracion({
          autosCantidad: contar(vehiculos, "CAR"), autosVelocidad: porTipo.get("CAR")?.velocidadKmh ?? 40,
          motosCantidad: contar(vehiculos, "MOTORCYCLE"), motosVelocidad: porTipo.get("MOTORCYCLE")?.velocidadKmh ?? 25,
          bicicletasCantidad: contar(vehiculos, "BICYCLE"), bicicletasVelocidad: porTipo.get("BICYCLE")?.velocidadKmh ?? 12,
        });
        setPeriodosCargados(periodos);
        setConfiguracionLista(true);
        setError(null);
      } catch (cause) {
        if (vigente) setError(mensajeDeError(cause, "No fue posible cargar la configuración inicial."));
      } finally {
        if (vigente) setConsultandoPeriodos(false);
      }
    }
    void inicializar();
    return () => { vigente = false; };
  }, [claveActivas, recarga, actualizarConfiguracion]);

  function validarConfiguracion():
    string | null {
    if (
      configuracion.autosCantidad < 1 ||
      configuracion.motosCantidad < 1 ||
      configuracion.bicicletasCantidad < 1
    ) {
      return "La flota debe tener al menos una unidad de cada tipo.";
    }

    if (
      configuracion.autosVelocidad <= 0 ||
      configuracion.motosVelocidad <= 0 ||
      configuracion.bicicletasVelocidad <= 0
    ) {
      return "Todas las velocidades deben ser mayores que cero.";
    }

    const umbrales = [
      configuracion.almacenVerdeHasta,
      configuracion.almacenAmbarHasta,
      configuracion.vehiculoVerdeHasta,
      configuracion.vehiculoAmbarHasta,
    ];

    if (
      umbrales.some(
        (valor) =>
          !Number.isFinite(valor) ||
          valor < 0 ||
          valor > 100,
      )
    ) {
      return "Los umbrales de semáforo deben estar entre 0 y 100%.";
    }

    if (
      configuracion.almacenVerdeHasta >=
        configuracion.almacenAmbarHasta ||
      configuracion.vehiculoVerdeHasta >=
        configuracion.vehiculoAmbarHasta
    ) {
      return "El límite verde debe ser menor que el límite ámbar.";
    }

    if (
      configuracion.usarConfiguracionSa
    ) {
      if (
        configuracion.saTemperaturaInicial <=
          0 ||
        configuracion.saTemperaturaMinima <=
          0 ||
        configuracion.saTemperaturaMinima >=
          configuracion.saTemperaturaInicial
      ) {
        return "La configuración avanzada requiere temperaturas positivas y Tmin menor que T0.";
      }

      if (
        configuracion.saFactorEnfriamiento <
          0.8 ||
        configuracion.saFactorEnfriamiento >
          0.99
      ) {
        return "El factor de enfriamiento debe estar entre 0.80 y 0.99.";
      }

      if (
        configuracion.saIteracionesPorTemperatura <=
        0
      ) {
        return "Las iteraciones por temperatura deben ser mayores que cero.";
      }

      if (
        configuracion.saSemilla.trim() &&
        !/^-?\d+$/.test(
          configuracion.saSemilla.trim(),
        )
      ) {
        return "La semilla debe ser un número entero.";
      }
    }

    if (
      configuracion.escenario ===
      "DIA_A_DIA"
    ) {
      return validarBloqueosDiaADia();
    }

    if (consultandoPeriodos) {
      return "Espera mientras se verifican los meses de pedidos cargados.";
    }

    if (
      !configuracion.inicioSimulado
    ) {
      return "Selecciona la fecha y hora inicial de la simulación.";
    }

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

    if (archivosPedidos.length > 2 || archivosBloqueos.length > 2) {
      return "Selecciona como máximo dos archivos de pedidos y dos de bloqueos.";
    }

    if (periodosArchivosPedidos.some((periodo) => !periodo)) {
      return "Los archivos de pedidos deben llamarse ventas.AAAAMM.txt.";
    }
    if (new Set(periodosArchivosPedidos).size !== periodosArchivosPedidos.length) {
      return "No puedes seleccionar dos archivos de pedidos del mismo mes.";
    }
    for (let i = 0; i < archivosPedidos.length; i++) {
      if (!mesesRequeridos.includes(periodosArchivosPedidos[i]!)) {
        return `El archivo ${archivosPedidos[i].name} no corresponde a los meses requeridos por el escenario.`;
      }
    }

    const periodosBloqueos = archivosBloqueos.map((archivo) =>
      periodoDesdeArchivoBloqueos(archivo.name),
    );
    if (periodosBloqueos.some((periodo) => !periodo)) {
      return "Los archivos de bloqueos deben llamarse bloqueo.AAMM.txt.";
    }
    if (new Set(periodosBloqueos).size !== periodosBloqueos.length) {
      return "No puedes seleccionar dos archivos de bloqueos del mismo mes.";
    }
    for (let i = 0; i < archivosBloqueos.length; i++) {
      if (!mesesRequeridos.includes(periodosBloqueos[i]!)) {
        return `El archivo ${archivosBloqueos[i].name} no corresponde a los meses requeridos por el escenario.`;
      }
    }

    if (
      mesesFaltantes.length >
      0
    ) {
      return mesesFaltantes
        .map(
          (periodo) =>
            `Falta ${periodoLegible(
              periodo,
            )}: carga ${archivoVentasEsperado(
              periodo,
            )}.`,
        )
        .join("\n");
    }

    return null;
  }

  /** Día a Día solo acepta bloqueos del mes actual y del siguiente. */
  function validarBloqueosDiaADia() {
    if (archivosBloqueos.length > 2) {
      return "Selecciona como máximo dos archivos de bloqueos.";
    }
    const periodos = archivosBloqueos.map((archivo) =>
      periodoDesdeArchivoBloqueos(archivo.name),
    );
    if (periodos.some((periodo) => !periodo)) {
      return "Los archivos de bloqueos deben llamarse bloqueo.AAMM.txt.";
    }
    if (new Set(periodos).size !== periodos.length) {
      return "No puedes seleccionar dos archivos de bloqueos del mismo mes.";
    }
    const permitidos = periodosBloqueosDiaADia();
    for (let i = 0; i < archivosBloqueos.length; i++) {
      if (!permitidos.includes(periodos[i]!)) {
        return `El archivo ${archivosBloqueos[i].name} no corresponde al mes actual ni al siguiente (${permitidos
          .map(periodoLegible)
          .join(" o ")}).`;
      }
    }
    return null;
  }

  function prepararInicio() {
    if (escenarioActivo || verificandoActivo || errorActividad || !configuracionLista || iniciando) return;
    const validacion =
      validarConfiguracion();

    if (validacion) {
      setError(validacion);

      mostrarToast({
        titulo:
          "Revisa la configuración",

        descripcion:
          validacion,

        variante:
          "warning",
      });

      return;
    }

    setError(null);
    setMostrarConfirmacion(true);
  }

  async function handleIniciarSimulacion() {
  if (inicioEnCurso.current) return;
  inicioEnCurso.current = true;
  let etapa = "comprobar disponibilidad";
  try {
    setIniciando(true);
    setError(null);
    const validacion = validarConfiguracion();
    if (validacion) throw new Error(validacion);
    const activasActuales = await actualizarActivas();
    const conflicto = activaDelGrupo(activasActuales, convertirEscenario(configuracion.escenario));
    if (conflicto) {
      throw new Error(`${nombreTipoEjecucion(conflicto.tipo)} #${conflicto.id} ya está ${conflicto.estado === "PAUSED" ? "en pausa" : "en curso"}. Abre esa ejecución para continuar.`);
    }

    // Las cantidades y las velocidades son globales en este backend.
    // Al iniciar en paralelo se heredan: no se escribe ninguna configuración de flota.
    if (activasActuales.length === 0) {
      etapa = "preparar la flota";
      await reconciliarCantidadFlota();
      etapa = "actualizar las velocidades";
      await actualizarParametrosFlota();
    }

    if (
      usaDatosHistoricos &&
      archivosPedidos.length > 0
    ) {
      etapa = "cargar archivos de pedidos";

      const carga =
        await pedidosService.cargarArchivos(archivosPedidos);

      if (!carga) {
        throw new Error(
          "El backend no devolvió el resultado de la carga de pedidos.",
        );
      }

      mostrarToast({
        titulo: "Pedidos cargados",
        descripcion:
          `${carga.registrados} registrados · ${carga.omitidos} omitidos`,
        variante: "success",
      });

      etapa = "actualizar periodos cargados";

      const periodos =
        await pedidosService.periodosCargados();

      if (!Array.isArray(periodos)) {
        throw new Error(
          "El backend no devolvió una lista válida de periodos cargados.",
        );
      }

      setPeriodosCargados(
        periodos,
      );
    }

    // Los bloqueos se cargan también en Día a Día. Solo los meses cargados aquí cuentan para la ejecución.
    if (
      archivosBloqueos.length > 0
    ) {
      etapa = "cargar bloqueos";

      const carga =
        await bloqueosService.cargarArchivos(archivosBloqueos);

      if (!carga) {
        throw new Error(
          "El backend no devolvió el resultado de la carga de bloqueos.",
        );
      }

      mostrarToast({
        titulo: "Bloqueos cargados",
        descripcion:
          `${carga.registrados} registrados · ${carga.reemplazados} reemplazados en ${carga.archivos.length} mes(es)`,
        variante: "success",
      });
    }

    etapa = "construir escenario";

    const request =
      construirRequestEscenario();

    etapa = "crear escenario";

    const creado =
      await escenariosService.crear(
        request,
      );

    if (
      !creado ||
      typeof creado.id !== "number"
    ) {
      throw new Error(
        "El backend no devolvió un escenario válido.",
      );
    }

    if (!guardarUmbrales(claveUmbralesEjecucion(creado.id), configuracion)) {
      mostrarToast({ titulo: "Umbrales no guardados", descripcion: "Este navegador no permite conservar los umbrales de la ejecución.", variante: "warning" });
    }

    etapa = "iniciar escenario";

    const iniciado =
      await escenariosService.iniciar(
        creado.id,
      );

    if (
      !iniciado ||
      typeof iniciado.id !== "number"
    ) {
      throw new Error(
        "El backend no devolvió la ejecución iniciada.",
      );
    }

    mostrarToast({
      titulo: esOperacion ? "Operación iniciada" : "Simulación iniciada",
      descripcion:
        `Ejecución #${iniciado.id} · ${nombreTipoEjecucion(
          iniciado.tipo,
        )}`,
      variante: "success",
    });

    setMostrarConfirmacion(
      false,
    );

    // La ejecución ya inició: una falla de refresco no debe presentarse como una falla de inicio.
    void actualizarActivas().catch(() => undefined);
    navigate(`/ejecucion/${iniciado.id}`);
  } catch (error) {
    const mensajeOriginal =
      mensajeDeError(
        error,
        esOperacion ? "No fue posible iniciar la operación." : "No fue posible iniciar la simulación.",
      );

    const mensaje =
      `Etapa: ${etapa}\n${mensajeOriginal}`;

    setError(
      mensaje,
    );

    mostrarToast({
      titulo: "No se pudo iniciar",
      descripcion: mensaje,
      variante: "error",
    });
  } finally {
    inicioEnCurso.current = false;
    setIniciando(false);
  }
}

async function reconciliarCantidadFlota() {
  const vehiculos =
    await flotaService.listarVehiculos();

  if (
    !Array.isArray(
      vehiculos,
    )
  ) {
    throw new Error(
      "La respuesta de /api/flota/vehiculos no es una lista válida.",
    );
  }

  const objetivos:
    Record<
      TipoVehiculoBackend,
      number
    > = {
    CAR:
      configuracion.autosCantidad,

    MOTORCYCLE:
      configuracion.motosCantidad,

    BICYCLE:
      configuracion.bicicletasCantidad,
  };

  for (
    const tipo of Object.keys(
      objetivos,
    ) as TipoVehiculoBackend[]
  ) {
    const existentes =
      vehiculos
        .filter(
          (vehiculo) =>
            vehiculo.tipo ===
            tipo,
        )
        .sort(
          (a, b) =>
            b.id.localeCompare(
              a.id,
              undefined,
              {
                numeric: true,
              },
            ),
        );

    /*
     * Una unidad con historial (rutas, averías o mantenimientos) no se
     * puede borrar: el backend la da de baja lógica dejándola UNAVAILABLE,
     * y la simulación la excluye. Por eso la cantidad configurada se
     * compara solo con las unidades operativas, y para subirla se
     * reactivan primero las dadas de baja (menor código primero) antes
     * de crear unidades nuevas.
     */
    const operativos =
      existentes.filter(
        (vehiculo) =>
          vehiculo.estado !==
          "UNAVAILABLE",
      );

    const inactivos =
      existentes
        .filter(
          (vehiculo) =>
            vehiculo.estado ===
            "UNAVAILABLE",
        )
        .reverse();

    const diferencia =
      objetivos[tipo] -
      operativos.length;

    if (
      diferencia > 0
    ) {
      const reactivar =
        inactivos.slice(
          0,
          diferencia,
        );

      for (
        const vehiculo of
          reactivar
      ) {
        await flotaService.cambiarEstadoVehiculo(
          vehiculo.id,
          "AVAILABLE",
        );
      }

      for (
        let i = reactivar.length;
        i < diferencia;
        i += 1
      ) {
        await flotaService.altaVehiculo(
          tipo,
        );
      }
    } else if (
      diferencia < 0
    ) {
      const candidatos =
        ordenarParaBaja(
          operativos,
        ).slice(
          0,
          Math.abs(
            diferencia,
          ),
        );

      for (
        const vehiculo of
          candidatos
      ) {
        await flotaService.bajaVehiculo(
          vehiculo.id,
        );
      }
    }
  }
}

async function actualizarParametrosFlota() {
  const tipos =
    await flotaService.listarTipos();

  if (
    !Array.isArray(
      tipos,
    )
  ) {
    throw new Error(
      "La respuesta de /api/flota/tipos no es una lista válida.",
    );
  }

  const velocidad:
    Record<
      TipoVehiculoBackend,
      number
    > = {
    CAR:
      configuracion.autosVelocidad,

    MOTORCYCLE:
      configuracion.motosVelocidad,

    BICYCLE:
      configuracion.bicicletasVelocidad,
  };

  await Promise.all(
    tipos.map(
      (tipo) =>
        flotaService.actualizarTipo(
          tipo.tipo,
          {
            capacidadPaquetes:
              tipo.capacidadPaquetes,

            velocidadKmh:
              velocidad[
                tipo.tipo
              ],

            costoPorKm:
              tipo.costoPorKm,
          },
        ),
    ),
  );
}

  function construirRequestEscenario():
    CrearEscenarioRequestDto {
    const tipo =
      convertirEscenario(
        configuracion.escenario,
      );

    const request:
      CrearEscenarioRequestDto = {
      tipo,

      // Solo los bloqueos de estos meses existen para la ejecución; sin archivos, no hay bloqueos aunque la BD
      // tenga bloqueos guardados de cargas anteriores.
      periodosBloqueos:
        archivosBloqueos
          .map((archivo) =>
            periodoDesdeArchivoBloqueos(archivo.name),
          )
          .filter((periodo): periodo is string => Boolean(periodo)),
    };

    if (
      tipo !==
      "DAY_TO_DAY"
    ) {
      request.inicioSimulado =
        convertirHoraLimaAIso(
          configuracion.inicioSimulado,
        );
    }

    if (
      configuracion.usarConfiguracionSa
    ) {
      request.configuracionSa = {
        ...(
          configuracion.saSemilla.trim()
            ? {
                semilla:
                  Number(
                    configuracion.saSemilla.trim(),
                  ),
              }
            : {}
        ),

        temperaturaInicial:
          configuracion.saTemperaturaInicial,

        temperaturaMinima:
          configuracion.saTemperaturaMinima,

        factorEnfriamiento:
          configuracion.saFactorEnfriamiento,

        iteracionesPorTemperatura:
          configuracion.saIteracionesPorTemperatura,
      };
    }

    return request;
  }

  const validacion = validarConfiguracion();
  const puedeIniciar = !iniciando && !verificandoActivo && !errorActividad && configuracionLista &&
    !(usaDatosHistoricos && consultandoPeriodos) && !escenarioActivo && validacion === null;
  const IconoModo = esOperacion ? Radio : FlaskConical;
  const tono = esOperacion ? "text-teal-300" : "text-orange-300";

  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b border-slate-800 px-5 py-7 sm:px-8">
        <div className="mx-auto flex max-w-[1500px] flex-wrap items-start justify-between gap-5">
          <div className="flex gap-4">
            <span className={`flex size-12 shrink-0 items-center justify-center rounded-2xl border ${esOperacion ? "border-teal-800/60 bg-teal-950/40 text-teal-300" : "border-orange-800/60 bg-orange-950/30 text-orange-300"}`}><IconoModo className="size-6" /></span>
            <div>
              <p className={`text-[10px] font-semibold uppercase tracking-[0.2em] ${tono}`}>{esOperacion ? "Operación en tiempo real" : "Entorno de simulación"}</p>
              <h1 className="mt-1.5 text-2xl font-semibold tracking-tight text-white">{esOperacion ? "Día a Día" : "Simulaciones"}</h1>
              <p className="mt-2 max-w-2xl text-sm leading-relaxed text-slate-400">{esOperacion ? "Gestiona las entregas de la jornada y registra pedidos durante la operación." : "Evalúa las entregas en cinco días o continúa hasta el colapso logístico."}</p>
            </div>
          </div>
          <span className="rounded-full border border-slate-700 bg-[#151b23] px-3 py-1.5 text-xs text-slate-300">{esOperacion ? "Tiempo real · 1×" : "5 días / Colapso"}</span>
        </div>
      </header>

      <div className="mx-auto w-full max-w-[1564px] flex-1 p-4 sm:p-6">
        {errorActividad && <div role="alert" className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-800/60 bg-amber-950/20 px-4 py-3 text-sm text-amber-200">
          <span>No se pudo comprobar qué ejecuciones siguen activas. {errorActividad}</span>
          <button type="button" onClick={() => void actualizarActivas().catch(() => undefined)} className="flex items-center gap-2 rounded-md border border-amber-700 px-3 py-2"><RefreshCcw className="size-4" /> Reintentar</button>
        </div>}
        {verificandoActivo && <p role="status" className="mb-4 flex items-center gap-2 text-sm text-slate-400"><Loader2 className="size-4 animate-spin" /> Consultando actividad actual…</p>}

        {otraActiva && <div className="mb-5 flex flex-wrap items-center justify-between gap-4 rounded-xl border border-slate-700/80 bg-[#151b23] px-4 py-3.5">
          <div className="flex items-start gap-3">
            {esOperacion ? <FlaskConical className="mt-0.5 size-5 shrink-0 text-orange-300" /> : <Radio className="mt-0.5 size-5 shrink-0 text-teal-300" />}
            <div><p className="text-sm font-medium text-slate-200">{nombreTipoEjecucion(otraActiva.tipo)} #{otraActiva.id} <span className="ml-1 text-xs font-normal text-slate-400">· {nombreEstadoEjecucion(otraActiva.estado)}</span></p>
              <p className="mt-1 text-xs leading-relaxed text-slate-400">{escenarioActivo ? "Ambas ejecuciones conservan su propio reloj y controles." : `Puedes iniciar ${esOperacion ? "la operación diaria" : "una simulación"} en paralelo. Cada una tiene su propio reloj y controles.`}</p>
            </div>
          </div>
          <button type="button" onClick={() => navigate(`/ejecucion/${otraActiva.id}`)} className="flex shrink-0 items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-xs text-slate-300 hover:bg-slate-800">Abrir {esOperacion ? "simulación" : "operación"}<ArrowRight className="size-3.5" /></button>
        </div>}

        {escenarioActivo ? (
          <section className={`rounded-2xl border p-6 sm:p-8 ${esOperacion ? "border-teal-900/70 bg-teal-950/15" : "border-orange-900/70 bg-orange-950/10"}`}>
            <div className="flex flex-wrap items-center justify-between gap-5">
              <div><p className={`text-xs font-medium ${tono}`}>{nombreEstadoEjecucion(escenarioActivo.estado)} · #{escenarioActivo.id}</p>
                <h2 className="mt-3 text-xl font-semibold">{esOperacion ? "Tu operación diaria está disponible" : `${nombreTipoEjecucion(escenarioActivo.tipo)} activa`}</h2>
                <p className="mt-3 max-w-xl text-sm leading-6 text-slate-400">{esOperacion ? "Continúa con el seguimiento de pedidos, las rutas y las incidencias desde su mapa." : "Retoma el seguimiento, consulta sus indicadores o controla el avance desde su mapa."} {escenarioActivo.estado === "PAUSED" && "Está en pausa; puedes reanudarla desde allí."}</p>
              </div>
              <button type="button" onClick={() => navigate(`/ejecucion/${escenarioActivo.id}`)} className={`flex items-center gap-2 rounded-lg px-5 py-3 text-sm font-semibold text-white ${esOperacion ? "bg-teal-700 hover:bg-teal-600" : "bg-orange-600 hover:bg-orange-500"}`}><ExternalLink className="size-4" />{esOperacion ? "Abrir operación" : "Abrir simulación"}</button>
            </div>
            <div className="mt-7 border-t border-slate-800 pt-5 text-xs leading-6 text-slate-400">{esOperacion ? "Solo puede haber una operación Día a Día activa o pausada." : "5D y Colapso comparten una plaza: termina la simulación actual para iniciar otra."}
              {!otraActiva && <button type="button" onClick={() => navigate(esOperacion ? "/simulacion" : "/operacion")} className={`ml-1 inline-flex items-center gap-1 font-medium ${esOperacion ? "text-orange-300" : "text-teal-300"}`}>{esOperacion ? "Configurar una simulación en paralelo" : "Iniciar la operación Día a Día"}<ArrowRight className="size-3" /></button>}
            </div>
          </section>
        ) : (
          <fieldset disabled={iniciando} className="grid min-w-0 gap-4 xl:grid-cols-[250px_minmax(0,1fr)] 2xl:grid-cols-[260px_minmax(0,1fr)_270px]">
            <OrigenDatos periodosCargados={periodosCargados} mesesRequeridos={mesesRequeridos} mesesFaltantes={mesesFaltantes} consultandoPeriodos={consultandoPeriodos} />
            <section className="min-w-0 space-y-4">
              <ParametrosOperacion />
              {flotaBloqueada && <div className="flex items-start gap-3 rounded-xl border border-slate-700 bg-[#151b23] px-4 py-3 text-xs leading-5 text-slate-400"><LockKeyhole className="mt-0.5 size-4 shrink-0 text-slate-300" /><div><p className="font-medium text-slate-200">Se reutiliza la flota vigente</p>Las cantidades y velocidades son compartidas. Iniciar en paralelo conserva sus valores para no alterar la ejecución en curso.</div></div>}
              <fieldset disabled={flotaBloqueada || !configuracionLista} className="min-w-0 disabled:opacity-75"><FlotaConfiguracion bloqueada={flotaBloqueada} /></fieldset>
              <SemaforoConfiguracion />
              <AveriaConfiguracion bloqueada={flotaBloqueada} />
            </section>
            <div className="xl:col-start-2 2xl:col-start-auto"><ResumenConfiguracion mesesRequeridos={mesesRequeridos} mesesFaltantes={mesesFaltantes} /></div>
          </fieldset>
        )}

        {error && <div role="alert" className="mt-4 whitespace-pre-line rounded-xl border border-red-900 bg-red-950/30 px-4 py-3 text-sm text-red-300">{error}
          {!configuracionLista && <button type="button" onClick={() => setRecarga((valor) => valor + 1)} className="mt-3 flex items-center gap-2 rounded-md border border-red-800 px-3 py-2 text-xs"><RefreshCcw className="size-4" /> Volver a cargar configuración</button>}
        </div>}
      </div>

      {!escenarioActivo && <footer className="sticky bottom-0 z-10 flex flex-wrap items-center justify-between gap-3 border-t border-slate-800 bg-[#0d1117]/95 px-5 py-4 backdrop-blur sm:px-8">
        <p className="max-w-2xl text-xs leading-5 text-slate-400">{verificandoActivo || !configuracionLista ? "Verificando la configuración y la disponibilidad…" : errorActividad ? "Comprueba la conexión antes de iniciar." : validacion ?? (otraActiva ? "Todo listo para iniciar en paralelo con la flota vigente." : "Revisa la configuración antes de iniciar.")}</p>
        <Button type="button" disabled={!puedeIniciar} onClick={prepararInicio} className={esOperacion ? "bg-teal-600 text-white hover:bg-teal-500" : "bg-orange-600 text-white hover:bg-orange-500"}>
          {iniciando ? <><Loader2 className="mr-2 size-4 animate-spin" /> Iniciando…</> : <>{esOperacion ? "Revisar e iniciar operación" : "Revisar e iniciar simulación"}<ArrowRight className="ml-2 size-4" /></>}
        </Button>
      </footer>}

      <ConfirmarInicioModal abierto={mostrarConfirmacion} configuracion={configuracion} archivosBloqueos={archivosBloqueos} confirmando={iniciando}
        enParalelo={!!otraActiva} onCancelar={() => { if (!iniciando) setMostrarConfirmacion(false); }}
        fuentePedidos={esOperacion ? "Registro en línea" : archivosPedidos.length > 0 ? archivosPedidos.map((archivo) => archivo.name).join(" · ") : `Base de datos · ${mesesRequeridos.join(", ")}`}
        onConfirmar={() => void handleIniciarSimulacion()} />
    </div>
  );
}

function contar(
  vehiculos:
    VehiculoResponseDto[],
  tipo:
    TipoVehiculoBackend,
) {
  return vehiculos.filter(
    (vehiculo) =>
      vehiculo.tipo === tipo && vehiculo.estado !== "UNAVAILABLE",
  ).length;
}

function ordenarParaBaja(
  vehiculos:
    VehiculoResponseDto[],
) {
  return [
    ...vehiculos,
  ].sort(
    (a, b) => {
      const prioridadA =
        a.estado ===
        "AVAILABLE"
          ? 0
          : 1;

      const prioridadB =
        b.estado ===
        "AVAILABLE"
          ? 0
          : 1;

      return (
        prioridadA -
          prioridadB ||
        b.id.localeCompare(
          a.id,
          undefined,
          {
            numeric:
              true,
          },
        )
      );
    },
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

function normalizarFechaLocal(
  valor:
    string,
) {
  return valor.length ===
    16
    ? `${valor}:00`
    : valor;
}

function convertirHoraLimaAIso(
  valor:
    string,
) {
  const fecha =
    new Date(
      `${normalizarFechaLocal(
        valor,
      )}-05:00`,
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
