import {
  createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode,
} from "react";
import type { ConfiguracionPlanificador } from "@/features/configuracion/configuracion.types";
import { claveUmbralesActuales, claveUmbralesEjecucion, extraerUmbrales, guardarUmbrales, leerUmbrales, type UmbralesSemaforo } from "@/features/configuracion/semaforo";

export type ModoConfiguracion = "operacion" | "simulacion";
interface Borrador {
  configuracion: ConfiguracionPlanificador;
  archivosPedidos: File[];
  archivosBloqueos: File[];
}
interface BancoConfiguraciones {
  borradores: Record<ModoConfiguracion, Borrador>;
  actualizar: (modo: ModoConfiguracion, cambios: Partial<ConfiguracionPlanificador>) => void;
  archivos: (modo: ModoConfiguracion, tipo: "archivosPedidos" | "archivosBloqueos", archivos: File[]) => void;
}

const configuracionInicial: ConfiguracionPlanificador = {
  escenario: "SIMULACION_5D", inicioSimulado: "",
  autosCantidad: 10, autosVelocidad: 40,
  motosCantidad: 15, motosVelocidad: 25,
  bicicletasCantidad: 12, bicicletasVelocidad: 12,
  almacenVerdeHasta: 33, almacenAmbarHasta: 66,
  vehiculoVerdeHasta: 60, vehiculoAmbarHasta: 85,
  usarConfiguracionSa: false, saSemilla: "",
  saTemperaturaInicial: 1000, saTemperaturaMinima: 1,
  saFactorEnfriamiento: 0.95, saIteracionesPorTemperatura: 50,
};

const ConfiguracionContext = createContext<BancoConfiguraciones | undefined>(undefined);
const ModoContext = createContext<ModoConfiguracion>("simulacion");

function nuevoBorrador(modo: ModoConfiguracion): Borrador {
  return {
    configuracion: {
      ...configuracionInicial,
      ...(leerUmbrales(claveUmbralesActuales) ?? {}),
      escenario: modo === "operacion" ? "DIA_A_DIA" : "SIMULACION_5D",
    },
    archivosPedidos: [], archivosBloqueos: [],
  };
}

/** Los dos formularios conservan borradores y selecciones de archivos independientes. */
export function ConfiguracionProvider({ children }: { children: ReactNode }) {
  const [borradores, setBorradores] = useState<Record<ModoConfiguracion, Borrador>>(() => ({
    operacion: nuevoBorrador("operacion"), simulacion: nuevoBorrador("simulacion"),
  }));
  const [ultimoModo, setUltimoModo] = useState<ModoConfiguracion>("simulacion");

  const actualizar = useCallback((modo: ModoConfiguracion, cambios: Partial<ConfiguracionPlanificador>) => {
    setUltimoModo(modo);
    setBorradores((actual) => ({
      ...actual,
      [modo]: { ...actual[modo], configuracion: {
        ...actual[modo].configuracion, ...cambios,
        // Nunca se transforma la operación diaria en una simulación al navegar.
        escenario: modo === "operacion" ? "DIA_A_DIA"
          : cambios.escenario === "DIA_A_DIA" ? actual[modo].configuracion.escenario
            : cambios.escenario ?? actual[modo].configuracion.escenario,
      } },
    }));
  }, []);

  const archivos = useCallback((modo: ModoConfiguracion, tipo: "archivosPedidos" | "archivosBloqueos", valores: File[]) => {
    setBorradores((actual) => ({ ...actual, [modo]: { ...actual[modo], [tipo]: valores } }));
  }, []);

  useEffect(() => {
    const umbrales = extraerUmbrales(borradores[ultimoModo].configuracion);
    if (umbrales.almacenVerdeHasta < umbrales.almacenAmbarHasta &&
        umbrales.vehiculoVerdeHasta < umbrales.vehiculoAmbarHasta &&
        Object.values(umbrales).every((valor) => Number.isFinite(valor) && valor >= 0 && valor <= 100)) {
      guardarUmbrales(claveUmbralesActuales, umbrales);
    }
  }, [borradores, ultimoModo]);

  const value = useMemo(() => ({ borradores, actualizar, archivos }), [borradores, actualizar, archivos]);
  return <ConfiguracionContext.Provider value={value}>{children}</ConfiguracionContext.Provider>;
}

export function ConfiguracionModoProvider({ modo, children }: { modo: ModoConfiguracion; children: ReactNode }) {
  return <ModoContext.Provider value={modo}>{children}</ModoContext.Provider>;
}

export function useConfiguracion() {
  const banco = useContext(ConfiguracionContext);
  const modo = useContext(ModoContext);
  if (!banco) throw new Error("useConfiguracion debe usarse dentro de ConfiguracionProvider");
  const borrador = banco.borradores[modo];
  const actualizar = banco.actualizar;
  const actualizarConfiguracion = useCallback((cambios: Partial<ConfiguracionPlanificador>) => actualizar(modo, cambios), [actualizar, modo]);
  return {
    modo,
    ...borrador,
    actualizarConfiguracion,
    archivoPedidos: borrador.archivosPedidos[0] ?? null,
    archivoBloqueos: borrador.archivosBloqueos[0] ?? null,
    setArchivoPedidos: (archivo: File | null) => banco.archivos(modo, "archivosPedidos", archivo ? [archivo] : []),
    setArchivoBloqueos: (archivo: File | null) => banco.archivos(modo, "archivosBloqueos", archivo ? [archivo] : []),
    setArchivosPedidos: (archivos: File[]) => banco.archivos(modo, "archivosPedidos", archivos),
    setArchivosBloqueos: (archivos: File[]) => banco.archivos(modo, "archivosBloqueos", archivos),
  };
}

const UmbralesEjecucionContext = createContext<{ umbrales: UmbralesSemaforo; guardados: boolean } | null>(null);

export function UmbralesEjecucionProvider({ ejecucionId, children }: { ejecucionId: number; children: ReactNode }) {
  const { configuracion } = useConfiguracion();
  const guardados = useMemo(() => leerUmbrales(claveUmbralesEjecucion(ejecucionId)), [ejecucionId]);
  return (
    <UmbralesEjecucionContext.Provider value={{ umbrales: guardados ?? configuracion, guardados: guardados !== null }}>
      {children}
    </UmbralesEjecucionContext.Provider>
  );
}

export function useUmbralesEjecucion() {
  const contexto = useContext(UmbralesEjecucionContext);
  if (!contexto) throw new Error("useUmbralesEjecucion requiere UmbralesEjecucionProvider");
  return contexto;
}
