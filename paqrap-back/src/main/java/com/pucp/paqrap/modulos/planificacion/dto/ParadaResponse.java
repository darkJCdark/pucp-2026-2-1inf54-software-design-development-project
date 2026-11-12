package com.pucp.paqrap.modulos.planificacion.dto;

import java.time.Instant;
import java.util.List;

/**
 * Parada de una ruta.
 *
 * {@code tipo} es:
 *
 * - ENTREGA: utiliza pedidoId.
 * - ALMACEN: utiliza almacenId.
 * - TRASVASE: recibe los paquetes de un vehículo averiado; pedidoId y almacenId son null.
 *
 * El campo que no corresponde es null.
 *
 * En una entrega, {@code paquetes} representa
 * paquetes entregados.
 *
 * En un almacén, representa paquetes recargados.
 *
 * Tiempos:
 *
 * {@code llegada}
 *     Hora física en la que el vehículo llega
 *     a la parada. Para un pedido, esta es la
 *     hora utilizada para comprobar el SLA.
 *
 * {@code inicioRefrigerio}
 *     Si el conductor toma refrigerio después
 *     de atender esta parada, indica cuándo
 *     comienza el refrigerio.
 *
 *     En una entrega normalmente equivale a:
 *
 *       llegada + 1 hora de servicio.
 *
 *     Es null cuando no se toma refrigerio.
 *
 * {@code fin}
 *     Hora en la que el vehículo puede abandonar
 *     la parada.
 *
 *     Incluye:
 *
 *       servicio
 *       +
 *       refrigerio, cuando corresponda.
 *
 * {@code camino}
 *     Nodos reales de la cuadrícula utilizados
 *     para llegar a esta parada. Incluye origen
 *     y destino y respeta los bloqueos.
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

        Instant inicioRefrigerio,

        List<Punto> camino

) {

    public static final String ENTREGA =
            "ENTREGA";

    public static final String ALMACEN =
            "ALMACEN";

    /** Trasvase: recibe la carga del vehículo averiado ({@code paquetes}) en el lugar de la avería. */
    public static final String TRASVASE =
            "TRASVASE";

    /**
     * Nodo de la cuadrícula.
     */
    public record Punto(
            int x,
            int y
    ) {
    }

    /**
     * Parada sin refrigerio ni camino conocido.
     */
    public ParadaResponse(
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

        this(
                orden,
                tipo,
                pedidoId,
                almacenId,
                x,
                y,
                paquetes,
                llegada,
                fin,
                cargaAntes,
                cargaDespues,
                null,
                null
        );
    }

    /**
     * Parada sin camino conocido.
     */
    public ParadaResponse(
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

        this(
                orden,
                tipo,
                pedidoId,
                almacenId,
                x,
                y,
                paquetes,
                llegada,
                fin,
                cargaAntes,
                cargaDespues,
                inicioRefrigerio,
                null
        );
    }
}