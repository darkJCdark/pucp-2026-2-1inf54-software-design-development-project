package com.pucp.paqrap.modulos.monitoreo.dto;

import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;

import java.time.Instant;
import java.util.List;

/**
 * Detalle de un almacén en el instante simulado de una ejecución (P-07). En el central (stock ilimitado)
 * {@code stock}, {@code capacidad}, {@code ocupacionPct}, las recargas y el {@code historial} son nulos o vacíos.
 * {@code consumoVentana} son los paquetes despachados en las últimas {@code ventanaHoras} horas simuladas; el
 * {@code historial} trae un punto por hora hacia atrás desde el instante, sin pasar del inicio del escenario.
 * El semáforo no se calcula aquí: sus umbrales los aplica el front sobre {@code ocupacionPct}.
 */
public record AlmacenDetalleResponse(String id, WarehouseKind tipo, int x, int y, Instant instante, Integer stock,
                                     Integer capacidad, Double ocupacionPct, int ventanaHoras, long consumoVentana,
                                     Instant ultimaRecarga, Instant proximaRecarga, List<PuntoStock> historial) {

    public record PuntoStock(Instant instante, int stock) {
    }
}
