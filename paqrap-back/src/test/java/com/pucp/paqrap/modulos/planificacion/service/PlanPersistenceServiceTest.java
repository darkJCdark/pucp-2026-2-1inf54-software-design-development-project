package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
import com.pucp.paqrap.modulos.almacenes.repository.WarehouseRepository;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.dto.ModoOperacion;
import com.pucp.paqrap.modulos.planificacion.dto.MotivoReplanificacion;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.planificacion.persistence.PlanEntity;
import com.pucp.paqrap.modulos.planificacion.persistence.RouteEntity;
import com.pucp.paqrap.modulos.planificacion.persistence.RouteStopEntity;
import com.pucp.paqrap.modulos.planificacion.repository.PlanRepository;
import com.pucp.paqrap.modulos.planificacion.repository.RouteRepository;
import com.pucp.paqrap.modulos.planificacion.repository.RouteStopRepository;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class PlanPersistenceServiceTest {

    private static final Instant HORA = Instant.parse("2026-09-09T12:00:00Z");

    @Autowired private PlanPersistenceService servicio;
    @Autowired private PlanRepository planRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private RouteStopRepository routeStopRepository;
    @Autowired private ScenarioExecutionRepository ejecucionRepository;
    @Autowired private WarehouseRepository almacenRepository;
    @Autowired private VehicleRepository vehiculoRepository;
    @Autowired private OrderRepository pedidoRepository;

    private long ejecucionId;
    private Warehouse central;
    private Vehicle vehiculo;
    private Order pedido;

    @BeforeEach
    void prepararDatos() {
        ScenarioExecutionEntity ejecucion = ejecucionRepository.save(
                new ScenarioExecutionEntity(ScenarioType.FIVE_DAY, HORA));
        ejecucionId = ejecucion.getExecutionId();
        almacenRepository.save(new WarehouseEntity("CENTRAL", WarehouseKind.CENTRAL, 27, 14, null));
        vehiculoRepository.save(new VehicleEntity("TA01", VehicleType.CAR, VehicleOperationalStatus.AVAILABLE));
        pedidoRepository.save(new OrderEntity("P1", "C1", new Location(30, 14), 3, HORA,
                DeliveryType.PRIORITY, 4));
        central = Warehouse.central("CENTRAL", new Location(27, 14));
        vehiculo = new Vehicle("TA01", VehicleType.CAR, true);
        pedido = new Order("P1", new Location(30, 14), 3, HORA, HORA.plusSeconds(4 * 3600));
    }

    @AfterEach
    void limpiar() {
        routeStopRepository.deleteAll();
        routeRepository.deleteAll();
        planRepository.deleteAll();
        pedidoRepository.deleteAll();
        vehiculoRepository.deleteAll();
        almacenRepository.deleteAll();
        ejecucionRepository.deleteAll();
    }

    @Test
    void persisteElPlanInicialConRutaYParadas() {
        PlanEntity plan = persistir(MotivoReplanificacion.INICIO, ruta("SA-TA01", pedido));

        assertEquals("INITIAL", plan.getPlanType());
        assertEquals(1, planRepository.count());
        RouteEntity rutaGuardada = routeRepository.findByPlanPlanIdOrderByRouteIdAsc(plan.getPlanId()).getFirst();
        assertEquals("SA-TA01", rutaGuardada.getOperationalRouteId());
        assertEquals("TA01", rutaGuardada.getVehicle().getVehicleId());
        assertEquals("CENTRAL", rutaGuardada.getStartWarehouse().getWarehouseId());
        assertEquals(27, rutaGuardada.getOriginX());
        assertEquals(14, rutaGuardada.getOriginY());
        assertEquals(3, rutaGuardada.getInitialLoad());
        assertEquals(HORA, rutaGuardada.getDepartureAt());
        assertEquals(RouteEntity.PLANNED, rutaGuardada.getStatus());
        assertNull(rutaGuardada.getCompletedAt());
        assertEquals(0, rutaGuardada.getTotalDistanceKm().compareTo(new BigDecimal("10.00")));
        assertEquals(0, rutaGuardada.getTotalCost().compareTo(new BigDecimal("8.00")));

        List<RouteStopEntity> paradas = routeStopRepository.findByRouteRouteIdOrderBySequenceNoAsc(
                rutaGuardada.getRouteId());
        assertEquals(2, paradas.size());
        assertEquals(RouteStopEntity.DELIVERY, paradas.get(0).getStopType());
        assertEquals("P1", paradas.get(0).getOrder().getOrderId());
        assertEquals(3, paradas.get(0).getDeliveredPackages());
        assertEquals(HORA.plusSeconds(60), paradas.get(0).getPlannedArrivalAt());
        assertEquals(RouteStopEntity.WAREHOUSE, paradas.get(1).getStopType());
        assertEquals("CENTRAL", paradas.get(1).getWarehouse().getWarehouseId());
        assertNull(paradas.get(1).getDeliveredPackages());
    }

    @Test
    void unaReplanificacionAgregaOtroSnapshotSinBorrarElInicial() {
        PlanEntity inicial = persistir(MotivoReplanificacion.INICIO, ruta("SA-TA01", pedido));
        PlanEntity replanificacion = persistir(MotivoReplanificacion.PERIODICO, ruta("SA-TM01", pedido));

        List<PlanEntity> planes = planRepository.findByExecutionExecutionIdOrderByPlanIdAsc(ejecucionId);
        assertEquals(List.of(inicial.getPlanId(), replanificacion.getPlanId()),
                planes.stream().map(PlanEntity::getPlanId).toList());
        assertEquals(List.of("INITIAL", "REPLANNING"), planes.stream().map(PlanEntity::getPlanType).toList());
        assertEquals(2, routeRepository.count());
    }

    @Test
    void permiteSA_TA01EnSnapshotsDistintos() {
        PlanEntity inicial = persistir(MotivoReplanificacion.INICIO, ruta("SA-TA01", pedido));
        PlanEntity replanificacion = persistir(MotivoReplanificacion.NUEVO_PEDIDO, ruta("SA-TA01", pedido));

        assertEquals("SA-TA01", routeRepository.findByPlanPlanIdOrderByRouteIdAsc(inicial.getPlanId())
                .getFirst().getOperationalRouteId());
        assertEquals("SA-TA01", routeRepository.findByPlanPlanIdOrderByRouteIdAsc(replanificacion.getPlanId())
                .getFirst().getOperationalRouteId());
    }

    @Test
    void rechazaElMismoIdentificadorOperativoDosVecesEnUnPlan() {
        PlanEntity plan = planRepository.save(new PlanEntity(ejecucionRepository.getReferenceById(ejecucionId),
                "INITIAL", true, BigDecimal.ZERO, BigDecimal.ZERO));
        routeRepository.saveAndFlush(rutaPersistida(plan, "SA-TA01"));

        assertThrows(DataIntegrityViolationException.class,
                () -> routeRepository.saveAndFlush(rutaPersistida(plan, "SA-TA01")));
    }

    @Test
    void revierteTodoElSnapshotSiFallaUnaParada() {
        Order inexistente = new Order("NO-EXISTE", new Location(30, 14), 3, HORA, HORA.plusSeconds(4 * 3600));

        assertThrows(DataIntegrityViolationException.class,
                () -> persistir(MotivoReplanificacion.INICIO, ruta("SA-TA01", inexistente)));

        assertEquals(0, planRepository.count());
        assertEquals(0, routeRepository.count());
        assertEquals(0, routeStopRepository.count());
    }

    @Test
    void revierteTodoElSnapshotSiFaltaProgramacion() {
        DeliveryRoute ruta = ruta("SA-TA01", pedido);

        assertThrows(IllegalStateException.class,
                () -> servicio.persistir(ejecucionId, MotivoReplanificacion.INICIO,
                        OperationalPlan.empty().withRoute(ruta), Map.of(), respuesta()));

        assertEquals(0, planRepository.count());
        assertEquals(0, routeRepository.count());
        assertEquals(0, routeStopRepository.count());
    }

    private PlanEntity persistir(MotivoReplanificacion motivo, DeliveryRoute ruta) {
        return servicio.persistir(ejecucionId, motivo, OperationalPlan.empty().withRoute(ruta),
                Map.of(ruta.id(), programada(ruta)), respuesta());
    }

    private DeliveryRoute ruta(String id, Order orden) {
        return DeliveryRoute.startScenarioAtCentral(id, vehiculo, central, orden.packages(), HORA)
                .withAppendedStop(new DeliveryStop(orden, orden.packages()))
                .withAppendedStop(new WarehouseVisit(central, 0));
    }

    private ScheduledDeliveryRoute programada(DeliveryRoute ruta) {
        List<ScheduledRouteStop> paradas = new ArrayList<>();
        int carga = ruta.initialLoad();
        for (int indice = 0; indice < ruta.stops().size(); indice++) {
            RouteStop parada = ruta.stops().get(indice);
            int cargaDespues = switch (parada) {
                case DeliveryStop entrega -> carga - entrega.deliveredPackages();
                case WarehouseVisit almacen -> carga + almacen.pickupPackages();
            };
            Instant llegada = HORA.plusSeconds((indice + 1L) * 60);
            paradas.add(new ScheduledRouteStop(parada,
                    new RoadPath(parada.location(), parada.location(), llegada, List.of()),
                    llegada, llegada.plusSeconds(30), carga, cargaDespues));
            carga = cargaDespues;
        }
        return new ScheduledDeliveryRoute(ruta, paradas, HORA.plusSeconds(150), 10.0, 8.0);
    }

    private RouteEntity rutaPersistida(PlanEntity plan, String operationalRouteId) {
        return new RouteEntity(plan, operationalRouteId, vehiculoRepository.getReferenceById("TA01"),
                almacenRepository.getReferenceById("CENTRAL"), 27, 14, 0, HORA, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private PlanResponse respuesta() {
        return new PlanResponse(ModoOperacion.SIMULACION_5D, HORA, true, false, 10, 8, 7, 1, 10, false,
                List.of(), List.of(), List.of());
    }
}
