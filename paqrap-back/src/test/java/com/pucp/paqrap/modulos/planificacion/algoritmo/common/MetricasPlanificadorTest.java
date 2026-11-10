package com.pucp.paqrap.modulos.planificacion.algoritmo.common;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.AnnealingConfig;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.OperationalSimulatedAnnealingPlanner;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.service.FuenteDatosOperativos;
import com.pucp.paqrap.modulos.planificacion.service.FuenteDatosOperativosMock;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
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

/** Contadores del planificador (Semana 13, paso 2): apagados no alteran nada y encendidos cuentan bien. */
class MetricasPlanificadorTest {

    private static final Instant HORA = Instant.parse("2026-09-09T12:00:00Z");
    private static final AnnealingConfig CONFIG_PRUEBA = new AnnealingConfig(100.0, 1.0, 0.90, 5, 30, 30);
    private static final MetricasPlanificador.Resumen CERO = new MetricasPlanificador.Resumen(0, 0, 0, 0, 0, 0, 0);

    @Test
    void noop_noLeeElRelojNiRegistra() {
        MetricasPlanificador noop = MetricasPlanificador.NOOP;

        noop.registrarEvaluacion(noop.inicio());
        noop.registrarCamino(noop.inicio(), new Location(1, 1), new Location(2, 2), HORA);
        noop.registrarAciertoCache();

        assertEquals(0L, noop.inicio());
        assertTrue(!noop.estanActivas());
        assertEquals(CERO, noop.resumen());
    }

    @Test
    void activas_cuentanParesUnicosYSeReinician() {
        MetricasPlanificador metricas = MetricasPlanificador.activas();
        Location a = new Location(1, 1);
        Location b = new Location(5, 1);

        metricas.registrarEvaluacion(metricas.inicio());
        metricas.registrarEvaluacion(metricas.inicio());
        metricas.registrarCamino(metricas.inicio(), a, b, HORA);
        metricas.registrarCamino(metricas.inicio(), a, b, HORA.plusSeconds(60));
        metricas.registrarCamino(metricas.inicio(), a, b, HORA);
        metricas.registrarCamino(metricas.inicio(), b, a, HORA);
        metricas.registrarAciertoCache();
        MetricasPlanificador.Resumen resumen = metricas.resumen();

        assertTrue(metricas.estanActivas());
        assertEquals(2L, resumen.evaluaciones());
        assertEquals(4L, resumen.caminos());
        assertEquals(2, resumen.paresUnicos());
        assertEquals(3, resumen.paresConHoraUnicos());
        assertEquals(1L, resumen.aciertosCache());
        assertTrue(resumen.msEvaluaciones() >= 0 && resumen.msCaminos() >= 0);

        metricas.reiniciar();
        assertEquals(CERO, metricas.resumen());
    }

    @Test
    void activas_cadaInstanciaEsIndependiente() {
        MetricasPlanificador primera = MetricasPlanificador.activas();
        MetricasPlanificador segunda = MetricasPlanificador.activas();

        primera.registrarEvaluacion(primera.inicio());

        assertEquals(1L, primera.resumen().evaluaciones());
        assertEquals(0L, segunda.resumen().evaluaciones());
    }

    @Test
    void constructores_rechazanMetricasNulas() {
        assertThrows(NullPointerException.class, () -> new RouteScheduler(new RoadNetwork(), null));
        assertThrows(NullPointerException.class,
                () -> new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()), 0, null));
    }

    @Test
    void evaluador_registraUnaEvaluacionPorLlamada() {
        MetricasPlanificador metricas = MetricasPlanificador.activas();
        OperationalPlanEvaluator evaluador =
                new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork(), metricas), 0, metricas);
        OperationalSnapshot snapshot = snapshot(new FuenteDatosOperativosMock());

        evaluador.evaluate(OperationalPlan.empty(), snapshot, List.of(), List.of());
        evaluador.evaluate(OperationalPlan.empty(), snapshot, List.of(), List.of());

        assertEquals(2L, metricas.resumen().evaluaciones());
        assertEquals(0L, metricas.resumen().caminos());
    }

    @Test
    void planificador_conYSinMetricas_daElMismoResultadoConLaMismaSemilla() {
        OperationalSnapshot snapshot = snapshot(new FuenteDatosOperativosMock());
        MetricasPlanificador metricas = MetricasPlanificador.activas();
        OperationalSimulatedAnnealingPlanner sinMetricas = new OperationalSimulatedAnnealingPlanner(
                new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork())));
        OperationalSimulatedAnnealingPlanner conMetricas = new OperationalSimulatedAnnealingPlanner(
                new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork(), metricas), 0, metricas));

        ResultadoPlanificacion esperado = sinMetricas.planificar(snapshot, pedidos(), List.<RoadBlock>of(),
                CONFIG_PRUEBA, new Random(7));
        ResultadoPlanificacion real = conMetricas.planificar(snapshot, pedidos(), List.<RoadBlock>of(),
                CONFIG_PRUEBA, new Random(7));

        assertEquals(esperado.costoTotal(), real.costoTotal());
        assertEquals(esperado.esFactible(), real.esFactible());
        assertEquals(esperado.noAtendidos().stream().map(Order::id).toList(),
                real.noAtendidos().stream().map(Order::id).toList());
        assertEquals(resumenRutas(esperado), resumenRutas(real));
        assertTrue(metricas.resumen().evaluaciones() > 0);
        assertTrue(metricas.resumen().caminos() > 0);
    }

    // ---------------------------------------------------------------- auxiliares

    private static List<Order> pedidos() {
        Instant plazo = HORA.plusSeconds(10 * 3600);
        return List.of(
                new Order("P01", new Location(31, 14), 12, HORA, plazo),
                new Order("P02", new Location(27, 18), 8, HORA, plazo),
                new Order("P03", new Location(29, 16), 4, HORA, plazo));
    }

    private static List<String> resumenRutas(ResultadoPlanificacion resultado) {
        return resultado.plan().routes().stream()
                .sorted(Comparator.comparing(r -> r.vehicle().id()))
                .map(MetricasPlanificadorTest::describir)
                .toList();
    }

    private static String describir(DeliveryRoute ruta) {
        return ruta.id() + "|" + ruta.vehicle().id() + "|" + ruta.departureAt() + "|" + ruta.stops();
    }

    private static OperationalSnapshot snapshot(FuenteDatosOperativos fuente) {
        Warehouse central = fuente.almacenes().stream().filter(Warehouse::isCentral).findFirst().orElseThrow();
        Map<String, VehicleOperationalState> estados = new LinkedHashMap<>();
        for (Vehicle vehiculo : fuente.flota()) {
            estados.put(vehiculo.id(),
                    new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, central.location(), HORA));
        }
        return new OperationalSnapshot(HORA, fuente.perfilFlota(), InventorySnapshot.from(fuente.almacenes()), estados,
                fuente.calendarioMantenimiento(), new ShiftSchedule(ShiftSchedule.DEFAULT_ZONE), fuente.averias(null));
    }
}
