package com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing;

import com.pucp.paqrap.modulos.flota.service.CargadorMantenimiento;
import com.pucp.paqrap.modulos.flota.service.MaintenanceDay;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.pedidos.service.CargadorPedidos;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.service.CargadorBloqueos;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatosProfesorCargaSaTest {
    private static final YearMonth ENERO_2026 = YearMonth.of(2026, 1);

    @Test
    void cargaLosTresArchivosRealesDelProfesor() throws Exception {
        List<Order> pedidos = new CargadorPedidos().cargar(recurso("ventas/ventas.202601.txt"), ENERO_2026,
                ShiftSchedule.DEFAULT_ZONE);
        List<RoadBlock> bloqueos = new CargadorBloqueos().cargar(recurso("bloqueos/bloqueo.2601.txt"), ENERO_2026,
                ShiftSchedule.DEFAULT_ZONE);
        List<MaintenanceDay> mantenimientos = new CargadorMantenimiento().cargar(recurso("mant.preventivo.09.10.txt"));

        assertEquals(641, pedidos.size());
        assertEquals("c4910-0001", pedidos.getFirst().id());
        assertEquals(new Location(56, 30), pedidos.getFirst().destination());
        assertEquals(2, pedidos.getFirst().packages());
        assertEquals(LocalDateTime.of(2026, 1, 1, 1, 30), local(pedidos.getFirst()));
        assertEquals(Duration.ofHours(36), Duration.between(pedidos.getFirst().registeredAt(), pedidos.getFirst().deadline()));
        assertEquals("c4264-0321", pedidos.get(320).id());
        assertEquals(new Location(32, 37), pedidos.get(320).destination());
        assertEquals(1, pedidos.get(320).packages());
        assertEquals(Duration.ofHours(4), Duration.between(pedidos.get(320).registeredAt(), pedidos.get(320).deadline()));
        assertEquals("c5444-0641", pedidos.getLast().id());
        assertEquals(new Location(10, 35), pedidos.getLast().destination());
        assertEquals(3, pedidos.getLast().packages());
        assertEquals(Duration.ofHours(36), Duration.between(pedidos.getLast().registeredAt(), pedidos.getLast().deadline()));

        assertEquals(669, bloqueos.size());
        RoadBlock primero = bloqueos.getFirst();
        assertEquals(LocalDateTime.of(2026, 1, 1, 2, 22), primero.startsAt().atZone(ShiftSchedule.DEFAULT_ZONE).toLocalDateTime());
        assertEquals(LocalDateTime.of(2026, 1, 1, 4, 42), primero.endsAt().atZone(ShiftSchedule.DEFAULT_ZONE).toLocalDateTime());
        assertEquals(List.of(new Location(25, 45), new Location(45, 45), new Location(45, 40)), primero.nodes());
        assertEquals(25, primero.blockedSegments().size());
        assertEquals(new Location(15, 3), bloqueos.get(334).nodes().getFirst());
        assertEquals(new Location(37, 12), bloqueos.get(334).nodes().getLast());
        assertEquals(List.of(new Location(25, 25), new Location(30, 25), new Location(30, 30), new Location(35, 30)),
                bloqueos.getLast().nodes());

        assertEquals(37, mantenimientos.size());
        assertEquals(new MaintenanceDay("TA01", LocalDate.of(2026, 9, 1)), mantenimientos.getFirst());
        assertTrue(mantenimientos.stream().anyMatch(registro -> registro.vehicleId().startsWith("TA")));
        assertTrue(mantenimientos.stream().anyMatch(registro -> registro.vehicleId().startsWith("TM")));
        assertTrue(mantenimientos.stream().anyMatch(registro -> registro.vehicleId().startsWith("TB")));
        assertEquals(new MaintenanceDay("TA10", LocalDate.of(2026, 10, 28)), mantenimientos.getLast());

        System.out.println("\n====================================================");
        System.out.println("DATOS DEL PROFESOR - CARGA");
        System.out.println("====================================================");
        System.out.printf("Pedidos cargados: %d%n", pedidos.size());
        System.out.printf("Bloqueos cargados: %d%n", bloqueos.size());
        System.out.printf("Mantenimientos cargados: %d%n", mantenimientos.size());
        System.out.println("Registros rechazados: 0");
        System.out.println("====================================================");
    }

    @Test
    void rechazaRegistrosInvalidosSinCorregirlosSilenciosamente() {
        CargadorPedidos pedidos = new CargadorPedidos();
        CargadorBloqueos bloqueos = new CargadorBloqueos();
        CargadorMantenimiento mantenimientos = new CargadorMantenimiento();

        assertThrows(IllegalArgumentException.class, () -> pedidos.parsearLinea(
                "01d10h00m:10,10,c1,00,04", 1, ENERO_2026, ShiftSchedule.DEFAULT_ZONE));
        assertThrows(IllegalArgumentException.class, () -> pedidos.parsearLinea(
                "01d24h00m:10,10,c1,01,04", 1, ENERO_2026, ShiftSchedule.DEFAULT_ZONE));
        assertThrows(IllegalArgumentException.class, () -> bloqueos.parsearLinea(
                "01d02h00m-01d03h00m:1,1,2", 1, ENERO_2026, ShiftSchedule.DEFAULT_ZONE));
        assertThrows(IllegalArgumentException.class, () -> mantenimientos.parsearLinea("20260230:TA01", 1));
        assertThrows(IllegalArgumentException.class, () -> mantenimientos.parsearLinea("20260901:TX01", 1));
    }

    private Path recurso(String nombre) throws URISyntaxException {
        return Path.of(getClass().getResource("/datos-profesor/" + nombre).toURI());
    }

    private LocalDateTime local(Order pedido) {
        return pedido.registeredAt().atZone(ShiftSchedule.DEFAULT_ZONE).toLocalDateTime();
    }
}
