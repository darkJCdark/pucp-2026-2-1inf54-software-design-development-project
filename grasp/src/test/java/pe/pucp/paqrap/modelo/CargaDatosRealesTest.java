package pe.pucp.paqrap.modelo;

import org.junit.jupiter.api.Test;
import pe.edu.pucp.paqrap.planner.domain.MaintenanceDay;
import pe.edu.pucp.paqrap.planner.domain.Order;
import pe.edu.pucp.paqrap.planner.domain.RoadBlock;
import pe.edu.pucp.paqrap.planner.domain.ShiftSchedule;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Prueba los 3 cargadores contra la data real entregada por el docente
 *  (carpeta data/ en la raiz del repo, ver README): ventas.202609.txt,
 *  bloqueo.2609.txt, mant.preventivo.09.10.txt. */
class CargaDatosRealesTest {

    private final ZoneId zona = ShiftSchedule.DEFAULT_ZONE;
    private final YearMonth setiembre2026 = YearMonth.of(2026, 9);

    @Test
    void cargaPedidosDeUnTurnoCompleto() {
        Path archivo = Path.of("../data/ventas/ventas.202609.txt");
        Instant desde = LocalDateTime.of(2026, 9, 9, 7, 0).atZone(zona).toInstant();
        Instant hasta = LocalDateTime.of(2026, 9, 9, 15, 0).atZone(zona).toInstant();

        List<Order> pedidos = CargadorPedidos.desdeArchivo(archivo, setiembre2026, zona, desde, hasta);

        assertFalse(pedidos.isEmpty(), "el turno 07:00-15:00 del 9 de setiembre deberia tener pedidos reales");
        for (Order pedido : pedidos) {
            assertFalse(pedido.registeredAt().isBefore(desde));
            assertTrue(pedido.registeredAt().isBefore(hasta));
            assertTrue(pedido.packages() > 0);
            assertTrue(pedido.deadline().isAfter(pedido.registeredAt()));
        }
        // ids unicos (cIdCliente + mes + numero de linea)
        long idsUnicos = pedidos.stream().map(Order::id).distinct().count();
        assertEquals(pedidos.size(), idsUnicos, "cada pedido cargado debe tener un id unico");
    }

    @Test
    void cargaBloqueosActivosEnUnaVentanaAcotada() {
        Path archivo = Path.of("../data/bloqueos/bloqueo.2609.txt");
        Instant desde = LocalDateTime.of(2026, 9, 1, 0, 0).atZone(zona).toInstant();
        Instant hasta = LocalDateTime.of(2026, 9, 1, 6, 0).atZone(zona).toInstant();

        List<RoadBlock> bloqueos = CargadorBloqueos.desdeArchivo(archivo, setiembre2026, zona, desde, hasta);

        assertFalse(bloqueos.isEmpty(), "deberia haber bloqueos activos en las primeras 6h del 1 de setiembre");
        for (RoadBlock bloqueo : bloqueos) {
            assertTrue(bloqueo.overlaps(desde, hasta));
            assertTrue(bloqueo.nodes().size() >= 2);
        }
    }

    @Test
    void cargaTodosLosBloqueosDelMesSinVentana() {
        Path archivo = Path.of("../data/bloqueos/bloqueo.2609.txt");
        List<RoadBlock> bloqueos = CargadorBloqueos.desdeArchivo(archivo, setiembre2026, zona);
        assertEquals(558, bloqueos.size());
    }

    @Test
    void cargaMantenimientoCompleto() {
        Path archivo = Path.of("../data/mant.preventivo.09.10.txt");
        List<MaintenanceDay> entradas = CargadorMantenimiento.desdeArchivo(archivo);

        assertEquals(37, entradas.size());
        assertEquals(new MaintenanceDay("TA01", LocalDate.of(2026, 9, 1)), entradas.get(0));
        assertEquals(new MaintenanceDay("TA10", LocalDate.of(2026, 10, 28)), entradas.get(entradas.size() - 1));
    }
}
