package com.pucp.paqrap.modulos.monitoreo.dto;

import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;

import java.time.Instant;
import java.util.List;

/**
 * Detalle de una unidad en el instante simulado de una ejecución (P-05). {@code ventanaAlimentacion} es el rango en
 * el que el turno permite el refrigerio, no una hora exacta. {@code rutaId} y {@code proximasEntregas} salen del
 * último plan: {@code rutaId} es nulo si la unidad no tiene entregas pendientes.
 */
public record VehiculoDetalleResponse(String id, VehicleType tipo, VehicleOperationalStatus estado, int x, int y,
                                      int cargaActual, int capacidad, Instant disponibleDesde,
                                      MonitoreoResponse.MotivoDetencion motivoDetencion, Instant detenidoHasta,
                                      Instant instante,
                                      Turno turnoVigente, Turno ventanaAlimentacion, String rutaId,
                                      List<ProximaEntrega> proximasEntregas) {

    public record Turno(Instant inicio, Instant fin) {
    }

    public record ProximaEntrega(int orden, String pedidoId, int x, int y, int paquetes, Instant llegadaPrevista) {
    }
}
