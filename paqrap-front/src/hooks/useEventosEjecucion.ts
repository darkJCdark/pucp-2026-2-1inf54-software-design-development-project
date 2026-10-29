import { useEffect, useRef, useState } from "react";

import type { MonitoreoResponseDto } from "@/api/monitoreo.dto";
import type { ReplanificacionResponseDto } from "@/api/planificacion.dto";
import type {
  MotivoDetencion,
  PedidoOperacion,
  VehiculoMapa,
} from "@/features/ejecucion/ejecucion.types";

export type TipoEventoEjecucion =
  | "PEDIDO"
  | "ENTREGA"
  | "RUTA"
  | "REPLANIFICACION"
  | "AVERIA"
  | "BLOQUEO"
  | "DETENCION";

export interface EventoEjecucion {
  id: string;
  instante: string;
  tipo: TipoEventoEjecucion;
  titulo: string;
  detalle?: string;
}

interface EstadoVehiculoAnterior {
  estado: VehiculoMapa["estado"];
  motivo: MotivoDetencion | null;
}

const MAX_EVENTOS = 10;

export function useEventosEjecucion(
  monitoreo: MonitoreoResponseDto | null,
  pedidos: PedidoOperacion[],
  vehiculos: VehiculoMapa[],
  replanificacion: ReplanificacionResponseDto | null,
) {
  const [eventos, setEventos] = useState<EventoEjecucion[]>([]);
  const inicializadoRef = useRef(false);
  const pedidosRef = useRef(new Map<string, PedidoOperacion["estado"]>());
  const vehiculosRef = useRef(new Map<string, EstadoVehiculoAnterior>());
  const averiasRef = useRef(new Set<number>());
  const bloqueosRef = useRef(new Set<number>());
  const replanRef = useRef<number | null>(null);

  useEffect(() => {
    if (!monitoreo) return;

    if (!inicializadoRef.current) {
      pedidosRef.current = new Map(pedidos.map((pedido) => [pedido.id, pedido.estado]));
      vehiculosRef.current = new Map(
        vehiculos.map((vehiculo) => [
          vehiculo.id,
          { estado: vehiculo.estado, motivo: vehiculo.motivoDetencion },
        ]),
      );
      averiasRef.current = new Set(monitoreo.averiasActivas.map((averia) => averia.id));
      bloqueosRef.current = new Set(monitoreo.bloqueosVigentes.map((bloqueo) => bloqueo.id));
      replanRef.current = replanificacion?.numeroReplanificacion ?? null;
      inicializadoRef.current = true;
      return;
    }

    const nuevos: EventoEjecucion[] = [];
    const instante = monitoreo.instante;

    for (const pedido of pedidos) {
      const anterior = pedidosRef.current.get(pedido.id);
      if (!anterior) {
        nuevos.push({
          id: `pedido-${pedido.id}-${instante}`,
          instante,
          tipo: "PEDIDO",
          titulo: `Pedido ${pedido.id} ingresó`,
          detalle: `${pedido.cantidad} paquetes · cliente ${pedido.clienteId}`,
        });
      } else if (anterior !== pedido.estado && pedido.estado === "ENTREGADO") {
        nuevos.push({
          id: `entrega-${pedido.id}-${instante}`,
          instante: pedido.entregadoEn ?? instante,
          tipo: "ENTREGA",
          titulo: `Pedido ${pedido.id} entregado`,
          detalle: `${pedido.cantidad} paquetes`,
        });
      }
      pedidosRef.current.set(pedido.id, pedido.estado);
    }

    for (const vehiculo of vehiculos) {
      const anterior = vehiculosRef.current.get(vehiculo.id);

      if (anterior && anterior.estado !== vehiculo.estado) {
        if (vehiculo.estado === "EN_RUTA") {
          nuevos.push({
            id: `ruta-inicio-${vehiculo.id}-${instante}`,
            instante,
            tipo: "RUTA",
            titulo: `${vehiculo.id} inició ruta`,
            detalle: `Carga actual: ${vehiculo.cargaActual}`,
          });
        }
        if (anterior.estado === "EN_RUTA" && vehiculo.estado === "DISPONIBLE") {
          nuevos.push({
            id: `ruta-fin-${vehiculo.id}-${instante}`,
            instante,
            tipo: "RUTA",
            titulo: `${vehiculo.id} terminó su ruta`,
          });
        }
      }

      if (anterior && anterior.motivo !== vehiculo.motivoDetencion) {
        if (vehiculo.motivoDetencion) {
          nuevos.push({
            id: `detencion-${vehiculo.id}-${vehiculo.motivoDetencion}-${instante}`,
            instante,
            tipo:
              vehiculo.motivoDetencion === "AVERIA" ? "AVERIA" : "DETENCION",
            titulo: tituloDetencion(vehiculo),
            detalle: vehiculo.detenidoHasta
              ? `Hasta ${formatearHora(vehiculo.detenidoHasta)}`
              : undefined,
          });
        } else if (anterior.motivo) {
          nuevos.push({
            id: `detencion-fin-${vehiculo.id}-${instante}`,
            instante,
            tipo: "DETENCION",
            titulo: `${vehiculo.id} volvió a estar operativo`,
            detalle: `Finalizó ${nombreMotivoDetencion(anterior.motivo).toLowerCase()}`,
          });
        }
      }

      vehiculosRef.current.set(vehiculo.id, {
        estado: vehiculo.estado,
        motivo: vehiculo.motivoDetencion,
      });
    }

    const averiasActuales = new Set(monitoreo.averiasActivas.map((averia) => averia.id));
    for (const averia of monitoreo.averiasActivas) {
      if (!averiasRef.current.has(averia.id)) {
        nuevos.push({
          id: `averia-${averia.id}`,
          instante,
          tipo: "AVERIA",
          titulo: `Avería en ${averia.vehiculoId}`,
          detalle: `Tipo ${averia.tipo} · (${averia.x}, ${averia.y})`,
        });
      }
    }
    averiasRef.current = averiasActuales;

    const bloqueosActuales = new Set(monitoreo.bloqueosVigentes.map((bloqueo) => bloqueo.id));
    for (const bloqueo of monitoreo.bloqueosVigentes) {
      if (!bloqueosRef.current.has(bloqueo.id)) {
        nuevos.push({
          id: `bloqueo-${bloqueo.id}`,
          instante,
          tipo: "BLOQUEO",
          titulo: `Bloqueo #${bloqueo.id} activo`,
          detalle: `${bloqueo.nodos.length} nodos afectados`,
        });
      }
    }
    bloqueosRef.current = bloqueosActuales;

    if (
      replanificacion &&
      replanificacion.numeroReplanificacion !== replanRef.current
    ) {
      nuevos.push({
        id: `replan-${replanificacion.numeroReplanificacion}`,
        instante: replanificacion.hora,
        tipo: "REPLANIFICACION",
        titulo: `SA replanificó · ${nombreMotivo(replanificacion.motivo)}`,
        detalle:
          `${replanificacion.rutasConservadas.length} conservadas · ` +
          `${replanificacion.rutasNuevas.length} nuevas · ` +
          `${replanificacion.pedidosReasignados.length} reasignados`,
      });
      replanRef.current = replanificacion.numeroReplanificacion;
    }

    if (nuevos.length > 0) {
      setEventos((actuales) => [...nuevos.reverse(), ...actuales].slice(0, MAX_EVENTOS));
    }
  }, [monitoreo, pedidos, replanificacion, vehiculos]);

  return eventos;
}

function tituloDetencion(vehiculo: VehiculoMapa) {
  switch (vehiculo.motivoDetencion) {
    case "REFRIGERIO":
      return `${vehiculo.id} inició su refrigerio`;
    case "MANTENIMIENTO":
      return `${vehiculo.id} entró en mantenimiento`;
    case "AVERIA":
      return `${vehiculo.id} quedó fuera de servicio`;
    default:
      return `${vehiculo.id} quedó detenido`;
  }
}

function nombreMotivoDetencion(motivo: MotivoDetencion) {
  switch (motivo) {
    case "REFRIGERIO":
      return "Refrigerio";
    case "MANTENIMIENTO":
      return "Mantenimiento";
    case "AVERIA":
      return "Avería";
  }
}

function nombreMotivo(motivo: ReplanificacionResponseDto["motivo"]) {
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

function formatearHora(valor: string) {
  return new Intl.DateTimeFormat("es-PE", {
    timeZone: "America/Lima",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(new Date(valor));
}
