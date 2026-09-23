package pe.pucp.paqrap.planificador;

import org.junit.jupiter.api.Test;
import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.route.*;
import pe.pucp.paqrap.modelo.InicializadorFlota;
import pe.pucp.paqrap.modelo.ResultadoPlanificacion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private OperationalSnapshot snapshotCon(Instant planificacion, List<Vehicle> flota, List<Warehouse> almacenes,
                                            List<MaintenanceDay> mantenimiento) {
        Map<String, VehicleOperationalState> estados = new LinkedHashMap<>();
        for (Vehicle v : flota) {
            estados.put(v.id(), new VehicleOperationalState(v, VehicleStatus.AVAILABLE, central.location(), planificacion));
        }
        return new OperationalSnapshot(planificacion, perfilDePrueba(), InventorySnapshot.from(almacenes), estados,
                new MaintenanceCalendar(zona, mantenimiento), new ShiftSchedule(zona), List.of());
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
    void unPedidoMayorQueCualquierVehiculoSeDivideEnEntregasParciales() {
        // P&R 13: hay entregas parciales. 30 paquetes no caben en ningun vehiculo (maximo 24), pero
        // si en la flota: GRASP-v2 lo reparte. Antes quedaba siempre sin atender.
        List<Warehouse> almacenes = List.of(central);
        Order grande = new Order("C-005", new Location(5, 5), 30, horaInicio, horaInicio.plusSeconds(36 * 3600));

        ResultadoPlanificacion resultado = planificadorDePrueba(7L)
                .planificar(snapshotDePrueba(almacenes), List.of(grande), List.of(), 0.3, 30);

        assertTrue(resultado.esFactible(), () -> "plan infactible: " + resultado.evaluacion().violations());
        assertTrue(resultado.noAtendidos().isEmpty());
        int entregados = 0;
        int entregasParciales = 0;
        for (DeliveryRoute ruta : resultado.plan().routes()) {
            int capacidad = perfilDePrueba().parametersFor(ruta.vehicle().type()).capacity();
            for (RouteStop parada : ruta.stops()) {
                if (parada instanceof DeliveryStop entrega) {
                    assertTrue(entrega.deliveredPackages() <= capacidad);
                    entregados += entrega.deliveredPackages();
                    entregasParciales++;
                }
            }
        }
        assertEquals(30, entregados);
        assertTrue(entregasParciales >= 2);
    }

    @Test
    void unPedidoConPlazoImposibleQuedaSinAtenderYNoDejaPartesSueltas() {
        // (65,48) esta a 72 km del central: ni el auto (40 km/h) llega en 30 minutos.
        List<Warehouse> almacenes = List.of(central);
        Order imposible = new Order("C-LEJOS", new Location(65, 48), 30, horaInicio, horaInicio.plusSeconds(30 * 60));
        Order posible = new Order("C-CERCA", new Location(28, 15), 3, horaInicio, horaInicio.plusSeconds(8 * 3600));

        ResultadoPlanificacion resultado = planificadorDePrueba(3L)
                .planificar(snapshotDePrueba(almacenes), List.of(imposible, posible), List.of(), 0.3, 10);

        assertTrue(resultado.esFactible(), () -> "plan infactible: " + resultado.evaluacion().violations());
        assertEquals(List.of(imposible), resultado.noAtendidos());
        boolean quedoAlgunaParte = resultado.plan().routes().stream().flatMap(r -> r.stops().stream())
                .anyMatch(p -> p instanceof DeliveryStop d && d.order().equals(imposible));
        assertFalse(quedoAlgunaParte, "un pedido no atendido no debe dejar entregas parciales en el plan");
    }

    @Test
    void noAsignaUnaRutaQueTerminaDentroDelMantenimientoDelVehiculo() {
        // Planificacion a las 22:00 (Lima): la unica unidad entra a mantenimiento a las 00:00 del
        // dia siguiente. Una entrega lejana obligaria a seguir en ruta pasada la medianoche.
        Instant noche = LocalDateTime.of(2026, 9, 9, 22, 0).atZone(zona).toInstant();
        Vehicle auto = new Vehicle("TA01", VehicleType.CAR, true);
        OperationalSnapshot snapshot = snapshotCon(noche, List.of(auto), List.of(central),
                List.of(new MaintenanceDay("TA01", LocalDate.of(2026, 9, 10))));
        Order lejos = new Order("C-LEJOS", new Location(65, 48), 4, noche, noche.plusSeconds(36 * 3600));

        ResultadoPlanificacion resultado = planificadorDePrueba(11L)
                .planificar(snapshot, List.of(lejos), List.of(), 0.3, 5);

        assertTrue(resultado.esFactible(), () -> "plan infactible: " + resultado.evaluacion().violations());
        assertEquals(List.of(lejos), resultado.noAtendidos());
        assertTrue(resultado.plan().routes().isEmpty());
    }

    @Test
    void consolidaVariosPedidosEnElPrimerViajeSinVolverAUnAlmacen() {
        // Un solo auto (24) y tres pedidos pequenos cercanos: deben ir en el mismo primer viaje,
        // con una sola visita a almacen (el regreso final). Antes el primer tramo solo admitia un pedido.
        Vehicle auto = new Vehicle("TA01", VehicleType.CAR, true);
        OperationalSnapshot snapshot = snapshotCon(horaInicio, List.of(auto), List.of(central), List.of());
        List<Order> pedidos = List.of(
                new Order("C-1", new Location(30, 14), 3, horaInicio, horaInicio.plusSeconds(8 * 3600)),
                new Order("C-2", new Location(31, 15), 4, horaInicio, horaInicio.plusSeconds(8 * 3600)),
                new Order("C-3", new Location(32, 16), 5, horaInicio, horaInicio.plusSeconds(8 * 3600)));

        ResultadoPlanificacion resultado = planificadorDePrueba(5L).planificar(snapshot, pedidos, List.of(), 0.0, 3);

        assertTrue(resultado.esFactible(), () -> "plan infactible: " + resultado.evaluacion().violations());
        assertTrue(resultado.noAtendidos().isEmpty());
        DeliveryRoute ruta = resultado.plan().routes().iterator().next();
        assertEquals(12, ruta.initialLoad());
        assertEquals(1, ruta.stops().stream().filter(p -> p instanceof WarehouseVisit).count());
        assertEquals(3, ruta.stops().stream().filter(p -> p instanceof DeliveryStop).count());
    }

    @Test
    void conBloqueosMantenimientoYCruceDeMedianocheLosPlanesSonFactibles() {
        // La factibilidad que usa la construccion debe coincidir con la del evaluador: planificando
        // a las 19:00 las rutas cruzan de turno y de dia, con bloqueos activos y unidades que entran
        // a mantenimiento al dia siguiente.
        Instant tarde = LocalDateTime.of(2026, 9, 9, 19, 0).atZone(zona).toInstant();
        List<Vehicle> flota = InicializadorFlota.crearFlotaInicial();
        List<MaintenanceDay> mantenimiento = List.of(new MaintenanceDay("TA01", LocalDate.of(2026, 9, 10)),
                new MaintenanceDay("TM01", LocalDate.of(2026, 9, 10)), new MaintenanceDay("TB01", LocalDate.of(2026, 9, 10)));
        OperationalSnapshot snapshot = snapshotCon(tarde, flota, List.of(central, intNorOeste, intEste), mantenimiento);
        List<RoadBlock> bloqueos = List.of(
                new RoadBlock(tarde, tarde.plusSeconds(3 * 3600), List.of(new Location(27, 16), new Location(40, 16))),
                new RoadBlock(tarde.plusSeconds(1800), tarde.plusSeconds(5 * 3600), List.of(new Location(20, 10), new Location(20, 30))),
                new RoadBlock(tarde.plusSeconds(3600), tarde.plusSeconds(8 * 3600), List.of(new Location(45, 20), new Location(45, 35), new Location(55, 35))));
        Random generador = new Random(99L);
        int[] plazos = {4, 8, 12, 18, 36};
        List<Order> pedidos = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            int paquetes = i % 6 == 0 ? 25 + generador.nextInt(8) : 1 + generador.nextInt(8);
            pedidos.add(new Order(String.format("R-%02d", i), new Location(5 + generador.nextInt(61), 5 + generador.nextInt(41)),
                    paquetes, tarde, tarde.plusSeconds(plazos[generador.nextInt(plazos.length)] * 3600L)));
        }

        for (long semilla : new long[]{1L, 2L, 3L, 4L, 5L}) {
            ResultadoPlanificacion resultado = planificadorDePrueba(semilla).planificar(snapshot, pedidos, bloqueos, 0.3, 4);
            assertTrue(resultado.esFactible(),
                    () -> "semilla " + semilla + " produjo un plan infactible: " + resultado.evaluacion().violations());
        }
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

    @Test
    void pasoInsercionPendientesRescataUnPedidoQueCabeEnUnaRutaYaConstruida() {
        // Regresion del "hueco" reportado: un pedido puede quedar en
        // noAtendidos solo por el orden en que la construccion greedy lo
        // proceso, no porque fuera imposible. Se arma a mano un plan con UNA
        // ruta ya devuelta al almacen (como llega desde
        // construirGreedyAleatorizada) que tiene un segundo tramo (recarga
        // intermedia en almacen con pickup=0, dejado sin usar), y un pedido
        // "varado" que si cabe ahi -- pasoInsercionPendientes debe
        // encontrarlo y rescatarlo ajustando el pickup de esa recarga ya
        // existente, sin abrir ningun WarehouseVisit nuevo.
        List<Warehouse> almacenes = List.of(central, intNorOeste, intEste);
        Vehicle auto = InicializadorFlota.crearFlotaInicial().stream()
                .filter(v -> v.type() == VehicleType.CAR).findFirst().orElseThrow();
        Order pedidoYaEnRuta = new Order("C-EN-RUTA", new Location(28, 15), 5, horaInicio, horaInicio.plusSeconds(8 * 3600));
        Order pedidoVarado = new Order("C-VARADO", new Location(29, 16), 6, horaInicio, horaInicio.plusSeconds(8 * 3600));

        DeliveryRoute ruta = DeliveryRoute.startScenarioAtCentral(auto.id() + "-R", auto, central, 5, horaInicio)
                .withAppendedStop(new DeliveryStop(pedidoYaEnRuta, 5))
                .withAppendedStop(new WarehouseVisit(central, 0))
                .returningTo(central);
        OperationalPlan plan = OperationalPlan.empty().withRoute(ruta);

        GraspPlanificador grasp = planificadorDePrueba(1L);
        List<Order> pendientes = new ArrayList<>(List.of(pedidoVarado));
        GraspPlanificador.ResultadoInsercion insercion = grasp.pasoInsercionPendientes(
                plan, pendientes, snapshotDePrueba(almacenes), List.of());

        assertNotNull(insercion, "el pedido varado deberia haber sido rescatado, cabe en la ruta existente");
        assertEquals("C-VARADO", insercion.pedidoInsertado().id());
        DeliveryRoute rutaActualizada = insercion.plan().routes().stream()
                .filter(r -> r.vehicle().id().equals(auto.id())).findFirst().orElseThrow();
        long entregas = rutaActualizada.stops().stream().filter(s -> s instanceof DeliveryStop).count();
        assertEquals(2, entregas, "la ruta rescatada deberia tener ambas entregas");
    }
}
