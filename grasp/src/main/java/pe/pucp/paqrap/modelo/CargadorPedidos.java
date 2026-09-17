package pe.pucp.paqrap.modelo;

import pe.edu.pucp.paqrap.planner.domain.Location;
import pe.edu.pucp.paqrap.planner.domain.Order;

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

/** Parsea el archivo historico/proyectado de ventas (hoja "Preguntas y Respuestas",
 *  pregunta 8): un archivo por mes, nombrado ventas&lt;aaaamm&gt;.txt, con un pedido
 *  por linea:
 *
 *  <pre>##d##h##m:posX,posY,cIdCliente,qq,hl</pre>
 *
 *  ##d##h##m es dia-del-mes/hora/minuto en que el pedido llego, qq la cantidad de
 *  unidades del producto P y hl las horas limite de la entrega (plazo = llegada + hl
 *  horas). El dia y mes del propio pedido salen del NOMBRE del archivo (formato
 *  aaaamm), no de la linea -- el cargador lo recibe como YearMonth explicito para no
 *  depender de que el nombre de archivo se mantenga exacto. */
public final class CargadorPedidos {

    private static final Pattern LINEA = Pattern.compile(
            "^(\\d{2})d(\\d{2})h(\\d{2})m:(\\d+),(\\d+),([^,]+),(\\d+),(\\d+)$");

    private CargadorPedidos() {
    }

    /** Carga TODOS los pedidos del archivo (un mes completo puede ser miles de
     *  pedidos -- usar {@link #desdeArchivo(Path, YearMonth, ZoneId, Instant, Instant)}
     *  para acotar a la ventana de un escenario real antes de pasarlo a GraspPlanificador,
     *  que evalua candidatos por pedido x vehiculo y no esta pensado para miles de
     *  pedidos en una sola corrida). */
    public static List<Order> desdeArchivo(Path archivo, YearMonth mesArchivo, ZoneId zona) {
        return desdeArchivo(archivo, mesArchivo, zona, null, null);
    }

    /** Carga solo los pedidos cuyo instante de llegada (registeredAt) cae dentro de
     *  [desdeInclusive, hastaExclusive). Pasar null en cualquiera de los dos para no
     *  acotar por ese extremo. */
    public static List<Order> desdeArchivo(Path archivo, YearMonth mesArchivo, ZoneId zona,
                                            Instant desdeInclusive, Instant hastaExclusive) {
        List<Order> pedidos = new ArrayList<>();
        List<String> lineas;
        try {
            lineas = Files.readAllLines(archivo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo de ventas: " + archivo, e);
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

            int dia = Integer.parseInt(m.group(1));
            int hora = Integer.parseInt(m.group(2));
            int minuto = Integer.parseInt(m.group(3));
            int posX = Integer.parseInt(m.group(4));
            int posY = Integer.parseInt(m.group(5));
            String idCliente = m.group(6);
            int cantidad = Integer.parseInt(m.group(7));
            int horasLimite = Integer.parseInt(m.group(8));

            LocalDate fecha = mesArchivo.atDay(dia);
            Instant registradoEn = fecha.atTime(hora, minuto).atZone(zona).toInstant();

            if (desdeInclusive != null && registradoEn.isBefore(desdeInclusive)) continue;
            if (hastaExclusive != null && !registradoEn.isBefore(hastaExclusive)) continue;

            Instant deadline = registradoEn.plusSeconds(horasLimite * 3600L);
            String idPedido = idCliente + "-" + mesArchivo + "-" + numeroLinea;
            pedidos.add(new Order(idPedido, new Location(posX, posY), cantidad, registradoEn, deadline));
        }
        return pedidos;
    }
}
