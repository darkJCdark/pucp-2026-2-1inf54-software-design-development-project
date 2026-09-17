package com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.service.CargadorMantenimiento;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.flota.service.MaintenanceDay;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.pedidos.service.CargadorPedidos;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolationType;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.entity.StreetSegment;
import com.pucp.paqrap.modulos.redvial.service.CargadorBloqueos;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoDatosProfesorSaTest {
    private static final Pattern ARCHIVO_VENTAS = Pattern.compile("^ventas\\.(\\d{6})\\.txt$");
    private static final Pattern ARCHIVO_BLOQUEOS = Pattern.compile("^bloqueo\\.(\\d{2})(\\d{2})\\.txt$");
    private static final DateTimeFormatter PERIODO_VENTAS = DateTimeFormatter.ofPattern("uuuuMM");
    private static final long SEMILLA = 20260101L;

    @Test
    void demuestraSaConDatosRealesYValidaElDatasetCompleto() throws Exception {
        Dataset dataset = cargarYValidarDatasetCompleto();
        ArchivoMensual ventasEnero = dataset.ventas().getFirst();
        ArchivoMensual bloqueosEnero = dataset.bloqueosPorPeriodo().get(ventasEnero.periodo());
        assertNotNull(bloqueosEnero, "Debe existir el archivo de bloqueos del mismo mes de la demo");

        Instant inicioPeriodo = ventasEnero.periodo().atDay(1).atStartOfDay(ShiftSchedule.DEFAULT_ZONE).toInstant();
        Instant finPeriodo = ventasEnero.periodo().atDay(6).atStartOfDay(ShiftSchedule.DEFAULT_ZONE).toInstant();
        Instant instantePlanificacion = ventasEnero.periodo().atDay(1).atTime(10, 19)
                .atZone(ShiftSchedule.DEFAULT_ZONE).toInstant();
        List<Order> pedidos = dataset.pedidosPorPeriodo().get(ventasEnero.periodo()).stream()
                .filter(pedido -> !pedido.registeredAt().isBefore(inicioPeriodo))
                .filter(pedido -> !pedido.registeredAt().isAfter(instantePlanificacion))
                .toList();
        List<RoadBlock> bloqueos = dataset.bloqueosPorPeriodoCargados().get(ventasEnero.periodo()).stream()
                .filter(bloqueo -> bloqueo.isActiveAt(instantePlanificacion)).toList();
        Warehouse central = Warehouse.central("CENTRAL", new Location(27, 14));
        List<Vehicle> flota = List.of(new Vehicle("TA01", VehicleType.CAR, true), new Vehicle("TA02", VehicleType.CAR, true),
                new Vehicle("TM01", VehicleType.MOTORCYCLE, true), new Vehicle("TB01", VehicleType.BICYCLE, true));
        OperationalSnapshot snapshot = snapshot(central, flota, instantePlanificacion, List.of());
        AnnealingConfig configuracion = new AnnealingConfig(100.0, 1.0, 0.90, 3, 30, 30);
        OperationalSimulatedAnnealingPlanner.Ejecucion ejecucion = planificador().ejecutar(snapshot, pedidos, bloqueos,
                configuracion, random(SEMILLA));
        ResultadoPlanificacion resultado = ejecucion.resultado();

        assertFalse(pedidos.isEmpty());
        assertFalse(bloqueos.isEmpty());
        assertNotNull(resultado);
        assertNotNull(resultado.evaluacion());
        assertTrue(resultado.esFactible(), () -> "violaciones: " + resultado.evaluacion().violations());
        assertFalse(tieneCargaNegativa(resultado));
        assertEquals(0, violaciones(resultado, PlanViolationType.VEHICLE_CAPACITY));
        assertEquals(0, violaciones(resultado, PlanViolationType.SLA_MISSED));
        assertEquals(0, bloqueosAtravesados(resultado, bloqueos));
        assertPedidosCoherentes(resultado, pedidos);

        imprimirCabecera("SA - VALIDACION CON DATOS REALES DEL PROFESOR");
        System.out.println("PERIODO ANALIZADO");
        System.out.println("------------------------------------------------------------");
        System.out.printf("Inicio: %s%nFin: %s%nInstante de planificacion: %s%n", inicioPeriodo, finPeriodo, instantePlanificacion);
        System.out.println("Validacion del planificador en un instante real dentro de una ventana de cinco dias; no es una simulacion temporal de cinco dias.");
        System.out.println("\nARCHIVOS UTILIZADOS");
        System.out.println("------------------------------------------------------------");
        System.out.printf("Ventas: %s%nBloqueos: %s%nMantenimiento: NO APLICA PARA ESTE PERIODO%n",
                ventasEnero.path().getFileName(), bloqueosEnero.path().getFileName());
        System.out.println("\nDATASET DISPONIBLE");
        System.out.println("------------------------------------------------------------");
        System.out.printf("Archivos de ventas: %d%nPeriodo ventas: %s a %s%nRegistros totales ventas: %d%n%n",
                dataset.ventas().size(), dataset.ventas().getFirst().periodo(), dataset.ventas().getLast().periodo(), dataset.totalPedidos());
        System.out.printf("Archivos de bloqueos: %d%nPeriodo bloqueos: %s a %s%nRegistros totales bloqueos: %d%n%n",
                dataset.bloqueos().size(), dataset.bloqueos().getFirst().periodo(), dataset.bloqueos().getLast().periodo(), dataset.totalBloqueos());
        System.out.printf("Mantenimientos: %d%n", dataset.mantenimientos().size());
        System.out.println("\nDATOS UTILIZADOS EN ESTA EJECUCION");
        System.out.println("------------------------------------------------------------");
        System.out.printf("Pedidos considerados: %d%nBloqueos vigentes: %d%n", pedidos.size(), bloqueos.size());
        System.out.println("\nPEDIDOS");
        System.out.println("------------------------------------------------------------");
        pedidos.forEach(pedido -> System.out.printf("%s | destino: (%d,%d) | cantidad: %d | registro: %s | plazo: %d h | deadline: %s%n",
                pedido.id(), pedido.destination().x(), pedido.destination().y(), pedido.packages(), pedido.registeredAt(),
                java.time.Duration.between(pedido.registeredAt(), pedido.deadline()).toHours(), pedido.deadline()));
        System.out.println("\nCONFIGURACION SA");
        System.out.println("------------------------------------------------------------");
        System.out.printf("Temperatura inicial: %.2f%nTemperatura minima: %.2f%nFactor de enfriamiento: %.2f%n"
                        + "Iteraciones por temperatura: %d%nMaximo de iteraciones: %d%nMaximo sin mejora: %d%nSemilla: %d%n",
                configuracion.initialTemperature(), configuracion.minimumTemperature(), configuracion.coolingFactor(),
                configuracion.iterationsPerTemperature(), configuracion.maximumIterations(),
                configuracion.maximumIterationsWithoutImprovement(), SEMILLA);
        imprimirResultado(resultado, ejecucion, snapshot, bloqueos);
        System.out.println("============================================================");
        System.out.println("                FIN DE VALIDACION SA");
        System.out.println("============================================================");
    }

    @Test
    void demuestraMantenimientoPreventivoRealConSa() throws Exception {
        List<MaintenanceDay> mantenimientos = new CargadorMantenimiento().cargar(recurso("mant.preventivo.09.10.txt"));
        assertEquals(37, mantenimientos.size());
        imprimirCabecera("SA - MANTENIMIENTO PREVENTIVO");
        demostrarMantenimiento(mantenimientos, "TA01", VehicleType.CAR, "TA02");
        demostrarMantenimiento(mantenimientos, "TM07", VehicleType.MOTORCYCLE, "TM01");
        demostrarMantenimiento(mantenimientos, "TB01", VehicleType.BICYCLE, "TB02");
        System.out.println("RESULTADO: MANTENIMIENTO RESPETADO CORRECTAMENTE");
        System.out.println("============================================================");
    }

    private Dataset cargarYValidarDatasetCompleto() throws Exception {
        List<ArchivoMensual> ventas = archivosMensuales("ventas", ARCHIVO_VENTAS, true);
        List<ArchivoMensual> bloqueos = archivosMensuales("bloqueos", ARCHIVO_BLOQUEOS, false);
        assertEquals(36, ventas.size());
        assertEquals(36, bloqueos.size());
        Map<YearMonth, List<Order>> pedidosPorPeriodo = new HashMap<>();
        Map<YearMonth, List<RoadBlock>> bloqueosPorPeriodo = new HashMap<>();
        int totalPedidos = 0;
        int totalBloqueos = 0;
        for (ArchivoMensual archivo : ventas) {
            List<Order> pedidos = cargarPedidos(archivo);
            assertFalse(pedidos.isEmpty(), () -> "Archivo de ventas vacio: " + archivo.path());
            pedidosPorPeriodo.put(archivo.periodo(), pedidos);
            totalPedidos += pedidos.size();
        }
        for (ArchivoMensual archivo : bloqueos) {
            List<RoadBlock> cargados = cargarBloqueos(archivo);
            assertFalse(cargados.isEmpty(), () -> "Archivo de bloqueos vacio: " + archivo.path());
            assertTrue(cargados.stream().allMatch(bloqueo -> bloqueo.endsAt().isAfter(bloqueo.startsAt())));
            bloqueosPorPeriodo.put(archivo.periodo(), cargados);
            totalBloqueos += cargados.size();
        }
        List<MaintenanceDay> mantenimientos = new CargadorMantenimiento().cargar(recurso("mant.preventivo.09.10.txt"));
        assertEquals(37, mantenimientos.size());
        assertTrue(mantenimientos.stream().anyMatch(dia -> dia.vehicleId().startsWith("TA")));
        assertTrue(mantenimientos.stream().anyMatch(dia -> dia.vehicleId().startsWith("TM")));
        assertTrue(mantenimientos.stream().anyMatch(dia -> dia.vehicleId().startsWith("TB")));
        return new Dataset(ventas, bloqueos, pedidosPorPeriodo, bloqueosPorPeriodo, mantenimientos, totalPedidos, totalBloqueos);
    }

    private List<ArchivoMensual> archivosMensuales(String directorio, Pattern patron, boolean ventas) throws Exception {
        try (var archivos = Files.list(recurso(directorio))) {
            List<ArchivoMensual> encontrados = new ArrayList<>();
            for (Path archivo : archivos.filter(Files::isRegularFile).toList()) {
                Matcher coincidencia = patron.matcher(archivo.getFileName().toString());
                if (coincidencia.matches()) {
                    YearMonth periodo = ventas
                            ? YearMonth.parse(coincidencia.group(1), PERIODO_VENTAS)
                            : YearMonth.of(2000 + Integer.parseInt(coincidencia.group(1)), Integer.parseInt(coincidencia.group(2)));
                    encontrados.add(new ArchivoMensual(archivo, periodo));
                }
            }
            encontrados.sort(Comparator.comparing(ArchivoMensual::periodo));
            return List.copyOf(encontrados);
        }
    }

    private List<Order> cargarPedidos(ArchivoMensual archivo) throws IOException {
        try {
            return new CargadorPedidos().cargar(archivo.path(), archivo.periodo(), ShiftSchedule.DEFAULT_ZONE);
        } catch (IllegalArgumentException excepcion) {
            throw rechazo(archivo.path(), excepcion);
        }
    }

    private List<RoadBlock> cargarBloqueos(ArchivoMensual archivo) throws IOException {
        try {
            return new CargadorBloqueos().cargar(archivo.path(), archivo.periodo(), ShiftSchedule.DEFAULT_ZONE);
        } catch (IllegalArgumentException excepcion) {
            throw rechazo(archivo.path(), excepcion);
        }
    }

    private AssertionError rechazo(Path archivo, IllegalArgumentException excepcion) throws IOException {
        Matcher coincidencia = Pattern.compile("linea (\\d+)").matcher(excepcion.getMessage());
        String contenido = "no disponible";
        if (coincidencia.find()) {
            int numeroLinea = Integer.parseInt(coincidencia.group(1));
            List<String> lineas = Files.readAllLines(archivo);
            if (numeroLinea <= lineas.size()) contenido = lineas.get(numeroLinea - 1);
        }
        return new AssertionError("Archivo invalido: " + archivo.getFileName() + "; contenido: " + contenido
                + "; motivo: " + excepcion.getMessage(), excepcion);
    }

    private void demostrarMantenimiento(List<MaintenanceDay> mantenimientos, String idMantenimiento,
                                        VehicleType tipo, String idAlternativo) {
        MaintenanceDay registro = mantenimientos.stream().filter(dia -> dia.vehicleId().equals(idMantenimiento)).findFirst()
                .orElseThrow(() -> new AssertionError("No existe registro real para " + idMantenimiento));
        Instant instante = registro.date().atTime(12, 0).atZone(ShiftSchedule.DEFAULT_ZONE).toInstant();
        Warehouse central = Warehouse.central("CENTRAL", new Location(10, 10));
        Vehicle detenido = new Vehicle(idMantenimiento, tipo, true);
        Vehicle alternativo = new Vehicle(idAlternativo, tipo, true);
        OperationalSnapshot snapshot = snapshot(central, List.of(detenido, alternativo), instante, mantenimientos);
        Order estimulo = new Order("ESTIMULO-" + idMantenimiento, new Location(10, 12), 2, instante, instante.plusSeconds(14_400));
        ResultadoPlanificacion resultado = planificador().planificar(snapshot, List.of(estimulo), List.of(),
                new AnnealingConfig(100.0, 1.0, 0.90, 2, 10, 10), random(idMantenimiento.hashCode()));

        assertFalse(snapshot.isVehiclePlannableAt(idMantenimiento, instante));
        assertTrue(resultado.esFactible(), () -> "violaciones: " + resultado.evaluacion().violations());
        assertTrue(resultado.plan().routeForVehicle(idMantenimiento).isEmpty());
        assertTrue(resultado.plan().routeForVehicle(idAlternativo).isPresent());
        System.out.println("------------------------------------------------------------");
        System.out.printf("Fecha: %s%nVehiculo: %s%nRegistro de mantenimiento encontrado: SI%nVehiculo planificable: NO%n"
                        + "Ruta asignada por SA: NO%nVehiculo alternativo: %s%n", registro.date(), idMantenimiento, idAlternativo);
    }

    private void imprimirResultado(ResultadoPlanificacion resultado, OperationalSimulatedAnnealingPlanner.Ejecucion ejecucion,
                                   OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        Set<String> atendidos = pedidosEntregados(resultado);
        System.out.println("\nRESULTADO");
        System.out.println("------------------------------------------------------------");
        System.out.printf("Plan factible: %s%nPedidos atendidos: %s%nPedidos no atendidos: %s%nCosto total: S/ %.2f%n"
                        + "Distancia total: %.2f km%nVecinos evaluados: %d%nVecinos aceptados: %d%nIteraciones ejecutadas: %d%nTemperatura final: %.4f%n",
                resultado.esFactible() ? "SI" : "NO", atendidos, resultado.noAtendidos().stream().map(Order::id).toList(),
                resultado.costoTotal(), distancia(resultado), ejecucion.evaluatedNeighbors(), ejecucion.acceptedNeighbors(),
                ejecucion.iterations(), ejecucion.finalTemperature());
        System.out.println("\nRUTAS");
        System.out.println("------------------------------------------------------------");
        resultado.evaluacion().schedulesByRouteId().values().stream()
                .sorted(Comparator.comparing(ruta -> ruta.route().vehicle().id())).forEach(ruta -> imprimirRuta(ruta, snapshot));
        System.out.println("\nVALIDACIONES");
        System.out.println("------------------------------------------------------------");
        System.out.printf("Bloqueos vigentes atravesados: %d%nCarga negativa detectada: %s%nViolaciones de capacidad: %d%n"
                        + "Violaciones de plazo: %d%nViolaciones totales: %d%n",
                bloqueosAtravesados(resultado, bloqueos), tieneCargaNegativa(resultado) ? "SI" : "NO",
                violaciones(resultado, PlanViolationType.VEHICLE_CAPACITY), violaciones(resultado, PlanViolationType.SLA_MISSED),
                resultado.evaluacion().violations().size());
    }

    private void imprimirRuta(ScheduledDeliveryRoute ruta, OperationalSnapshot snapshot) {
        Vehicle vehiculo = ruta.route().vehicle();
        var parametros = snapshot.fleetProfile().parametersFor(vehiculo.type());
        List<String> destinos = new ArrayList<>();
        destinos.add(formato(ruta.route().startLocation()));
        List<String> entregados = new ArrayList<>();
        for (RouteStop parada : ruta.route().stops()) {
            if (parada instanceof DeliveryStop entrega) {
                destinos.add(formato(entrega.order().destination()));
                entregados.add(entrega.order().id());
            } else if (parada instanceof WarehouseVisit visita) {
                destinos.add(formato(visita.warehouse().location()));
            }
        }
        int cargaFinal = ruta.scheduledStops().isEmpty() ? ruta.route().initialLoad() : ruta.scheduledStops().getLast().loadAfter();
        System.out.printf("Vehiculo: %s%nTipo: %s%nCapacidad: %d%nVelocidad: %.2f km/h%nRuta: %s%nPedidos entregados: %s%n"
                        + "Carga inicial: %d%nCarga final: %d%nDistancia: %.2f km%nCosto: S/ %.2f%n%n",
                vehiculo.id(), vehiculo.type(), parametros.capacity(), parametros.speedKmPerHour(), String.join(" -> ", destinos),
                entregados, ruta.route().initialLoad(), cargaFinal, ruta.totalDistanceKm(), ruta.totalCost());
    }

    private OperationalSnapshot snapshot(Warehouse central, List<Vehicle> flota, Instant instante,
                                         List<MaintenanceDay> mantenimientos) {
        Map<String, VehicleOperationalState> estados = flota.stream().collect(Collectors.toMap(Vehicle::id,
                vehiculo -> new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, central.location(), instante)));
        return new OperationalSnapshot(instante, FleetProfile.defaults(), InventorySnapshot.from(List.of(central)), estados,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, mantenimientos), ShiftSchedule.defaultSchedule(), List.of());
    }

    private OperationalSimulatedAnnealingPlanner planificador() {
        return new OperationalSimulatedAnnealingPlanner(new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork())));
    }

    private boolean tieneCargaNegativa(ResultadoPlanificacion resultado) {
        return resultado.evaluacion().schedulesByRouteId().values().stream().flatMap(ruta -> ruta.scheduledStops().stream())
                .anyMatch(parada -> parada.loadBefore() < 0 || parada.loadAfter() < 0);
    }

    private int bloqueosAtravesados(ResultadoPlanificacion resultado, List<RoadBlock> bloqueos) {
        return (int) resultado.evaluacion().schedulesByRouteId().values().stream().flatMap(ruta -> ruta.scheduledStops().stream())
                .flatMap(parada -> parada.approach().legs().stream()).filter(tramo -> bloqueos.stream().anyMatch(bloqueo ->
                        bloqueo.overlaps(tramo.departsAt(), tramo.arrivesAt())
                                && (bloqueo.blockedSegments().contains(new StreetSegment(tramo.from(), tramo.to()))
                                || bloqueo.blockedNodes().contains(tramo.from()) || bloqueo.blockedNodes().contains(tramo.to())))).count();
    }

    private int violaciones(ResultadoPlanificacion resultado, PlanViolationType tipo) {
        return (int) resultado.evaluacion().violations().stream().filter(violacion -> violacion.type() == tipo).count();
    }

    private void assertPedidosCoherentes(ResultadoPlanificacion resultado, List<Order> pedidos) {
        Set<String> atendidos = pedidosEntregados(resultado);
        Set<String> noAtendidos = resultado.noAtendidos().stream().map(Order::id).collect(Collectors.toSet());
        Set<String> esperados = pedidos.stream().map(Order::id).collect(Collectors.toSet());
        assertTrue(java.util.Collections.disjoint(atendidos, noAtendidos));
        Set<String> cubiertos = new HashSet<>(atendidos);
        cubiertos.addAll(noAtendidos);
        assertEquals(esperados, cubiertos);
    }

    private Set<String> pedidosEntregados(ResultadoPlanificacion resultado) {
        return resultado.plan().routes().stream().flatMap(ruta -> ruta.stops().stream()).filter(DeliveryStop.class::isInstance)
                .map(DeliveryStop.class::cast).map(parada -> parada.order().id()).collect(Collectors.toSet());
    }

    private double distancia(ResultadoPlanificacion resultado) {
        return resultado.evaluacion().schedulesByRouteId().values().stream().mapToDouble(ScheduledDeliveryRoute::totalDistanceKm).sum();
    }

    private RandomGenerator random(long semilla) {
        return RandomGeneratorFactory.<RandomGenerator>of("L64X128MixRandom").create(semilla);
    }

    private Path recurso(String nombre) throws URISyntaxException {
        URL url = getClass().getResource("/datos-profesor/" + nombre);
        if (url == null) throw new IllegalStateException("No se encontro el recurso: " + nombre);
        return Path.of(url.toURI());
    }

    private String formato(Location ubicacion) {
        return "(" + ubicacion.x() + "," + ubicacion.y() + ")";
    }

    private void imprimirCabecera(String titulo) {
        System.out.println("\n============================================================");
        System.out.printf("     %s%n", titulo);
        System.out.println("============================================================");
    }

    private record ArchivoMensual(Path path, YearMonth periodo) { }

    private record Dataset(List<ArchivoMensual> ventas, List<ArchivoMensual> bloqueos,
                           Map<YearMonth, List<Order>> pedidosPorPeriodo,
                           Map<YearMonth, List<RoadBlock>> bloqueosPorPeriodoCargados,
                           List<MaintenanceDay> mantenimientos, int totalPedidos, int totalBloqueos) {
        Map<YearMonth, ArchivoMensual> bloqueosPorPeriodo() {
            return bloqueos.stream().collect(Collectors.toMap(ArchivoMensual::periodo, archivo -> archivo));
        }
    }
}
