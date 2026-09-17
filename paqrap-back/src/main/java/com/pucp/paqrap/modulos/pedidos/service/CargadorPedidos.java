package com.pucp.paqrap.modulos.pedidos.service;

import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.redvial.entity.Location;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Transforms the course sales-file notation into domain orders. */
public final class CargadorPedidos {
    private static final Pattern FORMATO = Pattern.compile(
            "^(\\d{2})d(\\d{2})h(\\d{2})m:(\\d+),(\\d+),([A-Za-z][A-Za-z0-9]*),(\\d+),(\\d+)$");
    private static final Set<Integer> PLAZOS_ADMITIDOS = Set.of(4, 8, 12, 18, 36);

    public List<Order> cargar(Path archivo, YearMonth periodo, ZoneId zona) throws IOException {
        Objects.requireNonNull(archivo, "archivo is required");
        Objects.requireNonNull(periodo, "periodo is required");
        Objects.requireNonNull(zona, "zona is required");

        List<Order> pedidos = new ArrayList<>();
        List<String> lineas = Files.readAllLines(archivo);
        for (int indice = 0; indice < lineas.size(); indice++) {
            pedidos.add(parsearLinea(lineas.get(indice), indice + 1, periodo, zona));
        }
        return List.copyOf(pedidos);
    }

    public Order parsearLinea(String linea, int numeroLinea, YearMonth periodo, ZoneId zona) {
        Objects.requireNonNull(linea, "linea is required");
        Objects.requireNonNull(periodo, "periodo is required");
        Objects.requireNonNull(zona, "zona is required");
        Matcher coincidencia = FORMATO.matcher(linea);
        if (!coincidencia.matches()) {
            throw error(numeroLinea, "formato invalido: " + linea);
        }
        try {
            int dia = entero(coincidencia.group(1));
            int hora = entero(coincidencia.group(2));
            int minuto = entero(coincidencia.group(3));
            int x = entero(coincidencia.group(4));
            int y = entero(coincidencia.group(5));
            int cantidad = entero(coincidencia.group(7));
            int plazoHoras = entero(coincidencia.group(8));
            if (cantidad <= 0) {
                throw error(numeroLinea, "la cantidad debe ser positiva");
            }
            if (!PLAZOS_ADMITIDOS.contains(plazoHoras)) {
                throw error(numeroLinea, "plazo no admitido: " + plazoHoras);
            }
            Instant registro = LocalDateTime.of(periodo.getYear(), periodo.getMonthValue(), dia, hora, minuto)
                    .atZone(zona).toInstant();
            Location destino = new Location(x, y);
            String idCliente = coincidencia.group(6);
            String idPedido = idCliente + "-" + String.format("%04d", numeroLinea);
            return new Order(idPedido, destino, cantidad, registro, registro.plusSeconds(plazoHoras * 3_600L));
        } catch (DateTimeException | NumberFormatException excepcion) {
            throw error(numeroLinea, "fecha, hora o coordenada invalida", excepcion);
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
