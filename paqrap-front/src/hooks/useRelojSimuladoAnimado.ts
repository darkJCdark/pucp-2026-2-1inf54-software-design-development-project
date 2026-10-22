import {
  useEffect,
  useRef,
  useState,
} from "react";

import type {
  EstadoEscenarioBackend,
} from "@/api/escenarios.dto";

const FPS_OBJETIVO = 30;

const INTERVALO_FRAME_MS =
  1000 / FPS_OBJETIVO;

function ahoraRealMs() {
  if (
    typeof performance !==
    "undefined"
  ) {
    return performance.now();
  }

  return Date.now();
}

/**
 * Genera un reloj visual suave entre
 * respuestas del backend.
 *
 * El backend continúa siendo la fuente
 * real del tiempo.
 */
export function useRelojSimuladoAnimado(
  instanteServidor: string,
  factorAceleracion: number,
  estado: EstadoEscenarioBackend,
) {
  const instanteInicial =
    new Date(
      instanteServidor,
    ).getTime();

  const [
    instanteAnimadoMs,
    setInstanteAnimadoMs,
  ] = useState(
    Number.isFinite(
      instanteInicial,
    )
      ? instanteInicial
      : Date.now(),
  );

  const baseSimuladaRef =
    useRef(
      instanteAnimadoMs,
    );

  const baseRealRef =
    useRef(
      ahoraRealMs(),
    );

  useEffect(() => {
    const servidorMs =
      new Date(
        instanteServidor,
      ).getTime();

    if (
      !Number.isFinite(
        servidorMs,
      )
    ) {
      return;
    }

    baseSimuladaRef.current =
      servidorMs;

    baseRealRef.current =
      ahoraRealMs();

    setInstanteAnimadoMs(
      servidorMs,
    );
  }, [instanteServidor]);

  useEffect(() => {
    if (
      estado !== "RUNNING"
    ) {
      return;
    }

    let frameId = 0;
    let ultimoFrame = 0;

    const animar = (
      timestamp: number,
    ) => {
      if (
        timestamp -
          ultimoFrame >=
        INTERVALO_FRAME_MS
      ) {
        const transcurridoReal =
          ahoraRealMs() -
          baseRealRef.current;

        setInstanteAnimadoMs(
          baseSimuladaRef.current +
            transcurridoReal *
              factorAceleracion,
        );

        ultimoFrame =
          timestamp;
      }

      frameId =
        requestAnimationFrame(
          animar,
        );
    };

    frameId =
      requestAnimationFrame(
        animar,
      );

    return () => {
      cancelAnimationFrame(
        frameId,
      );
    };
  }, [
    estado,
    factorAceleracion,
  ]);

  return instanteAnimadoMs;
}