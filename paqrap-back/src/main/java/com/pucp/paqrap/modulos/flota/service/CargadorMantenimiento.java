package com.pucp.paqrap.modulos.flota.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Transforms preventive-maintenance records into the existing calendar entries. */
public final class CargadorMantenimiento {
    private static final Pattern FORMATO = Pattern.compile("^(\\d{8}):(TA|TM|TB)(\\d{2})$");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("uuuuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);

    public List<MaintenanceDay> cargar(Path archivo) throws IOException {
        Objects.requireNonNull(archivo, "archivo is required");
        List<MaintenanceDay> mantenimientos = new ArrayList<>();
        List<String> lineas = Files.readAllLines(archivo);
        for (int indice = 0; indice < lineas.size(); indice++) {
            mantenimientos.add(parsearLinea(lineas.get(indice), indice + 1));
        }
        return List.copyOf(mantenimientos);
    }

    public MaintenanceDay parsearLinea(String linea, int numeroLinea) {
        Objects.requireNonNull(linea, "linea is required");
        Matcher coincidencia = FORMATO.matcher(linea);
        if (!coincidencia.matches()) {
            throw error(numeroLinea, "formato o codigo de vehiculo invalido: " + linea);
        }
        try {
            LocalDate fecha = LocalDate.parse(coincidencia.group(1), FECHA);
            return new MaintenanceDay(coincidencia.group(2) + coincidencia.group(3), fecha);
        } catch (DateTimeException excepcion) {
            throw new IllegalArgumentException("Mantenimiento en linea " + numeroLinea + ": fecha invalida", excepcion);
        }
    }

    private IllegalArgumentException error(int numeroLinea, String detalle) {
        return new IllegalArgumentException("Mantenimiento en linea " + numeroLinea + ": " + detalle);
    }
}
