package com.pucp.paqrap.modulos.pedidos.service;

import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.redvial.entity.Location;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Transforma la notación de los archivos {@code ventas.AAAAMM.txt} del curso en pedidos. */
public final class CargadorPedidos {
    /** ddHHhMMm:x,y,cliente,paquetes,plazoHoras, p. ej. 01d01h30m:56,30,c4910,02,36 */
    private static final Pattern FORMATO = Pattern.compile(
            "^(\\d{2})d(\\d{2})h(\\d{2})m:(\\d+),(\\d+),([A-Za-z][A-Za-z0-9]*),(\\d+),(\\d+)$");

    /** Pedido leído de una línea. El id es {@code AAAAMM-NNNNN} (mes y número de línea), único entre archivos. */
    public record PedidoArchivo(String id, String clienteId, Location destino, int paquetes, Instant registradoEn,
                                DeliveryType tipoEntrega, int horasPrometidas) {
    }

    /** Parsea las líneas de un archivo mensual; las líneas en blanco se ignoran. */
    public List<PedidoArchivo> cargar(List<String> lineas, YearMonth periodo, ZoneId zona) {
        Objects.requireNonNull(lineas, "lineas is required");
        Objects.requireNonNull(periodo, "periodo is required");
        Objects.requireNonNull(zona, "zona is required");

        List<PedidoArchivo> pedidos = new ArrayList<>();
        for (int indice = 0; indice < lineas.size(); indice++) {
            String linea = lineas.get(indice).strip();
            if (!linea.isEmpty()) {
                pedidos.add(parsearLinea(linea, indice + 1, periodo, zona));
            }
        }
        return List.copyOf(pedidos);
    }

    public PedidoArchivo parsearLinea(String linea, int numeroLinea, YearMonth periodo, ZoneId zona) {
        Matcher coincidencia = FORMATO.matcher(linea);
        if (!coincidencia.matches()) {
            throw error(numeroLinea, "formato invalido: " + linea);
        }
        try {
            Instant registro = LocalDateTime.of(periodo.getYear(), periodo.getMonthValue(),
                    entero(coincidencia.group(1)), entero(coincidencia.group(2)), entero(coincidencia.group(3)))
                    .atZone(zona).toInstant();
            Location destino = new Location(entero(coincidencia.group(4)), entero(coincidencia.group(5)));
            int paquetes = entero(coincidencia.group(7));
            if (paquetes <= 0) {
                throw error(numeroLinea, "la cantidad debe ser positiva");
            }
            int plazoHoras = entero(coincidencia.group(8));
            String id = String.format("%04d%02d-%05d", periodo.getYear(), periodo.getMonthValue(), numeroLinea);
            return new PedidoArchivo(id, coincidencia.group(6), destino, paquetes, registro,
                    DeliveryType.segunPlazo(plazoHoras), plazoHoras);
        } catch (DateTimeException | NumberFormatException excepcion) {
            throw error(numeroLinea, "fecha, hora o coordenada invalida", excepcion);
        } catch (IllegalArgumentException excepcion) {
            if (excepcion.getMessage().startsWith("Pedido en linea")) {
                throw excepcion;
            }
            throw error(numeroLinea, excepcion.getMessage(), excepcion);
        }
    }

    private int entero(String texto) {
        return Integer.parseInt(texto);
    }

    private IllegalArgumentException error(int numeroLinea, String detalle) {
        return new IllegalArgumentException("Pedido en linea " + numeroLinea + ": " + detalle);
    }

    private IllegalArgumentException error(int numeroLinea, String detalle, Exception causa) {
        return new IllegalArgumentException("Pedido en linea " + numeroLinea + ": " + detalle, causa);
    }
}
