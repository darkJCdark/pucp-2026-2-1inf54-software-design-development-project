package com.pucp.paqrap.modulos.planificacion.dto;

import java.time.Instant;

/**
 * Parada de una ruta. {@code tipo} es "ENTREGA" (usa {@code pedidoId}) o "ALMACEN" (usa
 * {@code almacenId}); el campo que no corresponde es null. En una entrega {@code paquetes} son los
 * paquetes entregados; en un almacén, los paquetes recargados. Los tiempos y cargas son null si la
 * ruta no pudo programarse.
 *
 * <p>{@code inicioRefrigerio}: si el conductor toma su refrigerio al llegar a esta parada, la hora en que llegó
 * al lugar; descansa en la parada hasta {@code llegada} (una hora después). Null si aquí no hay refrigerio.
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
        Integer cargaDespues,
        Instant inicioRefrigerio
) {
    public static final String ENTREGA = "ENTREGA";
    public static final String ALMACEN = "ALMACEN";

    /** Parada sin refrigerio. */
    public ParadaResponse(int orden, String tipo, String pedidoId, String almacenId, int x, int y, int paquetes,
                          Instant llegada, Instant fin, Integer cargaAntes, Integer cargaDespues) {
        this(orden, tipo, pedidoId, almacenId, x, y, paquetes, llegada, fin, cargaAntes, cargaDespues, null);
    }
}
