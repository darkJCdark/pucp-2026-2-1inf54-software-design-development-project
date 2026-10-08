package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolationType;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.dto.ModoOperacion;
import com.pucp.paqrap.modulos.planificacion.dto.NoAtendidoResponse;
import com.pucp.paqrap.modulos.planificacion.dto.ParadaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.RutaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.ViolacionResponse;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadPath;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los resultados se arman con los constructores originales del dominio y se comparan campo por campo
 * contra los mismos objetos originales: el mapper no puede inventar ni perder datos.
 */
class PlanResponseMapperTest {

    private static final Instant HORA = Instant.parse("2026-09-09T12:00:00Z");
    private static final Warehouse CENTRAL = Warehouse.central("CENTRAL", new Location(10, 10));
    private static final Vehicle TA01 = new Vehicle("TA01", VehicleType.CAR, true);
    private static final Vehicle TB01 = new Vehicle("TB01", VehicleType.BICYCLE, true);

    private final PlanResponseMapper mapper = new PlanResponseMapper();

    private static Order pedido(String id, int x, int y, int paquetes) {
        return new Order(id, new Location(x, y), paquetes, HORA, HORA.plusSeconds(36 * 3600));
    }

    private static ScheduledRouteStop programada(com.pucp.paqrap.modulos.planificacion.entity.RouteStop parada,
                                                 Instant llegada, Instant fin, int antes, int despues) {
        RoadPath camino = new RoadPath(parada.location(), parada.location(), llegada, List.of());
        return new ScheduledRouteStop(parada, camino, llegada, fin, antes, despues);
    }

    /** Ruta TA01: entrega P01 (12 paquetes) y vuelve al central sin recargar. */
    private record Escenario(DeliveryRoute ruta, ScheduledDeliveryRoute programada, DeliveryStop entrega,
                             WarehouseVisit regreso, ScheduledRouteStop paradaEntrega, ScheduledRouteStop paradaRegreso) {
    }

    private static Escenario escenarioTa01() {
        Order p01 = pedido("P01", 14, 10, 12);
        DeliveryStop entrega = new DeliveryStop(p01, 12);
        WarehouseVisit regreso = new WarehouseVisit(CENTRAL, 0);
        DeliveryRoute ruta = DeliveryRoute.startScenarioAtCentral("R-TA01", TA01, CENTRAL, 12, HORA)
                .withAppendedStop(entrega).withAppendedStop(regreso);
        ScheduledRouteStop s1 = programada(entrega, HORA.plusSeconds(360), HORA.plusSeconds(3960), 12, 0);
        ScheduledRouteStop s2 = programada(regreso, HORA.plusSeconds(4320), HORA.plusSeconds(4320), 0, 0);
        ScheduledDeliveryRoute sched = new ScheduledDeliveryRoute(ruta, List.of(s1, s2), HORA.plusSeconds(4320), 8.0, 64.0);
        return new Escenario(ruta, sched, entrega, regreso, s1, s2);
    }

    private static PlanEvaluation evaluacion(Map<String, ScheduledDeliveryRoute> programadas,
                                             List<PlanViolation> violaciones, double costo) {
        return new PlanEvaluation(programadas, violaciones, InventorySnapshot.from(List.of(CENTRAL)), costo);
    }

    @Test
    void copiaLosCamposGlobalesDelResultadoOriginal() {
        Escenario e = escenarioTa01();
        Order sinAtender = pedido("P99", 60, 40, 5);
        PlanEvaluation ev = evaluacion(Map.of(e.ruta().id(), e.programada()), List.of(), 64.0);
        ResultadoPlanificacion resultado =
                new ResultadoPlanificacion(OperationalPlan.empty().withRoute(e.ruta()), ev, List.of(sinAtender));

        PlanResponse respuesta = mapper.aResponse(ModoOperacion.COLAPSO, HORA, resultado, 123L, 45L);

        assertEquals(ModoOperacion.COLAPSO, respuesta.modo());
        assertEquals(HORA, respuesta.planificadoEn());
        assertEquals(resultado.esFactible(), respuesta.factible());
        assertEquals(resultado.esColapso(), respuesta.colapso());
        assertEquals(resultado.costoTotal(), respuesta.costoTotal());
        assertEquals(123L, respuesta.semilla());
        assertEquals(45L, respuesta.duracionMs());
        assertTrue(respuesta.colapso());
        assertTrue(respuesta.factible());
    }

    @Test
    void mapeaCadaCampoDeLaRuta() {
        Escenario e = escenarioTa01();
        ResultadoPlanificacion resultado = new ResultadoPlanificacion(OperationalPlan.empty().withRoute(e.ruta()),
                evaluacion(Map.of(e.ruta().id(), e.programada()), List.of(), 64.0), List.of());

        RutaResponse ruta = mapper.aResponse(ModoOperacion.DIA_A_DIA, HORA, resultado, 1L, 0L).rutas().getFirst();

        assertEquals(e.ruta().id(), ruta.id());
        assertEquals(e.ruta().vehicle().id(), ruta.vehiculoId());
        assertEquals(e.ruta().vehicle().type().name(), ruta.tipoVehiculo());
        assertEquals(e.ruta().departureAt(), ruta.salida());
        assertEquals(e.programada().completedAt(), ruta.llegadaFinal());
        assertEquals(e.programada().totalDistanceKm(), ruta.distanciaKm());
        assertEquals(e.programada().totalCost(), ruta.costo());
    }

    @Test
    void mapeaLaParadaDeEntregaConSusTiemposYCargas() {
        Escenario e = escenarioTa01();
        ResultadoPlanificacion resultado = new ResultadoPlanificacion(OperationalPlan.empty().withRoute(e.ruta()),
                evaluacion(Map.of(e.ruta().id(), e.programada()), List.of(), 64.0), List.of());

        ParadaResponse parada = mapper.aResponse(ModoOperacion.DIA_A_DIA, HORA, resultado, 1L, 0L)
                .rutas().getFirst().paradas().get(0);

        assertEquals(1, parada.orden());
        assertEquals(ParadaResponse.ENTREGA, parada.tipo());
        assertEquals(e.entrega().order().id(), parada.pedidoId());
        assertNull(parada.almacenId());
        assertEquals(e.entrega().location().x(), parada.x());
        assertEquals(e.entrega().location().y(), parada.y());
        assertEquals(e.entrega().deliveredPackages(), parada.paquetes());
        assertEquals(e.paradaEntrega().arrivedAt(), parada.llegada());
        assertEquals(e.paradaEntrega().completedAt(), parada.fin());
        assertEquals(Integer.valueOf(e.paradaEntrega().loadBefore()), parada.cargaAntes());
        assertEquals(Integer.valueOf(e.paradaEntrega().loadAfter()), parada.cargaDespues());
    }

    @Test
    void mapeaLaParadaDeAlmacen() {
        Escenario e = escenarioTa01();
        ResultadoPlanificacion resultado = new ResultadoPlanificacion(OperationalPlan.empty().withRoute(e.ruta()),
                evaluacion(Map.of(e.ruta().id(), e.programada()), List.of(), 64.0), List.of());

        ParadaResponse parada = mapper.aResponse(ModoOperacion.DIA_A_DIA, HORA, resultado, 1L, 0L)
                .rutas().getFirst().paradas().get(1);

        assertEquals(2, parada.orden());
        assertEquals(ParadaResponse.ALMACEN, parada.tipo());
        assertNull(parada.pedidoId());
        assertEquals(e.regreso().warehouse().id(), parada.almacenId());
        assertEquals(e.regreso().location().x(), parada.x());
        assertEquals(e.regreso().location().y(), parada.y());
        assertEquals(e.regreso().pickupPackages(), parada.paquetes());
        assertEquals(e.paradaRegreso().arrivedAt(), parada.llegada());
        assertEquals(e.paradaRegreso().completedAt(), parada.fin());
    }

    @Test
    void ordenaLasRutasPorVehiculoYSumaLasDistanciasProgramadas() {
        Escenario ta = escenarioTa01();
        Order p02 = pedido("P02", 10, 12, 4);
        DeliveryStop entregaTb = new DeliveryStop(p02, 4);
        DeliveryRoute rutaTb = DeliveryRoute.startScenarioAtCentral("R-TB01", TB01, CENTRAL, 4, HORA)
                .withAppendedStop(entregaTb);
        ScheduledRouteStop sTb = programada(entregaTb, HORA.plusSeconds(1000), HORA.plusSeconds(4600), 4, 0);
        ScheduledDeliveryRoute schedTb =
                new ScheduledDeliveryRoute(rutaTb, List.of(sTb), HORA.plusSeconds(4600), 5.0, 15.0);

        OperationalPlan plan = OperationalPlan.empty().withRoute(rutaTb).withRoute(ta.ruta());
        Map<String, ScheduledDeliveryRoute> programadas = new LinkedHashMap<>();
        programadas.put(rutaTb.id(), schedTb);
        programadas.put(ta.ruta().id(), ta.programada());
        ResultadoPlanificacion resultado =
                new ResultadoPlanificacion(plan, evaluacion(programadas, List.of(), 79.0), List.of());

        PlanResponse respuesta = mapper.aResponse(ModoOperacion.DIA_A_DIA, HORA, resultado, 1L, 0L);

        assertEquals(List.of("TA01", "TB01"), respuesta.rutas().stream().map(RutaResponse::vehiculoId).toList());
        assertEquals(ta.programada().totalDistanceKm() + schedTb.totalDistanceKm(), respuesta.distanciaTotalKm());
    }

    @Test
    void mapeaLasViolacionesDelEvaluador() {
        Escenario e = escenarioTa01();
        List<PlanViolation> violaciones = List.of(
                new PlanViolation(PlanViolationType.SLA_MISSED, "R-TA01", "plazo vencido"),
                new PlanViolation(PlanViolationType.VEHICLE_CAPACITY, "R-TA01", "excede capacidad"));
        ResultadoPlanificacion resultado = new ResultadoPlanificacion(OperationalPlan.empty().withRoute(e.ruta()),
                evaluacion(Map.of(e.ruta().id(), e.programada()), violaciones, 64.0), List.of());

        PlanResponse respuesta = mapper.aResponse(ModoOperacion.DIA_A_DIA, HORA, resultado, 1L, 0L);

        assertFalse(respuesta.factible());
        assertEquals(List.of(
                new ViolacionResponse("SLA_MISSED", "R-TA01", "plazo vencido"),
                new ViolacionResponse("VEHICLE_CAPACITY", "R-TA01", "excede capacidad")), respuesta.violaciones());
    }

    @Test
    void mapeaLosPedidosNoAtendidos() {
        Order a = pedido("P90", 60, 40, 7);
        Order b = pedido("P91", 65, 45, 3);
        ResultadoPlanificacion resultado = new ResultadoPlanificacion(OperationalPlan.empty(),
                evaluacion(Map.of(), List.of(), 0.0), List.of(a, b));

        PlanResponse respuesta = mapper.aResponse(ModoOperacion.DIA_A_DIA, HORA, resultado, 1L, 0L);

        assertTrue(respuesta.colapso());
        assertEquals(List.of(
                new NoAtendidoResponse(a.id(), a.packages(), a.deadline()),
                new NoAtendidoResponse(b.id(), b.packages(), b.deadline())), respuesta.noAtendidos());
    }

    @Test
    void unaRutaSinProgramacionConservaSusParadasSinTiemposNiCargas() {
        Escenario e = escenarioTa01();
        ResultadoPlanificacion resultado = new ResultadoPlanificacion(OperationalPlan.empty().withRoute(e.ruta()),
                evaluacion(Map.of(), List.of(new PlanViolation(PlanViolationType.NO_ROAD_PATH, "R-TA01", "sin camino")), 0.0),
                List.of());

        PlanResponse respuesta = mapper.aResponse(ModoOperacion.DIA_A_DIA, HORA, resultado, 1L, 0L);
        RutaResponse ruta = respuesta.rutas().getFirst();

        assertNull(ruta.llegadaFinal());
        assertEquals(0.0, ruta.distanciaKm());
        assertEquals(0.0, ruta.costo());
        assertEquals(0.0, respuesta.distanciaTotalKm());
        assertEquals(2, ruta.paradas().size());
        assertEquals(e.entrega().order().id(), ruta.paradas().get(0).pedidoId());
        assertEquals(ParadaResponse.ALMACEN, ruta.paradas().get(1).tipo());
        for (ParadaResponse parada : ruta.paradas()) {
            assertNull(parada.llegada());
            assertNull(parada.fin());
            assertNull(parada.cargaAntes());
            assertNull(parada.cargaDespues());
        }
    }

    @Test
    void unPlanVacioProduceUnaRespuestaVacia() {
        ResultadoPlanificacion resultado =
                new ResultadoPlanificacion(OperationalPlan.empty(), evaluacion(Map.of(), List.of(), 0.0), List.of());

        PlanResponse respuesta = mapper.aResponse(ModoOperacion.SIMULACION_5D, HORA, resultado, 5L, 1L);

        assertTrue(respuesta.rutas().isEmpty());
        assertTrue(respuesta.violaciones().isEmpty());
        assertTrue(respuesta.noAtendidos().isEmpty());
        assertEquals(0.0, respuesta.distanciaTotalKm());
        assertTrue(respuesta.factible());
        assertFalse(respuesta.colapso());
    }
}
