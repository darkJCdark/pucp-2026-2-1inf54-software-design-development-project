package com.pucp.paqrap.modulos.planificacion.dto;

import java.time.Instant;
import java.util.List;

/**
 * Parada programada de una ruta.
 *
 * <p>Tipos soportados:</p>
 * <ul>
 *   <li>ENTREGA: utiliza {@code pedidoId}.</li>
 *   <li>ALMACEN: utiliza {@code almacenId}.</li>
 *   <li>TRANSBORDO_SALIDA / TRANSBORDO_ENTRADA: utiliza los campos de transferencia.</li>
 * </ul>
 *
 * <p>{@code inicioServicio} puede ser posterior a {@code llegada} cuando la
 * unidad debe esperar a su contraparte en un encuentro sincronizado.</p>
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
        List<Punto> camino,
        Instant inicioServicio,
        String transferenciaId,
        String vehiculoContraparteId,
        String direccionTransferencia
) {

    public static final String ENTREGA = "ENTREGA";
    public static final String ALMACEN = "ALMACEN";
    public static final String TRANSBORDO_SALIDA = "TRANSBORDO_SALIDA";
    public static final String TRANSBORDO_ENTRADA = "TRANSBORDO_ENTRADA";

    public record Punto(int x, int y) {
    }

    /** Conserva el contrato JSON/Java anterior. */
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
            Instant inicioRefrigerio,
            List<Punto> camino
    ) {
        this(orden, tipo, pedidoId, almacenId, x, y, paquetes, llegada, fin,
                cargaAntes, cargaDespues, inicioRefrigerio, camino,
                llegada, null, null, null);
    }

    /** Parada sin refrigerio ni camino conocido. */
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
        this(orden, tipo, pedidoId, almacenId, x, y, paquetes, llegada, fin,
                cargaAntes, cargaDespues, null, null, llegada, null, null, null);
    }

    /** Parada sin camino conocido. */
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
        this(orden, tipo, pedidoId, almacenId, x, y, paquetes, llegada, fin,
                cargaAntes, cargaDespues, inicioRefrigerio, null,
                llegada, null, null, null);
    }

    public boolean esTransbordo() {
        return TRANSBORDO_SALIDA.equals(tipo) || TRANSBORDO_ENTRADA.equals(tipo);
    }
}
