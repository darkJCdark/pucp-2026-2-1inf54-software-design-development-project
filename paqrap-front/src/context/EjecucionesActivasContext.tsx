import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import type { EscenarioResponseDto } from "@/api/escenarios.dto";
import { activaDelGrupo } from "@/lib/ejecuciones";
import { mensajeDeError } from "@/lib/api-error";
import { escenariosService } from "@/services/escenarios.service";

interface EjecucionesActivasContextType {
  activas: EscenarioResponseDto[];
  operacion: EscenarioResponseDto | null;
  simulacion: EscenarioResponseDto | null;
  cargando: boolean;
  error: string | null;
  actualizar: () => Promise<EscenarioResponseDto[]>;
}

const Contexto = createContext<EjecucionesActivasContextType | null>(null);

/** Una sola consulta compartida por navegación, configuración y mapas. */
export function EjecucionesActivasProvider({ children }: { children: ReactNode }) {
  const [activas, setActivas] = useState<EscenarioResponseDto[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const version = useRef(0);
  const ultimaConsulta = useRef<{
    version: number;
    promesa: Promise<EscenarioResponseDto[]>;
  } | null>(null);

  const actualizar = useCallback(() => {
    const consulta = ++version.current;
    const promesa = (async () => {
      try {
        const resultado = await escenariosService.listarActivas();
        // También los consumidores (inicio y cambios de flota) deben recibir
        // la respuesta más reciente, no solo el estado que pinta la navegación.
        const reciente = ultimaConsulta.current;
        if (reciente && reciente.version > consulta) return reciente.promesa;
        if (consulta === version.current) {
          setActivas(resultado);
          setError(null);
        }
        return resultado;
      } catch (cause) {
        const reciente = ultimaConsulta.current;
        if (reciente && reciente.version > consulta) return reciente.promesa;
        if (consulta === version.current) {
          setError(mensajeDeError(cause, "No se pudo comprobar la actividad actual."));
        }
        throw cause;
      } finally {
        if (consulta === version.current) setCargando(false);
      }
    })();
    ultimaConsulta.current = { version: consulta, promesa };
    return promesa;
  }, []);

  useEffect(() => {
    let vigente = true;
    let timer: number | undefined;
    async function consultar() {
      try { await actualizar(); } catch { /* El error se expone en el contexto. */ }
      if (vigente) timer = window.setTimeout(() => void consultar(), 3000);
    }
    const alVolver = () => { void actualizar().catch(() => undefined); };
    void consultar();
    window.addEventListener("focus", alVolver);
    return () => {
      vigente = false;
      version.current += 1;
      ultimaConsulta.current = null;
      window.clearTimeout(timer);
      window.removeEventListener("focus", alVolver);
    };
  }, [actualizar]);

  const value = useMemo(() => ({
    activas, cargando, error, actualizar,
    operacion: activaDelGrupo(activas, "DAY_TO_DAY"),
    simulacion: activaDelGrupo(activas, "FIVE_DAY"),
  }), [activas, cargando, error, actualizar]);

  return <Contexto.Provider value={value}>{children}</Contexto.Provider>;
}

export function useEjecucionesActivas() {
  const contexto = useContext(Contexto);
  if (!contexto) throw new Error("useEjecucionesActivas requiere EjecucionesActivasProvider");
  return contexto;
}
