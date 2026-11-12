import {
  useCallback,
  useEffect,
  useRef,
} from "react";

import {
  flushSync,
} from "react-dom";

import type Konva from "konva";

import {
  interpolarVista,
} from "@/features/ejecucion/vistaMapa";

export interface VistaCompositor {
  escala: number;
  x: number;
  y: number;
}

interface PanActivo {
  clientX: number;
  clientY: number;

  base:
    VistaCompositor;
}

interface Props {
  vista:
    VistaCompositor;

  onConfirmar:
    (
      vista:
        VistaCompositor,
    ) => void;
}

export function usePanZoomCompositor({
  vista,
  onConfirmar,
}: Props) {
  const stageRef =
    useRef<
      Konva.Stage | null
    >(null);

  const vistaConfirmadaRef =
    useRef(
      vista,
    );

  const vistaPreviewRef =
    useRef(
      vista,
    );

  const previewActivoRef =
    useRef(
      false,
    );

  const panRef =
    useRef<
      PanActivo | null
    >(null);

  const zoomTimerRef =
    useRef<
      number | null
    >(null);

  /*
   * Animación de enfoque (centrar en un vehículo).
   * Guarda el id de requestAnimationFrame para poder cancelarla
   * si el usuario arrastra o usa la rueda mientras se mueve.
   */
  const animacionRef =
    useRef<
      number | null
    >(null);

  const cancelarAnimacion =
    useCallback(
      () => {
        if (
          animacionRef.current !==
          null
        ) {
          window.cancelAnimationFrame(
            animacionRef.current,
          );

          animacionRef.current =
            null;
        }
      },
      [],
    );

  useEffect(
    () =>
      cancelarAnimacion,
    [
      cancelarAnimacion,
    ],
  );

  useEffect(() => {
    if (
      previewActivoRef.current
    ) {
      return;
    }

    vistaConfirmadaRef.current =
      vista;

    vistaPreviewRef.current =
      vista;
  }, [
    vista,
  ]);

  const aplicarPreview =
    useCallback(
      (
        siguiente:
          VistaCompositor,
      ) => {
        const stage =
          stageRef.current;

        if (!stage) {
          return;
        }

        const base =
          vistaConfirmadaRef.current;

        const ratio =
          siguiente.escala /
          base.escala;

        /*
         * Transformación necesaria para pasar
         * de la vista actualmente dibujada por
         * Konva a la vista visual deseada.
         */
        const translateX =
          siguiente.x -
          ratio *
            base.x;

        const translateY =
          siguiente.y -
          ratio *
            base.y;

        const contenedor =
          stage.container();

        contenedor.style.transformOrigin =
          "0 0";

        contenedor.style.willChange =
          "transform";

        contenedor.style.transform =
          `translate3d(${translateX}px, ${translateY}px, 0) scale(${ratio})`;

        vistaPreviewRef.current =
          siguiente;

        previewActivoRef.current =
          true;
      },
      [],
    );

  const confirmarPreview =
    useCallback(
      () => {
        if (
          !previewActivoRef.current
        ) {
          return;
        }

        const siguiente =
          vistaPreviewRef.current;

        const stage =
          stageRef.current;

        /*
         * Actualizamos Konva una sola vez.
         */
        vistaConfirmadaRef.current =
          siguiente;

        flushSync(() => {
          onConfirmar(
            siguiente,
          );
        });

        if (stage) {
          const contenedor =
            stage.container();

          /*
           * Ahora que Konva ya tiene la nueva
           * transformación real, eliminamos la
           * transformación CSS temporal.
           */
          contenedor.style.transform =
            "";

          contenedor.style.willChange =
            "";
        }

        previewActivoRef.current =
          false;
      },
      [
        onConfirmar,
      ],
    );

  const iniciarPan =
    useCallback(
      (
        clientX:
          number,

        clientY:
          number,
      ) => {
        cancelarAnimacion();

        /*
         * Si había un zoom temporal pendiente,
         * lo consolidamos primero.
         */
        confirmarPreview();

        panRef.current = {
          clientX,
          clientY,

          base:
            vistaConfirmadaRef.current,
        };
      },
      [
        cancelarAnimacion,
        confirmarPreview,
      ],
    );

  useEffect(() => {
    function mover(
      event:
        MouseEvent,
    ) {
      const pan =
        panRef.current;

      if (!pan) {
        return;
      }

      const deltaX =
        event.clientX -
        pan.clientX;

      const deltaY =
        event.clientY -
        pan.clientY;

      aplicarPreview({
        ...pan.base,

        x:
          pan.base.x +
          deltaX,

        y:
          pan.base.y +
          deltaY,
      });
    }

    function terminar() {
      if (
        !panRef.current
      ) {
        return;
      }

      panRef.current =
        null;

      confirmarPreview();
    }

    window.addEventListener(
      "mousemove",
      mover,
    );

    window.addEventListener(
      "mouseup",
      terminar,
    );

    return () => {
      window.removeEventListener(
        "mousemove",
        mover,
      );

      window.removeEventListener(
        "mouseup",
        terminar,
      );
    };
  }, [
    aplicarPreview,
    confirmarPreview,
  ]);

  const aplicarZoom =
    useCallback(
      (
        punteroX:
          number,

        punteroY:
          number,

        deltaY:
          number,
      ) => {
        cancelarAnimacion();

        const actual =
          previewActivoRef.current
            ? vistaPreviewRef.current
            : vistaConfirmadaRef.current;

        const factor =
          deltaY > 0
            ? 0.9
            : 1.1;

        const nuevaEscala =
          Math.min(
            3.5,

            Math.max(
              0.75,

              actual.escala *
                factor,
            ),
          );

        const puntoMapaX =
          (
            punteroX -
            actual.x
          ) /
          actual.escala;

        const puntoMapaY =
          (
            punteroY -
            actual.y
          ) /
          actual.escala;

        const siguiente = {
          escala:
            nuevaEscala,

          x:
            punteroX -
            puntoMapaX *
              nuevaEscala,

          y:
            punteroY -
            puntoMapaY *
              nuevaEscala,
        };

        /*
         * Solo transformación CSS.
         * Cero redraw de Konva durante la rueda.
         */
        aplicarPreview(
          siguiente,
        );

        if (
          zoomTimerRef.current !==
          null
        ) {
          window.clearTimeout(
            zoomTimerRef.current,
          );
        }

        /*
         * Cuando el usuario deja de hacer zoom,
         * consolidamos una sola vez.
         */
        zoomTimerRef.current =
          window.setTimeout(
            () => {
              confirmarPreview();

              zoomTimerRef.current =
                null;
            },
            120,
          );
      },
      [
        aplicarPreview,
        cancelarAnimacion,
        confirmarPreview,
      ],
    );

  /*
   * Lleva la cámara a `destino` en `duracionMs` usando la misma
   * vista previa CSS que el zoom con rueda: no se redibuja Konva
   * en cada cuadro, solo una vez al final (confirmarPreview).
   */
  const animarHacia =
    useCallback(
      (
        destino:
          VistaCompositor,

        duracionMs:
          number,
      ) => {
        cancelarAnimacion();

        if (
          zoomTimerRef.current !==
          null
        ) {
          window.clearTimeout(
            zoomTimerRef.current,
          );

          zoomTimerRef.current =
            null;
        }

        confirmarPreview();

        if (
          !stageRef.current ||
          duracionMs <= 0
        ) {
          vistaConfirmadaRef.current =
            destino;

          vistaPreviewRef.current =
            destino;

          onConfirmar(
            destino,
          );

          return;
        }

        const desde =
          vistaConfirmadaRef.current;

        const inicio =
          performance.now();

        const paso = (
          ahora: number,
        ) => {
          const t =
            Math.min(
              1,
              (ahora - inicio) /
                duracionMs,
            );

          aplicarPreview(
            interpolarVista(
              desde,
              destino,
              t,
            ),
          );

          if (t < 1) {
            animacionRef.current =
              window.requestAnimationFrame(
                paso,
              );

            return;
          }

          animacionRef.current =
            null;

          confirmarPreview();
        };

        animacionRef.current =
          window.requestAnimationFrame(
            paso,
          );
      },
      [
        aplicarPreview,
        cancelarAnimacion,
        confirmarPreview,
        onConfirmar,
      ],
    );

  return {
    stageRef,
    iniciarPan,
    aplicarZoom,
    animarHacia,
  };
}