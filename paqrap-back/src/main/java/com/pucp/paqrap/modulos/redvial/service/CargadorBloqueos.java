package com.pucp.paqrap.modulos.redvial.service;

import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Transforms the course blocked-polyline notation into time-bounded road blocks. */
public final class CargadorBloqueos {
    private static final Pattern MARCA_TIEMPO = Pattern.compile("^(\\d{2})d(\\d{2})h(\\d{2})m$");

    public List<RoadBlock> cargar(Path archivo, YearMonth periodo, ZoneId zona) throws IOException {
        Objects.requireNonNull(archivo, "archivo is required");
        Objects.requireNonNull(periodo, "periodo is required");
        Objects.requireNonNull(zona, "zona is required");

        List<RoadBlock> bloqueos = new ArrayList<>();
        List<String> lineas = Files.readAllLines(archivo);
        for (int indice = 0; indice < lineas.size(); indice++) {
            bloqueos.add(parsearLinea(lineas.get(indice), indice + 1, periodo, zona));
        }
        return List.copyOf(bloqueos);
    }

    public RoadBlock parsearLinea(String linea, int numeroLinea, YearMonth periodo, ZoneId zona) {
        Objects.requireNonNull(linea, "linea is required");
        Objects.requireNonNull(periodo, "periodo is required");
        Objects.requireNonNull(zona, "zona is required");
        String[] partes = linea.split(":", -1);
        if (partes.length != 2) {
            throw error(numeroLinea, "formato invalido: " + linea);
        }
        String[] intervalo = partes[0].split("-", -1);
        if (intervalo.length != 2) {
            throw error(numeroLinea, "intervalo invalido");
        }
        try {
            Instant inicio = parsearMarca(intervalo[0], periodo, zona, numeroLinea);
            Instant fin = parsearMarca(intervalo[1], periodo, zona, numeroLinea);
            String[] coordenadas = partes[1].split(",", -1);
            if (coordenadas.length < 4 || coordenadas.length % 2 != 0) {
                throw error(numeroLinea, "la polilinea debe contener pares de coordenadas y al menos un tramo");
            }
            List<Location> nodos = new ArrayList<>();
            for (int indice = 0; indice < coordenadas.length; indice += 2) {
                nodos.add(new Location(Integer.parseInt(coordenadas[indice]), Integer.parseInt(coordenadas[indice + 1])));
            }
            return new RoadBlock(inicio, fin, nodos);
        } catch (DateTimeException | NumberFormatException excepcion) {
            throw error(numeroLinea, "fecha, hora o coordenada invalida", excepcion);
        } catch (IllegalArgumentException excepcion) {
            if (excepcion.getMessage().startsWith("Bloqueo en linea")) {
                throw excepcion;
            }
            throw error(numeroLinea, excepcion.getMessage(), excepcion);
        }
    }

    private Instant parsearMarca(String texto, YearMonth periodo, ZoneId zona, int numeroLinea) {
        Matcher coincidencia = MARCA_TIEMPO.matcher(texto);
        if (!coincidencia.matches()) {
            throw error(numeroLinea, "marca de tiempo invalida: " + texto);
        }
        return LocalDateTime.of(periodo.getYear(), periodo.getMonthValue(), entero(coincidencia.group(1)),
                entero(coincidencia.group(2)), entero(coincidencia.group(3))).atZone(zona).toInstant();
    }

    private int entero(String texto) {
        return Integer.parseInt(texto);
    }

    private IllegalArgumentException error(int numeroLinea, String detalle) {
        return new IllegalArgumentException("Bloqueo en linea " + numeroLinea + ": " + detalle);
    }

    private IllegalArgumentException error(int numeroLinea, String detalle, Exception causa) {
        return new IllegalArgumentException("Bloqueo en linea " + numeroLinea + ": " + detalle, causa);
    }
}
