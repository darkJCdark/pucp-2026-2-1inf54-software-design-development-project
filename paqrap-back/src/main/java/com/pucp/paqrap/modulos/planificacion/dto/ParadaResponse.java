package com.pucp.paqrap.modulos.planificacion.dto;

import java.time.Instant;

/**
 * Parada de una ruta. {@code tipo} es "ENTREGA" (usa {@code pedidoId}) o "ALMACEN" (usa
 * {@code almacenId}); el campo que no corresponde es null. En una entrega {@code paquetes} son los
 * paquetes entregados; en un almacén, los paquetes recargados. Los tiempos y cargas son null si la
 * ruta no pudo programarse.
 */
public record ParadaResponse(
        int orden,
        String tipo,
        String pedidoId,
        String almacenId,
        int x,
        int y,
        int paquetes,
        Instant llegada,
        Instant fin,
        Integer cargaAntes,
        Integer cargaDespues
) {
    public static final String ENTREGA = "ENTREGA";
    public static final String ALMACEN = "ALMACEN";
}
