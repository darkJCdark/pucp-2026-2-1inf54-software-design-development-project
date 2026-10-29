package com.pucp.paqrap.modulos.monitoreo.dto;

import com.pucp.paqrap.modulos.almacenes.persistence.MovementType;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;

import java.time.Instant;
import java.util.List;

/**
 * Detalle de un almacén en el instante simulado de una ejecución (P-07). En el central (stock ilimitado),
 * {@code stock}, {@code capacidad}, {@code ocupacionPct}, las recargas y el historial horario son nulos o vacíos.
 * Los movimientos sí se conservan también para el central, aun cuando sus saldos before/after sean nulos.
 */
public record AlmacenDetalleResponse(String id, WarehouseKind tipo, int x, int y, Instant instante, Integer stock,
                                     Integer capacidad, Double ocupacionPct, int ventanaHoras, long consumoVentana,
                                     Instant ultimaRecarga, Instant proximaRecarga, List<PuntoStock> historial,
                                     boolean alertaBajoStock, NivelAlerta nivelAlerta,
                                     List<MovimientoInventarioResponse> movimientos) {

    public enum NivelAlerta { VERDE, AMBAR, ROJO }

    public record PuntoStock(Instant instante, int stock) {
    }

    public record MovimientoInventarioResponse(Long id, Instant instante, MovementType tipo, int cantidad,
                                               Integer stockAntes, Integer stockDespues, String pedidoId) {
    }
}
