package com.pucp.paqrap.modulos.monitoreo.dto;

import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
import com.pucp.paqrap.modulos.escenarios.dto.EscenarioResponse;
import com.pucp.paqrap.modulos.escenarios.service.DetectorColapso;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.incidencias.dto.AveriaResponse;
import com.pucp.paqrap.modulos.pedidos.service.PedidoService;
import com.pucp.paqrap.modulos.redvial.dto.BloqueoResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Estado de la operación en el instante simulado de un escenario (CU-12): el instante actual si está activo, el
 * final si terminó y el inicial si aún no empezó. {@code colapso} es nulo mientras no haya colapso logístico.
 */
public record MonitoreoResponse(EscenarioResponse escenario, Instant instante, PedidoService.ResumenPedidos pedidos,
                                List<Vehiculo> vehiculos, List<Almacen> almacenes, List<BloqueoResponse> bloqueosVigentes,
                                List<AveriaResponse> averiasActivas, Ejecucion ejecucion,
                                DetectorColapso.Colapso colapso) {

    /**
     * Resultados de ejecutar el plan hasta {@code instante}: pedidos completos, cuántos llegaron dentro del plazo,
     * paquetes entregados (incluye parciales; nulo si se lee del resultado guardado, que no los registra), distancia y
     * costo recorridos, y vehículos en ruta por tipo.
     */
    public record Ejecucion(int pedidosEntregados, int entregadosATiempo, Integer paquetesEntregados, double distanciaKm,
                            double costo, Map<VehicleType, Long> vehiculosEnRuta) {
    }

    /** {@code averiadoHasta} es nulo si el vehículo no tiene una avería en curso. */
    public record Vehiculo(String id, VehicleType tipo, VehicleOperationalStatus estado, int x, int y, int cargaActual,
                           Instant averiadoHasta, MotivoDetencion motivoDetencion, Instant detenidoHasta) {
    }

    /**
     * Por qué un vehículo está detenido en {@code instante} (O3); si coinciden varios se informa el de mayor
     * prioridad: avería, luego mantenimiento, luego refrigerio. {@code detenidoHasta} es cuándo vuelve a operar.
     */
    public enum MotivoDetencion {
        AVERIA,
        MANTENIMIENTO,
        REFRIGERIO
    }

    /** {@code stock} y {@code capacidad} son nulos en el almacén central (ilimitado). */
    public record Almacen(String id, WarehouseKind tipo, int x, int y, Integer stock, Integer capacidad) {
    }
}
