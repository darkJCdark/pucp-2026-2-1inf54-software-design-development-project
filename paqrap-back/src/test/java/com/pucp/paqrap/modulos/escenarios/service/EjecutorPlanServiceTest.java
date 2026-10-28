package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
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
    @Autowired private BreakdownEventRepository averiaRepository;

    private long ejecucion;

    @BeforeEach
    void cargarDatos() {
        ejecucion = EJECUCIONES.incrementAndGet();
        almacenRepository.save(new WarehouseEntity("CENTRAL", WarehouseKind.CENTRAL, 27, 14, null));
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
}
