package com.pucp.paqrap.modulos.monitoreo.dto;

import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
import com.pucp.paqrap.modulos.escenarios.dto.EscenarioResponse;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.incidencias.dto.AveriaResponse;
import com.pucp.paqrap.modulos.pedidos.service.PedidoService;
import com.pucp.paqrap.modulos.redvial.dto.BloqueoResponse;

import java.time.Instant;
import java.util.List;

/**
 * Estado de la operación en el instante simulado de un escenario (CU-12): el instante actual si está activo, el
 * final si terminó y el inicial si aún no empezó.
 */
public record MonitoreoResponse(EscenarioResponse escenario, Instant instante, PedidoService.ResumenPedidos pedidos,
                                List<Vehiculo> vehiculos, List<Almacen> almacenes, List<BloqueoResponse> bloqueosVigentes,
                                List<AveriaResponse> averiasActivas) {

    /** {@code averiadoHasta} es nulo si el vehículo no tiene una avería en curso. */
    public record Vehiculo(String id, VehicleType tipo, VehicleOperationalStatus estado, int x, int y, int cargaActual,
                           Instant averiadoHasta) {
    }

    /** {@code stock} y {@code capacidad} son nulos en el almacén central (ilimitado). */
    public record Almacen(String id, WarehouseKind tipo, int x, int y, Integer stock, Integer capacidad) {
    }
}
