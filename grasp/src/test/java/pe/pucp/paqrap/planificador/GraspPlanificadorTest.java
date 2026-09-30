package pe.pucp.paqrap.planificador;

import org.junit.jupiter.api.Test;
import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.route.*;
import pe.pucp.paqrap.modelo.ResultadoPlanificacion;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Public-API regression tests. No dependency on deleted private GRASP helpers or old fleet classes. */
class GraspPlanificadorTest {
    private final Instant start=Instant.parse("2026-09-09T12:00:00Z");
    private final Warehouse central=Warehouse.central("CENTRAL",new Location(27,14));
    private OperationalSnapshot snapshot(int cars) {
        Map<String,VehicleOperationalState> states=new LinkedHashMap<>();
        for(int i=1;i<=cars;i++) {
            var v=new Vehicle("TA"+String.format("%02d",i),VehicleType.CAR,true);
            states.put(v.id(),new VehicleOperationalState(v,VehicleStatus.AVAILABLE,central.location(),start));
        }
        return new OperationalSnapshot(start,FleetProfile.defaults(),InventorySnapshot.from(List.of(central)),states,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE,List.of()),ShiftSchedule.defaultSchedule(),List.of());
    }
    private Order order(String id,int x,int y,int q,int hours) {
        return new Order(id,new Location(x,y),q,start,start.plusSeconds(hours*3600L));
    }
    private ResultadoPlanificacion solve(OperationalSnapshot snapshot,List<Order> orders,long seed,List<RoadBlock> blocks) {
        var road=new RoadNetwork();var scheduler=new RouteScheduler(road);var evaluator=new OperationalPlanEvaluator(scheduler);
        return new GraspPlanificador(road,scheduler,evaluator,seed).planificar(snapshot,orders,blocks,.3,3);
    }
    private void verify(ResultadoPlanificacion result,OperationalSnapshot snapshot,List<Order> orders,List<RoadBlock> blocks) {
        var evaluator=new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()));
        var audit=evaluator.evaluate(result.plan(),snapshot,orders,blocks);
        assertTrue(audit.isRouteFeasible(),()->audit.violations().toString());
        for(var route:audit.schedulesByRouteId().values()) {
            assertTrue(MealBreakAudit.validate(route,snapshot.shiftSchedule()).isEmpty());
            for(var stop:route.scheduledStops())assertTrue(stop.loadBefore()<=24 && stop.loadAfter()>=0 && stop.loadAfter()<=24);
        }
    }
    @Test void respetaCapacidadYDescansoObligatorio() {
        var state=snapshot(2);var orders=List.of(order("P",30,15,12,8));var result=solve(state,orders,42,List.of());
        assertTrue(result.esCompleta());verify(result,state,orders,List.of());
    }
    @Test void dividePedidoMayorQueCualquierVehiculo() {
        var state=snapshot(2);var orders=List.of(order("P",30,15,30,36));var result=solve(state,orders,7,List.of());
        assertTrue(result.esCompleta());verify(result,state,orders,List.of());
        long delivered=result.plan().routes().stream().flatMap(r->r.stops().stream()).filter(DeliveryStop.class::isInstance)
                .map(DeliveryStop.class::cast).mapToInt(DeliveryStop::deliveredPackages).sum();
        assertEquals(30L,delivered);
    }
    @Test void pedidoImposibleNoDescartaLosAtendiblesNiSeDeclaraCompleto() {
        var impossible=new Order("IMP",new Location(65,48),30,start,start.plusSeconds(30*60));
        var state=snapshot(2);var orders=List.of(impossible,order("OK",28,15,3,8));var result=solve(state,orders,3,List.of());
        assertTrue(result.esFactible());assertFalse(result.esCompleta());assertEquals(List.of(impossible),result.noAtendidos());
        assertTrue(result.plan().routes().stream().flatMap(r->r.stops().stream()).anyMatch(s->s instanceof DeliveryStop d&&d.order().id().equals("OK")));
        verify(result,state,orders,List.of());
    }
    @Test void consolidaPedidosCompatiblesEnUnViaje() {
        var state=snapshot(1);var orders=List.of(order("A",30,14,3,8),order("B",31,15,4,8),order("C",32,16,5,8));
        var result=solve(state,orders,5,List.of());assertTrue(result.esCompleta());verify(result,state,orders,List.of());
        assertEquals(1,result.plan().routes().size());
        assertEquals(12,result.plan().routes().iterator().next().initialLoad());
    }
    @Test void sinFlotaConservaLaDemandaYNoDeclaraExito() {
        var state=snapshot(0);var orders=List.of(order("P",30,15,2,8));var result=solve(state,orders,42,List.of());
        assertFalse(result.esCompleta());assertEquals(orders,result.noAtendidos());assertTrue(result.plan().routes().isEmpty());
    }
    @Test void insercionCompartidaRecuperaPedidosPendientes() {
        var state=snapshot(1);var vehicle=state.vehiclesById().get("TA01").vehicle();
        var first=order("A",28,15,5,8);var missing=order("B",29,16,6,8);var required=List.of(first,missing);
        var route=DeliveryRoute.startScenarioAtCentral("R",vehicle,central,5,start).withAppendedStop(new DeliveryStop(first,5)).returningTo(central);
        var plan=OperationalPlan.empty().withRoute(route);var evaluator=new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()));
        var candidates=new FeasibleInsertionService(evaluator).candidates(plan,evaluator.evaluate(plan,state,required,List.of()),state,required,missing,6,List.of());
        assertFalse(candidates.isEmpty());assertTrue(candidates.stream().anyMatch(c->c.evaluation().isFeasible()));
    }
    @Test void repitePlanConSemillaYPasosFijos() {
        var state=snapshot(2);var orders=List.of(order("A",28,15,5,8),order("B",29,16,6,8));
        var a=solve(state,orders,42,List.of());var b=solve(state,orders,42,List.of());
        assertEquals(a.costoTotal(),b.costoTotal());assertEquals(a.noAtendidos(),b.noAtendidos());
        verify(a,state,orders,List.of());verify(b,state,orders,List.of());
    }
}
