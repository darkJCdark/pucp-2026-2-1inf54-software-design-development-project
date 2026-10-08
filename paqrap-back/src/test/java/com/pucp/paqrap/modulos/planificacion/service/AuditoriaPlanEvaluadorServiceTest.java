package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolationType;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.AnnealingConfig;
import com.pucp.paqrap.modulos.planificacion.dto.AuditoriaPlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.ModoOperacion;
import com.pucp.paqrap.modulos.planificacion.dto.PedidoAtrasadoResponse;
import com.pucp.paqrap.modulos.planificacion.dto.ReplanificacionResponse;
import com.pucp.paqrap.modulos.planificacion.dto.SolicitudPlanificacion;
import com.pucp.paqrap.modulos.planificacion.dto.ViolacionDetalleResponse;
import com.pucp.paqrap.modulos.planificacion.dto.ViolacionResponse;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La referencia de las violaciones es el {@link OperationalPlanEvaluator} original invocado directamente.
 * Los planes de la API de dominio se arman a mano con una violación conocida cada uno.
 */
class AuditoriaPlanEvaluadorServiceTest {

    private static final Instant H = Instant.parse("2026-09-09T12:00:00Z");
    private static final Warehouse CENTRAL = Warehouse.central("CENTRAL", new Location(0, 0));
    private static final Vehicle TA01 = new Vehicle("TA01", VehicleType.CAR, true);
    private static final Vehicle TB01 = new Vehicle("TB01", VehicleType.BICYCLE, true);
    private static final AnnealingConfig CONFIG_PRUEBA = new AnnealingConfig(100.0, 1.0, 0.90, 5, 30, 30);

    private final FuenteDatosOperativos fuente = new FuenteDatosOperativosMock();
    private final ReplanificacionSaService replanificacion =
            new ReplanificacionSaService(fuente, new PlanificadorSaService(fuente, CONFIG_PRUEBA));
    private final AuditoriaPlanService auditoria = new AuditoriaPlanEvaluadorService(replanificacion);
    private final OperationalPlanEvaluator evaluadorOriginal =
            new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()));

    // ---------------------------------------------------------------- planes armados a mano

    private static OperationalSnapshot snapshot(List<BreakdownEvent> averias) {
        Map<String, VehicleOperationalState> estados = new LinkedHashMap<>();
        for (Vehicle v : List.of(TA01, TB01)) {
            estados.put(v.id(), new VehicleOperationalState(v, VehicleStatus.AVAILABLE, CENTRAL.location(), H));
        }
        return new OperationalSnapshot(H, FleetProfile.defaults(), InventorySnapshot.from(List.of(CENTRAL)), estados,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()), ShiftSchedule.defaultSchedule(), averias);
    }

    private static Order pedido(String id, int x, int y, int paquetes, Duration plazo) {
        return new Order(id, new Location(x, y), paquetes, H, H.plus(plazo));
    }

    private static DeliveryRoute ruta(String id, Vehicle vehiculo, int cargaInicial) {
        return DeliveryRoute.startScenarioAtCentral(id, vehiculo, CENTRAL, cargaInicial, H);
    }

    private static OperationalPlan plan(DeliveryRoute... rutas) {
        OperationalPlan plan = OperationalPlan.empty();
        for (DeliveryRoute r : rutas) {
            plan = plan.withRoute(r);
        }
        return plan;
    }

    /** Audita con la API de dominio y exige exactamente las violaciones del evaluador original. */
    private AuditoriaPlanResponse auditarYComparar(OperationalPlan plan, OperationalSnapshot snapshot, List<Order> pedidos,
                                                   Instant ahora) {
        return auditarYComparar(plan, snapshot, pedidos, List.of(), ahora);
    }

    private AuditoriaPlanResponse auditarYComparar(OperationalPlan plan, OperationalSnapshot snapshot, List<Order> pedidos,
                                                   List<RoadBlock> bloqueos, Instant ahora) {
        PlanEvaluation esperado = evaluadorOriginal.evaluate(plan, snapshot, pedidos, bloqueos);
        AuditoriaPlanResponse auditado = auditoria.auditar(plan, snapshot, pedidos, bloqueos, ahora);

        assertEquals(esperado.violations().size(), auditado.totalViolaciones());
        assertEquals(esperado.isFeasible(), auditado.factible());
        for (int i = 0; i < esperado.violations().size(); i++) {
            PlanViolation v = esperado.violations().get(i);
            ViolacionDetalleResponse a = auditado.violaciones().get(i);
            assertEquals(v.type().name(), a.tipo());
            assertEquals(v.routeId(), a.rutaId());
            assertEquals(v.detail(), a.detalle());
            assertEquals(DescripcionViolacion.de(v.type()), a.descripcion());
            String vehiculo = v.routeId() == null ? null
                    : plan.routes().stream().filter(r -> r.id().equals(v.routeId())).findFirst().orElseThrow().vehicle().id();
            assertEquals(vehiculo, a.vehiculoId());
        }
        Map<String, Integer> porTipo = new TreeMap<>();
        esperado.violations().forEach(v -> porTipo.merge(v.type().name(), 1, Integer::sum));
        assertEquals(porTipo, auditado.violacionesPorTipo());
        return auditado;
    }

    private static boolean contiene(AuditoriaPlanResponse auditado, PlanViolationType tipo) {
        return auditado.violaciones().stream().anyMatch(v -> v.tipo().equals(tipo.name()));
    }

    // ---------------------------------------------------------------- API de dominio: violaciones

    @Test
    void unPlanFactibleNoTieneViolacionesNiPedidosPendientes() {
        Order p01 = pedido("P01", 3, 0, 12, Duration.ofHours(36));
        OperationalPlan plan = plan(ruta("R1", TA01, 12).withAppendedStop(new DeliveryStop(p01, 12)).returningTo(CENTRAL));

        AuditoriaPlanResponse a = auditarYComparar(plan, snapshot(List.of()), List.of(p01), H.plus(Duration.ofHours(3)));

        assertTrue(a.factible());
        assertFalse(a.colapso());
        assertTrue(a.violaciones().isEmpty());
        assertTrue(a.pedidosVencidos().isEmpty() && a.pedidosAtrasados().isEmpty() && a.pedidosNoAtendidos().isEmpty());
        assertNull(a.ejecucionId());
    }

    @Test
    void detectaCapacidadExcedida() {
        Order p02 = pedido("P02", 2, 0, 2, Duration.ofHours(36));
        DeliveryRoute r = ruta("R1", TB01, 4).withAppendedStop(new DeliveryStop(p02, 2))
                .withAppendedStop(new WarehouseVisit(CENTRAL, 4)).returningTo(CENTRAL);

        AuditoriaPlanResponse a = auditarYComparar(plan(r), snapshot(List.of()), List.of(p02), H);

        assertTrue(contiene(a, PlanViolationType.VEHICLE_CAPACITY));
    }

    @Test
    void detectaEntregaFueraDePlazoYLaInformaComoAtrasada() {
        Order p03 = pedido("P03", 20, 0, 4, Duration.ofMinutes(10));
        OperationalPlan plan = plan(ruta("R1", TA01, 4).withAppendedStop(new DeliveryStop(p03, 4)).returningTo(CENTRAL));

        Instant ahora = H.plus(Duration.ofMinutes(20));
        AuditoriaPlanResponse a = auditarYComparar(plan, snapshot(List.of()), List.of(p03), ahora);

        assertTrue(contiene(a, PlanViolationType.SLA_MISSED));
        ScheduledRouteStop entrega = evaluadorOriginal.evaluate(plan, snapshot(List.of()), List.of(p03), List.of())
                .schedulesByRouteId().get("R1").scheduledStops().getFirst();
        assertTrue(entrega.arrivedAt().isAfter(ahora), "a los 20 minutos la entrega aún no ocurre");
        assertEquals(List.of(new PedidoAtrasadoResponse("P03", p03.deadline(), entrega.arrivedAt(),
                Duration.between(p03.deadline(), entrega.arrivedAt()).toMinutes(), 4)), a.pedidosAtrasados());
        // Plazo vencido a la hora auditada y aún sin entregar: los minutos se cuentan hasta la hora auditada.
        assertEquals(List.of(new PedidoAtrasadoResponse("P03", p03.deadline(), entrega.arrivedAt(), 10, 4)),
                a.pedidosVencidos());
    }

    @Test
    void detectaCargaNegativa() {
        Order p04 = pedido("P04", 3, 0, 4, Duration.ofHours(36));
        OperationalPlan plan = plan(ruta("R1", TA01, 2).withAppendedStop(new DeliveryStop(p04, 4)).returningTo(CENTRAL));

        assertTrue(contiene(auditarYComparar(plan, snapshot(List.of()), List.of(p04), H), PlanViolationType.NEGATIVE_LOAD));
    }

    @Test
    void detectaEntregaDeUnPedidoQueNoEstaEnElPlan() {
        Order ajeno = pedido("PX", 3, 0, 4, Duration.ofHours(36));
        OperationalPlan plan = plan(ruta("R1", TA01, 4).withAppendedStop(new DeliveryStop(ajeno, 4)).returningTo(CENTRAL));

        assertTrue(contiene(auditarYComparar(plan, snapshot(List.of()), List.of(), H), PlanViolationType.UNKNOWN_ORDER));
    }

    @Test
    void detectaRutaQueNoVuelveAUnAlmacen() {
        Order p05 = pedido("P05", 3, 0, 4, Duration.ofHours(36));
        OperationalPlan plan = plan(ruta("R1", TA01, 4).withAppendedStop(new DeliveryStop(p05, 4)));

        assertTrue(contiene(auditarYComparar(plan, snapshot(List.of()), List.of(p05), H),
                PlanViolationType.ROUTE_NOT_RETURNED_TO_WAREHOUSE));
    }

    @Test
    void detectaEntregaParcialSinCompletarYDejaElPedidoComoNoAtendido() {
        Order p06 = pedido("P06", 3, 0, 10, Duration.ofHours(36));
        OperationalPlan plan = plan(ruta("R1", TA01, 4).withAppendedStop(new DeliveryStop(p06, 4)).returningTo(CENTRAL));

        AuditoriaPlanResponse a = auditarYComparar(plan, snapshot(List.of()), List.of(p06), H);

        assertTrue(contiene(a, PlanViolationType.PARTIAL_DELIVERY_MISMATCH));
        assertEquals(List.of("P06"), a.pedidosNoAtendidos());
        assertTrue(a.colapso());
    }

    @Test
    void detectaTramoMayorA80Km() {
        Order lejano = pedido("P07", 70, 50, 4, Duration.ofHours(36));
        OperationalPlan plan = plan(ruta("R1", TA01, 4).withAppendedStop(new DeliveryStop(lejano, 4)).returningTo(CENTRAL));

        assertTrue(contiene(auditarYComparar(plan, snapshot(List.of()), List.of(lejano), H),
                PlanViolationType.LEG_DISTANCE_EXCEEDED));
    }

    @Test
    void detectaRutaQueCoincideConUnaAveria() {
        Order p08 = pedido("P08", 3, 0, 4, Duration.ofHours(36));
        OperationalPlan plan = plan(ruta("R1", TA01, 4).withAppendedStop(new DeliveryStop(p08, 4)).returningTo(CENTRAL));
        List<BreakdownEvent> averias =
                List.of(new BreakdownEvent("TA01", BreakdownType.MINOR, H.plusSeconds(120), new Location(1, 0)));

        assertTrue(contiene(auditarYComparar(plan, snapshot(averias), List.of(p08), H),
                PlanViolationType.MAINTENANCE_OR_BREAKDOWN));
    }

    @Test
    void consideraLosBloqueosIgualQueElEvaluadorOriginal() {
        Order p10 = pedido("P10", 5, 0, 4, Duration.ofMinutes(9));
        OperationalPlan plan = plan(ruta("R1", TA01, 4).withAppendedStop(new DeliveryStop(p10, 4)).returningTo(CENTRAL));
        RoadBlock bloqueo = new RoadBlock(H, H.plus(Duration.ofHours(8)), List.of(new Location(1, 0), new Location(4, 0)));

        AuditoriaPlanResponse sinBloqueo = auditarYComparar(plan, snapshot(List.of()), List.of(p10), H);
        AuditoriaPlanResponse conBloqueo = auditarYComparar(plan, snapshot(List.of()), List.of(p10), List.of(bloqueo), H);

        assertTrue(sinBloqueo.factible());
        assertFalse(conBloqueo.factible(), "el desvío por el bloqueo debe hacer llegar tarde la entrega");
    }

    // ---------------------------------------------------------------- API de dominio: estado de pedidos

    @Test
    void unPedidoSinRutaConPlazoCumplidoEsVencidoYColapso() {
        Order p01 = pedido("P01", 3, 0, 12, Duration.ofHours(36));
        Order p09 = pedido("P09", 5, 5, 4, Duration.ofHours(1));
        OperationalPlan plan = plan(ruta("R1", TA01, 12).withAppendedStop(new DeliveryStop(p01, 12)).returningTo(CENTRAL));

        AuditoriaPlanResponse a = auditarYComparar(plan, snapshot(List.of()), List.of(p01, p09), H.plus(Duration.ofHours(2)));

        assertEquals(List.of(new PedidoAtrasadoResponse("P09", p09.deadline(), null, 60, 4)), a.pedidosVencidos());
        assertEquals(List.of("P09"), a.pedidosNoAtendidos());
        assertTrue(a.colapso());
    }

    @Test
    void unPedidoAunEnCaminoAntesDeSuPlazoNoEsVencido() {
        Order p01 = pedido("P01", 3, 0, 12, Duration.ofHours(36));
        OperationalPlan plan = plan(ruta("R1", TA01, 12).withAppendedStop(new DeliveryStop(p01, 12)).returningTo(CENTRAL));

        AuditoriaPlanResponse a = auditarYComparar(plan, snapshot(List.of()), List.of(p01), H.plusSeconds(600));

        assertTrue(a.pedidosVencidos().isEmpty());
        assertFalse(a.colapso());
    }

    // ---------------------------------------------------------------- plan vigente de una ejecución

    private static SolicitudPlanificacion solicitud() {
        Instant plazo = H.plusSeconds(10 * 3600);
        List<Order> pedidos = List.of(
                new Order("P01", new Location(31, 14), 12, H, plazo),
                new Order("P02", new Location(27, 18), 8, H, plazo),
                new Order("P03", new Location(29, 16), 4, H, plazo));
        return new SolicitudPlanificacion(ModoOperacion.COLAPSO, H, pedidos, List.of(), 7L, 600_000L);
    }

    private static List<String> claves(List<ViolacionResponse> violaciones) {
        return violaciones.stream().map(v -> v.tipo() + "|" + v.rutaId() + "|" + v.detalle()).toList();
    }

    private static List<String> clavesAuditadas(AuditoriaPlanResponse a) {
        return a.violaciones().stream().map(v -> v.tipo() + "|" + v.rutaId() + "|" + v.detalle()).toList();
    }

    @Test
    void elPlanVigenteRecienIniciadoCoincideConElPlanDeLaReplanificacion() {
        ReplanificacionResponse inicio = replanificacion.iniciar(1L, solicitud());

        AuditoriaPlanResponse a = auditoria.auditarPlanVigente(1L, H);

        assertEquals(Long.valueOf(1L), a.ejecucionId());
        assertEquals(inicio.plan().factible(), a.factible());
        assertEquals(claves(inicio.plan().violaciones()), clavesAuditadas(a));
        assertEquals(inicio.plan().noAtendidos().stream().map(n -> n.pedidoId()).sorted().toList(), a.pedidosNoAtendidos());
        assertFalse(a.colapso());
    }

    @Test
    void unBloqueoQueRetrasaUnaRutaConservadaApareceComoViolacionYPedidoAtrasado() {
        replanificacion.iniciar(1L, solicitud());
        Instant t = H.plusSeconds(30 * 60);
        RutaVigente afectada = replanificacion.rutasVigentes(1L).stream()
                .filter(rv -> rv.programada().scheduledStops().stream()
                        .anyMatch(s -> s.stop() instanceof DeliveryStop && s.arrivedAt().isAfter(t)))
                .findFirst().orElseThrow(() -> new AssertionError("ninguna ruta tiene entregas después de t"));
        ScheduledRouteStop parada = afectada.programada().scheduledStops().stream()
                .filter(s -> s.stop() instanceof DeliveryStop && s.arrivedAt().isAfter(t)).findFirst().orElseThrow();
        Location destino = parada.stop().location();
        Location vecino = new Location(destino.x() + (destino.x() < Location.MAX_X ? 1 : -1), destino.y());
        ReplanificacionResponse r = replanificacion.activarBloqueo(1L,
                new RoadBlock(t, H.plusSeconds(20 * 3600), List.of(destino, vecino)), t);

        AuditoriaPlanResponse a = auditoria.auditarPlanVigente(1L, t);

        assertEquals(claves(r.plan().violaciones()), clavesAuditadas(a));
        assertTrue(a.violaciones().stream().anyMatch(v -> v.rutaId().equals(afectada.ruta().id())
                && v.vehiculoId().equals(afectada.vehiculoId()) && !v.descripcion().isBlank()));
        String pedidoId = ((DeliveryStop) parada.stop()).order().id();
        RutaVigente reprogramada = replanificacion.rutasVigentes(1L).stream()
                .filter(rv -> rv.ruta().id().equals(afectada.ruta().id())).findFirst().orElseThrow();
        Instant llegada = reprogramada.programada().scheduledStops().stream()
                .filter(s -> s.stop() instanceof DeliveryStop d && d.order().id().equals(pedidoId))
                .map(ScheduledRouteStop::arrivedAt).max(Instant::compareTo).orElseThrow();
        Instant plazo = ((DeliveryStop) parada.stop()).order().deadline();
        assertTrue(llegada.isAfter(plazo), "el bloqueo debe retrasar la entrega más allá del plazo");
        PedidoAtrasadoResponse atrasado = a.pedidosAtrasados().stream()
                .filter(p -> p.pedidoId().equals(pedidoId)).findFirst().orElseThrow();
        assertEquals(llegada, atrasado.llegadaPlanificada());
        assertEquals(Duration.between(plazo, llegada).toMinutes(), atrasado.minutosAtraso());
        assertFalse(a.factible());
    }

    @Test
    void unPedidoVencidoSinEntregarEsColapsoEnElPlanVigente() {
        replanificacion.iniciar(1L, solicitud());
        Order vencido = new Order("P99", new Location(30, 20), 4, H, H.plusSeconds(1800));
        Instant t = H.plusSeconds(3600);
        replanificacion.registrarPedido(1L, vencido, t);

        AuditoriaPlanResponse a = auditoria.auditarPlanVigente(1L, t);

        assertTrue(a.colapso());
        assertEquals(List.of(new PedidoAtrasadoResponse("P99", vencido.deadline(), null, 30, 4)), a.pedidosVencidos());
        assertTrue(a.pedidosNoAtendidos().contains("P99"));
    }

    @Test
    void lasEntregasDeRutasYaTerminadasCuentanComoHechas() {
        ReplanificacionResponse inicio = replanificacion.iniciar(1L, solicitud());
        Instant fin = inicio.plan().rutas().stream().map(r -> r.llegadaFinal()).max(Instant::compareTo).orElseThrow();
        Instant t = fin.plusSeconds(60);
        replanificacion.registrarPedido(1L, new Order("P04", new Location(35, 20), 6, t, t.plusSeconds(36000)), t);

        AuditoriaPlanResponse a = auditoria.auditarPlanVigente(1L, t.plusSeconds(11 * 3600));

        assertTrue(a.pedidosVencidos().stream().noneMatch(p -> List.of("P01", "P02", "P03").contains(p.pedidoId())));
        assertTrue(a.pedidosNoAtendidos().isEmpty());
    }

    @Test
    void validaEjecucionYHora() {
        assertThrows(RecursoNoEncontradoException.class, () -> auditoria.auditarPlanVigente(9L, H));
        replanificacion.iniciar(1L, solicitud());
        replanificacion.registrarPedido(1L, new Order("P04", new Location(35, 20), 6, H, H.plusSeconds(36000)),
                H.plusSeconds(600));

        assertThrows(ReglaNegocioException.class, () -> auditoria.auditarPlanVigente(1L, H));
        assertThrows(NullPointerException.class, () -> auditoria.auditarPlanVigente(1L, null));
    }
}
