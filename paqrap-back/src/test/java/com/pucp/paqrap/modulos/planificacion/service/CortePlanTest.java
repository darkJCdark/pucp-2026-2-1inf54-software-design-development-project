package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadPath;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rutas armadas con los constructores originales del dominio y horarios explícitos. */
class CortePlanTest {

    private static final Instant H = Instant.parse("2026-09-09T12:00:00Z");
    private static final Warehouse CENTRAL = Warehouse.central("CENTRAL", new Location(10, 10));
    private static final Warehouse NOROESTE = Warehouse.intermediate("NOROESTE", new Location(5, 20), 1_000);
    private static final Vehicle TA01 = new Vehicle("TA01", VehicleType.CAR, true);
    private static final Vehicle TA02 = new Vehicle("TA02", VehicleType.CAR, true);
    private static final Order P01 = new Order("P01", new Location(12, 10), 6, H, H.plusSeconds(36 * 3600));
    private static final Order P02 = new Order("P02", new Location(14, 10), 10, H, H.plusSeconds(36 * 3600));

    private static Instant min(int minutos) {
        return H.plusSeconds(minutos * 60L);
    }

    private static OperationalSnapshot snapshot() {
        Map<String, VehicleOperationalState> estados = new HashMap<>();
        for (Vehicle vehiculo : List.of(TA01, TA02)) {
            estados.put(vehiculo.id(), new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, CENTRAL.location(), H));
        }
        return new OperationalSnapshot(H, FleetProfile.defaults(), InventorySnapshot.from(List.of(CENTRAL, NOROESTE)),
                estados, new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()), ShiftSchedule.defaultSchedule(),
                List.of());
    }

    private record Parada(RouteStop stop, int llegada, int fin) {
    }

    /**
     * Ruta de TA01 que sale en {@code salida}: entrega 4 de P01 (llega 10, termina 70), recarga 5 en NOROESTE
     * (100), entrega 5 de P02 (llega 130, termina 190), entrega 2 más de P01 (220-280) y vuelve al central (300).
     */
    private static RutaVigente rutaTa01(int salida, Vehicle vehiculo) {
        List<Parada> paradas = List.of(
                new Parada(new DeliveryStop(P01, 4), salida + 10, salida + 70),
                new Parada(new WarehouseVisit(NOROESTE, 5), salida + 100, salida + 100),
                new Parada(new DeliveryStop(P02, 5), salida + 130, salida + 190),
                new Parada(new DeliveryStop(P01, 2), salida + 220, salida + 280),
                new Parada(new WarehouseVisit(CENTRAL, 0), salida + 300, salida + 300));
        DeliveryRoute ruta = DeliveryRoute.startScenarioAtCentral("SA-" + vehiculo.id(), vehiculo, CENTRAL, 6, min(salida));
        List<ScheduledRouteStop> programadas = new ArrayList<>();
        int carga = 6;
        for (Parada parada : paradas) {
            ruta = ruta.withAppendedStop(parada.stop());
            int despues = parada.stop() instanceof DeliveryStop entrega ? carga - entrega.deliveredPackages()
                    : carga + ((WarehouseVisit) parada.stop()).pickupPackages();
            programadas.add(new ScheduledRouteStop(parada.stop(),
                    new RoadPath(parada.stop().location(), parada.stop().location(), min(parada.llegada()), List.of()),
                    min(parada.llegada()), min(parada.fin()), carga, despues));
            carga = despues;
        }
        ScheduledDeliveryRoute programada = new ScheduledDeliveryRoute(ruta, programadas, min(salida + 300), 20.0, 160.0);
        return new RutaVigente(ruta, snapshot(), programada, List.of());
    }

    @Test
    void unaRutaQueAunNoPartioOQuePartePrecisamenteAhoraSeLibera() {
        CortePlan.Resultado enLaSalida = CortePlan.cortar(List.of(rutaTa01(0, TA01)), min(0), Map.of());
        CortePlan.Resultado antes = CortePlan.cortar(List.of(rutaTa01(30, TA01)), min(10), Map.of());

        assertEquals(1, enLaSalida.liberadas().size());
        assertEquals(1, antes.liberadas().size());
        assertTrue(enLaSalida.conservadas().isEmpty() && enLaSalida.entregasSalientes().isEmpty());
    }

    @Test
    void unaRutaSinProgramacionSeLibera() {
        RutaVigente conProgramacion = rutaTa01(0, TA01);
        RutaVigente sinProgramacion = new RutaVigente(conProgramacion.ruta(), conProgramacion.snapshotOrigen(), null, List.of());

        CortePlan.Resultado corte = CortePlan.cortar(List.of(sinProgramacion), min(100), Map.of());

        assertEquals(List.of(sinProgramacion), corte.liberadas());
    }

    @Test
    void unaRutaEnMarchaSeConservaSinAportarEntregasSalientes() {
        RutaVigente ruta = rutaTa01(0, TA01);

        CortePlan.Resultado corte = CortePlan.cortar(List.of(ruta), min(150), Map.of());

        assertEquals(List.of(ruta), corte.conservadas());
        assertTrue(corte.entregasSalientes().isEmpty());
        assertTrue(corte.retirosSalientes().isEmpty());
    }

    @Test
    void unaRutaQueTerminaJustoAhoraSeDaPorTerminadaConTodasSusEntregasYRetiros() {
        CortePlan.Resultado corte = CortePlan.cortar(List.of(rutaTa01(0, TA01)), min(300), Map.of());

        assertEquals(1, corte.terminadas().size());
        assertEquals(Map.of("P01", 6, "P02", 5), corte.entregasSalientes());
        assertEquals(Map.of("NOROESTE", 5), corte.retirosSalientes());
    }

    /** Igual que el ejecutor del plan: una parada cuenta si el vehículo llegó antes de la avería, aunque no terminara. */
    @Test
    void unaRutaInterrumpidaSoloAportaLasParadasAlcanzadasAntesDeLaAveria() {
        CortePlan.Resultado antesDeP02 = CortePlan.cortar(List.of(rutaTa01(0, TA01)), min(150), Map.of("TA01", min(120)));
        CortePlan.Resultado alLlegarAP02 = CortePlan.cortar(List.of(rutaTa01(0, TA01)), min(150), Map.of("TA01", min(130)));
        CortePlan.Resultado enServicioDeP02 = CortePlan.cortar(List.of(rutaTa01(0, TA01)), min(150), Map.of("TA01", min(131)));

        assertEquals(1, antesDeP02.interrumpidas().size());
        assertEquals(Map.of("P01", 4), antesDeP02.entregasSalientes());
        assertEquals(Map.of("NOROESTE", 5), antesDeP02.retirosSalientes());
        assertEquals(Map.of("P01", 4), alLlegarAP02.entregasSalientes());
        assertEquals(Map.of("P01", 4, "P02", 5), enServicioDeP02.entregasSalientes());
    }

    /** El reloj avanza a saltos: la avería puede caer antes de {@code ahora} en una ruta que ya habría terminado. */
    @Test
    void laAveriaSeAplicaEnSuInstanteAunqueAhoraSeaPosterior() {
        CortePlan.Resultado corte = CortePlan.cortar(List.of(rutaTa01(0, TA01)), min(400), Map.of("TA01", min(250)));

        assertEquals(1, corte.interrumpidas().size());
        assertEquals(Map.of("P01", 6, "P02", 5), corte.entregasSalientes());
    }

    @Test
    void unaRutaQueSaliaDespuesDeLaAveriaSeLibera() {
        CortePlan.Resultado corte = CortePlan.cortar(List.of(rutaTa01(30, TA01)), min(60), Map.of("TA01", min(20)));

        assertEquals(1, corte.liberadas().size());
        assertTrue(corte.entregasSalientes().isEmpty());
    }

    @Test
    void unaAveriaPosteriorAlFinDeLaRutaNoLaInterrumpe() {
        CortePlan.Resultado corte = CortePlan.cortar(List.of(rutaTa01(0, TA01)), min(320), Map.of("TA01", min(310)));

        assertEquals(1, corte.terminadas().size());
        assertTrue(corte.interrumpidas().isEmpty());
    }

    @Test
    void laInterrupcionSoloAfectaAlVehiculoIndicado() {
        RutaVigente ta01 = rutaTa01(0, TA01);
        RutaVigente ta02 = rutaTa01(0, TA02);

        CortePlan.Resultado corte = CortePlan.cortar(List.of(ta01, ta02), min(150), Map.of("TA02", min(150)));

        assertEquals(List.of(ta01), corte.conservadas());
        assertEquals(List.of(ta02), corte.interrumpidas());
    }

    @Test
    void lasEntregasSumanVariasParadasDelMismoPedidoYElCentralNoCuentaComoRetiro() {
        RutaVigente ruta = rutaTa01(0, TA01);

        assertEquals(Map.of("P01", 6, "P02", 5), CortePlan.entregas(ruta, null));
        assertEquals(Map.of("P01", 4), CortePlan.entregas(ruta, min(11)));
        assertEquals(Map.of(), CortePlan.entregas(ruta, min(10)));
        assertEquals(Map.of("NOROESTE", 5), CortePlan.retirosIntermedios(ruta, null));
    }

    @Test
    void sumarAcumulaPorClave() {
        Map<String, Integer> destino = new HashMap<>(Map.of("P01", 2));

        CortePlan.sumar(destino, Map.of("P01", 3, "P02", 1));

        assertEquals(Map.of("P01", 5, "P02", 1), destino);
    }
}
