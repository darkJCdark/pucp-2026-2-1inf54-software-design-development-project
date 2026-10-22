import {
  useEffect,
  useRef,
  useState,
} from "react";

import type {
  MonitoreoResponseDto,
} from "@/api/monitoreo.dto";

import type {
  ReplanificacionResponseDto,
} from "@/api/planificacion.dto";

import type {
  PedidoOperacion,
  VehiculoMapa,
} from "@/features/ejecucion/ejecucion.types";

export type TipoEventoEjecucion =
  | "PEDIDO"
  | "ENTREGA"
  | "RUTA"
  | "REPLANIFICACION"
  | "AVERIA"
  | "BLOQUEO";

export interface EventoEjecucion {
  id: string;
  instante: string;
  tipo: TipoEventoEjecucion;
  titulo: string;
  detalle?: string;
}

const MAX_EVENTOS = 8;

export function useEventosEjecucion(
  monitoreo:
    MonitoreoResponseDto | null,

  pedidos: PedidoOperacion[],

  vehiculos: VehiculoMapa[],

  replanificacion:
    ReplanificacionResponseDto | null,
) {
  const [
    eventos,
    setEventos,
  ] =
    useState<
      EventoEjecucion[]
    >([]);

  const inicializadoRef =
    useRef(false);

  const pedidosRef =
    useRef(
      new Map<
        string,
        PedidoOperacion["estado"]
      >(),
    );

  const vehiculosRef =
    useRef(
      new Map<
        string,
        VehiculoMapa["estado"]
      >(),
    );

  const averiasRef =
    useRef(
      new Set<number>(),
    );

  const bloqueosRef =
    useRef(
      new Set<number>(),
    );

  const replanRef =
    useRef<
      number | null
    >(null);

  useEffect(() => {
    if (!monitoreo) {
      return;
    }

    /*
     * Primera carga:
     * solo guardamos el estado actual
     * para no llenar el feed con todo
     * lo que ya había sucedido antes
     * de entrar a la pantalla.
     */
    if (
      !inicializadoRef.current
    ) {
      pedidosRef.current =
        new Map(
          pedidos.map(
            (pedido) => [
              pedido.id,
              pedido.estado,
            ],
          ),
        );

      vehiculosRef.current =
        new Map(
          vehiculos.map(
            (vehiculo) => [
              vehiculo.id,
              vehiculo.estado,
            ],
          ),
        );

      averiasRef.current =
        new Set(
          monitoreo
            .averiasActivas
            .map(
              (averia) =>
                averia.id,
            ),
        );

      bloqueosRef.current =
        new Set(
          monitoreo
            .bloqueosVigentes
            .map(
              (bloqueo) =>
                bloqueo.id,
            ),
        );

      replanRef.current =
        replanificacion
          ?.numeroReplanificacion ??
        null;

      inicializadoRef.current =
        true;

      return;
    }

    const nuevos:
      EventoEjecucion[] = [];

    const instante =
      monitoreo.instante;

    /*
     * PEDIDOS
     */
    for (
      const pedido of pedidos
    ) {
      const anterior =
        pedidosRef.current.get(
          pedido.id,
        );

      if (!anterior) {
        nuevos.push({
          id:
            `pedido-${pedido.id}-${instante}`,

          instante,

          tipo: "PEDIDO",

          titulo:
            `Pedido ${pedido.id} ingresó`,

          detalle:
            `${pedido.cantidad} paquetes · cliente ${pedido.clienteId}`,
        });
      } else if (
        anterior !==
          pedido.estado &&
        pedido.estado ===
          "ENTREGADO"
      ) {
        nuevos.push({
          id:
            `entrega-${pedido.id}-${instante}`,

          instante:
            pedido.entregadoEn ??
            instante,

          tipo: "ENTREGA",

          titulo:
            `Pedido ${pedido.id} entregado`,

          detalle:
            `${pedido.cantidad} paquetes`,
        });
      }

      pedidosRef.current.set(
        pedido.id,
        pedido.estado,
      );
    }

    /*
     * VEHÍCULOS
     */
    for (
      const vehiculo of vehiculos
    ) {
      const anterior =
        vehiculosRef.current.get(
          vehiculo.id,
        );

      if (
        anterior &&
        anterior !==
          vehiculo.estado
      ) {
        if (
          vehiculo.estado ===
          "EN_RUTA"
        ) {
          nuevos.push({
            id:
              `ruta-inicio-${vehiculo.id}-${instante}`,

            instante,

            tipo: "RUTA",

            titulo:
              `${vehiculo.id} inició ruta`,

            detalle:
              `Carga actual: ${vehiculo.cargaActual}`,
          });
        }

        if (
          anterior ===
            "EN_RUTA" &&
          vehiculo.estado ===
            "DISPONIBLE"
        ) {
          nuevos.push({
            id:
              `ruta-fin-${vehiculo.id}-${instante}`,

            instante,

            tipo: "RUTA",

            titulo:
              `${vehiculo.id} terminó su ruta`,
          });
        }
      }

      vehiculosRef.current.set(
        vehiculo.id,
        vehiculo.estado,
      );
    }

    /*
     * AVERÍAS
     */
    const averiasActuales =
      new Set(
        monitoreo
          .averiasActivas
          .map(
            (averia) =>
              averia.id,
          ),
      );

    for (
      const averia of
      monitoreo.averiasActivas
    ) {
      if (
        !averiasRef.current.has(
          averia.id,
        )
      ) {
        nuevos.push({
          id:
            `averia-${averia.id}`,

          instante,

          tipo: "AVERIA",

          titulo:
            `Avería en ${averia.vehiculoId}`,

          detalle:
            `Tipo ${averia.tipo} · (${averia.x}, ${averia.y})`,
        });
      }
    }

    averiasRef.current =
      averiasActuales;

    /*
     * BLOQUEOS
     */
    const bloqueosActuales =
      new Set(
        monitoreo
          .bloqueosVigentes
          .map(
            (bloqueo) =>
              bloqueo.id,
          ),
      );

    for (
      const bloqueo of
      monitoreo.bloqueosVigentes
    ) {
      if (
        !bloqueosRef.current.has(
          bloqueo.id,
        )
      ) {
        nuevos.push({
          id:
            `bloqueo-${bloqueo.id}`,

          instante,

          tipo: "BLOQUEO",

          titulo:
            `Bloqueo #${bloqueo.id} activo`,

          detalle:
            `${bloqueo.nodos.length} nodos afectados`,
        });
      }
    }

    bloqueosRef.current =
      bloqueosActuales;

    /*
     * REPLANIFICACIÓN
     */
    if (
      replanificacion &&
      replanificacion
        .numeroReplanificacion !==
        replanRef.current
    ) {
      nuevos.push({
        id:
          `replan-${replanificacion.numeroReplanificacion}`,

        instante:
          replanificacion.hora,

        tipo:
          "REPLANIFICACION",

        titulo:
          `SA replanificó · ${nombreMotivo(
            replanificacion.motivo,
          )}`,

        detalle:
          `${replanificacion.rutasConservadas.length} conservadas · ` +
          `${replanificacion.rutasNuevas.length} nuevas · ` +
          `${replanificacion.pedidosReasignados.length} reasignados`,
      });

      replanRef.current =
        replanificacion
          .numeroReplanificacion;
    }

    if (
      nuevos.length > 0
    ) {
      setEventos(
        (actuales) =>
          [
            ...nuevos.reverse(),
            ...actuales,
          ].slice(
            0,
            MAX_EVENTOS,
          ),
      );
    }
  }, [
    monitoreo,
    pedidos,
    replanificacion,
    vehiculos,
  ]);

  return eventos;
}

function nombreMotivo(
  motivo:
    ReplanificacionResponseDto["motivo"],
) {
  switch (motivo) {
    case "INICIO":
      return "inicio";

    case "NUEVO_PEDIDO":
      return "nuevo pedido";

    case "BLOQUEO":
      return "bloqueo";

    case "AVERIA":
      return "avería";

    case "PERIODICO":
      return "ciclo periódico";
  }
}