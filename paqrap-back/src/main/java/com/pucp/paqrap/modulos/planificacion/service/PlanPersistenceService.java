package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
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
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Persiste un snapshot completo e inmutable de un ciclo de planificaci?n. */
@Service
public class PlanPersistenceService {

    private static final String INITIAL = "INITIAL";
    private static final String REPLANNING = "REPLANNING";

    private final PlanRepository planRepository;
    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final EntityManager entityManager;

    public PlanPersistenceService(PlanRepository planRepository, RouteRepository routeRepository,
                                  RouteStopRepository routeStopRepository, EntityManager entityManager) {
        this.planRepository = planRepository;
        this.routeRepository = routeRepository;
        this.routeStopRepository = routeStopRepository;
        this.entityManager = entityManager;
    }

    /**
     * Guarda el resultado completo de un ciclo. Los snapshots anteriores no se leen ni se modifican.
     * Si falla una ruta o una parada, la transacci?n revierte tambi?n el plan y las rutas ya insertadas.
     */
    @Transactional
    public PlanEntity persistir(long ejecucionId, MotivoReplanificacion motivo, OperationalPlan plan,
                                Map<String, ScheduledDeliveryRoute> programadas, PlanResponse respuesta) {
        Objects.requireNonNull(motivo, "motivo es requerido");
        Objects.requireNonNull(plan, "plan es requerido");
        Objects.requireNonNull(programadas, "programadas es requerido");
        Objects.requireNonNull(respuesta, "respuesta es requerida");

        PlanEntity snapshot = planRepository.save(new PlanEntity(
                referencia(ScenarioExecutionEntity.class, ejecucionId), tipoDe(motivo), respuesta.factible(),
                BigDecimal.valueOf(respuesta.distanciaTotalKm()), BigDecimal.valueOf(respuesta.costoTotal())));
        for (DeliveryRoute ruta : plan.routes()) {
            ScheduledDeliveryRoute programada = programacionDe(ruta, programadas);
            RouteEntity rutaPersistida = routeRepository.save(aRuta(snapshot, ruta, programada));
            routeStopRepository.saveAll(aParadas(rutaPersistida, ruta, programada));
        }
        return snapshot;
    }


    /** Identidades durables de rutas, indexadas por id operacional único dentro de un snapshot. */
    @Transactional(readOnly = true)
    public Map<String, Long> rutasPersistidasPorOperacion(long planId) {
        Map<String, Long> resultado = new LinkedHashMap<>();
        routeRepository.findByPlanPlanIdOrderByRouteIdAsc(planId)
                .forEach(ruta -> resultado.put(ruta.getOperationalRouteId(), ruta.getRouteId()));
        return resultado;
    }
    private String tipoDe(MotivoReplanificacion motivo) {
        return motivo == MotivoReplanificacion.INICIO ? INITIAL : REPLANNING;
    }

    private ScheduledDeliveryRoute programacionDe(DeliveryRoute ruta,
                                                   Map<String, ScheduledDeliveryRoute> programadas) {
        ScheduledDeliveryRoute programada = programadas.get(ruta.id());
        if (programada == null) {
            throw new IllegalStateException("Falta programacion para la ruta " + ruta.id());
        }
        return programada;
    }

    private RouteEntity aRuta(PlanEntity plan, DeliveryRoute ruta, ScheduledDeliveryRoute programada) {
        WarehouseEntity almacenInicial = ruta.initialWarehouse()
                .map(almacen -> referencia(WarehouseEntity.class, almacen.id()))
                .orElse(null);
        return new RouteEntity(plan, ruta.id(), referencia(VehicleEntity.class, ruta.vehicle().id()), almacenInicial,
                ruta.startLocation().x(), ruta.startLocation().y(), ruta.initialLoad(), ruta.departureAt(),
                BigDecimal.valueOf(programada.totalDistanceKm()),
                BigDecimal.valueOf(programada.totalCost()));
    }

    private List<RouteStopEntity> aParadas(RouteEntity rutaPersistida, DeliveryRoute ruta,
                                            ScheduledDeliveryRoute programada) {
        List<ScheduledRouteStop> programadas = programada.scheduledStops();
        List<RouteStopEntity> paradas = new ArrayList<>();
        for (int indice = 0; indice < ruta.stops().size(); indice++) {
            RouteStop parada = ruta.stops().get(indice);
            Instant llegada = indice < programadas.size() ? programadas.get(indice).arrivedAt() : null;
            paradas.add(switch (parada) {
                case DeliveryStop entrega -> new RouteStopEntity(rutaPersistida, indice + 1, RouteStopEntity.DELIVERY,
                        referencia(OrderEntity.class, entrega.order().id()), null, entrega.deliveredPackages(),
                        entrega.location().x(), entrega.location().y(), llegada);
                case WarehouseVisit almacen -> new RouteStopEntity(rutaPersistida, indice + 1,
                        RouteStopEntity.WAREHOUSE, null, referencia(WarehouseEntity.class, almacen.warehouse().id()),
                        null, almacen.location().x(), almacen.location().y(), llegada);
            });
        }
        return paradas;
    }

    private <T> T referencia(Class<T> tipo, Object id) {
        return entityManager.getReference(tipo, id);
    }
}
