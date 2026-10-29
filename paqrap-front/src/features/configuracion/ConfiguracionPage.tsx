import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  CircleStop,
  ExternalLink,
  Loader2,
  TriangleAlert,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { useToast } from "@/components/feedback/ToastProvider";
import { useConfiguracion } from "@/context/ConfiguracionContext";
import type {
  CrearEscenarioRequestDto,
  EscenarioResponseDto,
  TipoEscenarioBackend,
} from "@/api/escenarios.dto";
import type {
  TipoVehiculoBackend,
  VehiculoResponseDto,
} from "@/api/flota.dto";
import { escenariosService } from "@/services/escenarios.service";
import { pedidosService } from "@/services/pedidos.service";
import { bloqueosService } from "@/services/bloqueos.service";
import { flotaService } from "@/services/flota.service";
import { mensajeDeError } from "@/lib/api-error";

import { OrigenDatos } from "./OrigenDatos";
import { ParametrosOperacion } from "./ParametrosOperacion";
import { FlotaConfiguracion } from "./FlotaConfiguracion";
import { SemaforoConfiguracion } from "./SemaforoConfiguracion";
import { ResumenConfiguracion } from "./ResumenConfiguracion";
import { ConfirmarInicioModal } from "./ConfirmarInicioModal";

export default function ConfiguracionPage() {
  const navigate = useNavigate();
  const { mostrarToast } = useToast();
  const {
    configuracion,
    actualizarConfiguracion,
    archivoPedidos,
    archivoBloqueos,
  } = useConfiguracion();

  const [iniciando, setIniciando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [escenarioActivo, setEscenarioActivo] =
    useState<EscenarioResponseDto | null>(null);
  const [verificandoActivo, setVerificandoActivo] = useState(true);
  const [deteniendo, setDeteniendo] = useState(false);
  const [mostrarConfirmacion, setMostrarConfirmacion] = useState(false);

  const necesitaArchivo = configuracion.escenario !== "DIA_A_DIA";

  useEffect(() => {
    let vigente = true;

    async function inicializar() {
      try {
        const [activo, tipos, vehiculos] = await Promise.all([
          buscarEscenarioActivo(),
          flotaService.listarTipos(),
          flotaService.listarVehiculos(),
        ]);

        if (!vigente) return;

        setEscenarioActivo(activo);
        const porTipo = new Map(tipos.map((tipo) => [tipo.tipo, tipo]));

        actualizarConfiguracion({
          autosCantidad: contar(vehiculos, "CAR"),
          autosVelocidad:
            porTipo.get("CAR")?.velocidadKmh ?? configuracion.autosVelocidad,
          motosCantidad: contar(vehiculos, "MOTORCYCLE"),
          motosVelocidad:
            porTipo.get("MOTORCYCLE")?.velocidadKmh ?? configuracion.motosVelocidad,
          bicicletasCantidad: contar(vehiculos, "BICYCLE"),
          bicicletasVelocidad:
            porTipo.get("BICYCLE")?.velocidadKmh ??
            configuracion.bicicletasVelocidad,
        });
      } catch (error) {
        if (vigente) {
          setError(
            mensajeDeError(error, "No fue posible cargar la configuración inicial."),
          );
        }
      } finally {
        if (vigente) setVerificandoActivo(false);
      }
    }

    void inicializar();

    return () => {
      vigente = false;
    };
  }, []);

  async function buscarEscenarioActivo(): Promise<EscenarioResponseDto | null> {
    const pagina = await escenariosService.listar(0, 100);
    return (
      pagina.contenido.find(
        (escenario) =>
          escenario.estado === "RUNNING" || escenario.estado === "PAUSED",
      ) ?? null
    );
  }

  async function handleDetenerActivo() {
    if (!escenarioActivo) return;

    try {
      setDeteniendo(true);
      setError(null);
      await escenariosService.detener(escenarioActivo.id);
      mostrarToast({
        titulo: "Simulación detenida",
        descripcion: `La ejecución #${escenarioActivo.id} fue detenida correctamente.`,
        variante: "success",
      });
      setEscenarioActivo(null);
    } catch (error) {
      const mensaje = mensajeDeError(
        error,
        "No fue posible detener la simulación activa.",
      );
      setError(mensaje);
      mostrarToast({ titulo: "No se pudo detener", descripcion: mensaje, variante: "error" });
    } finally {
      setDeteniendo(false);
    }
  }

  function validarConfiguracion(): string | null {
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

    if (
      configuracion.almacenVerdeHasta >= configuracion.almacenAmbarHasta ||
      configuracion.pedidoVerdeHasta >= configuracion.pedidoAmbarHasta
    ) {
      return "Los límites verdes deben ser menores que los límites ámbar.";
    }

    if (configuracion.usarConfiguracionSa) {
      if (
        configuracion.saTemperaturaInicial <= 0 ||
        configuracion.saTemperaturaMinima <= 0 ||
        configuracion.saTemperaturaMinima >= configuracion.saTemperaturaInicial
      ) {
        return "Los parámetros de SA requieren temperaturas positivas y Tmin menor que T0.";
      }

      if (
        configuracion.saFactorEnfriamiento < 0.8 ||
        configuracion.saFactorEnfriamiento > 0.99
      ) {
        return "El factor de enfriamiento debe estar entre 0.80 y 0.99.";
      }

      if (configuracion.saIteracionesPorTemperatura <= 0) {
        return "Las iteraciones por temperatura deben ser mayores que cero.";
      }

      if (
        configuracion.saSemilla.trim() &&
        !/^-?\d+$/.test(configuracion.saSemilla.trim())
      ) {
        return "La semilla debe ser un número entero.";
      }
    }

    if (configuracion.escenario === "DIA_A_DIA") {
      return null;
    }

    if (!archivoPedidos) {
      return "Selecciona el archivo de pedidos antes de iniciar.";
    }

    const ventas = /^ventas\.(\d{4})(\d{2})\.txt$/i.exec(archivoPedidos.name);
    if (!ventas) {
      return "El archivo de pedidos debe llamarse ventas.AAAAMM.txt.";
    }

    if (!configuracion.inicioSimulado) {
      return "Selecciona la fecha y hora inicial de la simulación.";
    }

    if (archivoBloqueos) {
      const bloqueos = /^bloqueo\.(\d{2})(\d{2})\.txt$/i.exec(
        archivoBloqueos.name,
      );

      if (!bloqueos) {
        return "El archivo de bloqueos debe llamarse bloqueo.AAMM.txt.";
      }

      if (
        2000 + Number(bloqueos[1]) !== Number(ventas[1]) ||
        Number(bloqueos[2]) !== Number(ventas[2])
      ) {
        return "Los archivos de pedidos y bloqueos deben corresponder al mismo periodo.";
      }
    }

    const fecha = new Date(
      `${normalizarFechaLocal(configuracion.inicioSimulado)}-05:00`,
    );

    if (Number.isNaN(fecha.getTime())) {
      return "La fecha inicial no es válida.";
    }

    const partes = new Intl.DateTimeFormat("en-CA", {
      timeZone: "America/Lima",
      year: "numeric",
      month: "2-digit",
    }).formatToParts(fecha);

    const anio = Number(partes.find((parte) => parte.type === "year")?.value);
    const mes = Number(partes.find((parte) => parte.type === "month")?.value);

    if (anio !== Number(ventas[1]) || mes !== Number(ventas[2])) {
      return `La simulación debe comenzar dentro del periodo ${ventas[1]}-${ventas[2]}.`;
    }

    return null;
  }

  function prepararInicio() {
    const validacion = validarConfiguracion();
    if (validacion) {
      setError(validacion);
      mostrarToast({ titulo: "Revisa la configuración", descripcion: validacion, variante: "warning" });
      return;
    }

    setError(null);
    setMostrarConfirmacion(true);
  }

  async function handleIniciarSimulacion() {
    try {
      setIniciando(true);
      setError(null);

      const activo = await buscarEscenarioActivo();
      if (activo) {
        setEscenarioActivo(activo);
        throw new Error("Ya existe una simulación activa; deténla antes de iniciar otra.");
      }

      await reconciliarCantidadFlota();
      await actualizarParametrosFlota();

      if (necesitaArchivo && archivoPedidos) {
        const carga = await pedidosService.cargarArchivo(archivoPedidos);
        mostrarToast({
          titulo: "Pedidos cargados",
          descripcion: `${carga.registrados} registrados · ${carga.omitidos} omitidos`,
          variante: "success",
        });
      }

      if (necesitaArchivo && archivoBloqueos) {
        const periodo = periodoDesdeVentas(archivoPedidos?.name ?? "");
        const carga = await bloqueosService.cargarArchivo(
          archivoBloqueos,
          periodo ?? undefined,
        );
        mostrarToast({
          titulo: "Bloqueos cargados",
          descripcion: `${carga.registrados} registrados · ${carga.reemplazados} reemplazados en ${carga.periodo}`,
          variante: "success",
        });
      }

      const request = construirRequestEscenario();
      const creado = await escenariosService.crear(request);
      const iniciado = await escenariosService.iniciar(creado.id);

      mostrarToast({
        titulo: "Simulación iniciada",
        descripcion: `Ejecución #${iniciado.id} · ${nombreTipoEscenario(iniciado.tipo)}`,
        variante: "success",
      });

      setMostrarConfirmacion(false);
      navigate(`/ejecucion/${iniciado.id}`);
    } catch (error) {
      const mensaje = mensajeDeError(error, "No fue posible iniciar la simulación.");
      setError(mensaje);
      mostrarToast({ titulo: "No se pudo iniciar", descripcion: mensaje, variante: "error" });
    } finally {
      setIniciando(false);
    }
  }

  async function reconciliarCantidadFlota() {
    const vehiculos = await flotaService.listarVehiculos();
    const objetivos: Record<TipoVehiculoBackend, number> = {
      CAR: configuracion.autosCantidad,
      MOTORCYCLE: configuracion.motosCantidad,
      BICYCLE: configuracion.bicicletasCantidad,
    };

    for (const tipo of Object.keys(objetivos) as TipoVehiculoBackend[]) {
      const existentes = vehiculos
        .filter((vehiculo) => vehiculo.tipo === tipo)
        .sort((a, b) => b.id.localeCompare(a.id, undefined, { numeric: true }));
      const diferencia = objetivos[tipo] - existentes.length;

      if (diferencia > 0) {
        for (let i = 0; i < diferencia; i += 1) {
          await flotaService.altaVehiculo(tipo);
        }
      } else if (diferencia < 0) {
        const candidatos = ordenarParaBaja(existentes).slice(0, Math.abs(diferencia));
        for (const vehiculo of candidatos) {
          await flotaService.bajaVehiculo(vehiculo.id);
        }
      }
    }
  }

  async function actualizarParametrosFlota() {
    const tipos = await flotaService.listarTipos();
    const velocidad: Record<TipoVehiculoBackend, number> = {
      CAR: configuracion.autosVelocidad,
      MOTORCYCLE: configuracion.motosVelocidad,
      BICYCLE: configuracion.bicicletasVelocidad,
    };

    await Promise.all(
      tipos.map((tipo) =>
        flotaService.actualizarTipo(tipo.tipo, {
          capacidadPaquetes: tipo.capacidadPaquetes,
          velocidadKmh: velocidad[tipo.tipo],
          costoPorKm: tipo.costoPorKm,
        }),
      ),
    );
  }

  function construirRequestEscenario(): CrearEscenarioRequestDto {
    const tipo = convertirEscenario(configuracion.escenario);
    const request: CrearEscenarioRequestDto = { tipo };

    if (tipo !== "DAY_TO_DAY") {
      request.inicioSimulado = convertirHoraLimaAIso(configuracion.inicioSimulado);
    }

    if (configuracion.usarConfiguracionSa) {
      request.configuracionSa = {
        ...(configuracion.saSemilla.trim()
          ? { semilla: Number(configuracion.saSemilla.trim()) }
          : {}),
        temperaturaInicial: configuracion.saTemperaturaInicial,
        temperaturaMinima: configuracion.saTemperaturaMinima,
        factorEnfriamiento: configuracion.saFactorEnfriamiento,
        iteracionesPorTemperatura: configuracion.saIteracionesPorTemperatura,
      };
    }

    return request;
  }

  const puedeIniciar =
    !iniciando &&
    !verificandoActivo &&
    !escenarioActivo &&
    validarConfiguracion() === null;

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
                  Ejecución #{escenarioActivo.id} · {nombreTipoEscenario(escenarioActivo.tipo)} · {nombreEstado(escenarioActivo.estado)}
                </p>
                <p className="mt-1 text-xs text-slate-500">
                  Puedes volver a la ejecución actual o detenerla antes de iniciar otra.
                </p>
              </div>
            </div>

            <div className="flex shrink-0 gap-2">
              <button
                type="button"
                onClick={() => navigate(`/ejecucion/${escenarioActivo.id}`)}
                className="flex items-center gap-2 rounded-md border border-slate-600 bg-slate-800 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
              >
                <ExternalLink className="size-4" />
                Ir a ejecución
              </button>

              <button
                type="button"
                disabled={deteniendo}
                onClick={() => void handleDetenerActivo()}
                className="flex items-center gap-2 rounded-md bg-red-700 px-4 py-2 text-sm font-semibold text-white hover:bg-red-600 disabled:opacity-50"
              >
                {deteniendo ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <CircleStop className="size-4" />
                )}
                {deteniendo ? "Deteniendo..." : "Detener simulación"}
              </button>
            </div>
          </div>
        </div>
      )}

      {error && (
        <div className="mx-4 mb-3 whitespace-pre-line rounded-md border border-red-900 bg-red-950/40 px-4 py-3 text-sm text-red-400">
          {error}
        </div>
      )}

      <div className="sticky bottom-0 flex items-center justify-between border-t border-slate-800 bg-[#0d1117] px-4 py-3">
        <div className="text-xs text-slate-500">
          {escenarioActivo
            ? "Existe una ejecución activa."
            : "Listo para revisar e iniciar una ejecución."}
        </div>

        <Button
          type="button"
          disabled={!puedeIniciar}
          onClick={prepararInicio}
          className="bg-[#ff6b35] text-white hover:bg-[#ff7b4d]"
        >
          {iniciando ? (
            <>
              <Loader2 className="mr-2 size-4 animate-spin" />
              Iniciando...
            </>
          ) : (
            "Revisar e iniciar"
          )}
        </Button>
      </div>

      <ConfirmarInicioModal
        abierto={mostrarConfirmacion}
        configuracion={configuracion}
        archivoPedidos={archivoPedidos}
        archivoBloqueos={archivoBloqueos}
        confirmando={iniciando}
        onCancelar={() => setMostrarConfirmacion(false)}
        onConfirmar={() => void handleIniciarSimulacion()}
      />
    </div>
  );
}

function contar(vehiculos: VehiculoResponseDto[], tipo: TipoVehiculoBackend) {
  return vehiculos.filter((vehiculo) => vehiculo.tipo === tipo).length;
}

function ordenarParaBaja(vehiculos: VehiculoResponseDto[]) {
  return [...vehiculos].sort((a, b) => {
    const prioridadA = a.estado === "AVAILABLE" ? 0 : 1;
    const prioridadB = b.estado === "AVAILABLE" ? 0 : 1;
    return prioridadA - prioridadB || b.id.localeCompare(a.id, undefined, { numeric: true });
  });
}

function periodoDesdeVentas(nombre: string) {
  const match = /^ventas\.(\d{4})(\d{2})\.txt$/i.exec(nombre);
  return match ? `${match[1]}-${match[2]}` : null;
}

function convertirEscenario(
  escenario: "DIA_A_DIA" | "SIMULACION_5D" | "COLAPSO",
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

function nombreTipoEscenario(tipo: TipoEscenarioBackend) {
  switch (tipo) {
    case "DAY_TO_DAY":
      return "Día a Día";
    case "FIVE_DAY":
      return "Simulación 5D";
    case "COLLAPSE":
      return "Colapso";
  }
}

function nombreEstado(estado: EscenarioResponseDto["estado"]) {
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

function normalizarFechaLocal(valor: string) {
  return valor.length === 16 ? `${valor}:00` : valor;
}

function convertirHoraLimaAIso(valor: string) {
  const fecha = new Date(`${normalizarFechaLocal(valor)}-05:00`);
  if (Number.isNaN(fecha.getTime())) {
    throw new Error("La fecha inicial de la simulación no es válida.");
  }
  return fecha.toISOString();
}
