import {
  createContext,
  useContext,
  useState,
  type ReactNode,
} from "react";

import type {
  ConfiguracionPlanificador,
} from "@/features/configuracion/configuracion.types";

interface ConfiguracionContextType {
  configuracion: ConfiguracionPlanificador;

  actualizarConfiguracion: (
    cambios: Partial<ConfiguracionPlanificador>,
  ) => void;

  archivoPedidos: File | null;

  setArchivoPedidos: (archivo: File | null) => void;
}

const configuracionInicial: ConfiguracionPlanificador = {
  escenario: "SIMULACION_5D",

  compararAlgoritmos: false,
  algoritmo: "GRASP",
  semilla: 42,

  // GRASP
  graspAlpha: 0.3,
  graspMaxIter: 500,

  // Simulated Annealing
  saTemperaturaInicial: 1000,
  saFactorEnfriamiento: 0.95,
  saIteracionesNivel: 50,
  saTemperaturaMinima: 1,

  // Flota
  autosCantidad: 12,
  autosVelocidad: 40,

  motosCantidad: 20,
  motosVelocidad: 25,

  bicicletasCantidad: 10,
  bicicletasVelocidad: 12,

  // Semaforización
  almacenVerdeHasta: 33,
  almacenAmbarHasta: 66,

  pedidoVerdeHasta: 60,
  pedidoAmbarHasta: 85,
};

const ConfiguracionContext =
  createContext<ConfiguracionContextType | undefined>(
    undefined,
  );

interface ConfiguracionProviderProps {
  children: ReactNode;
}

export function ConfiguracionProvider({
  children,
}: ConfiguracionProviderProps) {
  const [configuracion, setConfiguracion] =
    useState<ConfiguracionPlanificador>(
      configuracionInicial,
    );

  const [archivoPedidos, setArchivoPedidos] =
    useState<File | null>(null);

  const actualizarConfiguracion = (
    cambios: Partial<ConfiguracionPlanificador>,
  ) => {
    setConfiguracion((configuracionActual) => ({
      ...configuracionActual,
      ...cambios,
    }));
  };

  return (
    <ConfiguracionContext.Provider
      value={{
        configuracion,
        actualizarConfiguracion,
        archivoPedidos,
        setArchivoPedidos,
      }}
    >
      {children}
    </ConfiguracionContext.Provider>
  );
}

export function useConfiguracion() {
  const context = useContext(ConfiguracionContext);

  if (!context) {
    throw new Error(
      "useConfiguracion debe usarse dentro de ConfiguracionProvider",
    );
  }

  return context;
}