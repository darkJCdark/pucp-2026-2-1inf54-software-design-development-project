package pe.pucp.paqrap.planificador;

import org.junit.jupiter.api.Test;
import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.route.*;
import pe.pucp.paqrap.modelo.InicializadorFlota;
import pe.pucp.paqrap.modelo.ResultadoPlanificacion;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GraspPlanificadorTest {

    private final ZoneId zona = ShiftSchedule.DEFAULT_ZONE;
    private final Instant horaInicio = Instant.parse("2026-09-09T12:00:00Z");

    // Coordenadas actualizadas (ver README raiz): Central (27,14), Este (57,27).
    private final Warehouse central = Warehouse.central("CENTRAL", new Location(27, 14));
    private final Warehouse intNorOeste = Warehouse.intermediate("INT-NOROESTE", new Location(12, 38), 1000);
    private final Warehouse intEste = Warehouse.intermediate("INT-ESTE", new Location(57, 27), 1000);

    private FleetProfile perfilDePrueba() {
        Map<VehicleType, VehicleParameters> parametros = new EnumMap<>(VehicleType.class);
        parametros.put(VehicleType.CAR, new VehicleParameters(24, 40, 8.00));
        parametros.put(VehicleType.MOTORCYCLE, new VehicleParameters(8, 25, 6.00));
        parametros.put(VehicleType.BICYCLE, new VehicleParameters(4, 12, 3.00));
        return new FleetProfile(parametros);
    }

    private OperationalSnapshot snapshotDePrueba(List<Warehouse> almacenes) {
        List<Vehicle> flota = InicializadorFlota.crearFlotaInicial();
        Map<String, VehicleOperationalState> estados = new LinkedHashMap<>();
        for (Vehicle v : flota) {
            estados.put(v.id(), new VehicleOperationalState(v, VehicleStatus.AVAILABLE, central.location(), horaInicio));
        }
        return new OperationalSnapshot(horaInicio, perfilDePrueba(), InventorySnapshot.from(almacenes), estados,
                new MaintenanceCalendar(zona, List.of()), new ShiftSchedule(zona), List.of());
    }

    private GraspPlanificador planificadorDePrueba(long semilla) {
        RoadNetwork roadNetwork = new RoadNetwork();
        RouteScheduler scheduler = new RouteScheduler(roadNetwork);
        OperationalPlanEvaluator evaluator = new OperationalPlanEvaluator(scheduler);
        return new GraspPlanificador(roadNetwork, scheduler, evaluator, semilla);
    }

    @Test
    void ningunaRutaExcedeLaCapacidadDeSuVehiculo() {
        List<Warehouse> almacenes = List.of(central, intNorOeste, intEste);
        // Pedido de 12 unidades: no cabe en bicicleta (4) ni en moto (8), si en auto (24).
        List<Order> pedidos = List.of(new Order("C-002", new Location(10, 40), 12, horaInicio, horaInicio.plusSeconds(12 * 3600)));

        ResultadoPlanificacion resultado = planificadorDePrueba(42L)
                .planificar(snapshotDePrueba(almacenes), pedidos, List.of(), 0.3, 30);

        assertTrue(resultado.esFactible(), () -> "plan infactible: " + resultado.evaluacion().violations());
        assertEquals(0, resultado.noAtendidos().size());
        for (DeliveryRoute ruta : resultado.plan().routes()) {
            int capacidad = perfilDePrueba().parametersFor(ruta.vehicle().type()).capacity();
            int cargaMaxima = ruta.stops().stream().filter(s -> s instanceof DeliveryStop)
                    .mapToInt(s -> ((DeliveryStop) s).deliveredPackages()).sum();
            assertTrue(cargaMaxima <= capacidad);
        }
    }

    @Test
    void unPedidoQueExcedeTodaLaFlotaQuedaSinAtenderYNoRompeCapacidad() {
        List<Warehouse> almacenes = List.of(central);
        List<Order> pedidos = List.of(new Order("C-005", new Location(5, 5), 30, horaInicio, horaInicio.plusSeconds(36 * 3600)));

        ResultadoPlanificacion resultado = planificadorDePrueba(7L)
                .planificar(snapshotDePrueba(almacenes), pedidos, List.of(), 0.3, 30);

        assertEquals(1, resultado.noAtendidos().size());
        assertEquals("C-005", resultado.noAtendidos().get(0).id());
        assertTrue(resultado.plan().routes().isEmpty());
    }

    @Test
    void conVariosPedidosElPlanFinalSiempreEsFactibleYSinRutasVacias() {
        // Regresion combinada: (1) reubicacion insertando despues del
        // regreso al almacen (ROUTE_NOT_RETURNED_TO_WAREHOUSE), (2) mover
        // una entrega sin ajustar el WarehouseVisit que la recoge
        // (NEGATIVE_LOAD), (3) una ruta que queda con cero entregas tras
        // una reubicacion y debe eliminarse del plan, no quedar como viaje
        // vacio (bug encontrado en una revision anterior, corregido en
        // pasoReubicacion).
        List<Warehouse> almacenes = List.of(central, intNorOeste, intEste);
        List<Order> pedidos = List.of(
                new Order("C-001", new Location(30, 20), 5, horaInicio, horaInicio.plusSeconds(8 * 3600)),
                new Order("C-002", new Location(10, 40), 12, horaInicio, horaInicio.plusSeconds(12 * 3600)),
                new Order("C-003", new Location(50, 25), 3, horaInicio, horaInicio.plusSeconds(4 * 3600)),
                new Order("C-004", new Location(28, 18), 6, horaInicio, horaInicio.plusSeconds(18 * 3600)),
                new Order("C-005", new Location(15, 35), 4, horaInicio, horaInicio.plusSeconds(36 * 3600)),
                new Order("C-006", new Location(48, 27), 2, horaInicio, horaInicio.plusSeconds(36 * 3600))
        );

        for (long semilla : new long[]{1L, 2L, 3L, 4L, 5L}) {
            ResultadoPlanificacion resultado = planificadorDePrueba(semilla)
                    .planificar(snapshotDePrueba(almacenes), pedidos, List.of(), 0.3, 20);

            assertTrue(resultado.esFactible(),
                    () -> "semilla " + semilla + " produjo un plan infactible: " + resultado.evaluacion().violations());

            for (DeliveryRoute ruta : resultado.plan().routes()) {
                boolean tieneAlgunaEntrega = ruta.stops().stream().anyMatch(s -> s instanceof DeliveryStop);
                assertTrue(tieneAlgunaEntrega,
                        () -> "semilla " + semilla + ": la ruta de " + ruta.vehicle().id() + " quedo vacia (sin eliminar del plan)");
            }
        }
    }
}
