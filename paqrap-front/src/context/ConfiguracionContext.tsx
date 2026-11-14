import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

import type {
  ConfiguracionPlanificador,
} from "@/features/configuracion/configuracion.types";
import { claveUmbralesActuales, claveUmbralesEjecucion, extraerUmbrales, guardarUmbrales, leerUmbrales, type UmbralesSemaforo } from "@/features/configuracion/semaforo";

interface ConfiguracionContextType {
  configuracion: ConfiguracionPlanificador;

  actualizarConfiguracion: (
    cambios: Partial<ConfiguracionPlanificador>,
  ) => void;

  // Contrato anterior, conservado para pantallas que cargan un solo archivo.
  archivoPedidos: File | null;
  setArchivoPedidos: (archivo: File | null) => void;
  archivoBloqueos: File | null;
  setArchivoBloqueos: (archivo: File | null) => void;

  // Nuevos selectores de hasta dos meses consecutivos.
  archivosPedidos: File[];
  setArchivosPedidos: (archivos: File[]) => void;
  archivosBloqueos: File[];
  setArchivosBloqueos: (archivos: File[]) => void;
}

const configuracionInicial: ConfiguracionPlanificador = {
  escenario: "SIMULACION_5D",
  inicioSimulado: "",

  autosCantidad: 10,
  autosVelocidad: 40,

  motosCantidad: 15,
  motosVelocidad: 25,

  bicicletasCantidad: 12,
  bicicletasVelocidad: 12,

  almacenVerdeHasta: 33,
  almacenAmbarHasta: 66,

  vehiculoVerdeHasta: 60,
  vehiculoAmbarHasta: 85,

  usarConfiguracionSa: false,
  saSemilla: "",
  saTemperaturaInicial: 1000,
  saTemperaturaMinima: 1,
  saFactorEnfriamiento: 0.95,
  saIteracionesPorTemperatura: 50,
};

const ConfiguracionContext =
  createContext<
    ConfiguracionContextType | undefined
  >(undefined);

export function ConfiguracionProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [configuracion, setConfiguracion] =
    useState<ConfiguracionPlanificador>(() => ({
      ...configuracionInicial,
      ...(leerUmbrales(claveUmbralesActuales) ?? {}),
    }));

  useEffect(() => {
    const umbrales = extraerUmbrales(configuracion);
    if (umbrales.almacenVerdeHasta < umbrales.almacenAmbarHasta &&
        umbrales.vehiculoVerdeHasta < umbrales.vehiculoAmbarHasta &&
        Object.values(umbrales).every((valor) => Number.isFinite(valor) && valor >= 0 && valor <= 100)) {
      guardarUmbrales(claveUmbralesActuales, umbrales);
    }
  }, [configuracion]);

  const [archivosPedidos, setArchivosPedidos] = useState<File[]>([]);
  const [archivosBloqueos, setArchivosBloqueos] = useState<File[]>([]);

  const archivoPedidos = archivosPedidos[0] ?? null;
  const archivoBloqueos = archivosBloqueos[0] ?? null;
  const setArchivoPedidos = (archivo: File | null) =>
    setArchivosPedidos(archivo ? [archivo] : []);
  const setArchivoBloqueos = (archivo: File | null) =>
    setArchivosBloqueos(archivo ? [archivo] : []);

  function actualizarConfiguracion(
    cambios: Partial<ConfiguracionPlanificador>,
  ) {
    setConfiguracion((actual) => ({
      ...actual,
      ...cambios,
    }));
  }

  return (
    <ConfiguracionContext.Provider
      value={{
        configuracion,
        actualizarConfiguracion,
        archivoPedidos,
        setArchivoPedidos,
        archivoBloqueos,
        setArchivoBloqueos,
        archivosPedidos,
        setArchivosPedidos,
        archivosBloqueos,
        setArchivosBloqueos,
      }}
    >
      {children}
    </ConfiguracionContext.Provider>
  );
}

export function useConfiguracion() {
  const context = useContext(
    ConfiguracionContext,
  );

  if (!context) {
    throw new Error(
      "useConfiguracion debe usarse dentro de ConfiguracionProvider",
    );
  }

  return context;
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
