package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.dto.AuditoriaPlanResponse;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Auditoría de planes con el evaluador original: violaciones y estado de los pedidos en un instante. */
public interface AuditoriaPlanService {

    /** Audita el plan vigente de una ejecución de la replanificación a la hora indicada. */
    AuditoriaPlanResponse auditarPlanVigente(long ejecucionId, Instant ahora);

    /** Audita cualquier plan armado con objetos del dominio. */
    AuditoriaPlanResponse auditar(OperationalPlan plan, OperationalSnapshot snapshot, Collection<Order> pedidos,
                                  List<RoadBlock> bloqueos, Instant ahora);
}
