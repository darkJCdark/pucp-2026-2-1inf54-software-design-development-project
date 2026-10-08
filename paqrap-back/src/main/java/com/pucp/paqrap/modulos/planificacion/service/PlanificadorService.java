package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.dto.ModoOperacion;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.SolicitudPlanificacion;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

import java.time.Instant;
import java.util.List;

/** Punto de entrada del planificador para el resto del backend (servicios y controllers). */
public interface PlanificadorService {

    PlanResponse planificar(SolicitudPlanificacion solicitud);

    /** Atajo con semilla generada por el servicio y el presupuesto de tiempo por defecto. */
    default PlanResponse planificar(ModoOperacion modo, Instant ahora,
                                    List<Order> pedidosPendientes, List<RoadBlock> bloqueosActivos) {
        return planificar(new SolicitudPlanificacion(modo, ahora, pedidosPendientes, bloqueosActivos, null, null));
    }
}
