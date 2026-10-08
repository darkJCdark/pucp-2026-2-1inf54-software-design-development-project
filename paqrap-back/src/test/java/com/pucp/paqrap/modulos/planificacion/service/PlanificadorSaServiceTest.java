package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.service.InicializadorFlota;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.AnnealingConfig;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.OperationalSimulatedAnnealingPlanner;
import com.pucp.paqrap.modulos.planificacion.dto.ModoOperacion;
import com.pucp.paqrap.modulos.planificacion.dto.ParadaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.RutaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.SolicitudPlanificacion;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La referencia de cada comparación es el planificador original
 * ({@link OperationalSimulatedAnnealingPlanner}) invocado directamente con un snapshot armado en la
 * propia prueba, con los constructores originales y la misma semilla.
 */
class PlanificadorSaServiceTest {

    private static final Instant HORA = Instant.parse("2026-09-09T12:00:00Z");

    /** Configuración ligera para que las pruebas sean rápidas; la de producción está en PlanificadorSaService. */
    private static final AnnealingConfig CONFIG_PRUEBA = new AnnealingConfig(100.0, 1.0, 0.90, 5, 30, 30);

    /** Tope holgado para que las comparaciones con el SA original no dependan de la velocidad de la máquina. */
    private static final Long PRESUPUESTO_AMPLIO = 600_000L;

    private final PlanificadorService servicio =
            new PlanificadorSaService(new FuenteDatosOperativosMock(), CONFIG_PRUEBA);

    // ---------------------------------------------------------------- referencia original

    private static List<Order> pedidos() {
        Instant plazo = HORA.plusSeconds(10 * 3600);
        return List.of(
                new Order("P01", new Location(31, 14), 12, HORA, plazo),
                new Order("P02", new Location(27, 18), 8, HORA, plazo),
                new Order("P03", new Location(29, 16), 4, HORA, plazo));
    }

    /** Bloqueo sobre el tramo recto entre el central (27,14) y P01 (31,14). */
    private static RoadBlock bloqueoCentralP01() {
        return new RoadBlock(HORA, HORA.plusSeconds(8 * 3600), List.of(new Location(28, 14), new Location(31, 14)));
    }

    private static OperationalSnapshot snapshotOriginal(List<BreakdownEvent> averias) {
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
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()), ShiftSchedule.defaultSchedule(), averias);
    }

    private static ResultadoPlanificacion directo(List<Order> pedidos, List<RoadBlock> bloqueos, long semilla,
                                                  List<BreakdownEvent> averias) {
        OperationalSimulatedAnnealingPlanner original = new OperationalSimulatedAnnealingPlanner(
                new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork())));
        return original.planificar(snapshotOriginal(averias), pedidos, bloqueos, CONFIG_PRUEBA, new Random(semilla));
    }

    private static void assertEquivalente(ResultadoPlanificacion esperado, PlanResponse real) {
        assertEquals(esperado.esFactible(), real.factible());
        assertEquals(esperado.esColapso(), real.colapso());
        assertEquals(esperado.costoTotal(), real.costoTotal());
        assertEquals(esperado.evaluacion().violations().size(), real.violaciones().size());
        assertEquals(esperado.noAtendidos().stream().map(Order::id).toList(),
                real.noAtendidos().stream().map(n -> n.pedidoId()).toList());

        List<DeliveryRoute> rutasEsperadas = esperado.plan().routes().stream()
                .sorted(Comparator.comparing(r -> r.vehicle().id())).toList();
        assertEquals(rutasEsperadas.size(), real.rutas().size());

        double distancia = 0;
        for (int i = 0; i < rutasEsperadas.size(); i++) {
            DeliveryRoute ruta = rutasEsperadas.get(i);
            ScheduledDeliveryRoute programada = esperado.evaluacion().schedulesByRouteId().get(ruta.id());
            RutaResponse r = real.rutas().get(i);
            assertEquals(ruta.id(), r.id());
            assertEquals(ruta.vehicle().id(), r.vehiculoId());
            assertEquals(ruta.vehicle().type().name(), r.tipoVehiculo());
            assertEquals(ruta.departureAt(), r.salida());
            assertEquals(programada.completedAt(), r.llegadaFinal());
            assertEquals(programada.totalDistanceKm(), r.distanciaKm());
            assertEquals(programada.totalCost(), r.costo());
            distancia += programada.totalDistanceKm();

            assertEquals(programada.scheduledStops().size(), r.paradas().size());
            for (int j = 0; j < programada.scheduledStops().size(); j++) {
                ScheduledRouteStop parada = programada.scheduledStops().get(j);
                ParadaResponse p = r.paradas().get(j);
                assertEquals(j + 1, p.orden());
                assertEquals(parada.stop().location().x(), p.x());
                assertEquals(parada.stop().location().y(), p.y());
                assertEquals(parada.arrivedAt(), p.llegada());
                assertEquals(parada.completedAt(), p.fin());
                assertEquals(Integer.valueOf(parada.loadBefore()), p.cargaAntes());
                assertEquals(Integer.valueOf(parada.loadAfter()), p.cargaDespues());
                if (parada.stop() instanceof DeliveryStop entrega) {
                    assertEquals(ParadaResponse.ENTREGA, p.tipo());
                    assertEquals(entrega.order().id(), p.pedidoId());
                    assertNull(p.almacenId());
                    assertEquals(entrega.deliveredPackages(), p.paquetes());
                } else if (parada.stop() instanceof WarehouseVisit visita) {
                    assertEquals(ParadaResponse.ALMACEN, p.tipo());
                    assertEquals(visita.warehouse().id(), p.almacenId());
                    assertNull(p.pedidoId());
                    assertEquals(visita.pickupPackages(), p.paquetes());
                }
            }
        }
        assertEquals(distancia, real.distanciaTotalKm());
    }

    // ---------------------------------------------------------------- comportamiento general

    @Test
    void planificaTodosLosPedidosConDatosMock() {
        PlanResponse plan = servicio.planificar(
                new SolicitudPlanificacion(ModoOperacion.DIA_A_DIA, HORA, pedidos(), List.of(), 7L, PRESUPUESTO_AMPLIO));

        assertEquals(ModoOperacion.DIA_A_DIA, plan.modo());
        assertEquals(HORA, plan.planificadoEn());
        assertEquals(7L, plan.semilla());
        assertTrue(plan.factible(), "violaciones: " + plan.violaciones());
        assertFalse(plan.colapso());
        assertTrue(plan.noAtendidos().isEmpty());
        assertTrue(plan.violaciones().isEmpty());
        assertFalse(plan.rutas().isEmpty());
        assertTrue(plan.costoTotal() > 0);
        assertTrue(plan.distanciaTotalKm() > 0);

        Map<String, Integer> entregado = new HashMap<>();
        for (RutaResponse ruta : plan.rutas()) {
            assertTrue(ruta.llegadaFinal() != null && !ruta.llegadaFinal().isBefore(ruta.salida()));
            for (ParadaResponse parada : ruta.paradas()) {
                if (ParadaResponse.ENTREGA.equals(parada.tipo())) {
                    entregado.merge(parada.pedidoId(), parada.paquetes(), Integer::sum);
                }
            }
        }
        assertEquals(Map.of("P01", 12, "P02", 8, "P03", 4), entregado);
    }

    @Test
    void mismaSemillaProduceElMismoPlan() {
        SolicitudPlanificacion solicitud =
                new SolicitudPlanificacion(ModoOperacion.SIMULACION_5D, HORA, pedidos(), List.of(), 42L, PRESUPUESTO_AMPLIO);

        PlanResponse primero = servicio.planificar(solicitud);
        PlanResponse segundo = servicio.planificar(solicitud);

        assertEquals(primero.costoTotal(), segundo.costoTotal());
        assertEquals(primero.rutas(), segundo.rutas());
    }

    @Test
    void sinSemillaGeneraUnaYLaDevuelve() {
        PlanResponse plan = servicio.planificar(ModoOperacion.COLAPSO, HORA, pedidos(), List.of());

        assertEquals(ModoOperacion.COLAPSO, plan.modo());
        assertTrue(plan.factible());

        PlanResponse repetido = servicio.planificar(
                new SolicitudPlanificacion(ModoOperacion.COLAPSO, HORA, pedidos(), List.of(), plan.semilla(), PRESUPUESTO_AMPLIO));
        assertEquals(plan.rutas(), repetido.rutas());
    }

    @Test
    void sinPedidosDevuelvePlanVacio() {
        PlanResponse plan = servicio.planificar(ModoOperacion.DIA_A_DIA, HORA, List.of(), List.of());

        assertTrue(plan.rutas().isEmpty());
        assertTrue(plan.noAtendidos().isEmpty());
        assertFalse(plan.colapso());
    }

    @Test
    void elModoYLaHoraSeDevuelvenSinInterpretarse() {
        Instant otraHora = HORA.plusSeconds(3600);
        for (ModoOperacion modo : ModoOperacion.values()) {
            PlanResponse plan = servicio.planificar(modo, otraHora, List.of(), List.of());

            assertEquals(modo, plan.modo());
            assertEquals(otraHora, plan.planificadoEn());
        }
    }

    // ---------------------------------------------------------------- equivalencia con el código original

    @Test
    void esEquivalenteAlSaOriginalConLaMismaSemilla() {
        ResultadoPlanificacion esperado = directo(pedidos(), List.of(), 7L, List.of());

        PlanResponse real = servicio.planificar(
                new SolicitudPlanificacion(ModoOperacion.DIA_A_DIA, HORA, pedidos(), List.of(), 7L, PRESUPUESTO_AMPLIO));

        assertEquivalente(esperado, real);
    }

    @Test
    void esEquivalenteAlSaOriginalConOtraSemillaYOtraCantidadDePedidos() {
        List<Order> muchos = new ArrayList<>(pedidos());
        Instant plazo = HORA.plusSeconds(10 * 3600);
        muchos.add(new Order("P04", new Location(35, 20), 6, HORA, plazo));
        muchos.add(new Order("P05", new Location(22, 10), 10, HORA, plazo));
        muchos.add(new Order("P06", new Location(30, 9), 2, HORA, plazo));

        ResultadoPlanificacion esperado = directo(muchos, List.of(), 2026L, List.of());
        PlanResponse real = servicio.planificar(
                new SolicitudPlanificacion(ModoOperacion.SIMULACION_5D, HORA, muchos, List.of(), 2026L, PRESUPUESTO_AMPLIO));

        assertEquivalente(esperado, real);
    }

    @Test
    void entregaAlAlgoritmoLosBloqueosYSigueSuResultado() {
        List<RoadBlock> bloqueos = List.of(bloqueoCentralP01());
        ResultadoPlanificacion sinBloqueo = directo(pedidos(), List.of(), 7L, List.of());
        ResultadoPlanificacion conBloqueo = directo(pedidos(), bloqueos, 7L, List.of());

        PlanResponse real = servicio.planificar(
                new SolicitudPlanificacion(ModoOperacion.DIA_A_DIA, HORA, pedidos(), bloqueos, 7L, PRESUPUESTO_AMPLIO));

        assertEquivalente(conBloqueo, real);
        assertTrue(conBloqueo.costoTotal() != sinBloqueo.costoTotal(),
                "el bloqueo elegido debe alterar el plan original para que la prueba demuestre que se pasa al algoritmo");
    }

    @Test
    void unVehiculoAveriadoNoRecibeRutaYElPlanSigueAlOriginal() {
        Location central = new Location(27, 14);
        BreakdownEvent averia = new BreakdownEvent("TA01", BreakdownType.MINOR, HORA, central);
        FuenteDatosOperativos conAveria = new FuenteDatosOperativosMock() {
            @Override
            public List<BreakdownEvent> averias() {
                return List.of(averia);
            }
        };
        PlanificadorService conAveriaServicio = new PlanificadorSaService(conAveria, CONFIG_PRUEBA);

        ResultadoPlanificacion esperado = directo(pedidos(), List.of(), 7L, List.of(averia));
        PlanResponse real = conAveriaServicio.planificar(
                new SolicitudPlanificacion(ModoOperacion.DIA_A_DIA, HORA, pedidos(), List.of(), 7L, PRESUPUESTO_AMPLIO));

        assertEquivalente(esperado, real);
        assertTrue(real.rutas().stream().noneMatch(r -> r.vehiculoId().equals("TA01")));
    }

    @Test
    void losVehiculosDelPlanSalenDeLaFlotaOriginal() {
        List<String> idsOriginales = InicializadorFlota.crearFlotaInicial().stream().map(Vehicle::id).toList();

        PlanResponse plan = servicio.planificar(ModoOperacion.DIA_A_DIA, HORA, pedidos(), List.of());

        for (RutaResponse ruta : plan.rutas()) {
            assertTrue(idsOriginales.contains(ruta.vehiculoId()), "vehículo desconocido: " + ruta.vehiculoId());
        }
    }

    @Test
    void unPedidoImposibleDeAtenderSeInformaComoColapsoIgualQueEnElOriginal() {
        List<Order> imposible = List.of(new Order("P99", new Location(70, 50), 5, HORA, HORA.plusSeconds(60)));

        ResultadoPlanificacion esperado = directo(imposible, List.of(), 7L, List.of());
        PlanResponse real = servicio.planificar(
                new SolicitudPlanificacion(ModoOperacion.COLAPSO, HORA, imposible, List.of(), 7L, PRESUPUESTO_AMPLIO));

        assertEquivalente(esperado, real);
        assertTrue(real.colapso());
        assertEquals(List.of("P99"), real.noAtendidos().stream().map(n -> n.pedidoId()).toList());
    }

    // ---------------------------------------------------------------- contrato del servicio

    @Test
    void elAtajoArmaLaSolicitudCompletaSinSemilla() {
        AtomicReference<SolicitudPlanificacion> recibida = new AtomicReference<>();
        PlanificadorService espia = solicitud -> {
            recibida.set(solicitud);
            return null;
        };
        List<RoadBlock> bloqueos = List.of(bloqueoCentralP01());

        espia.planificar(ModoOperacion.SIMULACION_5D, HORA, pedidos(), bloqueos);

        assertEquals(new SolicitudPlanificacion(ModoOperacion.SIMULACION_5D, HORA, pedidos(), bloqueos, null, null),
                recibida.get());
    }

    @Test
    void sinAlmacenCentralLaPlanificacionFalla() {
        FuenteDatosOperativos sinCentral = new FuenteDatosOperativosMock() {
            @Override
            public Collection<Warehouse> almacenes() {
                return List.of(Warehouse.intermediate("NOROESTE", new Location(12, 38), 1_000));
            }
        };
        PlanificadorService falla = new PlanificadorSaService(sinCentral, CONFIG_PRUEBA);

        assertThrows(IllegalStateException.class, () -> falla.planificar(ModoOperacion.DIA_A_DIA, HORA, pedidos(), List.of()));
    }

    @Test
    void rechazaSolicitudNulaYDependenciasNulas() {
        assertThrows(NullPointerException.class, () -> servicio.planificar((SolicitudPlanificacion) null));
        assertThrows(NullPointerException.class, () -> new PlanificadorSaService(null, CONFIG_PRUEBA));
        assertThrows(NullPointerException.class, () -> new PlanificadorSaService(new FuenteDatosOperativosMock(), null));
    }

    /** Valores de config/formal-aprobado.properties (rama feature/expnumerica) y tope de smoke/readiness. */
    @Test
    void laConfiguracionPorDefectoEsValidaYEstable() {
        AnnealingConfig config = PlanificadorSaService.CONFIG_POR_DEFECTO;

        assertEquals(1000.0, config.initialTemperature());
        assertEquals(1.0, config.minimumTemperature());
        assertEquals(0.95, config.coolingFactor());
        assertEquals(50, config.iterationsPerTemperature());
        assertEquals(1_000_000, config.maximumIterations());
        assertEquals(1_000_000, config.maximumIterationsWithoutImprovement());
        assertEquals(1_500L, PlanificadorSaService.PRESUPUESTO_POR_DEFECTO_MS);
        assertTrue(config.minimumTemperature() < config.initialTemperature());
    }

    @Test
    void elConstructorPublicoUsaLaConfiguracionPorDefectoSinFallar() {
        PlanificadorService conDefecto = new PlanificadorSaService(new FuenteDatosOperativosMock());

        PlanResponse plan = conDefecto.planificar(ModoOperacion.DIA_A_DIA, HORA, List.of(), List.of());

        assertTrue(plan.rutas().isEmpty());
    }

    // ---------------------------------------------------------------- presupuesto de tiempo

    @Test
    void sinPresupuestoEnLaSolicitudSeAplicaElPorDefecto() {
        PlanResponse plan = servicio.planificar(ModoOperacion.DIA_A_DIA, HORA, pedidos(), List.of());

        assertEquals(PlanificadorSaService.PRESUPUESTO_POR_DEFECTO_MS, plan.presupuestoMs());
    }

    @Test
    void elPresupuestoDeLaSolicitudSeDevuelveYNoSeMarcaAgotadoSiSobraTiempo() {
        PlanResponse plan = servicio.planificar(
                new SolicitudPlanificacion(ModoOperacion.DIA_A_DIA, HORA, pedidos(), List.of(), 7L, PRESUPUESTO_AMPLIO));

        assertEquals(PRESUPUESTO_AMPLIO.longValue(), plan.presupuestoMs());
        assertFalse(plan.presupuestoAgotado());
        assertTrue(plan.duracionMs() < plan.presupuestoMs());
    }

    /**
     * Con los parámetros del experimento el SA original tarda varios segundos aun con pocos pedidos
     * (6 750 iteraciones hasta llegar a Tmin). Con un tope de 300 ms el ciclo debe cortarse mucho antes
     * y devolver un plan factible.
     */
    @Test
    void conLaConfiguracionDelExperimentoElTopeCortaElCiclo() {
        PlanificadorService conExperimento = new PlanificadorSaService(new FuenteDatosOperativosMock());
        List<Order> muchos = new ArrayList<>(pedidos());
        Instant plazo = HORA.plusSeconds(10 * 3600);
        muchos.add(new Order("P04", new Location(35, 20), 6, HORA, plazo));
        muchos.add(new Order("P05", new Location(22, 10), 10, HORA, plazo));
        muchos.add(new Order("P06", new Location(30, 9), 2, HORA, plazo));

        PlanResponse plan = conExperimento.planificar(
                new SolicitudPlanificacion(ModoOperacion.SIMULACION_5D, HORA, muchos, List.of(), 42L, 300L));

        assertTrue(plan.presupuestoAgotado());
        assertTrue(plan.duracionMs() < 3_000, "duración: " + plan.duracionMs() + " ms");
        assertTrue(plan.factible(), "violaciones: " + plan.violaciones());
        assertTrue(plan.noAtendidos().isEmpty());
    }
}
