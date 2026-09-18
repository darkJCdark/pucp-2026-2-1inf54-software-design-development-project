package pe.pucp.paqrap.app;

import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.route.*;
import pe.pucp.paqrap.modelo.*;
import pe.pucp.paqrap.planificador.GraspPlanificador;
import pe.pucp.paqrap.reportes.ValidadorUtilizacionFlota;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

/** Corre GraspPlanificador contra la data REAL entregada por el docente
 *  (carpeta data/ en la raiz del repo: ventas.202609.txt, bloqueo.2609.txt,
 *  mant.preventivo.09.10.txt), en vez de los 3 pedidos de juguete de
 *  DemoPlanificacion.
 *
 *  Escenario: 09-sep-2026, una ventana de horas configurable dentro del
 *  turno 07:00-15:00 (por defecto 1 hora, ~7 pedidos reales -- para probar
 *  rapido). Cargar el mes completo (~5000 pedidos) o el turno completo
 *  (52 pedidos) de una sola vez NO es realista todavia: 52 pedidos reales
 *  no termino ni en 5 minutos (ver README, seccion de rendimiento) -- la
 *  generacion de candidatos escala con el largo de ruta ya construida, asi
 *  que el costo crece mas rapido que lineal con la cantidad de pedidos.
 *  Subir la ventana de a poco (1h -> 2h -> ...) para encontrar en que
 *  punto se vuelve impractico, en vez de asumir que el turno completo
 *  corre en un tiempo razonable.
 *
 *  Ejecutar con Maven desde la raiz del repo:
 *    mvn install -DskipTests
 *    cd grasp
 *    mvn compile exec:java -Dexec.mainClass=pe.pucp.paqrap.app.EscenarioReal
 *    mvn compile exec:java -Dexec.mainClass=pe.pucp.paqrap.app.EscenarioReal -Dexec.args="7 9 20"
 *        (horaInicio horaFin maxIteraciones -- todos opcionales, en ese orden)
 *    mvn compile exec:java -Dexec.mainClass=pe.pucp.paqrap.app.EscenarioReal -Dexec.args="0 24 5"
 *        (dia completo, 174 pedidos reales -- empezar con pocas iteraciones,
 *        ver README seccion de rendimiento antes de subir maxIteraciones)
 */
public class EscenarioReal {
    public static void main(String[] args) throws Exception {
        int horaInicio = args.length > 0 ? Integer.parseInt(args[0]) : 7;
        int horaFin = args.length > 1 ? Integer.parseInt(args[1]) : 8;
        int maxIteraciones = args.length > 2 ? Integer.parseInt(args[2]) : 50;

        ZoneId zona = ShiftSchedule.DEFAULT_ZONE;
        YearMonth setiembre2026 = YearMonth.of(2026, 9);
        // horaFin=24 (dia completo) se sale del rango valido de LocalDateTime.of
        // (0-23) -- plusHours lo interpreta correctamente como medianoche del
        // dia siguiente, sin necesitar un caso especial.
        LocalDateTime inicioLocal = LocalDateTime.of(2026, 9, 9, 0, 0).plusHours(horaInicio);
        LocalDateTime finLocal = LocalDateTime.of(2026, 9, 9, 0, 0).plusHours(horaFin);
        Instant inicioTurno = inicioLocal.atZone(zona).toInstant();
        Instant finTurno = finLocal.atZone(zona).toInstant();

        Warehouse central = Warehouse.central("CENTRAL", new Location(27, 14));
        Warehouse intNorOeste = Warehouse.intermediate("INT-NOROESTE", new Location(12, 38), 1000);
        Warehouse intEste = Warehouse.intermediate("INT-ESTE", new Location(57, 27), 1000);
        List<Warehouse> almacenes = List.of(central, intNorOeste, intEste);

        List<Vehicle> flota = InicializadorFlota.crearFlotaInicial();
        Map<String, VehicleOperationalState> estadosPorVehiculo = new LinkedHashMap<>();
        for (Vehicle v : flota) {
            estadosPorVehiculo.put(v.id(), new VehicleOperationalState(v, VehicleStatus.AVAILABLE, central.location(), inicioTurno));
        }

        FleetProfile perfil = CargadorParametros.desdeArchivo(
                Path.of("src/main/resources/velocidades-situacion-autentica.properties"));

        List<MaintenanceDay> mantenimiento = CargadorMantenimiento.desdeArchivo(Path.of("../data/mant.preventivo.09.10.txt"));

        OperationalSnapshot snapshot = new OperationalSnapshot(
                inicioTurno, perfil, InventorySnapshot.from(almacenes), estadosPorVehiculo,
                new MaintenanceCalendar(zona, mantenimiento), new ShiftSchedule(zona), List.of());

        List<Order> pedidos = CargadorPedidos.desdeArchivo(
                Path.of("../data/ventas/ventas.202609.txt"), setiembre2026, zona, inicioTurno, finTurno);
        List<RoadBlock> bloqueos = CargadorBloqueos.desdeArchivo(
                Path.of("../data/bloqueos/bloqueo.2609.txt"), setiembre2026, zona, inicioTurno, finTurno);

        double alpha = 0.3;
        long semilla = 42L;
        String archivoVelocidades = "src/main/resources/velocidades-situacion-autentica.properties";

        System.out.println("=== Configuracion del algoritmo ===");
        System.out.printf("Fecha/ventana: 09-sep-2026 %02d:00-%02d:00%n", horaInicio, horaFin);
        System.out.println("maxIteraciones (reinicios GRASP): " + maxIteraciones);
        System.out.println("alpha (umbral RCL): " + alpha);
        System.out.println("Semilla aleatoria: " + semilla + " (deterministica: misma entrada -> mismo resultado)");
        System.out.println("Archivo de velocidades: " + archivoVelocidades);
        System.out.println("Almacenes: CENTRAL " + central.location() + ", INT-NOROESTE " + intNorOeste.location()
                + ", INT-ESTE " + intEste.location());
        System.out.println("Flota disponible: " + flota.size() + " vehiculos (10 CAR, 15 MOTORCYCLE, 12 BICYCLE)");
        System.out.println("Entradas de mantenimiento (archivo completo, set-oct 2026): " + mantenimiento.size());

        System.out.println("\n=== Pedidos cargados en la ventana (" + pedidos.size() + ") ===");
        for (Order pedido : pedidos) {
            System.out.printf("  %s: destino %s, %d u., llega %s, deadline %s%n",
                    pedido.id(), pedido.destination(), pedido.packages(), pedido.registeredAt(), pedido.deadline());
        }

        System.out.println("\n=== Bloqueos activos en la ventana (" + bloqueos.size() + ") ===");
        for (RoadBlock bloqueo : bloqueos) {
            System.out.printf("  %s a %s: nodos %s%n", bloqueo.startsAt(), bloqueo.endsAt(), bloqueo.nodes());
        }

        RoadNetwork roadNetwork = new RoadNetwork();
        RouteScheduler scheduler = new RouteScheduler(roadNetwork);
        OperationalPlanEvaluator evaluator = new OperationalPlanEvaluator(scheduler);
        GraspPlanificador grasp = new GraspPlanificador(roadNetwork, scheduler, evaluator, semilla);

        long inicioCronometro = System.currentTimeMillis();
        ResultadoPlanificacion resultado = grasp.planificar(snapshot, pedidos, bloqueos, alpha, maxIteraciones);
        long segundos = (System.currentTimeMillis() - inicioCronometro) / 1000;

        System.out.println("\n=== Resultado ===");
        System.out.println("Tiempo de planificacion: " + segundos + "s");
        System.out.println("Plan factible: " + resultado.esFactible());
        if (!resultado.esFactible()) {
            resultado.evaluacion().violations().forEach(v -> System.out.println("  VIOLACION: " + v));
        }
        System.out.println("Costo total: S/ " + resultado.costoTotal());
        System.out.println("Pedidos no atendidos: " + resultado.noAtendidos().size() + " / " + pedidos.size());
        resultado.noAtendidos().forEach(p -> System.out.println("  NO ATENDIDO: " + p.id()));
        System.out.println("COLAPSO: " + (resultado.esColapso() ? "SI (no toda la demanda fue cubierta)" : "no"));

        System.out.println("\n=== Rutas generadas (" + resultado.plan().routes().size() + ") ===");
        for (DeliveryRoute ruta : resultado.plan().routes()) {
            System.out.println("Vehiculo " + ruta.vehicle().id() + " (" + ruta.vehicle().type() + "):");
            for (RouteStop stop : ruta.stops()) {
                if (stop instanceof DeliveryStop entrega) {
                    System.out.println("   -> entrega " + entrega.order().id() + " en " + entrega.order().destination()
                            + " (" + entrega.deliveredPackages() + " u.)");
                } else if (stop instanceof WarehouseVisit visita) {
                    System.out.println("   -> almacen " + visita.warehouse().id() + " (" + visita.pickupPackages() + " u. recogidas)");
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
