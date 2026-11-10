package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.service.ClasificadorNoAtendidos.Causa;
import com.pucp.paqrap.modulos.planificacion.service.ClasificadorNoAtendidos.Resultado;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

/**
 * Cada prueba arma un escenario mínimo sobre la flota real del mock (10 autos de capacidad 24, motos de 8 y
 * bicicletas de 4) y comprueba la causa asignada. Los pedidos de 10 paquetes solo caben en un auto.
 */
class ClasificadorNoAtendidosTest {

    /** 07:00 en Lima: inicio del turno diurno. */
    private static final Instant T = Instant.parse("2028-12-01T12:00:00Z");

    private final FuenteDatosOperativos fuente = new FuenteDatosOperativosMock();
    private final Warehouse central = fuente.almacenes().stream().filter(Warehouse::isCentral).findFirst().orElseThrow();
    private final List<Vehicle> flota = fuente.flota();
    private final RouteScheduler programador = new RouteScheduler(new RoadNetwork());
    private final ClasificadorNoAtendidos clasificador =
            new ClasificadorNoAtendidos(new OperationalPlanEvaluator(programador));
    private final OperationalSnapshot base = ClasificadorNoAtendidos.snapshotEnCentral(fuente, central, T);

    private Order pedido(String id, int x, int y, int paquetes, Duration plazo) {
        return new Order(id, new Location(x, y), paquetes, T.minusSeconds(60), T.plus(plazo));
    }

    private List<Vehicle> autos() {
        return flota.stream().filter(v -> v.type() == VehicleType.CAR).toList();
    }

    /** Un auto por ruta, cada uno con un paquete para {@code destino}, saliendo del central en T. */
    private List<RutaVigente> ocuparAutos(int x, int y, Duration plazo) {
        List<RutaVigente> rutas = new ArrayList<>();
        for (Vehicle auto : autos()) {
            Order ocupado = pedido("OC-" + auto.id(), x, y, 1, plazo);
            DeliveryRoute ruta = DeliveryRoute.startScenarioAtCentral("R-" + auto.id(), auto, central, 1, T)
                    .withAppendedStop(new DeliveryStop(ocupado, 1)).returningTo(central);
            ScheduledDeliveryRoute programada = programador.schedule(ruta, base, List.of());
            rutas.add(new RutaVigente(ruta, base, programada, List.of()));
        }
        return rutas;
    }

    private Resultado clasificar(Order pedido, List<RutaVigente> activas) {
        return clasificador.clasificar(pedido, T, base, central, flota, activas, List.of());
    }

    @Test
    void pedidoConPlazoCumplido_esVencido() {
        Resultado r = clasificar(pedido("P1", 30, 14, 10, Duration.ZERO), List.of());

        assertEquals(Causa.VENCIDO, r.causa());
        assertFalse(r.detalle().isBlank());
    }

    @Test
    void pedidoLejanoConPlazoCorto_esImposibleFisico() {
        // (69,49) está a 77 km del central: un auto a 20 km/h tarda casi 4 h y el plazo es de 2 h.
        Resultado r = clasificar(pedido("P2", 69, 49, 10, Duration.ofHours(2)), List.of());

        assertEquals(Causa.IMPOSIBLE_FISICO, r.causa());
    }

    @Test
    void pedidoAlcanzableConAutosLibres_esFalloDelPlanificador() {
        Resultado r = clasificar(pedido("P3", 30, 14, 10, Duration.ofHours(8)), List.of());

        assertEquals(Causa.LIBRE_VIABLE_NO_ASIGNADO, r.causa());
        assertTrue(r.detalle().contains("TA"), r.detalle());
    }

    @Test
    void todosLosAutosEnRutaConHolgura_esEncolableEnUnaRutaActiva() {
        List<RutaVigente> activas = ocuparAutos(30, 14, Duration.ofHours(12));

        Resultado r = clasificar(pedido("P4", 24, 14, 10, Duration.ofHours(12)), activas);

        assertEquals(Causa.ENCOLABLE_EN_RUTA_ACTIVA, r.causa(), r.detalle());
        assertTrue(r.detalle().contains("posición"), r.detalle());
    }

    @Test
    void todosLosAutosEnRutaSinHolgura_esSinVehiculosLibres() {
        // Cada auto ocupado debe llegar a (30,14) antes de T+72 min y el pedido nuevo a (24,14) antes de T+78 min:
        // solo cabe si sale solo desde el central, y no hay autos libres.
        List<RutaVigente> activas = ocuparAutos(30, 14, Duration.ofMinutes(72));

        Resultado r = clasificar(pedido("P5", 24, 14, 10, Duration.ofMinutes(78)), activas);

        assertEquals(Causa.SIN_VEHICULOS_LIBRES, r.causa(), r.detalle());
    }

    @Test
    void vehiculoOcupadoNoCuentaComoLibre_peroUnoLibreSi() {
        List<RutaVigente> activas = ocuparAutos(30, 14, Duration.ofHours(12)).subList(0, 9);

        Resultado r = clasificar(pedido("P6", 24, 14, 10, Duration.ofHours(12)), activas);

        assertEquals(Causa.LIBRE_VIABLE_NO_ASIGNADO, r.causa(), r.detalle());
    }

    @Test
    void primeraParadaFutura_ignoraLasParadasYaIniciadas() {
        RutaVigente ruta = ocuparAutos(30, 14, Duration.ofHours(12)).get(0);
        Instant llegada = ruta.programada().scheduledStops().get(0).arrivedAt();

        assertEquals(0, ClasificadorNoAtendidos.primeraParadaFutura(ruta, llegada.minusSeconds(1)));
        assertEquals(1, ClasificadorNoAtendidos.primeraParadaFutura(ruta, llegada));
        assertEquals(2, ClasificadorNoAtendidos.primeraParadaFutura(ruta, Instant.parse("2030-01-01T00:00:00Z")));
        assertEquals(ruta.ruta().stops().size(),
                ClasificadorNoAtendidos.primeraParadaFutura(new RutaVigente(ruta.ruta(), base, null, List.of()), T));
    }

    @Test
    void primeraPosicionInsertable_noDesviaAUnVehiculoQueYaVaEnCamino() {
        RutaVigente ruta = ocuparAutos(30, 14, Duration.ofHours(12)).get(0);
        Instant llegada = ruta.programada().scheduledStops().get(0).arrivedAt();
        Instant finServicio = ruta.programada().scheduledStops().get(0).completedAt();

        // En el central justo al salir: puede ir antes de la primera parada.
        assertEquals(0, ClasificadorNoAtendidos.primeraPosicionInsertable(ruta, T));
        // Ya salió hacia la primera parada: solo después de ella.
        assertEquals(1, ClasificadorNoAtendidos.primeraPosicionInsertable(ruta, T.plusSeconds(60)));
        // Atendiendo la primera parada: solo después de ella (la próxima es el regreso, índice 1).
        assertEquals(1, ClasificadorNoAtendidos.primeraPosicionInsertable(ruta, llegada.plusSeconds(60)));
        // Terminó el servicio y va al central: ya no queda posición antes del regreso (índice 1 en tránsito => 2).
        assertEquals(2, ClasificadorNoAtendidos.primeraPosicionInsertable(ruta, finServicio.plusSeconds(60)));
        // Sin programación solo queda el final.
        assertEquals(ruta.ruta().stops().size(), ClasificadorNoAtendidos.primeraPosicionInsertable(
                new RutaVigente(ruta.ruta(), base, null, List.of()), T));
    }

    @Test
    void evaluarSolo_elVehiculoLibreCercanoCumpleYElLejanoNo() {
        Vehicle auto = autos().get(0);

        var cerca = clasificador.evaluarSolo(auto, pedido("P7", 30, 14, 10, Duration.ofHours(8)), T, base, central, List.of());
        var lejos = clasificador.evaluarSolo(auto, pedido("P8", 69, 49, 10, Duration.ofHours(2)), T, base, central, List.of());

        assertTrue(ClasificadorNoAtendidos.sinViolaciones(cerca), cerca.violations().toString());
        assertFalse(ClasificadorNoAtendidos.sinViolaciones(lejos));
    }
}
