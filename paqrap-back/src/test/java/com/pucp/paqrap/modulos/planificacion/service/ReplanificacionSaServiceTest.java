package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.service.InicializadorFlota;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownResolution;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
import com.pucp.paqrap.modulos.incidencias.service.BreakdownAvailabilityCalculator;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.AnnealingConfig;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.OperationalSimulatedAnnealingPlanner;
import com.pucp.paqrap.modulos.planificacion.dto.ModoOperacion;
import com.pucp.paqrap.modulos.planificacion.dto.MotivoReplanificacion;
import com.pucp.paqrap.modulos.planificacion.dto.ParadaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.ReplanificacionResponse;
import com.pucp.paqrap.modulos.planificacion.dto.RutaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.SolicitudPlanificacion;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.entity.RoadLeg;
import com.pucp.paqrap.modulos.redvial.entity.StreetSegment;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

/**
 * La referencia de cada comparación es el SA original ({@link OperationalSimulatedAnnealingPlanner}) y el
 * RouteScheduler original, invocados directamente con snapshots armados en la prueba. Los instantes de
 * los eventos se eligen a partir del plan obtenido para no depender de qué vehículos asigna el SA.
 */
class ReplanificacionSaServiceTest {

    private static final Instant H = Instant.parse("2026-09-09T12:00:00Z");
    private static final AnnealingConfig CONFIG_PRUEBA = new AnnealingConfig(100.0, 1.0, 0.90, 5, 30, 30);
    private static final long PRESUPUESTO_AMPLIO = 600_000L;
    private static final long SEMILLA = 7L;
    private static final long EJECUCION = 1L;

    private final FuenteDatosOperativos fuente = new FuenteDatosOperativosMock();
    private final PlanificadorSaService planificador = new PlanificadorSaService(fuente, CONFIG_PRUEBA);
    private final ReplanificacionSaService servicio = new ReplanificacionSaService(fuente, planificador, Mockito.mock(PlanPersistenceService.class));
    private final OperationalSimulatedAnnealingPlanner original = new OperationalSimulatedAnnealingPlanner(
            new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork())));

    // ---------------------------------------------------------------- datos y referencia original

    private static List<Order> pedidos() {
        Instant plazo = H.plusSeconds(10 * 3600);
        return List.of(
                new Order("P01", new Location(31, 14), 12, H, plazo),
                new Order("P02", new Location(27, 18), 8, H, plazo),
                new Order("P03", new Location(29, 16), 4, H, plazo),
                // Dos pedidos chicos en línea: un solo vehículo los lleva en el mismo viaje (consolidación), así el
                // plan siempre tiene una ruta con dos entregas para las pruebas de avería y bloqueo.
                new Order("P06", new Location(35, 14), 2, H, plazo),
                new Order("P07", new Location(37, 14), 2, H, plazo));
    }

    private static Order p04(Instant registro) {
        return new Order("P04", new Location(35, 20), 6, registro, registro.plusSeconds(10 * 3600));
    }

    private static SolicitudPlanificacion solicitud() {
        // Misma configuración que el planificador de referencia: con los vecindarios corregidos (Semana 13) el SA sí
        // mejora la semilla, así que la de producción (1M iteraciones) ya no da el mismo plan que CONFIG_PRUEBA.
        return new SolicitudPlanificacion(ModoOperacion.SIMULACION_5D, H, pedidos(), List.of(), SEMILLA, PRESUPUESTO_AMPLIO,
                CONFIG_PRUEBA);
    }

    private static Warehouse central() {
        return Warehouse.central("CENTRAL", new Location(27, 14));
    }

    private static Vehicle vehiculo(String id) {
        return InicializadorFlota.crearFlotaInicial().stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
    }

    private static VehicleOperationalState enRuta(String id, Instant hasta) {
        return new VehicleOperationalState(vehiculo(id), VehicleStatus.IN_ROUTE, central().location(), 0, hasta);
    }

    private static OperationalSnapshot snapshotOriginal(Instant hora, Map<String, VehicleOperationalState> forzados,
                                                        List<BreakdownEvent> averias) {
        List<Warehouse> almacenes = List.of(central(),
                Warehouse.intermediate("NOROESTE", new Location(12, 38), 1_000),
                Warehouse.intermediate("ESTE", new Location(57, 27), 1_000));
        Map<String, VehicleOperationalState> estados = new LinkedHashMap<>();
        for (Vehicle v : InicializadorFlota.crearFlotaInicial()) {
            estados.put(v.id(), forzados.getOrDefault(v.id(),
                    new VehicleOperationalState(v, VehicleStatus.AVAILABLE, central().location(), hora)));
        }
        return new OperationalSnapshot(hora, FleetProfile.defaults(), InventorySnapshot.from(almacenes), estados,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()), ShiftSchedule.defaultSchedule(), averias);
    }

    private ResultadoPlanificacion directo(OperationalSnapshot snapshot, List<Order> pedidos, List<RoadBlock> bloqueos,
                                           long semilla) {
        return original.planificar(snapshot, pedidos, bloqueos, CONFIG_PRUEBA, new Random(semilla));
    }

    // ---------------------------------------------------------------- huellas comparables

    private static List<String> huellas(ResultadoPlanificacion resultado) {
        List<String> huellas = new ArrayList<>();
        resultado.plan().routes().stream().sorted(Comparator.comparing(r -> r.vehicle().id())).forEach(ruta -> {
            ScheduledDeliveryRoute p = resultado.evaluacion().schedulesByRouteId().get(ruta.id());
            StringBuilder t = new StringBuilder(ruta.id() + "|" + ruta.vehicle().id() + "|" + ruta.departureAt() + "|"
                    + p.completedAt() + "|" + p.totalDistanceKm() + "|" + p.totalCost());
            for (ScheduledRouteStop s : p.scheduledStops()) {
                String nombre = s.stop() instanceof DeliveryStop e ? "ENTREGA:" + e.order().id() + ":" + e.deliveredPackages()
                        : "ALMACEN:" + ((WarehouseVisit) s.stop()).warehouse().id() + ":" + ((WarehouseVisit) s.stop()).pickupPackages();
                t.append(" ").append(nombre).append("@").append(s.arrivedAt()).append("-").append(s.completedAt())
                        .append(":").append(s.loadBefore()).append(">").append(s.loadAfter());
            }
            huellas.add(t.toString());
        });
        return huellas;
    }

    private static List<String> huellas(Collection<RutaResponse> rutas) {
        List<String> huellas = new ArrayList<>();
        rutas.stream().sorted(Comparator.comparing(RutaResponse::vehiculoId)).forEach(ruta -> {
            StringBuilder t = new StringBuilder(ruta.id() + "|" + ruta.vehiculoId() + "|" + ruta.salida() + "|"
                    + ruta.llegadaFinal() + "|" + ruta.distanciaKm() + "|" + ruta.costo());
            for (ParadaResponse p : ruta.paradas()) {
                String id = ParadaResponse.ENTREGA.equals(p.tipo()) ? p.pedidoId() : p.almacenId();
                t.append(" ").append(p.tipo()).append(":").append(id).append(":").append(p.paquetes()).append("@")
                        .append(p.llegada()).append("-").append(p.fin()).append(":").append(p.cargaAntes()).append(">")
                        .append(p.cargaDespues());
            }
            huellas.add(t.toString());
        });
        return huellas;
    }

    private static List<RutaResponse> rutas(ReplanificacionResponse respuesta, Collection<String> ids) {
        return respuesta.plan().rutas().stream().filter(r -> ids.contains(r.id())).toList();
    }

    private static Map<String, Integer> entregas(Collection<RutaResponse> rutas, Instant hasta) {
        Map<String, Integer> total = new HashMap<>();
        for (RutaResponse ruta : rutas) {
            for (ParadaResponse p : ruta.paradas()) {
                if (ParadaResponse.ENTREGA.equals(p.tipo()) && (hasta == null || !p.fin().isAfter(hasta))) {
                    total.merge(p.pedidoId(), p.paquetes(), Integer::sum);
                }
            }
        }
        return total;
    }

    /** Paquetes de las paradas a las que el vehículo llegó antes de {@code instante} (el ejecutor entrega al llegar). */
    private static Map<String, Integer> entregasAntesDe(Collection<RutaResponse> rutas, Instant instante) {
        Map<String, Integer> total = new HashMap<>();
        for (RutaResponse ruta : rutas) {
            for (ParadaResponse p : ruta.paradas()) {
                if (ParadaResponse.ENTREGA.equals(p.tipo()) && p.llegada().isBefore(instante)) {
                    total.merge(p.pedidoId(), p.paquetes(), Integer::sum);
                }
            }
        }
        return total;
    }

    /** Pendientes calculados de forma independiente: pedido menos lo que entregan las rutas indicadas. */
    private static List<Order> pendientes(List<Order> pedidos, Map<String, Integer> cubiertos) {
        List<Order> resultado = new ArrayList<>();
        for (Order p : pedidos) {
            int falta = p.packages() - cubiertos.getOrDefault(p.id(), 0);
            if (falta > 0) {
                resultado.add(falta == p.packages() ? p
                        : new Order(p.id(), p.destination(), falta, p.registeredAt(), p.deadline()));
            }
        }
        return resultado;
    }

    private static RutaResponse rutaQueAunNoLlegaASuPrimeraEntrega(ReplanificacionResponse inicial) {
        return inicial.plan().rutas().stream()
                .filter(r -> r.paradas().stream().filter(p -> ParadaResponse.ENTREGA.equals(p.tipo())).findFirst()
                        .map(p -> p.llegada().isAfter(H.plusSeconds(60))).orElse(false))
                .findFirst().orElseThrow(() -> new AssertionError("el plan inicial no tiene una ruta adecuada"));
    }

    private static Set<Location> ubicacionesDeParadas(List<RutaVigente> rutas) {
        Set<Location> ubicaciones = new java.util.HashSet<>();
        for (RutaVigente rv : rutas) {
            ubicaciones.add(rv.ruta().startLocation());
            rv.ruta().stops().forEach(parada -> ubicaciones.add(parada.location()));
        }
        return ubicaciones;
    }

    // ---------------------------------------------------------------- plan inicial

    @Test
    void elPlanInicialEsIgualAlDelPlanificadorConLaMismaSolicitud() {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        PlanResponse esperado = planificador.planificar(solicitud());

        assertEquals(0, inicio.numeroReplanificacion());
        assertEquals(MotivoReplanificacion.INICIO, inicio.motivo());
        assertEquals(H, inicio.hora());
        assertTrue(inicio.rutasConservadas().isEmpty());
        assertEquals(esperado.rutas().stream().map(RutaResponse::id).sorted().toList(), inicio.rutasNuevas());
        assertEquals(esperado.rutas(), inicio.plan().rutas());
        assertEquals(esperado.costoTotal(), inicio.plan().costoTotal());
        assertEquals(esperado.distanciaTotalKm(), inicio.plan().distanciaTotalKm());
        assertEquals(esperado.violaciones(), inicio.plan().violaciones());
        assertEquals(esperado.noAtendidos(), inicio.plan().noAtendidos());
        assertEquals(esperado.semilla(), inicio.plan().semilla());
        assertEquals(esperado.presupuestoMs(), inicio.plan().presupuestoMs());
    }

    // ---------------------------------------------------------------- pedido nuevo

    @Test
    void unPedidoEnElMismoInstanteReplanificaTodoIgualQueElSaOriginal() {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        List<Order> todos = new ArrayList<>(pedidos());
        todos.add(p04(H));

        ReplanificacionResponse r = servicio.registrarPedido(EJECUCION, p04(H), H);
        ResultadoPlanificacion esperado = directo(snapshotOriginal(H, Map.of(), List.of()), todos, List.of(), SEMILLA + 1);

        assertTrue(r.rutasConservadas().isEmpty(), "las rutas que salen en este mismo instante se liberan");
        assertEquals(huellas(esperado), huellas(r.plan().rutas()));
        assertEquals(esperado.costoTotal(), r.plan().costoTotal());
        Set<String> antes = inicio.plan().rutas().stream().flatMap(x -> x.paradas().stream())
                .map(ParadaResponse::pedidoId).filter(id -> id != null).collect(Collectors.toSet());
        assertEquals(antes.stream().sorted().toList(), r.pedidosReasignados());
    }

    @Test
    void unPedidoPosteriorConservaLasRutasEnMarchaYUsaSoloVehiculosLibres() {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        Instant t = H.plusSeconds(30 * 60);

        ReplanificacionResponse r = servicio.registrarPedido(EJECUCION, p04(t), t);

        assertEquals(inicio.rutasNuevas(), r.rutasConservadas());
        assertEquals(huellas(inicio.plan().rutas()), huellas(rutas(r, r.rutasConservadas())));
        Map<String, VehicleOperationalState> ocupados = new HashMap<>();
        inicio.plan().rutas().forEach(x -> ocupados.put(x.vehiculoId(), enRuta(x.vehiculoId(), x.llegadaFinal())));
        ResultadoPlanificacion esperado = directo(snapshotOriginal(t, ocupados, List.of()), List.of(p04(t)), List.of(),
                SEMILLA + 1);
        assertEquals(huellas(esperado), huellas(rutas(r, r.rutasNuevas())));
        assertTrue(r.pedidosReasignados().isEmpty());
        assertEquals(Map.of("P04", 6), entregas(rutas(r, r.rutasNuevas()), null));
        for (RutaResponse nueva : rutas(r, r.rutasNuevas())) {
            assertFalse(ocupados.containsKey(nueva.vehiculoId()), "vehículo ocupado reutilizado: " + nueva.vehiculoId());
        }
    }

    @Test
    void cuandoLasRutasTerminanSusVehiculosQuedanLibresYSusEntregasNoSeRepiten() {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        Instant fin = inicio.plan().rutas().stream().map(RutaResponse::llegadaFinal).max(Comparator.naturalOrder()).orElseThrow();
        Instant t = fin.plusSeconds(60);

        ReplanificacionResponse r = servicio.registrarPedido(EJECUCION, p04(t), t);
        ResultadoPlanificacion esperado = directo(snapshotOriginal(t, Map.of(), List.of()), List.of(p04(t)), List.of(),
                SEMILLA + 1);

        assertTrue(r.rutasConservadas().isEmpty());
        assertEquals(huellas(esperado), huellas(r.plan().rutas()));
        assertEquals(Map.of("P04", 6), entregas(r.plan().rutas(), null));
    }

    @Test
    void unPedidoConPlazoVencidoQuedaComoColapsoYSigueSinAtenderseDespues() {
        servicio.iniciar(EJECUCION, solicitud());
        Order vencido = new Order("P99", new Location(30, 20), 4, H, H.plusSeconds(1800));

        ReplanificacionResponse r1 = servicio.registrarPedido(EJECUCION, vencido, H.plusSeconds(3600));
        ReplanificacionResponse r2 = servicio.registrarPedido(EJECUCION, p04(H.plusSeconds(7200)), H.plusSeconds(7200));

        assertTrue(r1.plan().colapso());
        assertTrue(r1.plan().noAtendidos().stream().anyMatch(n -> n.pedidoId().equals("P99")));
        assertTrue(r2.plan().noAtendidos().stream().anyMatch(n -> n.pedidoId().equals("P99")));
    }

    // ---------------------------------------------------------------- bloqueo

    @Test
    void unBloqueoReprogramaLasRutasEnMarchaConElRouteSchedulerOriginalYEvitaElTramo() {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        Instant t = H.plusSeconds(30 * 60);
        Set<Location> paradas = ubicacionesDeParadas(servicio.rutasVigentes(EJECUCION));
        RoadLeg tramoFuturo = servicio.rutasVigentes(EJECUCION).stream()
                .flatMap(rv -> rv.programada().scheduledStops().stream())
                .flatMap(s -> s.approach().legs().stream())
                .filter(leg -> leg.departsAt().isAfter(t))
                .filter(leg -> !paradas.contains(leg.from()) && !paradas.contains(leg.to()))
                .findFirst().orElseThrow(() -> new AssertionError("ninguna ruta recorre un tramo intermedio después de t"));
        RoadBlock bloqueo = new RoadBlock(H, H.plusSeconds(8 * 3600), List.of(tramoFuturo.from(), tramoFuturo.to()));
        RoadBlock efectivo = new RoadBlock(t, bloqueo.endsAt(), bloqueo.nodes());

        ReplanificacionResponse r = servicio.activarBloqueo(EJECUCION, bloqueo, t);

        assertEquals(MotivoReplanificacion.BLOQUEO, r.motivo());
        assertEquals(inicio.rutasNuevas(), r.rutasConservadas());
        RouteScheduler programadorOriginal = new RouteScheduler(new RoadNetwork());
        double distanciaAntes = inicio.plan().distanciaTotalKm();
        for (RutaVigente rv : servicio.rutasVigentes(EJECUCION)) {
            ScheduledDeliveryRoute esperado = programadorOriginal.schedule(rv.ruta(), rv.snapshotOrigen(), List.of(efectivo));
            assertEquals(esperado.completedAt(), rv.programada().completedAt());
            assertEquals(esperado.totalDistanceKm(), rv.programada().totalDistanceKm());
            assertEquals(esperado.totalCost(), rv.programada().totalCost());
            for (ScheduledRouteStop s : rv.programada().scheduledStops()) {
                for (RoadLeg leg : s.approach().legs()) {
                    assertFalse(efectivo.blockedSegments().contains(new StreetSegment(leg.from(), leg.to()))
                                    && efectivo.overlaps(leg.departsAt(), leg.arrivesAt()),
                            "la ruta " + rv.ruta().id() + " usa el tramo bloqueado");
                }
            }
        }
        assertTrue(r.plan().distanciaTotalKm() > distanciaAntes, "el desvío debe alargar el recorrido");
        // Lo ya completado antes del bloqueo no cambia.
        for (RutaResponse antes : inicio.plan().rutas()) {
            RutaResponse despues = rutas(r, List.of(antes.id())).getFirst();
            for (int i = 0; i < antes.paradas().size(); i++) {
                if (!antes.paradas().get(i).fin().isAfter(t)) {
                    assertEquals(antes.paradas().get(i), despues.paradas().get(i));
                }
            }
        }
    }

    /**
     * Indicación del profesor: si una parada pendiente queda sobre un bloqueo, se llega por la calle perpendicular, se
     * atiende y se sale por la misma calle. La ruta conservada sigue siendo factible y nunca recorre el bloqueo.
     */
    @Test
    void unBloqueoSobreUnaParadaPendienteSeAtiendeLlegandoPorLaPerpendicular() {
        servicio.iniciar(EJECUCION, solicitud());
        Instant t = H.plusSeconds(30 * 60);
        RutaVigente afectada = servicio.rutasVigentes(EJECUCION).stream()
                .filter(rv -> rv.programada().scheduledStops().stream()
                        .anyMatch(s -> s.stop() instanceof DeliveryStop && s.arrivedAt().isAfter(t)))
                .findFirst().orElseThrow(() -> new AssertionError("ninguna ruta tiene entregas después de t"));
        Location destino = afectada.programada().scheduledStops().stream()
                .filter(s -> s.stop() instanceof DeliveryStop && s.arrivedAt().isAfter(t))
                .findFirst().orElseThrow().stop().location();
        Location vecino = new Location(destino.x() + (destino.x() < Location.MAX_X ? 1 : -1), destino.y());
        RoadBlock bloqueo = new RoadBlock(t, H.plusSeconds(20 * 3600), List.of(destino, vecino));

        ReplanificacionResponse r = servicio.activarBloqueo(EJECUCION, bloqueo, t);

        assertTrue(r.rutasConservadas().contains(afectada.ruta().id()));
        assertTrue(r.plan().violaciones().stream().noneMatch(v -> afectada.ruta().id().equals(v.rutaId())),
                () -> "violaciones: " + r.plan().violaciones());
        RutaVigente reprogramada = servicio.rutasVigentes(EJECUCION).stream()
                .filter(rv -> rv.ruta().id().equals(afectada.ruta().id())).findFirst().orElseThrow();
        RoadBlock efectivo = new RoadBlock(t, bloqueo.endsAt(), bloqueo.nodes());
        for (ScheduledRouteStop s : reprogramada.programada().scheduledStops()) {
            for (RoadLeg leg : s.approach().legs()) {
                assertFalse(efectivo.blockedSegments().contains(new StreetSegment(leg.from(), leg.to()))
                        && efectivo.overlaps(leg.departsAt(), leg.arrivesAt()), "recorre el bloqueo");
            }
        }
    }

    @Test
    void unBloqueoYaVencidoNoCambiaElPlan() {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        Instant t = H.plusSeconds(30 * 60);
        RoadBlock vencido = new RoadBlock(H, t.minusSeconds(60), List.of(new Location(28, 14), new Location(31, 14)));

        ReplanificacionResponse r = servicio.activarBloqueo(EJECUCION, vencido, t);

        assertEquals(inicio.rutasNuevas(), r.rutasConservadas());
        assertTrue(r.rutasNuevas().isEmpty());
        assertEquals(inicio.plan().rutas(), r.plan().rutas());
    }

    // ---------------------------------------------------------------- avería

    @Test
    void unaAveriaEnRutaReasignaSusPaquetesIgualQueElSaOriginalYDejaAlVehiculoFuera() {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        RutaResponse afectada = rutaQueAunNoLlegaASuPrimeraEntrega(inicio);
        Instant t = H.plusSeconds(60);
        BreakdownEvent averia = new BreakdownEvent(afectada.vehiculoId(), BreakdownType.INTERMEDIATE, t, new Location(28, 14));
        BreakdownResolution resolucion = new BreakdownAvailabilityCalculator(ShiftSchedule.defaultSchedule()).resolve(averia);
        Instant disponible = resolucion.unavailableUntil().isAfter(resolucion.returnsToCentralAt())
                ? resolucion.unavailableUntil() : resolucion.returnsToCentralAt();

        ReplanificacionResponse r = servicio.registrarAveria(EJECUCION, averia, t);

        List<RutaResponse> otras = inicio.plan().rutas().stream().filter(x -> !x.id().equals(afectada.id())).toList();
        assertEquals(otras.stream().map(RutaResponse::id).sorted().toList(), r.rutasConservadas());
        assertTrue(r.vehiculosNoDisponibles().contains(afectada.vehiculoId()));
        assertTrue(r.plan().rutas().stream().noneMatch(x -> x.vehiculoId().equals(afectada.vehiculoId())));

        Map<String, VehicleOperationalState> forzados = new HashMap<>();
        otras.forEach(x -> forzados.put(x.vehiculoId(), enRuta(x.vehiculoId(), x.llegadaFinal())));
        forzados.put(afectada.vehiculoId(), new VehicleOperationalState(vehiculo(afectada.vehiculoId()),
                VehicleStatus.AVAILABLE, new Location(27, 14), disponible));
        List<Order> pendientes = pendientes(pedidos(), entregas(otras, null));
        ResultadoPlanificacion esperado = directo(snapshotOriginal(t, forzados, List.of(averia)), pendientes, List.of(),
                SEMILLA + 1);
        assertEquals(huellas(esperado), huellas(rutas(r, r.rutasNuevas())));

        Set<String> pedidosAfectados = afectada.paradas().stream().map(ParadaResponse::pedidoId)
                .filter(id -> id != null).collect(Collectors.toSet());
        assertTrue(r.pedidosReasignados().containsAll(pedidosAfectados));
        Map<String, Integer> total = entregas(r.plan().rutas(), null);
        for (Order p : pedidos()) {
            assertEquals(Integer.valueOf(p.packages()), total.get(p.id()), "paquetes de " + p.id());
        }
    }

    @Test
    void unaAveriaTrasEntregarSoloReplanificaLoQueFaltaba() {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        RutaResponse afectada = inicio.plan().rutas().stream()
                .filter(x -> x.paradas().stream().filter(p -> ParadaResponse.ENTREGA.equals(p.tipo())).count() >= 2)
                .findFirst().orElseThrow(() -> new AssertionError("el plan inicial no tiene una ruta con dos entregas"));
        List<ParadaResponse> entregasRuta = afectada.paradas().stream()
                .filter(p -> ParadaResponse.ENTREGA.equals(p.tipo())).toList();
        Instant t = entregasRuta.get(0).fin().plusSeconds(60);
        assertTrue(t.isBefore(entregasRuta.get(1).llegada()), "el instante debe caer entre la primera y la segunda entrega");

        ReplanificacionResponse r = servicio.registrarAveria(EJECUCION,
                new BreakdownEvent(afectada.vehiculoId(), BreakdownType.MINOR, t, new Location(28, 14)), t);

        Map<String, Integer> hecho = entregas(inicio.plan().rutas(), t); // también las rutas que ya terminaron
        Map<String, Integer> total = new HashMap<>(hecho);
        CortePlan.sumar(total, entregas(r.plan().rutas(), null));
        for (Order p : pedidos()) {
            assertEquals(Integer.valueOf(p.packages()), total.get(p.id()), "paquetes de " + p.id());
        }
        assertTrue(r.plan().noAtendidos().isEmpty());
    }

    /**
     * Trasvase (indicación del profesor): la moto que lleva P11 se avería a mitad de camino. Un despacho nuevo desde el
     * central ya no llegaría con holgura; la moto que volvía de entregar P10 está cerca, se desvía, recibe los 6
     * paquetes (30 min) y entrega P11 a tiempo.
     */
    @Test
    void unaAveriaConUnPedidoUrgenteSeResuelveConTrasvaseDeUnaUnidadCercana() {
        Instant t0 = H;
        Order p10 = new Order("P10", new Location(45, 14), 3, t0, t0.plusSeconds(60 * 60));
        servicio.iniciar(EJECUCION, new SolicitudPlanificacion(ModoOperacion.SIMULACION_5D, t0, List.of(p10),
                List.of(), SEMILLA, PRESUPUESTO_AMPLIO, CONFIG_PRUEBA));
        RutaVigente conP10 = servicio.rutasVigentes(EJECUCION).getFirst();
        Instant regresa = conP10.programada().scheduledStops().getFirst().completedAt();
        // P11 llega cuando la unidad de P10 está por volver; su moto pasa por (45,14) mientras la otra regresa.
        Instant t1 = regresa.minusSeconds(27 * 60);
        Order p11 = new Order("P11", new Location(60, 14), 6, t1, t1.plusSeconds(100 * 60));
        servicio.registrarPedido(EJECUCION, p11, t1);
        RutaVigente conP11 = servicio.rutasVigentes(EJECUCION).stream()
                .filter(rv -> rv.ruta().stops().stream().anyMatch(st -> st instanceof DeliveryStop e
                        && e.order().id().equals("P11")))
                .findFirst().orElseThrow();
        assertFalse(conP11.vehiculoId().equals(conP10.vehiculoId()), "el caso necesita dos unidades");
        Instant t = regresa.plusSeconds(5 * 60);
        Location donde = conP11.programada().scheduledStops().stream()
                .flatMap(st -> st.approach().legs().stream())
                .filter(leg -> !leg.departsAt().isAfter(t) && leg.arrivesAt().isAfter(t))
                .findFirst().orElseThrow(() -> new AssertionError("la unidad de P11 debe ir en camino en t")).from();
        BreakdownEvent averia = new BreakdownEvent(conP11.vehiculoId(), BreakdownType.INTERMEDIATE, t, donde);

        ReplanificacionResponse r = servicio.registrarAveria(EJECUCION, averia, t);

        RutaResponse rescate = r.plan().rutas().stream()
                .filter(x -> x.paradas().stream().anyMatch(pp -> ParadaResponse.TRASVASE.equals(pp.tipo())))
                .findFirst().orElseThrow(() -> new AssertionError("no hubo trasvase: " + r.plan().rutas()));
        assertEquals(conP10.vehiculoId(), rescate.vehiculoId());
        assertEquals(conP10.ruta().id() + ReplanificacionSaService.SUFIJO_DESVIO, rescate.id());
        assertEquals(t, rescate.salida());
        ParadaResponse trasvase = rescate.paradas().stream()
                .filter(pp -> ParadaResponse.TRASVASE.equals(pp.tipo())).findFirst().orElseThrow();
        assertEquals(donde.x(), trasvase.x());
        assertEquals(donde.y(), trasvase.y());
        assertEquals(6, trasvase.paquetes());
        assertEquals(trasvase.llegada().plus(Duration.ofMinutes(30)), trasvase.fin());
        ParadaResponse entregaP11 = rescate.paradas().stream().filter(pp -> "P11".equals(pp.pedidoId()))
                .findFirst().orElseThrow();
        assertFalse(entregaP11.llegada().isAfter(p11.deadline()), "P11 debe llegar a tiempo");
        assertTrue(r.plan().noAtendidos().isEmpty());
        assertTrue(r.plan().violaciones().isEmpty(), () -> "violaciones: " + r.plan().violaciones());
        assertTrue(r.plan().rutas().stream().noneMatch(x -> x.vehiculoId().equals(conP11.vehiculoId())));

        // El desvío sigue vigente en la replanificación siguiente.
        ReplanificacionResponse luego = servicio.replanificar(EJECUCION, ReplanificacionService.Eventos.NINGUNO,
                t.plusSeconds(60));
        assertTrue(luego.rutasConservadas().contains(rescate.id()));
    }

    /**
     * Inserción en rutas en marcha (Semana 13): con una sola moto, ya en camino a P20, llega P21 con plazo corto. Esperar
     * a que vuelva al central no llega a tiempo; en cambio, tras atender P20 recarga en ESTE (57,27), a 3 km, y entrega
     * P21 sin volver. La ruta conserva su id y su salida, y lo ya recorrido no cambia.
     */
    @Test
    void unPedidoUrgenteSeInsertaEnUnaRutaEnMarchaRecargandoEnUnIntermedio() {
        FuenteDatosOperativos unaMoto = new FuenteDatosOperativosMock() {
            @Override
            public List<Vehicle> flota() {
                return List.of(vehiculo("TM01"));
            }
        };
        PlanificadorSaService sa = new PlanificadorSaService(unaMoto, CONFIG_PRUEBA);
        ReplanificacionSaService conUnaMoto = new ReplanificacionSaService(unaMoto, sa,
                Mockito.mock(PlanPersistenceService.class));
        Order p20 = new Order("P20", new Location(60, 27), 2, H, H.plusSeconds(10 * 3600));
        conUnaMoto.iniciar(EJECUCION, new SolicitudPlanificacion(ModoOperacion.SIMULACION_5D, H, List.of(p20), List.of(),
                SEMILLA, PRESUPUESTO_AMPLIO, CONFIG_PRUEBA));
        RutaVigente antes = conUnaMoto.rutasVigentes(EJECUCION).getFirst();
        Instant t1 = H.plusSeconds(20 * 60);
        // Plazo 14:30: solo llega si recarga en ESTE y come después de entregar P21 (refrigerio postergado).
        Order p21 = new Order("P21", new Location(58, 30), 3, t1, t1.plusSeconds(130 * 60));

        ReplanificacionResponse r = conUnaMoto.registrarPedido(EJECUCION, p21, t1);

        assertTrue(r.plan().noAtendidos().isEmpty(), () -> "sin atender: " + r.plan().noAtendidos());
        RutaResponse ruta = r.plan().rutas().getFirst();
        assertEquals(antes.ruta().id(), ruta.id());
        assertEquals(antes.ruta().departureAt(), ruta.salida());
        List<String> secuencia = ruta.paradas().stream()
                .map(pp -> ParadaResponse.ENTREGA.equals(pp.tipo()) ? pp.pedidoId() : pp.almacenId()).toList();
        assertEquals(List.of("P20", "ESTE", "P21", "CENTRAL"), secuencia);
        assertEquals(3, ruta.paradas().get(1).paquetes(), "recarga en ESTE lo que lleva a P21");
        assertEquals(antes.programada().scheduledStops().getFirst().arrivedAt(), ruta.paradas().getFirst().llegada(),
                "lo ya recorrido no cambia");
        assertFalse(ruta.paradas().get(2).llegada().isAfter(p21.deadline()));
        assertTrue(r.plan().violaciones().isEmpty(), () -> "violaciones: " + r.plan().violaciones());
        assertTrue(r.rutasConservadas().contains(ruta.id()));
    }

    /**
     * Trasvase desde una unidad en mitad de su ruta (Semana 13): TM01 va con P30 y P31; TM02 lleva P32, urgente, y se
     * avería a mitad de camino sin otra unidad libre. TM01, tras atender P30, pasa por la avería, recibe los paquetes,
     * entrega P32 a tiempo y luego sigue con P31.
     */
    @Test
    void unaUnidadEnMitadDeSuRutaHaceElTrasvaseYLuegoSigueConLoSuyo() {
        FuenteDatosOperativos dosMotos = new FuenteDatosOperativosMock() {
            @Override
            public List<Vehicle> flota() {
                return List.of(vehiculo("TM01"), vehiculo("TM02"));
            }
        };
        ReplanificacionSaService conDosMotos = new ReplanificacionSaService(dosMotos,
                new PlanificadorSaService(dosMotos, CONFIG_PRUEBA), Mockito.mock(PlanPersistenceService.class));
        Order p30 = new Order("P30", new Location(40, 14), 2, H, H.plusSeconds(10 * 3600));
        Order p31 = new Order("P31", new Location(44, 14), 2, H, H.plusSeconds(10 * 3600));
        conDosMotos.iniciar(EJECUCION, new SolicitudPlanificacion(ModoOperacion.SIMULACION_5D, H, List.of(p30, p31),
                List.of(), SEMILLA, PRESUPUESTO_AMPLIO, CONFIG_PRUEBA));
        RutaVigente conP30 = conDosMotos.rutasVigentes(EJECUCION).getFirst();
        assertEquals(1, conDosMotos.rutasVigentes(EJECUCION).size(), "P30 y P31 van en la misma moto");
        Instant t1 = H.plusSeconds(10 * 60);
        Order p32 = new Order("P32", new Location(60, 14), 4, t1, t1.plusSeconds(135 * 60));
        conDosMotos.registrarPedido(EJECUCION, p32, t1);
        RutaVigente conP32 = conDosMotos.rutasVigentes(EJECUCION).stream()
                .filter(rv -> !rv.vehiculoId().equals(conP30.vehiculoId())).findFirst().orElseThrow();
        Instant t = H.plusSeconds(40 * 60);
        Location donde = conP32.programada().scheduledStops().stream()
                .flatMap(st -> st.approach().legs().stream())
                .filter(leg -> !leg.departsAt().isAfter(t) && leg.arrivesAt().isAfter(t))
                .findFirst().orElseThrow().from();

        ReplanificacionResponse r = conDosMotos.registrarAveria(EJECUCION,
                new BreakdownEvent(conP32.vehiculoId(), BreakdownType.INTERMEDIATE, t, donde), t);

        assertTrue(r.plan().noAtendidos().isEmpty(), () -> "sin atender: " + r.plan().noAtendidos());
        RutaResponse ruta = r.plan().rutas().stream().filter(x -> x.vehiculoId().equals(conP30.vehiculoId()))
                .findFirst().orElseThrow();
        assertEquals(conP30.ruta().id(), ruta.id(), "es la misma ruta, con el trasvase intercalado");
        List<String> secuencia = ruta.paradas().stream().map(pp -> switch (pp.tipo()) {
            case ParadaResponse.ENTREGA -> pp.pedidoId();
            case ParadaResponse.TRASVASE -> "TRASVASE";
            default -> pp.almacenId();
        }).toList();
        // Atiende una de sus entregas, hace el trasvase, entrega P32 y sigue con la otra (el orden de P30/P31 lo elige el SA).
        assertEquals(5, secuencia.size(), () -> "secuencia: " + secuencia);
        assertEquals(List.of("TRASVASE", "P32"), secuencia.subList(1, 3), () -> "secuencia: " + secuencia);
        assertEquals(java.util.Set.of("P30", "P31"), java.util.Set.of(secuencia.get(0), secuencia.get(3)));
        assertEquals("CENTRAL", secuencia.get(4));
        ParadaResponse entregaP32 = ruta.paradas().get(2);
        assertFalse(entregaP32.llegada().isAfter(p32.deadline()), "P32 llega a tiempo");
        assertTrue(r.plan().violaciones().isEmpty(), () -> "violaciones: " + r.plan().violaciones());
    }

    /** Indicación del profesor: tras una MINOR la unidad espera 2 horas y sigue su ruta con su carga, sin volver antes. */
    @ParameterizedTest
    @ValueSource(ints = {120, 60})
    void trasUnaAveriaMenorLaUnidadContinuaSuRutaDesdeDondeQuedo(int minutos) {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        RutaResponse afectada = rutaQueAunNoLlegaASuPrimeraEntrega(inicio);
        Instant t = H.plusSeconds(60);
        BreakdownEvent averia = new BreakdownEvent(afectada.vehiculoId(), BreakdownType.MINOR, t, new Location(28, 14), minutos);

        ReplanificacionResponse r = servicio.registrarAveria(EJECUCION, averia, t);

        RutaResponse continuacion = r.plan().rutas().stream()
                .filter(ruta -> ruta.vehiculoId().equals(afectada.vehiculoId())).findFirst().orElseThrow();
        assertEquals(afectada.id() + "-CONT", continuacion.id());
        assertTrue(r.plan().violaciones().isEmpty(), () -> "violaciones: " + r.plan().violaciones());
        assertEquals(t.plus(Duration.ofMinutes(minutos)), continuacion.salida());
        assertEquals(afectada.paradas().size(), continuacion.paradas().size());
        assertEquals(afectada.paradas().getFirst().cargaAntes(), continuacion.paradas().getFirst().cargaAntes());
        assertEquals(new ParadaResponse.Punto(28, 14), continuacion.paradas().getFirst().camino().getFirst());
        Map<String, Integer> total = entregas(r.plan().rutas(), null);
        for (Order p : pedidos()) {
            assertEquals(Integer.valueOf(p.packages()), total.get(p.id()), "paquetes de " + p.id());
        }

        // Una replanificación durante la espera no le quita la ruta.
        ReplanificacionResponse durante = servicio.replanificar(EJECUCION,
                new ReplanificacionService.Eventos(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                t.plusSeconds(1800));
        assertTrue(durante.rutasConservadas().contains(continuacion.id()));
    }

    /** Sin entregas pendientes (ya volvía al central), al terminar la avería retoma el regreso por las calles. */
    @Test
    void trasUnaAveriaMenorEnRutaElVehiculoVuelveAEstarDisponibleAlTerminarLaIndisponibilidad() {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        RutaResponse afectada = rutaQueAunNoLlegaASuPrimeraEntrega(inicio);
        Instant t = afectada.paradas().stream().filter(p -> ParadaResponse.ENTREGA.equals(p.tipo()))
                .reduce((a, b) -> b).orElseThrow().fin().plusSeconds(60);
        assertTrue(t.isBefore(afectada.llegadaFinal()), "la avería debe caer durante el regreso");
        BreakdownEvent averia = new BreakdownEvent(
                afectada.vehiculoId(), BreakdownType.MINOR, t, new Location(28, 14));
        servicio.registrarAveria(EJECUCION, averia, t);
        Instant despues = t.plus(BreakdownType.MINOR.minimumUnavailable()).plusSeconds(60);

        ReplanificacionResponse luego = servicio.replanificar(EJECUCION,
                new ReplanificacionService.Eventos(List.of(), List.of(), List.of(), List.of(),
                        List.of(averia), List.of()), despues);

        assertEquals(MotivoReplanificacion.AVERIA, luego.motivo());
        assertFalse(luego.vehiculosNoDisponibles().contains(afectada.vehiculoId()));
        // Regresa vacía al central por las calles, desde donde se averió.
        RutaResponse regreso = luego.plan().rutas().stream()
                .filter(ruta -> ruta.vehiculoId().equals(afectada.vehiculoId())).findFirst().orElseThrow();
        assertEquals("REGRESO-" + afectada.vehiculoId(), regreso.id());
        assertEquals(despues, regreso.salida());
        assertEquals(1, regreso.paradas().size());
        assertEquals(0, regreso.paradas().getFirst().cargaAntes());
        assertEquals(new ParadaResponse.Punto(28, 14), regreso.paradas().getFirst().camino().getFirst());
        assertTrue(luego.rutasConservadas().contains(regreso.id()));

        // Al llegar, vuelve a ser planificable.
        ReplanificacionResponse alLlegar = servicio.replanificar(EJECUCION,
                new ReplanificacionService.Eventos(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                regreso.llegadaFinal().plusSeconds(60));
        assertFalse(alLlegar.rutasConservadas().contains(regreso.id()));
        assertFalse(alLlegar.vehiculosNoDisponibles().contains(afectada.vehiculoId()));
    }

    @Test
    void unaAveriaIntermediaDevuelveElVehiculoAlCentralSegunElCalculoOriginal() {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        RutaResponse afectada = rutaQueAunNoLlegaASuPrimeraEntrega(inicio);
        Instant t = H.plusSeconds(60);
        BreakdownEvent averia = new BreakdownEvent(afectada.vehiculoId(), BreakdownType.INTERMEDIATE, t, new Location(28, 14));
        BreakdownResolution resolucion = new BreakdownAvailabilityCalculator(ShiftSchedule.defaultSchedule()).resolve(averia);
        Instant disponible = resolucion.unavailableUntil().isAfter(resolucion.returnsToCentralAt())
                ? resolucion.unavailableUntil() : resolucion.returnsToCentralAt();

        ReplanificacionResponse enAveria = servicio.registrarAveria(EJECUCION, averia, t);
        ReplanificacionResponse antes = servicio.registrarPedido(EJECUCION, p04(disponible.minusSeconds(60)),
                disponible.minusSeconds(60));
        Order p05 = new Order("P05", new Location(22, 10), 2, disponible, disponible.plusSeconds(10 * 3600));
        ReplanificacionResponse despues = servicio.registrarPedido(EJECUCION, p05, disponible);

        assertTrue(enAveria.vehiculosNoDisponibles().contains(afectada.vehiculoId()));
        assertTrue(antes.vehiculosNoDisponibles().contains(afectada.vehiculoId()));
        assertFalse(despues.vehiculosNoDisponibles().contains(afectada.vehiculoId()));
    }
    @Test
    void unaAveriaMayorExcluyeHastaElTurnoPosteriorYLuegoVuelveASerElegible() {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        RutaResponse afectada = rutaQueAunNoLlegaASuPrimeraEntrega(inicio);
        Instant t = H.plusSeconds(60);
        BreakdownEvent averia = new BreakdownEvent(afectada.vehiculoId(), BreakdownType.MAJOR, t, new Location(28, 14));
        BreakdownResolution resolucion = new BreakdownAvailabilityCalculator(ShiftSchedule.defaultSchedule()).resolve(averia);
        Instant disponible = resolucion.unavailableUntil().isAfter(resolucion.returnsToCentralAt())
                ? resolucion.unavailableUntil() : resolucion.returnsToCentralAt();
        ReplanificacionResponse enAveria = servicio.registrarAveria(EJECUCION, averia, t);
        ReplanificacionResponse antes = servicio.registrarPedido(
        EJECUCION,
        p04(disponible.minusSeconds(60)),
        disponible.minusSeconds(60));
        ReplanificacionResponse despues = servicio.registrarPedido(
        EJECUCION,
        new Order(
                "P-MAJOR-DESPUES",
                new Location(22, 10),
                2,
                disponible.plusSeconds(60),
                disponible.plusSeconds(10 * 3600)),
        disponible.plusSeconds(60));
        assertTrue(enAveria.vehiculosNoDisponibles().contains(afectada.vehiculoId()));
        assertTrue(antes.vehiculosNoDisponibles().contains(afectada.vehiculoId()));
        assertFalse(despues.vehiculosNoDisponibles().contains(afectada.vehiculoId()));
    }

    @Test
    void unaAveriaDeUnVehiculoLibreNoTocaLasRutasYLoDejaFueraSoloDuranteLaIndisponibilidad() {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        Set<String> usados = inicio.plan().rutas().stream().map(RutaResponse::vehiculoId).collect(Collectors.toSet());
        String libre = InicializadorFlota.crearFlotaInicial().stream().map(Vehicle::id)
                .filter(id -> !usados.contains(id)).reduce((a, b) -> b).orElseThrow();
        Instant t = H.plusSeconds(30 * 60);
        BreakdownEvent averia = new BreakdownEvent(libre, BreakdownType.MINOR, t, central().location());

        ReplanificacionResponse r = servicio.registrarAveria(EJECUCION, averia, t);
        Instant despues = t.plus(BreakdownType.MINOR.minimumUnavailable()).plusSeconds(60);
        ReplanificacionResponse luego = servicio.registrarPedido(EJECUCION, p04(despues), despues);

        assertEquals(inicio.rutasNuevas(), r.rutasConservadas());
        assertTrue(r.rutasNuevas().isEmpty());
        assertEquals(huellas(inicio.plan().rutas()), huellas(r.plan().rutas()));
        assertEquals(List.of(libre), r.vehiculosNoDisponibles());
        assertFalse(luego.vehiculosNoDisponibles().contains(libre));
    }

    @Test
    void minorMayorATresDiasConservaIndisponibilidadHastaSuFin() {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        Set<String> usados = inicio.plan().rutas().stream().map(RutaResponse::vehiculoId).collect(Collectors.toSet());
        String libre = InicializadorFlota.crearFlotaInicial().stream().map(Vehicle::id)
                .filter(id -> !usados.contains(id)).reduce((a, b) -> b).orElseThrow();
        Instant t = H.plusSeconds(1800);
        Instant fin = t.plus(Duration.ofDays(4));
        BreakdownEvent averia = new BreakdownEvent(libre, BreakdownType.MINOR, t, central().location(), 4 * 24 * 60);
        servicio.registrarAveria(EJECUCION, averia, t);
        Instant antes = t.plus(Duration.ofDays(3)).plusSeconds(60);
        ReplanificacionResponse durante = servicio.registrarPedido(EJECUCION, p04(antes), antes);
        Order pedido = new Order(String.valueOf(123456789), new Location(22, 10), 2, fin, fin.plusSeconds(36000));
        ReplanificacionResponse despues = servicio.registrarPedido(EJECUCION, pedido, fin);
        assertTrue(durante.vehiculosNoDisponibles().contains(libre));
        assertFalse(despues.vehiculosNoDisponibles().contains(libre));
    }

    /**
     * El ejecutor del plan entrega al llegar: una avería durante la atención de un cliente no deshace esa entrega,
     * y la ruta se corta en el instante de la avería aunque la replanificación ocurra después.
     */
    @Test
    void unaAveriaDuranteLaAtencionCuentaLaEntregaYCortaEnSuInstante() {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        RutaResponse afectada = inicio.plan().rutas().stream()
                .filter(x -> x.paradas().stream().filter(p -> ParadaResponse.ENTREGA.equals(p.tipo())).count() >= 2)
                .findFirst().orElseThrow(() -> new AssertionError("el plan inicial no tiene una ruta con dos entregas"));
        List<ParadaResponse> entregasRuta = afectada.paradas().stream()
                .filter(p -> ParadaResponse.ENTREGA.equals(p.tipo())).toList();
        Instant instanteAveria = entregasRuta.get(0).llegada().plusSeconds(60);
        assertTrue(instanteAveria.isBefore(entregasRuta.get(0).fin()), "la avería debe caer durante la atención");
        Instant ahora = instanteAveria.plusSeconds(120);

        ReplanificacionResponse r = servicio.registrarAveria(EJECUCION,
                new BreakdownEvent(afectada.vehiculoId(), BreakdownType.MINOR, instanteAveria, new Location(28, 14)), ahora);

        Map<String, Integer> total = entregasAntesDe(List.of(afectada), instanteAveria);
        assertEquals(Integer.valueOf(entregasRuta.get(0).paquetes()), total.get(entregasRuta.get(0).pedidoId()));
        CortePlan.sumar(total, entregas(r.plan().rutas(), null));
        for (Order p : pedidos()) {
            assertEquals(Integer.valueOf(p.packages()), total.get(p.id()), "paquetes de " + p.id());
        }
    }

    // ---------------------------------------------------------------- ciclo periódico y eventos combinados

    @Test
    void elCicloPeriodicoSinEventosConservaLasRutasEnMarchaSinResetearLaFlota() {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        Instant t = H.plusSeconds(30 * 60);

        ReplanificacionResponse r = servicio.replanificar(EJECUCION, ReplanificacionService.Eventos.NINGUNO, t);

        assertEquals(MotivoReplanificacion.PERIODICO, r.motivo());
        assertEquals(1, r.numeroReplanificacion());
        assertEquals(inicio.rutasNuevas(), r.rutasConservadas());
        assertTrue(r.rutasNuevas().isEmpty(), "no queda nada pendiente: todo va en las rutas en marcha");
        assertEquals(huellas(inicio.plan().rutas()), huellas(r.plan().rutas()));
        assertTrue(r.plan().noAtendidos().isEmpty());
    }

    @Test
    void elCicloPeriodicoReplanificaLoQueQuedoPendiente() {
        servicio.iniciar(EJECUCION, solicitud());
        Instant t = H.plusSeconds(30 * 60);
        servicio.registrarPedido(EJECUCION, p04(t), t);

        ReplanificacionResponse r = servicio.replanificar(EJECUCION, ReplanificacionService.Eventos.NINGUNO,
                t.plusSeconds(60));

        Map<String, Integer> total = entregas(r.plan().rutas(), null);
        assertEquals(Integer.valueOf(6), total.get("P04"), "P04 sigue cubierto tras el ciclo periódico");
        for (Order p : pedidos()) {
            assertEquals(Integer.valueOf(p.packages()), total.get(p.id()), "paquetes de " + p.id());
        }
    }

    @Test
    void variosEventosDelMismoTickSeAplicanEnUnaSolaReplanificacion() {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        RutaResponse afectada = rutaQueAunNoLlegaASuPrimeraEntrega(inicio);
        Instant t = H.plusSeconds(60);
        Order p05 = new Order("P05", new Location(22, 10), 2, t, t.plusSeconds(10 * 3600));
        RoadBlock bloqueo = new RoadBlock(t, H.plusSeconds(3600), List.of(new Location(40, 40), new Location(41, 40)));
        BreakdownEvent averia = new BreakdownEvent(afectada.vehiculoId(), BreakdownType.MINOR, t, new Location(28, 14));

        ReplanificacionResponse r = servicio.replanificar(EJECUCION,
                new ReplanificacionService.Eventos(List.of(p04(t), p05), List.of(bloqueo), List.of(averia)), t);

        assertEquals(MotivoReplanificacion.AVERIA, r.motivo());
        assertEquals(1, r.numeroReplanificacion());
        assertTrue(servicio.conocePedido(EJECUCION, "P04") && servicio.conocePedido(EJECUCION, "P05"));
        // Avería menor: la unidad solo puede seguir con su propia carga (ruta -CONT), no recibe pedidos nuevos.
        assertTrue(r.plan().rutas().stream().filter(x -> x.vehiculoId().equals(afectada.vehiculoId()))
                .allMatch(x -> x.id().endsWith(CortePlan.SUFIJO_CONTINUACION)));
        Map<String, Integer> total = entregas(r.plan().rutas(), null);
        assertEquals(Integer.valueOf(6), total.get("P04"));
        assertEquals(Integer.valueOf(2), total.get("P05"));
        for (Order p : pedidos()) {
            assertEquals(Integer.valueOf(p.packages()), total.get(p.id()), "paquetes de " + p.id());
        }
    }

    /** El adapter pasa en cada ciclo los bloqueos del horizonte: uno que la ejecución ya conoce no se repite. */
    @Test
    void unBloqueoYaConocidoNoSeVuelveARegistrar() {
        servicio.iniciar(EJECUCION, solicitud());
        RoadBlock bloqueo = new RoadBlock(H, H.plusSeconds(8 * 3600), List.of(new Location(40, 40), new Location(41, 40)));
        ReplanificacionResponse r1 = servicio.activarBloqueo(EJECUCION, bloqueo, H.plusSeconds(600));
        List<RoadBlock> despuesDelPrimero = servicio.bloqueosVigentes(EJECUCION);

        ReplanificacionResponse r2 = servicio.activarBloqueo(EJECUCION, bloqueo, H.plusSeconds(900));

        assertEquals(despuesDelPrimero, servicio.bloqueosVigentes(EJECUCION));
        assertEquals(huellas(r1.plan().rutas()), huellas(r2.plan().rutas()));
    }

    @Test
    void unPedidoRepetidoEnElMismoTickSeRechazaSinAlterarElEstado() {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        Instant t = H.plusSeconds(600);

        assertThrows(ReglaNegocioException.class, () -> servicio.replanificar(EJECUCION,
                new ReplanificacionService.Eventos(List.of(p04(t), p04(t)), List.of(), List.of()), t));
        assertFalse(servicio.conocePedido(EJECUCION, "P04"));
        assertEquals(inicio, servicio.planVigente(EJECUCION).orElseThrow());
        assertTrue(servicio.estaIniciada(EJECUCION));
        assertFalse(servicio.estaIniciada(9L));
    }

    // ---------------------------------------------------------------- estado y validaciones

    @Test
    void cadaReplanificacionUsaLaSemillaBaseMasSuNumero() {
        ReplanificacionResponse r0 = servicio.iniciar(EJECUCION, solicitud());
        ReplanificacionResponse r1 = servicio.registrarPedido(EJECUCION, p04(H.plusSeconds(600)), H.plusSeconds(600));
        ReplanificacionResponse r2 = servicio.activarBloqueo(EJECUCION,
                new RoadBlock(H, H.plusSeconds(3600), List.of(new Location(40, 40), new Location(41, 40))), H.plusSeconds(900));

        assertEquals(List.of(0, 1, 2), List.of(r0.numeroReplanificacion(), r1.numeroReplanificacion(), r2.numeroReplanificacion()));
        assertEquals(List.of(SEMILLA, SEMILLA + 1, SEMILLA + 2), List.of(r0.plan().semilla(), r1.plan().semilla(), r2.plan().semilla()));
        assertEquals(r2, servicio.planVigente(EJECUCION).orElseThrow());
    }

    @Test
    void acumulaElTiempoDeTodosLosCiclosSaExitosos() {
        ReplanificacionResponse inicial = servicio.iniciar(EJECUCION, solicitud());
        ReplanificacionResponse replanificado = servicio.registrarPedido(EJECUCION,
                p04(H.plusSeconds(600)), H.plusSeconds(600));

        ReplanificacionService.MetricasPlanificacion metricas = servicio.metricas(EJECUCION).orElseThrow();

        assertEquals(inicial.plan().duracionMs() + replanificado.plan().duracionMs(),
                metricas.totalComputationMs());
        assertEquals(1, metricas.replanningCount());
    }

    @Test
    void lasEjecucionesSonIndependientes() {
        ReplanificacionResponse a = servicio.iniciar(1L, solicitud());
        servicio.iniciar(2L, solicitud());
        servicio.registrarPedido(2L, p04(H.plusSeconds(600)), H.plusSeconds(600));

        assertEquals(a, servicio.planVigente(1L).orElseThrow());
        assertEquals(1, servicio.planVigente(2L).orElseThrow().numeroReplanificacion());
    }

    @Test
    void rechazaEventosInvalidosSinAlterarElEstado() {
        ReplanificacionResponse inicio = servicio.iniciar(EJECUCION, solicitud());
        Instant t = H.plusSeconds(600);
        ReplanificacionResponse r1 = servicio.registrarPedido(EJECUCION, p04(t), t);

        assertThrows(ReglaNegocioException.class, () -> servicio.iniciar(EJECUCION, solicitud()));
        assertThrows(ReglaNegocioException.class, () -> servicio.registrarPedido(EJECUCION, p04(t), t));
        assertThrows(ReglaNegocioException.class,
                () -> servicio.registrarPedido(EJECUCION, new Order("P50", new Location(1, 1), 1, H, H.plusSeconds(9000)), H));
        assertThrows(ReglaNegocioException.class, () -> servicio.registrarAveria(EJECUCION,
                new BreakdownEvent("XX99", BreakdownType.MINOR, t, central().location()), t));
        assertEquals(r1, servicio.planVigente(EJECUCION).orElseThrow());
        assertEquals(0, inicio.numeroReplanificacion());

        ReplanificacionResponse r2 = servicio.registrarPedido(EJECUCION,
                new Order("P05", new Location(22, 10), 2, t, t.plusSeconds(36000)), t);
        assertEquals(2, r2.numeroReplanificacion());
    }

    @Test
    void rechazaPedidosRepetidosEnLaSolicitudInicial() {
        List<Order> repetidos = List.of(pedidos().get(0), pedidos().get(0));

        assertThrows(ReglaNegocioException.class, () -> servicio.iniciar(EJECUCION,
                new SolicitudPlanificacion(ModoOperacion.DIA_A_DIA, H, repetidos, List.of(), SEMILLA, PRESUPUESTO_AMPLIO)));
        assertTrue(servicio.planVigente(EJECUCION).isEmpty());
    }

    @Test
    void unaEjecucionDesconocidaOFinalizadaNoExiste() {
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.registrarPedido(9L, p04(H), H));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.activarBloqueo(9L,
                new RoadBlock(H, H.plusSeconds(60), List.of(new Location(1, 1), new Location(2, 1))), H));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.registrarAveria(9L,
                new BreakdownEvent("TA01", BreakdownType.MINOR, H, central().location()), H));
        assertTrue(servicio.planVigente(9L).isEmpty());

        servicio.iniciar(EJECUCION, solicitud());
        servicio.finalizar(EJECUCION);

        assertTrue(servicio.planVigente(EJECUCION).isEmpty());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.registrarPedido(EJECUCION, p04(H), H));
    }
}
