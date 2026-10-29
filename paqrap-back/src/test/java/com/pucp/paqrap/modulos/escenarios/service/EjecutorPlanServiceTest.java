package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
import com.pucp.paqrap.modulos.almacenes.repository.InventoryMovementRepository;
import com.pucp.paqrap.modulos.almacenes.repository.WarehouseRepository;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
import com.pucp.paqrap.modulos.incidencias.persistence.BreakdownEventEntity;
import com.pucp.paqrap.modulos.incidencias.repository.BreakdownEventRepository;
import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.entity.OrderStatus;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderStatusHistoryEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.pedidos.repository.OrderStatusHistoryRepository;
import com.pucp.paqrap.modulos.planificacion.dto.ModoOperacion;
import com.pucp.paqrap.modulos.planificacion.dto.ParadaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.RutaResponse;
import com.pucp.paqrap.modulos.planificacion.service.PlanVigente;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ruta de prueba de TA01, que sale del central (27,14) a las 10:00:
 * P1 en (30,14) llega 10:30 (entrega completa de 5); P2 en (30,18) llega 12:00 (entrega parcial de 2 de 3);
 * vuelve al central a las 14:00. En la cuadrícula: 3 + 4 + 7 = 14 km, igual que la ruta; costo 8 por km.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EjecutorPlanServiceTest {

    private static final Instant INICIO = Instant.parse("2026-01-01T08:00:00Z");
    private static final Instant SALIDA = Instant.parse("2026-01-01T10:00:00Z");
    private static final AtomicLong EJECUCIONES = new AtomicLong(1000);

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        PlanesDePrueba planesDePrueba() {
            return new PlanesDePrueba();
        }
    }

    static final class PlanesDePrueba implements PlanVigente {
        final Map<Long, PlanResponse> planes = new ConcurrentHashMap<>();

        @Override
        public Optional<PlanResponse> ultimoPlan(long ejecucionId) {
            return Optional.ofNullable(planes.get(ejecucionId));
        }
    }

    @Autowired private EjecutorPlanService ejecutor;
    @Autowired private PlanesDePrueba planes;
    @Autowired private OrderRepository pedidoRepository;
    @Autowired private OrderStatusHistoryRepository historialRepository;
    @Autowired private VehicleRepository vehiculoRepository;
    @Autowired private WarehouseRepository almacenRepository;
    @Autowired private InventoryMovementRepository movimientoRepository;
    @Autowired private BreakdownEventRepository averiaRepository;

    private long ejecucion;

    @BeforeEach
    void cargarDatos() {
        ejecucion = EJECUCIONES.incrementAndGet();
        almacenRepository.save(new WarehouseEntity("CENTRAL", WarehouseKind.CENTRAL, 27, 14, null));
        almacenRepository.save(new WarehouseEntity("EAST", WarehouseKind.INTERMEDIATE, 57, 27, 10));
        vehiculoRepository.save(new VehicleEntity("TA01", VehicleType.CAR, VehicleOperationalStatus.AVAILABLE));
        vehiculoRepository.save(new VehicleEntity("TM01", VehicleType.MOTORCYCLE, VehicleOperationalStatus.AVAILABLE));
        for (OrderEntity pedido : List.of(
                new OrderEntity("P1", "c1", new Location(30, 14), 5, INICIO, DeliveryType.REGULAR, 36),
                new OrderEntity("P2", "c2", new Location(30, 18), 3, INICIO, DeliveryType.REGULAR, 36))) {
            pedidoRepository.save(pedido);
            historialRepository.save(OrderStatusHistoryEntity.registro(pedido));
        }
    }

    private static Instant hora(String hhmm) {
        return Instant.parse("2026-01-01T" + hhmm + ":00Z");
    }

    private PlanResponse planConRutaTa01() {
        RutaResponse ruta = new RutaResponse("SA-TA01", "TA01", "CAR", SALIDA, hora("14:00"), 14.0, 112.0, List.of(
                new ParadaResponse(1, ParadaResponse.ENTREGA, "P1", null, 30, 14, 5, hora("10:30"), hora("11:30"), 7, 2),
                new ParadaResponse(2, ParadaResponse.ENTREGA, "P2", null, 30, 18, 2, hora("12:00"), hora("13:00"), 2, 0),
                new ParadaResponse(3, ParadaResponse.ALMACEN, null, "CENTRAL", 27, 14, 0, hora("14:00"), hora("14:00"), 0, 0)));
        return plan(SALIDA, List.of(ruta));
    }

    private PlanResponse planReplanificadoEnTm01() {
        Instant salida = hora("12:30");
        RutaResponse ruta = new RutaResponse("SA-TM01", "TM01", "MOTORCYCLE", salida, hora("14:00"), 14.0, 84.0, List.of(
                new ParadaResponse(1, ParadaResponse.ENTREGA, "P1", null, 30, 14, 5, hora("13:00"), hora("13:00"), 8, 3),
                new ParadaResponse(2, ParadaResponse.ENTREGA, "P2", null, 30, 18, 3, hora("13:30"), hora("13:30"), 3, 0),
                new ParadaResponse(3, ParadaResponse.ALMACEN, null, "CENTRAL", 27, 14, 0, hora("14:00"), hora("14:00"), 0, 0)));
        return plan(salida, List.of(ruta));
    }
    private static PlanResponse plan(Instant planificadoEn, List<RutaResponse> rutas) {
        return new PlanResponse(ModoOperacion.SIMULACION_5D, planificadoEn, true, false, 0, 0, 1L, 0, 1500, false,
                rutas, List.of(), List.of());
    }

    private OrderEntity pedido(String id) {
        return pedidoRepository.findById(id).orElseThrow();
    }

    private VehicleEntity vehiculo(String id) {
        return vehiculoRepository.findById(id).orElseThrow();
    }

    @Test
    void ejecutaLaRutaSegunAvanzaElReloj() {
        planes.planes.put(ejecucion, planConRutaTa01());

        // Sale a las 10:00: el vehículo en ruta y el primer pedido en tránsito.
        ejecutor.avanzar(ejecucion, SALIDA, SALIDA);
        assertEquals(VehicleOperationalStatus.IN_ROUTE, vehiculo("TA01").getOperationalStatus());
        assertEquals(7, vehiculo("TA01").getCurrentLoad());
        assertEquals(OrderStatus.IN_TRANSIT, pedido("P1").getStatus());
        assertEquals(OrderStatus.ASSIGNED, pedido("P2").getStatus());

        // 10:30 llega a P1: entregado (con la hora de llegada) y el vehículo en el cliente.
        ejecutor.avanzar(ejecucion, SALIDA, hora("10:45"));
        assertEquals(OrderStatus.DELIVERED, pedido("P1").getStatus());
        assertEquals(hora("10:30"), pedido("P1").getDeliveredAt());
        assertEquals(30, vehiculo("TA01").getCurrentX());
        assertEquals(2, vehiculo("TA01").getCurrentLoad());
        EjecutorPlanService.Metricas metricas = ejecutor.metricas(ejecucion).orElseThrow();
        assertEquals(1, metricas.pedidosEntregados());
        assertEquals(1, metricas.entregadosATiempo());
        assertEquals(3.0, metricas.distanciaKm());
        assertEquals(24.0, metricas.costo());

        // Hasta las 14:30: P2 recibió 2 de 3 paquetes (sigue pendiente) y el vehículo volvió al central.
        ejecutor.avanzar(ejecucion, hora("10:45"), hora("14:30"));
        assertEquals(OrderStatus.IN_TRANSIT, pedido("P2").getStatus());
        assertNull(pedido("P2").getDeliveredAt());
        assertEquals(VehicleOperationalStatus.AVAILABLE, vehiculo("TA01").getOperationalStatus());
        assertEquals(27, vehiculo("TA01").getCurrentX());
        assertEquals(14, vehiculo("TA01").getCurrentY());
        metricas = ejecutor.metricas(ejecucion).orElseThrow();
        assertEquals(1, metricas.pedidosEntregados());
        assertEquals(7, metricas.paquetesEntregados());
        assertEquals(14.0, metricas.distanciaKm());
        assertEquals(112.0, metricas.costo());
        assertEquals(List.of(OrderStatus.REGISTERED, OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED),
                historialRepository.findByOrderIdOrderByChangedAtAscStatusHistoryIdAsc("P1").stream()
                        .map(OrderStatusHistoryEntity::getStatus).toList());
    }

    @Test
    void unaAveriaDetieneLaRutaYDevuelveSusPedidosPendientes() {
        planes.planes.put(ejecucion, planConRutaTa01());
        averiaRepository.save(new BreakdownEventEntity(ejecucion,
                new BreakdownEvent("TA01", BreakdownType.MINOR, hora("10:40"), new Location(30, 15))));

        ejecutor.avanzar(ejecucion, SALIDA, hora("14:30"));

        assertEquals(OrderStatus.DELIVERED, pedido("P1").getStatus(), "P1 se entregó antes de la avería");
        assertEquals(OrderStatus.REGISTERED, pedido("P2").getStatus(), "P2 vuelve a planificarse");
        assertEquals(15, vehiculo("TA01").getCurrentY(), "queda en el lugar de la avería");
        assertEquals(VehicleOperationalStatus.AVAILABLE, vehiculo("TA01").getOperationalStatus());
    }

    /**
     * La avería cae entre la salida y la primera llegada; el mismo tick puede sustituir el plan, así que la
     * interrupción debe ejecutarse sin esperar esa llegada posterior.
     */
    @Test
    void unaAveriaEntreEventosInterrumpeAntesDeQueElPlanNuevoReemplaceLaRuta() {
        planes.planes.put(ejecucion, planConRutaTa01());
        ejecutor.avanzar(ejecucion, SALIDA, SALIDA);
        averiaRepository.save(new BreakdownEventEntity(ejecucion,
                new BreakdownEvent("TA01", BreakdownType.MINOR, hora("10:15"), new Location(28, 14))));

        ejecutor.avanzar(ejecucion, SALIDA, hora("10:20"));
        planes.planes.put(ejecucion, plan(hora("10:20"), List.of()));
        ejecutor.avanzar(ejecucion, hora("10:20"), hora("10:21"));

        assertEquals(OrderStatus.REGISTERED, pedido("P1").getStatus());
        assertEquals(OrderStatus.REGISTERED, pedido("P2").getStatus());
        assertEquals(VehicleOperationalStatus.AVAILABLE, vehiculo("TA01").getOperationalStatus());
        assertEquals(List.of(OrderStatus.REGISTERED, OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT,
                        OrderStatus.REGISTERED),
                historialRepository.findByOrderIdOrderByChangedAtAscStatusHistoryIdAsc("P1").stream()
                        .map(OrderStatusHistoryEntity::getStatus).toList());
        assertEquals(List.of(OrderStatus.REGISTERED, OrderStatus.ASSIGNED, OrderStatus.REGISTERED),
                historialRepository.findByOrderIdOrderByChangedAtAscStatusHistoryIdAsc("P2").stream()
                        .map(OrderStatusHistoryEntity::getStatus).toList());
    }

    @Test
    void unaAveriaDuranteAtencionConservaLaEntregaParcialYLiberaLoPendiente() {
        planes.planes.put(ejecucion, planConRutaTa01());
        averiaRepository.save(new BreakdownEventEntity(ejecucion,
                new BreakdownEvent("TA01", BreakdownType.MINOR, hora("12:30"), new Location(30, 18))));

        ejecutor.avanzar(ejecucion, SALIDA, hora("12:45"));

        assertEquals(OrderStatus.DELIVERED, pedido("P1").getStatus());
        assertEquals(OrderStatus.REGISTERED, pedido("P2").getStatus());
        assertEquals(7, ejecutor.metricas(ejecucion).orElseThrow().paquetesEntregados());
        assertEquals(0, vehiculo("TA01").getCurrentLoad());
        assertTrue(vehiculo("TA01").getCurrentLoad() >= 0);
    }
    @Test
    void unaAveriaDeOtraEjecucionDelMismoPeriodoNoLaAfecta() {
        // Una corrida anterior del mismo periodo averió a TA01 a las 10:40; en esta corrida no le pasó nada.
        planes.planes.put(ejecucion, planConRutaTa01());
        averiaRepository.save(new BreakdownEventEntity(ejecucion + 1000,
                new BreakdownEvent("TA01", BreakdownType.MAJOR, hora("10:40"), new Location(30, 15))));

        ejecutor.avanzar(ejecucion, SALIDA, hora("12:30"));

        assertEquals(OrderStatus.DELIVERED, pedido("P1").getStatus());
        assertEquals(OrderStatus.IN_TRANSIT, pedido("P2").getStatus(), "sigue su ruta hacia P2");
        assertEquals(VehicleOperationalStatus.IN_ROUTE, vehiculo("TA01").getOperationalStatus());
    }

    @Test
    void unaAveriaAntesDeLaSalidaNoIniciaLaRutaYReasignaSusPedidosAOtroVehiculo() {
        // CP-45: avería menor a las 09:30 (inhabilitado hasta las 11:30); la ruta salía a las 10:00.
        PlanResponse planInicial = planConRutaTa01();
        assertEquals("TA01", planInicial.rutas().getFirst().vehiculoId());
        planes.planes.put(ejecucion, planInicial);
        BreakdownEvent averia = new BreakdownEvent("TA01", BreakdownType.MINOR, hora("09:30"), new Location(27, 14));
        assertTrue(averia.occurredAt().isBefore(planInicial.rutas().getFirst().salida()));
        averiaRepository.save(new BreakdownEventEntity(ejecucion,
                averia));

        ejecutor.avanzar(ejecucion, SALIDA, hora("12:30"));

        assertEquals(VehicleOperationalStatus.AVAILABLE, vehiculo("TA01").getOperationalStatus());
        assertEquals(OrderStatus.REGISTERED, pedido("P1").getStatus());
        assertEquals(OrderStatus.REGISTERED, pedido("P2").getStatus());
        assertEquals(0, ejecutor.metricas(ejecucion).orElseThrow().pedidosEntregados());

        // La replanificación por la avería asigna ambos pedidos liberados a la alternativa factible TM01.
        planes.planes.put(ejecucion, planReplanificadoEnTm01());
        ejecutor.avanzar(ejecucion, hora("12:30"), hora("12:30"));

        assertEquals(VehicleOperationalStatus.AVAILABLE, vehiculo("TA01").getOperationalStatus(),
                "la unidad averiada no inicia ni recibe la ruta de reemplazo");
        assertEquals(VehicleOperationalStatus.IN_ROUTE, vehiculo("TM01").getOperationalStatus());
        assertEquals(OrderStatus.IN_TRANSIT, pedido("P1").getStatus());
        assertEquals(OrderStatus.ASSIGNED, pedido("P2").getStatus());
        assertEquals("TM01", historialRepository.findByOrderIdOrderByChangedAtAscStatusHistoryIdAsc("P1").getLast().getVehicleId());
        assertEquals("TM01", historialRepository.findByOrderIdOrderByChangedAtAscStatusHistoryIdAsc("P2").getLast().getVehicleId());
    }

    @Test
    void unaAveriaIntermediaEnRutaLlevaElVehiculoAlCentralALasCuatroHoras() {
        // CP-47: se conserva lo entregado, el resto vuelve a planificarse y a las 15:40 la unidad pasa al central vacía.
        planes.planes.put(ejecucion, planConRutaTa01());
        averiaRepository.save(new BreakdownEventEntity(ejecucion,
                new BreakdownEvent("TA01", BreakdownType.INTERMEDIATE, hora("11:40"), new Location(30, 15))));

        ejecutor.avanzar(ejecucion, SALIDA, hora("15:30"));
        assertEquals(OrderStatus.DELIVERED, pedido("P1").getStatus());
        assertEquals(OrderStatus.REGISTERED, pedido("P2").getStatus());
        assertEquals(15, vehiculo("TA01").getCurrentY(), "hasta las 15:40 sigue donde se averió");

        ejecutor.avanzar(ejecucion, hora("15:30"), hora("16:00"));
        assertEquals(27, vehiculo("TA01").getCurrentX());
        assertEquals(14, vehiculo("TA01").getCurrentY());
        assertEquals(0, vehiculo("TA01").getCurrentLoad());
    }

    @Test
    void unaAveriaDespuesDeTerminarLaRutaNoCambiaLoEntregado() {
        // CP-50: la ruta termina a las 14:00 y la avería ocurre a las 14:10.
        planes.planes.put(ejecucion, planConRutaTa01());
        averiaRepository.save(new BreakdownEventEntity(ejecucion,
                new BreakdownEvent("TA01", BreakdownType.MINOR, hora("14:10"), new Location(27, 14))));

        ejecutor.avanzar(ejecucion, SALIDA, hora("14:30"));

        assertEquals(OrderStatus.DELIVERED, pedido("P1").getStatus());
        assertEquals(OrderStatus.IN_TRANSIT, pedido("P2").getStatus(), "su entrega parcial no se deshace");
        assertEquals(14.0, ejecutor.metricas(ejecucion).orElseThrow().distanciaKm());
        assertEquals(VehicleOperationalStatus.AVAILABLE, vehiculo("TA01").getOperationalStatus());
    }

    @Test
    void informaElRefrigerioDelConductorMientrasDura() {
        // Llega a P1 a las 10:20 y descansa hasta las 10:30, cuando lo atiende.
        RutaResponse ruta = new RutaResponse("SA-TA01", "TA01", "CAR", SALIDA, hora("11:30"), 3.0, 24.0, List.of(
                new ParadaResponse(1, ParadaResponse.ENTREGA, "P1", null, 30, 14, 5, hora("10:30"), hora("11:30"),
                        5, 0, hora("10:20"))));
        planes.planes.put(ejecucion, plan(SALIDA, List.of(ruta)));
        ejecutor.avanzar(ejecucion, SALIDA, SALIDA);

        assertEquals(Optional.of(hora("10:30")), ejecutor.refrigerioHasta(ejecucion, "TA01", hora("10:25")));
        assertEquals(Optional.empty(), ejecutor.refrigerioHasta(ejecucion, "TA01", hora("10:10")), "aún viaja");
        assertEquals(Optional.empty(), ejecutor.refrigerioHasta(ejecucion, "TA01", hora("10:45")), "ya atiende");
        assertEquals(Optional.empty(), ejecutor.refrigerioHasta(ejecucion, "TM01", hora("10:25")), "otro vehículo");
    }

    @Test
    void unPlanNuevoSinElVehiculoLoDejaDisponible() {
        planes.planes.put(ejecucion, planConRutaTa01());
        ejecutor.avanzar(ejecucion, SALIDA, hora("10:45"));
        assertEquals(VehicleOperationalStatus.IN_ROUTE, vehiculo("TA01").getOperationalStatus());

        planes.planes.put(ejecucion, plan(hora("11:00"), List.of()));
        ejecutor.avanzar(ejecucion, hora("10:45"), hora("11:00"));

        assertEquals(VehicleOperationalStatus.AVAILABLE, vehiculo("TA01").getOperationalStatus());
        assertEquals(30, vehiculo("TA01").getCurrentX(), "se queda donde iba");
    }

    /** La replanificación conserva la ruta en marcha: el plan nuevo trae la misma ruta (otro objeto) y sigue. */
    @Test
    void unaRutaConservadaEnUnPlanNuevoSigueEjecutandose() {
        planes.planes.put(ejecucion, planConRutaTa01());
        ejecutor.avanzar(ejecucion, SALIDA, hora("10:45"));

        planes.planes.put(ejecucion, plan(hora("10:45"), planConRutaTa01().rutas()));
        ejecutor.avanzar(ejecucion, hora("10:45"), hora("14:30"));

        assertEquals(OrderStatus.IN_TRANSIT, pedido("P2").getStatus(), "P2 recibió 2 de 3 paquetes");
        assertEquals(VehicleOperationalStatus.AVAILABLE, vehiculo("TA01").getOperationalStatus(), "volvió al central");
        assertEquals(27, vehiculo("TA01").getCurrentX());
        EjecutorPlanService.Metricas metricas = ejecutor.metricas(ejecucion).orElseThrow();
        assertEquals(1, metricas.pedidosEntregados());
        assertEquals(7, metricas.paquetesEntregados());
        assertEquals(14.0, metricas.distanciaKm());
    }

    /** Un evento justo en la hora del plan nuevo ya se aplicó con el anterior: la ruta conservada no lo repite. */
    @Test
    void unaRutaConservadaNoRepiteElEventoDelInstanteDelCorte() {
        planes.planes.put(ejecucion, planConRutaTa01());
        ejecutor.avanzar(ejecucion, SALIDA, hora("10:30"));
        assertEquals(3.0, ejecutor.metricas(ejecucion).orElseThrow().distanciaKm());

        planes.planes.put(ejecucion, plan(hora("10:30"), planConRutaTa01().rutas()));
        ejecutor.avanzar(ejecucion, hora("10:30"), hora("14:30"));

        EjecutorPlanService.Metricas metricas = ejecutor.metricas(ejecucion).orElseThrow();
        assertEquals(7, metricas.paquetesEntregados());
        assertEquals(14.0, metricas.distanciaKm());
        assertEquals(112.0, metricas.costo());
    }

    /** Una ruta del plan nuevo con el mismo vehículo pero otra salida es otra ruta: la anterior no continúa. */
    @Test
    void unaRutaConOtraSalidaNoContinuaLaRutaEnCurso() {
        planes.planes.put(ejecucion, planConRutaTa01());
        ejecutor.avanzar(ejecucion, SALIDA, hora("10:45"));
        RutaResponse original = planConRutaTa01().rutas().getFirst();
        RutaResponse otraSalida = new RutaResponse(original.id(), original.vehiculoId(), original.tipoVehiculo(),
                hora("16:00"), hora("20:00"), original.distanciaKm(), original.costo(), List.of());

        planes.planes.put(ejecucion, plan(hora("10:45"), List.of(otraSalida)));
        ejecutor.avanzar(ejecucion, hora("10:45"), hora("14:30"));

        assertEquals(OrderStatus.ASSIGNED, pedido("P2").getStatus(), "la ruta original ya no se ejecuta");
        assertEquals(3.0, ejecutor.metricas(ejecucion).orElseThrow().distanciaKm());
    }

    @Test
    void alIniciarReiniciaLaFlotaYLosPedidosDelPeriodo() {
        planes.planes.put(ejecucion, planConRutaTa01());
        ejecutor.avanzar(ejecucion, SALIDA, hora("14:30"));
        vehiculo("TM01").setOperationalStatus(VehicleOperationalStatus.UNAVAILABLE);
        vehiculo("TM01").moverA(40, 40, 0);

        ejecutor.prepararInicio(ejecucion + 1, INICIO);

        assertEquals(OrderStatus.REGISTERED, pedido("P1").getStatus());
        assertNull(pedido("P1").getDeliveredAt());
        assertEquals(1, historialRepository.findByOrderIdOrderByChangedAtAscStatusHistoryIdAsc("P1").size(),
                "solo queda el REGISTERED original");
        assertEquals(27, vehiculo("TA01").getCurrentX());
        assertEquals(VehicleOperationalStatus.UNAVAILABLE, vehiculo("TM01").getOperationalStatus(),
                "el operario lo marcó no disponible");
        assertEquals(40, vehiculo("TM01").getCurrentX());
    }
    @Test
    void laCargaInicialRegistraUnDespachoIdempotenteDesdeSuAlmacenDeOrigen() {
        RutaResponse ruta = new RutaResponse("SA-INICIAL", "TA01", "CAR", SALIDA, hora("11:00"), 3.0, 24.0,
                List.of(new ParadaResponse(1, ParadaResponse.ENTREGA, "P1", null, 30, 14, 5, hora("10:30"),
                        hora("10:30"), 5, 0)), "CENTRAL", 501L, 601L);
        planes.planes.put(ejecucion, plan(SALIDA, List.of(ruta)));

        ejecutor.avanzar(ejecucion, SALIDA, SALIDA);
        ejecutor.avanzar(ejecucion, SALIDA, SALIDA);

        assertEquals(5, vehiculo("TA01").getCurrentLoad());
        assertEquals(1, movimientoRepository.count());
        var movimiento = movimientoRepository.findAll().getFirst();
        assertEquals("CENTRAL", movimiento.getWarehouseId());
        assertEquals("INITIAL:ROUTE:601", movimiento.getEventKey());
        assertNull(movimiento.getStockBefore());
        assertNull(movimiento.getStockAfter());
    }

    @Test
    void unaCargaIntermediaSinStockNoAumentaLaCargaNiPersisteDespacho() {
        RutaResponse ruta = new RutaResponse("SA-SIN-STOCK", "TA01", "CAR", SALIDA, hora("11:00"), 1.0, 8.0,
                List.of(new ParadaResponse(1, ParadaResponse.ALMACEN, null, "EAST", 57, 27, 20, hora("10:30"),
                        hora("10:30"), 0, 20)), null, 502L, 602L);
        planes.planes.put(ejecucion, plan(SALIDA, List.of(ruta)));

        ejecutor.avanzar(ejecucion, SALIDA, hora("10:45"));

        assertEquals(0, vehiculo("TA01").getCurrentLoad());
        assertEquals(0, movimientoRepository.count());
    }

    @Test
    void unaEntregaParcialNoGeneraUnSegundoDespachoDeAlmacen() {
        RutaResponse ruta = new RutaResponse("SA-PARCIAL", "TA01", "CAR", SALIDA, hora("14:00"), 14.0, 112.0,
                planConRutaTa01().rutas().getFirst().paradas(), "CENTRAL", 503L, 603L);
        planes.planes.put(ejecucion, plan(SALIDA, List.of(ruta)));

        ejecutor.avanzar(ejecucion, SALIDA, hora("14:30"));

        assertEquals(1, movimientoRepository.count());
        assertEquals("INITIAL:ROUTE:603", movimientoRepository.findAll().getFirst().getEventKey());
        assertEquals(7, ejecutor.metricas(ejecucion).orElseThrow().paquetesEntregados());
    }

    @Test
    void snapshotsDistintosConLaMismaRutaOperacionalRegistranDosCargasInicialesYReintentoNoDuplica() {
        RutaResponse rutaA = new RutaResponse("SA-TA01", "TA01", "CAR", SALIDA, hora("10:30"), 3.0, 24.0,
                List.of(new ParadaResponse(1, ParadaResponse.ENTREGA, "P1", null, 30, 14, 5, hora("10:15"),
                        hora("10:15"), 5, 0)), "CENTRAL", 101L, 1001L);
        RutaResponse rutaB = new RutaResponse("SA-TA01", "TA01", "CAR", hora("11:00"), hora("11:30"), 3.0, 24.0,
                List.of(new ParadaResponse(1, ParadaResponse.ENTREGA, "P1", null, 30, 14, 5, hora("11:15"),
                        hora("11:15"), 5, 0)), "CENTRAL", 102L, 1002L);
        planes.planes.put(ejecucion, plan(SALIDA, List.of(rutaA)));
        ejecutor.avanzar(ejecucion, SALIDA, hora("10:30"));

        planes.planes.put(ejecucion, plan(hora("10:30"), List.of(rutaB)));
        ejecutor.avanzar(ejecucion, hora("10:30"), hora("11:30"));

        assertEquals(2, movimientoRepository.count());
        assertEquals(List.of("INITIAL:ROUTE:1001", "INITIAL:ROUTE:1002"), movimientoRepository.findAll().stream()
                .map(movimiento -> movimiento.getEventKey()).sorted().toList());
        assertEquals(0, vehiculo("TA01").getCurrentLoad());

        ejecutor.avanzar(ejecucion, hora("10:30"), hora("11:30"));

        assertEquals(2, movimientoRepository.count(), "el mismo evento del snapshot B no crea un tercer movimiento");
        assertEquals(0, vehiculo("TA01").getCurrentLoad(), "un false idempotente no vuelve a cargar el vehículo");
    }

    @Test
    void cargaIntermediaConMismaRutaYSecuenciaEnSnapshotsDistintosEsDurableEIdempotente() {
        RutaResponse rutaA = rutaConCargaIntermedia("SA-TA01", SALIDA, hora("10:30"), 201L, 2001L);
        RutaResponse rutaB = rutaConCargaIntermedia("SA-TA01", hora("11:00"), hora("11:30"), 202L, 2002L);
        planes.planes.put(ejecucion, plan(SALIDA, List.of(rutaA)));
        ejecutor.avanzar(ejecucion, SALIDA, hora("10:45"));
        assertEquals(3, vehiculo("TA01").getCurrentLoad());

        vehiculo("TA01").moverA(27, 14, 0);
        planes.planes.put(ejecucion, plan(hora("10:45"), List.of(rutaB)));
        ejecutor.avanzar(ejecucion, hora("10:45"), hora("11:45"));

        assertEquals(2, movimientoRepository.count());
        assertEquals(List.of("WAREHOUSE:ROUTE:2001:STOP:2", "WAREHOUSE:ROUTE:2002:STOP:2"),
                movimientoRepository.findAll().stream().map(movimiento -> movimiento.getEventKey()).sorted().toList());
        assertEquals(4, movimientoRepository.findAll().stream()
                .filter(movimiento -> "WAREHOUSE:ROUTE:2002:STOP:2".equals(movimiento.getEventKey()))
                .findFirst().orElseThrow().getStockAfter());
        assertEquals(3, vehiculo("TA01").getCurrentLoad());

        ejecutor.avanzar(ejecucion, hora("11:20"), hora("11:45"));

        assertEquals(2, movimientoRepository.count(), "reprocesar la parada B no duplica kardex");
        assertEquals(4, movimientoRepository.findAll().stream()
                .filter(movimiento -> "WAREHOUSE:ROUTE:2002:STOP:2".equals(movimiento.getEventKey()))
                .findFirst().orElseThrow().getStockAfter(), "el stock baja una sola vez");
        assertEquals(3, vehiculo("TA01").getCurrentLoad(), "registrarDespacho=false no incrementa la carga");
    }

    private static RutaResponse rutaConCargaIntermedia(String id, Instant salida, Instant llegadaAlmacen,
                                                        long planId, long routeId) {
        return new RutaResponse(id, "TA01", "CAR", salida, llegadaAlmacen.plusSeconds(300), 3.0, 24.0,
                List.of(
                        new ParadaResponse(1, ParadaResponse.ENTREGA, "P1", null, 30, 14, 0,
                                salida.plusSeconds(600), salida.plusSeconds(600), 0, 0),
                        new ParadaResponse(2, ParadaResponse.ALMACEN, null, "EAST", 57, 27, 3,
                                llegadaAlmacen, llegadaAlmacen, 0, 3)), null, planId, routeId);
    }
}
