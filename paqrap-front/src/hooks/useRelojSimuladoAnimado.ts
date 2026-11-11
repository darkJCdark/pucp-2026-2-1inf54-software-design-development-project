import {
  useEffect,
  useRef,
  useState,
} from "react";

import type {
  EstadoEscenarioBackend,
} from "@/api/escenarios.dto";

const FPS_OBJETIVO = 10;

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

export function useRelojSimuladoAnimado(
  instanteServidor: string,
  factorAceleracion: number,
  estado: EstadoEscenarioBackend,

  /*
   * Permite suspender completamente la
   * animación visual, por ejemplo mientras
   * el usuario arrastra el mapa.
   */
  activo = true,
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

  /*
   * El backend sigue siendo la fuente
   * verdadera del reloj.
   */
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

    /*
     * Mientras el reloj visual está suspendido
     * no provocamos otro render únicamente para
     * actualizar la animación.
     *
     * Al reactivarlo se interpolará desde la
     * última referencia recibida del backend.
     */
    if (activo) {
      setInstanteAnimadoMs(
        servidorMs,
      );
    }
  }, [
    instanteServidor,
    activo,
  ]);

  useEffect(() => {
    if (
      estado !==
        "RUNNING" ||
      !activo
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

        const siguienteInstante =
          baseSimuladaRef.current +
          transcurridoReal *
            factorAceleracion;

        setInstanteAnimadoMs(
          siguienteInstante,
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
    activo,
    estado,
    factorAceleracion,
  ]);

  /*
   * Al reactivar la animación hacemos un
   * salto inmediato al tiempo que corresponde.
   */
  useEffect(() => {
    if (!activo) {
      return;
    }

    const transcurridoReal =
      ahoraRealMs() -
      baseRealRef.current;

    setInstanteAnimadoMs(
      baseSimuladaRef.current +
        transcurridoReal *
          factorAceleracion,
    );
  }, [
    activo,
    factorAceleracion,
  ]);

  return instanteAnimadoMs;
}