package com.pucp.paqrap.modulos.planificacion.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleParameters;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.dto.ModoOperacion;
import com.pucp.paqrap.modulos.planificacion.dto.ParadaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.RutaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.TramoRutaResponse;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.entity.RoadLeg;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

/** Regresiones sobre código productivo, ejecutables con JDK 21 sin Spring, MySQL ni Maven.
 * TramosRutaTest ejecuta estos mismos casos mediante JUnit cuando las dependencias están disponibles. */
public final class VerificacionTramos {
    private static final Instant INICIO = Instant.parse("2026-09-10T12:00:00Z");
    private static final Warehouse CENTRAL = Warehouse.central("CENTRAL", new Location(10, 10));
    private static final Vehicle VEHICULO = new Vehicle("TM06", VehicleType.MOTORCYCLE, true);
    private static final PlanResponseMapper MAPPER = new PlanResponseMapper();

    private record Caso(DeliveryRoute ruta, ScheduledDeliveryRoute programada, PlanResponse plan,
                        List<RoadBlock> bloqueos) {
        RutaResponse respuesta() { return plan.rutas().getFirst(); }
    }

    private static OperationalSnapshot snapshot() {
        Map<VehicleType, VehicleParameters> parametros = new EnumMap<>(VehicleType.class);
        for (VehicleType tipo : VehicleType.values()) parametros.put(tipo, new VehicleParameters(24, 12, 1));
        return new OperationalSnapshot(INICIO, new FleetProfile(parametros), InventorySnapshot.from(List.of(CENTRAL)),
                Map.of(VEHICULO.id(), new VehicleOperationalState(VEHICULO, VehicleStatus.AVAILABLE, CENTRAL.location(), INICIO)),
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()), ShiftSchedule.defaultSchedule(), List.of());
    }

    private static Caso caso(Location destino, List<RoadBlock> bloqueos, boolean regresar) {
        Order pedido = new Order("P1", destino, 1, INICIO, INICIO.plusSeconds(36 * 3600));
        DeliveryRoute ruta = DeliveryRoute.startScenarioAtCentral("SA-TM06", VEHICULO, CENTRAL, 1, INICIO)
                .withAppendedStop(new DeliveryStop(pedido, 1));
        if (regresar) ruta = ruta.withAppendedStop(new WarehouseVisit(CENTRAL, 0));
        ScheduledDeliveryRoute programada = new RouteScheduler(new RoadNetwork()).schedule(ruta, snapshot(), bloqueos);
        ResultadoPlanificacion resultado = new ResultadoPlanificacion(OperationalPlan.empty().withRoute(ruta),
                new PlanEvaluation(Map.of(ruta.id(), programada), List.of(), snapshot().inventory(), programada.totalCost()), List.of());
        return new Caso(ruta, programada, MAPPER.aResponse(ModoOperacion.DIA_A_DIA, INICIO, resultado, 1, 0, 1500), bloqueos);
    }
    private static Caso desvio(boolean regresar) {
        RoadBlock bloqueo = new RoadBlock(INICIO.plusSeconds(8*60), INICIO.plusSeconds(16*60),
                List.of(new Location(10,12), new Location(10,13)));
        return caso(new Location(10,15), List.of(bloqueo), regresar);
    }
    /** Destino al otro lado del bloqueo: no se puede atravesar; esperar (12:29) es más rápido que rodearlo (12:30). */
    private static Caso espera() {
        RoadBlock bloqueo = new RoadBlock(INICIO.plusSeconds(8*60), INICIO.plusSeconds(14*60),
                List.of(new Location(10,12), new Location(10,13)));
        return caso(new Location(10,14), List.of(bloqueo), false);
    }
    private static void igual(Object esperado, Object obtenido, String mensaje) {
        if (!Objects.equals(esperado, obtenido)) throw new AssertionError(mensaje + ": esperado=" + esperado + ", obtenido=" + obtenido);
    }
    private static void cumple(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }

    public static void desvioSeConserva() {
        Caso c = desvio(false);
        igual(5.0, caso(new Location(10,15), List.of(), false).respuesta().distanciaKm(), "distancia directa");
        igual(7.0, c.respuesta().distanciaKm(), "distancia con desvío");
        igual(7, c.respuesta().tramos().size(), "las siete calles llegan al DTO");
        for (TramoRutaResponse tramo : c.respuesta().tramos()) {
            for (RoadBlock bloqueo : c.bloqueos()) {
                boolean coincideNodo = bloqueo.blockedNodes().contains(new Location(tramo.desdeX(), tramo.desdeY()))
                        || bloqueo.blockedNodes().contains(new Location(tramo.hastaX(), tramo.hastaY()));
                cumple(!coincideNodo || !bloqueo.overlaps(tramo.salida(), tramo.llegada()), "no atraviesa un nodo bloqueado durante tránsito");
            }
        }
    }
    public static void mapperCopiaTodosLosCampos() {
        Caso c = desvio(true);
        int indice = 0;
        int orden = 0;
        for (ScheduledRouteStop parada : c.programada().scheduledStops()) {
            orden++;
            for (RoadLeg leg : parada.approach().legs()) {
                TramoRutaResponse dto = c.respuesta().tramos().get(indice++);
                igual(new TramoRutaResponse(orden, leg.from().x(), leg.from().y(), leg.to().x(), leg.to().y(),
                        leg.departsAt(), leg.arrivesAt()), dto, "copia exacta de RoadLeg");
            }
        }
        igual(indice, c.respuesta().tramos().size(), "incluye el regreso al almacén");
        cumple(c.respuesta().tramos().stream().anyMatch(tramo -> tramo.ordenParada() == 2), "vincula tramos con la segunda parada");
    }
    public static void identidadesNoPierdenGeometria() {
        Caso c = desvio(false);
        RutaResponse guardada = MAPPER.conIdentidadesPersistidas(c.plan(), Map.of(c.ruta().id(), 73L), 41L).rutas().getFirst();
        igual(c.respuesta().tramos(), guardada.tramos(), "enriquecer IDs no elimina tramos");
        igual(73L, guardada.routePersistedId(), "ID persistido");
        try { guardada.tramos().clear(); throw new AssertionError("La lista debe ser inmutable"); }
        catch (UnsupportedOperationException esperada) { /* correcto */ }
    }
    public static void posicionSigueElDesvio() {
        RutaResponse ruta = desvio(false).respuesta();
        for (TramoRutaResponse tramo : ruta.tramos()) {
            Instant mitad = tramo.salida().plus(Duration.between(tramo.salida(), tramo.llegada()).dividedBy(2));
            Location esperada = new Location((int)Math.round((tramo.desdeX()+tramo.hastaX())/2.0),
                    (int)Math.round((tramo.desdeY()+tramo.hastaY())/2.0));
            igual(esperada, PosicionRuta.estimar(ruta, 10, 10, mitad), "posición a mitad de cada calle");
        }
    }
    public static void esperaEntreCalles() {
        RutaResponse ruta = espera().respuesta();
        igual(4, ruta.tramos().size(), "esperar es más rápido que desviarse en este caso");
        TramoRutaResponse segundo = ruta.tramos().get(1);
        cumple(segundo.salida().isAfter(ruta.tramos().getFirst().llegada()), "existe espera entre calles");
        igual(new Location(10,11), PosicionRuta.estimar(ruta, 10,10, INICIO.plusSeconds(10*60)), "quieto durante la espera");
        igual(INICIO.plusSeconds(14*60), segundo.salida(), "sale al terminar el bloqueo");
    }
    public static void esperaAntesDePrimerTramo() {
        RoadBlock bloqueo = new RoadBlock(INICIO, INICIO.plusSeconds(3*60),
                List.of(new Location(10,11), new Location(10,12)));
        // Destino más allá del bloqueo: no se puede pasar por (10,11) mientras rige, así que espera 3 min al salir.
        RutaResponse ruta = caso(new Location(10,13), List.of(bloqueo), false).respuesta();
        igual(INICIO.plusSeconds(3*60), ruta.tramos().getFirst().salida(), "espera inicial preservada");
        igual(new Location(10,10), PosicionRuta.estimar(ruta, 10,10, INICIO.plusSeconds(2*60)), "quieto antes del primer tramo");
    }
    public static void refrigerioYServicio() {
        RutaResponse original = desvio(false).respuesta();
        ParadaResponse p = original.paradas().getFirst();
        Instant arribo = p.llegada();
        ParadaResponse descanso = new ParadaResponse(p.orden(), p.tipo(), p.pedidoId(), p.almacenId(), p.x(), p.y(),
                p.paquetes(), arribo.plusSeconds(3600), arribo.plusSeconds(7200), p.cargaAntes(), p.cargaDespues(), arribo);
        RutaResponse ruta = new RutaResponse(original.id(), original.vehiculoId(), original.tipoVehiculo(), original.salida(),
                descanso.fin(), original.distanciaKm(), original.costo(), List.of(descanso), "CENTRAL", null, null, original.tramos());
        igual(new Location(p.x(),p.y()), PosicionRuta.estimar(ruta,10,10,arribo.plusSeconds(1800)), "quieto en refrigerio");
        igual(new Location(p.x(),p.y()), PosicionRuta.estimar(ruta,10,10,arribo.plusSeconds(5400)), "quieto en servicio");
    }
    public static void sinGeometriaNoInventa() {
        RutaResponse r = desvio(false).respuesta();
        RutaResponse legado = new RutaResponse(r.id(),r.vehiculoId(),r.tipoVehiculo(),r.salida(),r.llegadaFinal(),
                r.distanciaKm(),r.costo(),r.paradas());
        igual(List.of(), legado.tramos(), "constructor anterior compatible");
        igual(new Location(10,10), PosicionRuta.estimar(legado,10,10,INICIO.plusSeconds(600)), "sin ruta supuesta");
        RutaResponse conNull = new RutaResponse(r.id(),r.vehiculoId(),r.tipoVehiculo(),r.salida(),r.llegadaFinal(),
                r.distanciaKm(),r.costo(),r.paradas(),null,null,null,null);
        igual(List.of(), conNull.tramos(), "tramos null compatibles con JSON anterior");
    }
    public static void limitesTemporales() {
        RutaResponse ruta = desvio(false).respuesta();
        igual(new Location(9,10), PosicionRuta.estimar(ruta,9,10,INICIO.minusSeconds(1)), "no teletransporta antes de salida");
        igual(new Location(10,15), PosicionRuta.estimar(ruta,10,10,ruta.llegadaFinal().plusSeconds(1)), "queda al final sin retroceder");
    }
    public static void rutaSinProgramar() {
        Caso c = desvio(false);
        ResultadoPlanificacion resultado = new ResultadoPlanificacion(OperationalPlan.empty().withRoute(c.ruta()),
                new PlanEvaluation(Map.of(), List.of(), snapshot().inventory(), 0), List.of());
        RutaResponse ruta = MAPPER.aResponse(ModoOperacion.DIA_A_DIA,INICIO,resultado,1,0,1500).rutas().getFirst();
        igual(List.of(), ruta.tramos(), "no expone calles de una ruta no programada");
        igual(null, ruta.llegadaFinal(), "ruta sin programación");
    }

    /** Fixture generado por las clases reales de Java para comprobar el consumo desde TypeScript.
     * Este escritor no sustituye una prueba HTTP/Jackson del endpoint. */
    private static String fixture(Caso caso) {
        RutaResponse r = caso.respuesta();
        StringJoiner tramos = new StringJoiner(",");
        StringJoiner paradas = new StringJoiner(",");
        StringJoiner muestras = new StringJoiner(",");
        for (TramoRutaResponse t : r.tramos()) {
            tramos.add("{\"ordenParada\":"+t.ordenParada()+",\"desdeX\":"+t.desdeX()+",\"desdeY\":"+t.desdeY()
                    +",\"hastaX\":"+t.hastaX()+",\"hastaY\":"+t.hastaY()+",\"salida\":\""+t.salida()+"\",\"llegada\":\""+t.llegada()+"\"}");
        }
        for (ParadaResponse p : r.paradas()) paradas.add("{\"orden\":"+p.orden()+",\"tipo\":\""+p.tipo()+"\",\"pedidoId\":\"P1\",\"almacenId\":null,"
                +"\"x\":"+p.x()+",\"y\":"+p.y()+",\"paquetes\":1,\"llegada\":\""+p.llegada()+"\",\"fin\":\""+p.fin()
                +"\",\"cargaAntes\":1,\"cargaDespues\":0,\"inicioRefrigerio\":"+(p.inicioRefrigerio()==null?"null":"\""+p.inicioRefrigerio()+"\"")+"}");
        for (Instant t = INICIO; t.isBefore(r.llegadaFinal()); t = t.plusSeconds(30)) {
            Location pos = PosicionRuta.estimar(r,10,10,t);
            muestras.add("{\"instante\":\""+t+"\",\"x\":"+pos.x()+",\"y\":"+pos.y()+"}");
        }
        return "{\"ruta\":{\"id\":\""+r.id()+"\",\"vehiculoId\":\"TM06\",\"tipoVehiculo\":\"MOTORCYCLE\",\"salida\":\""+r.salida()
                +"\",\"llegadaFinal\":\""+r.llegadaFinal()+"\",\"distanciaKm\":"+r.distanciaKm()+",\"costo\":"+r.costo()
                +",\"almacenInicialId\":\"CENTRAL\",\"paradas\":["+paradas+"],\"tramos\":["+tramos+"]},\"muestras\":["+muestras+"]}";
    }
    public static void main(String[] args) throws Exception {
        Map<String, Runnable> casos = new LinkedHashMap<>();
        casos.put("desvio 5 km -> 7 km y ausencia de calles bloqueadas", VerificacionTramos::desvioSeConserva);
        casos.put("mapper copia todos los tramos, tiempos y regreso", VerificacionTramos::mapperCopiaTodosLosCampos);
        casos.put("IDs persistidos conservan geometría inmutable", VerificacionTramos::identidadesNoPierdenGeometria);
        casos.put("posición estimada sigue cada calle del desvío", VerificacionTramos::posicionSigueElDesvio);
        casos.put("espera real entre calles", VerificacionTramos::esperaEntreCalles);
        casos.put("espera antes del primer tramo", VerificacionTramos::esperaAntesDePrimerTramo);
        casos.put("refrigerio y servicio", VerificacionTramos::refrigerioYServicio);
        casos.put("compatibilidad sin geometría", VerificacionTramos::sinGeometriaNoInventa);
        casos.put("límites de salida y llegada", VerificacionTramos::limitesTemporales);
        casos.put("ruta no programada sin geometría", VerificacionTramos::rutaSinProgramar);
        for (var caso : casos.entrySet()) { caso.getValue().run(); System.out.println("OK: " + caso.getKey()); }
        System.out.println("PASS: " + casos.size() + " casos Java (clases reales, JDK sin dependencias externas)");
        if (args.length > 0) {
            Path archivo = Path.of(args[0]); Files.createDirectories(archivo.toAbsolutePath().getParent());
            Files.writeString(archivo, "["+fixture(desvio(false))+","+fixture(espera())+"]");
            System.out.println("Fixture Java -> frontend: " + archivo);
        }
    }
}
