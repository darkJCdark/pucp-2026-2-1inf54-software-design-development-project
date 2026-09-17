package pe.pucp.paqrap.modelo;

import pe.edu.pucp.paqrap.planner.domain.MaintenanceDay;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsea el archivo bimensual de mantenimiento preventivo (hoja "Preguntas y
 *  Respuestas", pregunta 19): nombrado mant.preventivo.&lt;mes1&gt;.&lt;mes2&gt;.txt,
 *  con una entrada por linea:
 *
 *  <pre>aaaammdd:TTNN</pre>
 *
 *  Cada entrada bloquea la unidad TTNN el dia completo aaaa-mm-dd (Nota 1 de la
 *  pregunta 19, confirmada: "NO estara disponible para programacion de rutas desde
 *  las 00:00 hasta las 23:59"), que es exactamente lo que ya modela MaintenanceDay.
 *
 *  IMPORTANTE -- pendiente de confirmar por el docente, NO resuelto aqui a proposito:
 *  la misma respuesta trae una nota suelta sin cerrar ("FALTA poner duracion de los
 *  mantenimientos.... bicicletas duran un turno, motos 1 dia, y autos 2 dias") que
 *  contradice la regla de "1 dia completo para todas" recien confirmada. Como el
 *  propio docente la marco como pendiente ("FALTA"), este cargador usa la unica regla
 *  ya confirmada (1 dia completo, para cualquier tipo) en vez de adivinar duraciones
 *  por tipo -- igual que las otras decisiones ya dejadas abiertas en el README. */
public final class CargadorMantenimiento {

    private static final Pattern LINEA = Pattern.compile("^(\\d{8}):([A-Z]{2}\\d{2})$");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyyMMdd");

    private CargadorMantenimiento() {
    }

    public static List<MaintenanceDay> desdeArchivo(Path archivo) {
        List<MaintenanceDay> entradas = new ArrayList<>();
        List<String> lineas;
        try {
            lineas = Files.readAllLines(archivo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo de mantenimiento: " + archivo, e);
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

            LocalDate fecha = LocalDate.parse(m.group(1), FECHA);
            String vehiculoId = m.group(2);
            entradas.add(new MaintenanceDay(vehiculoId, fecha));
        }
        return entradas;
    }
}
