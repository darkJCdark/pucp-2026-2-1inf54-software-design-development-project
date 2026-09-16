package pe.pucp.paqrap.app;

import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.route.*;
import pe.pucp.paqrap.modelo.CargadorParametros;
import pe.pucp.paqrap.modelo.InicializadorFlota;
import pe.pucp.paqrap.modelo.ResultadoPlanificacion;
import pe.pucp.paqrap.planificador.GraspPlanificador;
import pe.pucp.paqrap.reportes.ValidadorUtilizacionFlota;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Escenario minimo de ejemplo: arma 3 almacenes (coordenadas actualizadas,
 * ver README), la flota real (37 unidades) y 3 pedidos de prueba, y ejecuta
 * una corrida completa de GraspPlanificador sobre el dominio compartido.
 *
 * Ejecutar con Maven:
 *   mvn install -DskipTests          (una vez, desde la raiz del repo)
 *   cd grasp &amp;&amp; mvn compile exec:java
 */
public class DemoPlanificacion {
    public static void main(String[] args) throws Exception {
        ZoneId zona = ShiftSchedule.DEFAULT_ZONE;
        Instant horaInicio = LocalDateTime.of(2026, 9, 9, 7, 0).atZone(zona).toInstant();

        // Coordenadas de la fila mas reciente de la hoja de preguntas y
        // respuestas (ver README): Central (27,14), Este (57,27). La
        // version anterior tenia (25,15) y (55,27) -- ya reemplazada aqui.
        Warehouse central = Warehouse.central("CENTRAL", new Location(27, 14));
        Warehouse intNorOeste = Warehouse.intermediate("INT-NOROESTE", new Location(12, 38), 1000);
        Warehouse intEste = Warehouse.intermediate("INT-ESTE", new Location(57, 27), 1000);
        List<Warehouse> almacenes = List.of(central, intNorOeste, intEste);

        List<Vehicle> flota = InicializadorFlota.crearFlotaInicial();
        Map<String, VehicleOperationalState> estadosPorVehiculo = new LinkedHashMap<>();
        for (Vehicle v : flota) {
            estadosPorVehiculo.put(v.id(), new VehicleOperationalState(v, VehicleStatus.AVAILABLE, central.location(), horaInicio));
        }

        // NOTA: las velocidades usadas aqui son las de la situacion
        // autentica. Hay un conflicto sin resolver con la hoja "Flota"
        // (ver README) -- cambia el archivo cargado aqui en cuanto el
        // docente confirme los valores reales.
        FleetProfile perfil = CargadorParametros.desdeArchivo(
                Path.of("src/main/resources/velocidades-situacion-autentica.properties"));

        OperationalSnapshot snapshot = new OperationalSnapshot(
                horaInicio, perfil, InventorySnapshot.from(almacenes), estadosPorVehiculo,
                new MaintenanceCalendar(zona, List.of()), new ShiftSchedule(zona), List.of());

        List<Order> pedidos = List.of(
                new Order("CLIENTE-001", new Location(30, 20), 5, horaInicio, horaInicio.plusSeconds(8 * 3600)),
                new Order("CLIENTE-002", new Location(10, 40), 12, horaInicio, horaInicio.plusSeconds(12 * 3600)),
                new Order("CLIENTE-003", new Location(50, 25), 3, horaInicio, horaInicio.plusSeconds(4 * 3600))
        );

        // Ejemplo de bloqueo con ventana de tiempo: el tramo (40,5)-(41,5)
        // (lejos de almacenes y pedidos, para no bloquear un destino en si)
        // esta cerrado solo entre las 07:30 y las 08:30 del mismo dia.
        List<RoadBlock> bloqueos = List.of(new RoadBlock(
                horaInicio.plusSeconds(1800), horaInicio.plusSeconds(5400),
                List.of(new Location(40, 5), new Location(41, 5))));

        RoadNetwork roadNetwork = new RoadNetwork();
        RouteScheduler scheduler = new RouteScheduler(roadNetwork);
        OperationalPlanEvaluator evaluator = new OperationalPlanEvaluator(scheduler);
        GraspPlanificador grasp = new GraspPlanificador(roadNetwork, scheduler, evaluator, 42L);

        ResultadoPlanificacion resultado = grasp.planificar(snapshot, pedidos, bloqueos, 0.3, 50);

        System.out.println("Flota disponible: " + flota.size() + " vehiculos");
        System.out.println("Plan factible: " + resultado.esFactible());
        if (!resultado.esFactible()) {
            resultado.evaluacion().violations().forEach(v -> System.out.println("  VIOLACION: " + v));
        }
        System.out.println("Costo total: S/ " + resultado.costoTotal());
        System.out.println("Pedidos no atendidos: " + resultado.noAtendidos().size());
        System.out.println("COLAPSO: " + (resultado.esColapso() ? "SI (no toda la demanda fue cubierta)" : "no"));
        for (DeliveryRoute ruta : resultado.plan().routes()) {
            System.out.println("Vehiculo " + ruta.vehicle().id() + ":");
            for (RouteStop stop : ruta.stops()) {
                if (stop instanceof DeliveryStop entrega) {
                    System.out.println("   -> " + entrega.order().id() + " (" + entrega.deliveredPackages() + " u.)");
                } else if (stop instanceof WarehouseVisit visita) {
                    System.out.println("   -> [almacen " + visita.warehouse().id() + ", " + visita.pickupPackages() + " u.]");
                }
            }
        }

        System.out.println("\n--- Utilizacion vs. hoja Flota (informativo) ---");
        var validador = new ValidadorUtilizacionFlota(ValidadorUtilizacionFlota.OBJETIVOS_HOJA_FLOTA);
        Map<VehicleType, Integer> cantidadPorTipo = Map.of(
                VehicleType.CAR, 10, VehicleType.MOTORCYCLE, 15, VehicleType.BICYCLE, 12);
        validador.validar(resultado.plan(), cantidadPorTipo).forEach(System.out::println);
    }
}
