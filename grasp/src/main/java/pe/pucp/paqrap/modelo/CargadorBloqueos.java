package pe.pucp.paqrap.modelo;

import pe.edu.pucp.paqrap.planner.domain.Location;
import pe.edu.pucp.paqrap.planner.domain.RoadBlock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsea el archivo mensual de calles bloqueadas (hoja "Preguntas y Respuestas",
 *  pregunta 7): un archivo por mes, nombrado bloqueo&lt;aammm&gt;.txt (2 digitos de
 *  año, 2 de mes), con un bloqueo por linea:
 *
 *  <pre>##d##h##m-##d##h##m:x1,y1,x2,y2,...,xn,yn</pre>
 *
 *  El primer ##d##h##m es el inicio del bloqueo, el segundo el fin; el resto es la
 *  poligonal de nodos bloqueados (curso 2026-2: siempre poligono ABIERTO). El dia y
 *  mes salen del nombre del archivo, recibido aqui como YearMonth explicito. */
public final class CargadorBloqueos {

    private static final Pattern LINEA = Pattern.compile(
            "^(\\d{2})d(\\d{2})h(\\d{2})m-(\\d{2})d(\\d{2})h(\\d{2})m:(.+)$");

    private CargadorBloqueos() {
    }

    /** Carga todos los bloqueos del archivo. */
    public static List<RoadBlock> desdeArchivo(Path archivo, YearMonth mesArchivo, ZoneId zona) {
        return desdeArchivo(archivo, mesArchivo, zona, null, null);
    }

    /** Carga solo los bloqueos cuyo intervalo [startsAt, endsAt) se solapa con
     *  [desdeInclusive, hastaExclusive). Pasar null en cualquiera de los dos para no
     *  acotar por ese extremo. */
    public static List<RoadBlock> desdeArchivo(Path archivo, YearMonth mesArchivo, ZoneId zona,
                                                Instant desdeInclusive, Instant hastaExclusive) {
        List<RoadBlock> bloqueos = new ArrayList<>();
        List<String> lineas;
        try {
            lineas = Files.readAllLines(archivo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo de bloqueos: " + archivo, e);
        }

        int numeroLinea = 0;
        for (String linea : lineas) {
            numeroLinea++;
            String recorte = linea.strip();
            if (recorte.isEmpty()) continue;

            Matcher m = LINEA.matcher(recorte);
            if (!m.matches()) {
                throw new IllegalArgumentException(
                        "Linea %d con formato invalido en %s: %s".formatted(numeroLinea, archivo, linea));
            }

            Instant inicio = instanteDe(mesArchivo, zona, m.group(1), m.group(2), m.group(3));
            Instant fin = instanteDe(mesArchivo, zona, m.group(4), m.group(5), m.group(6));

            String[] coordenadas = m.group(7).split(",");
            if (coordenadas.length % 2 != 0) {
                throw new IllegalArgumentException(
                        "Linea %d con coordenadas impares en %s: %s".formatted(numeroLinea, archivo, linea));
            }
            List<Location> nodos = new ArrayList<>(coordenadas.length / 2);
            for (int i = 0; i < coordenadas.length; i += 2) {
                int x = Integer.parseInt(coordenadas[i].strip());
                int y = Integer.parseInt(coordenadas[i + 1].strip());
                nodos.add(new Location(x, y));
            }

            RoadBlock bloqueo = new RoadBlock(inicio, fin, nodos);
            Instant desde = desdeInclusive != null ? desdeInclusive : Instant.MIN;
            Instant hasta = hastaExclusive != null ? hastaExclusive : Instant.MAX;
            if (desdeInclusive == null && hastaExclusive == null || bloqueo.overlaps(desde, hasta)) {
                bloqueos.add(bloqueo);
            }
        }
        return bloqueos;
    }

    private static Instant instanteDe(YearMonth mesArchivo, ZoneId zona, String dia, String hora, String minuto) {
        LocalDate fecha = mesArchivo.atDay(Integer.parseInt(dia));
        return fecha.atTime(Integer.parseInt(hora), Integer.parseInt(minuto)).atZone(zona).toInstant();
    }
}
