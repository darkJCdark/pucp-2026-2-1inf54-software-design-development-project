package com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.service.InicializadorFlota;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Presupuesto de tiempo del SA. La referencia es la búsqueda sin presupuesto, que conserva el
 * comportamiento original: un presupuesto que no se agota no puede alterar el resultado.
 */
class PresupuestoTiempoSimulatedAnnealingTest {

    private static final Instant HORA = Instant.parse("2026-09-09T12:00:00Z");
    private static final AnnealingConfig LIGERA = new AnnealingConfig(100.0, 1.0, 0.90, 5, 30, 30);
    /** Enfriamiento muy lento y Tmin muy baja: sin presupuesto correría durante mucho tiempo. */
    private static final AnnealingConfig LARGA = new AnnealingConfig(1000.0, 0.0001, 0.99, 50, 1_000_000, 1_000_000);

    private final OperationalSimulatedAnnealingPlanner planificador = new OperationalSimulatedAnnealingPlanner(
            new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork())));

    private static OperationalSnapshot snapshot() {
        Warehouse central = Warehouse.central("CENTRAL", new Location(27, 14));
        List<Warehouse> almacenes = List.of(central,
                Warehouse.intermediate("NOROESTE", new Location(12, 38), 1_000),
                Warehouse.intermediate("ESTE", new Location(57, 27), 1_000));
        Map<String, VehicleOperationalState> estados = new LinkedHashMap<>();
        for (Vehicle vehiculo : InicializadorFlota.crearFlotaInicial()) {
            estados.put(vehiculo.id(),
                    new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, central.location(), HORA));
        }
        return new OperationalSnapshot(HORA, FleetProfile.defaults(), InventorySnapshot.from(almacenes), estados,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()), ShiftSchedule.defaultSchedule(), List.of());
    }

    private static List<Order> pedidos() {
        Instant plazo = HORA.plusSeconds(10 * 3600);
        return List.of(
                new Order("P01", new Location(31, 14), 12, HORA, plazo),
                new Order("P02", new Location(27, 18), 8, HORA, plazo),
                new Order("P03", new Location(29, 16), 4, HORA, plazo),
                new Order("P04", new Location(35, 20), 6, HORA, plazo),
                new Order("P05", new Location(22, 10), 10, HORA, plazo),
                new Order("P06", new Location(30, 9), 2, HORA, plazo));
    }

    /** Huella sin identidades de objetos: métricas de la búsqueda, plan, paradas, tiempos y cargas. */
    private static String huella(OperationalSimulatedAnnealingPlanner.Ejecucion ejecucion) {
        ResultadoPlanificacion resultado = ejecucion.resultado();
        StringBuilder texto = new StringBuilder()
                .append(ejecucion.iterations()).append('/').append(ejecucion.evaluatedNeighbors()).append('/')
                .append(ejecucion.acceptedNeighbors()).append('/').append(ejecucion.finalTemperature()).append('/')
                .append(resultado.costoTotal()).append('/').append(resultado.esFactible());
        resultado.plan().routes().stream().sorted(Comparator.comparing(r -> r.vehicle().id())).forEach(ruta -> {
            ScheduledDeliveryRoute programada = resultado.evaluacion().schedulesByRouteId().get(ruta.id());
            texto.append(" | ").append(ruta.id()).append(':').append(programada.completedAt());
            for (ScheduledRouteStop parada : programada.scheduledStops()) {
                String nombre = parada.stop() instanceof DeliveryStop entrega
                        ? "E" + entrega.order().id() + "x" + entrega.deliveredPackages()
                        : "A" + ((WarehouseVisit) parada.stop()).warehouse().id();
                texto.append(' ').append(nombre).append('@').append(parada.arrivedAt())
                        .append(' ').append(parada.loadBefore()).append('>').append(parada.loadAfter());
            }
        });
        return texto.toString();
    }

    @Test
    void unPresupuestoQueNoSeAgotaNoCambiaElResultado() {
        for (long semilla : new long[]{7L, 42L, 2026L}) {
            OperationalSimulatedAnnealingPlanner.Ejecucion sinPresupuesto =
                    planificador.ejecutar(snapshot(), pedidos(), List.of(), LIGERA, new Random(semilla));
            OperationalSimulatedAnnealingPlanner.Ejecucion conPresupuesto = planificador.ejecutar(snapshot(), pedidos(),
                    List.of(), LIGERA, new Random(semilla), OperationalSimulatedAnnealingPlanner.PRESUPUESTO_MAXIMO_MS);

            assertEquals(huella(sinPresupuesto), huella(conPresupuesto));
        }
    }

    @Test
    void elMetodoPublicoConPresupuestoAmplioDevuelveElMismoPlanQueElOriginal() {
        ResultadoPlanificacion original =
                planificador.planificar(snapshot(), pedidos(), List.of(), LIGERA, new Random(7L));
        ResultadoPlanificacion conPresupuesto = planificador.planificar(snapshot(), pedidos(), List.of(), LIGERA,
                new Random(7L), OperationalSimulatedAnnealingPlanner.PRESUPUESTO_MAXIMO_MS);

        assertEquals(original.costoTotal(), conPresupuesto.costoTotal());
        assertEquals(original.esFactible(), conPresupuesto.esFactible());
        assertEquals(original.noAtendidos(), conPresupuesto.noAtendidos());
        assertEquals(original.plan().routes().size(), conPresupuesto.plan().routes().size());
    }

    @Test
    void unPresupuestoCortoDetieneLaBusquedaAntesDeSusLimitesDeIteraciones() {
        long inicio = System.nanoTime();
        OperationalSimulatedAnnealingPlanner.Ejecucion ejecucion =
                planificador.ejecutar(snapshot(), pedidos(), List.of(), LARGA, new Random(42L), 300L);
        long transcurridoMs = (System.nanoTime() - inicio) / 1_000_000;

        assertTrue(ejecucion.iterations() < LARGA.maximumIterations(), "iteraciones: " + ejecucion.iterations());
        assertTrue(ejecucion.finalTemperature() > LARGA.minimumTemperature(),
                "temperatura final: " + ejecucion.finalTemperature());
        assertTrue(transcurridoMs < 3_000, "duración: " + transcurridoMs + " ms");
        assertTrue(transcurridoMs >= 300, "la búsqueda debe aprovechar el tope y no cortarse antes: " + transcurridoMs + " ms");
    }

    @Test
    void conPresupuestoCortoElResultadoSigueSiendoFactibleYNoPeorQueElPlanInicial() {
        OperationalSimulatedAnnealingPlanner.Ejecucion ejecucion =
                planificador.ejecutar(snapshot(), pedidos(), List.of(), LARGA, new Random(42L), 200L);

        assertTrue(ejecucion.resultado().esFactible(), "violaciones: " + ejecucion.resultado().evaluacion().violations());
        assertTrue(ejecucion.resultado().costoTotal() <= ejecucion.initialCost());
        assertTrue(ejecucion.resultado().noAtendidos().isEmpty());
    }

    @Test
    void elMetodoPublicoRechazaPresupuestosFueraDeRango() {
        assertThrows(IllegalArgumentException.class,
                () -> planificador.planificar(snapshot(), pedidos(), List.of(), LIGERA, new Random(1L), 0L));
        assertThrows(IllegalArgumentException.class,
                () -> planificador.planificar(snapshot(), pedidos(), List.of(), LIGERA, new Random(1L), -1L));
        assertThrows(IllegalArgumentException.class,
                () -> planificador.planificar(snapshot(), pedidos(), List.of(), LIGERA, new Random(1L),
                        OperationalSimulatedAnnealingPlanner.PRESUPUESTO_MAXIMO_MS + 1));
    }
}
