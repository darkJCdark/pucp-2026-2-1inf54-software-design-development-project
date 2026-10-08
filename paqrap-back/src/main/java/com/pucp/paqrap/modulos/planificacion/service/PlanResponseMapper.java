package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.dto.ModoOperacion;
import com.pucp.paqrap.modulos.planificacion.dto.NoAtendidoResponse;
import com.pucp.paqrap.modulos.planificacion.dto.ParadaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.RutaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.ViolacionResponse;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Convierte el resultado interno del algoritmo en el DTO que consumen controllers y frontend. */
final class PlanResponseMapper {

    PlanResponse aResponse(ModoOperacion modo, Instant planificadoEn, ResultadoPlanificacion resultado,
                           long semilla, long duracionMs) {
        PlanEvaluation evaluacion = resultado.evaluacion();
        List<DeliveryRoute> rutasOrdenadas = resultado.plan().routes().stream()
                .sorted(Comparator.comparing(ruta -> ruta.vehicle().id()))
                .toList();

        List<RutaResponse> rutas = new ArrayList<>();
        double distanciaTotal = 0;
        for (DeliveryRoute ruta : rutasOrdenadas) {
            ScheduledDeliveryRoute programada = evaluacion.schedulesByRouteId().get(ruta.id());
            if (programada != null) {
                distanciaTotal += programada.totalDistanceKm();
            }
            rutas.add(aRuta(ruta, programada));
        }

        List<ViolacionResponse> violaciones = evaluacion.violations().stream()
                .map(this::aViolacion)
                .toList();
        List<NoAtendidoResponse> noAtendidos = resultado.noAtendidos().stream()
                .map(this::aNoAtendido)
                .toList();

        return new PlanResponse(modo, planificadoEn, resultado.esFactible(), resultado.esColapso(),
                resultado.costoTotal(), distanciaTotal, semilla, duracionMs, rutas, violaciones, noAtendidos);
    }

    private RutaResponse aRuta(DeliveryRoute ruta, ScheduledDeliveryRoute programada) {
        List<ParadaResponse> paradas = new ArrayList<>();
        if (programada != null) {
            int orden = 1;
            for (ScheduledRouteStop parada : programada.scheduledStops()) {
                paradas.add(aParada(orden++, parada.stop(), parada.arrivedAt(), parada.completedAt(),
                        parada.loadBefore(), parada.loadAfter()));
            }
        } else {
            int orden = 1;
            for (RouteStop parada : ruta.stops()) {
                paradas.add(aParada(orden++, parada, null, null, null, null));
            }
        }
        return new RutaResponse(ruta.id(), ruta.vehicle().id(), ruta.vehicle().type().name(), ruta.departureAt(),
                programada == null ? null : programada.completedAt(),
                programada == null ? 0 : programada.totalDistanceKm(),
                programada == null ? 0 : programada.totalCost(), paradas);
    }

    private ParadaResponse aParada(int orden, RouteStop parada, Instant llegada, Instant fin,
                                   Integer cargaAntes, Integer cargaDespues) {
        return switch (parada) {
            case DeliveryStop entrega -> new ParadaResponse(orden, ParadaResponse.ENTREGA,
                    entrega.order().id(), null, entrega.location().x(), entrega.location().y(),
                    entrega.deliveredPackages(), llegada, fin, cargaAntes, cargaDespues);
            case WarehouseVisit almacen -> new ParadaResponse(orden, ParadaResponse.ALMACEN,
                    null, almacen.warehouse().id(), almacen.location().x(), almacen.location().y(),
                    almacen.pickupPackages(), llegada, fin, cargaAntes, cargaDespues);
        };
    }

    private ViolacionResponse aViolacion(PlanViolation violacion) {
        return new ViolacionResponse(violacion.type().name(), violacion.routeId(), violacion.detail());
    }

    private NoAtendidoResponse aNoAtendido(Order pedido) {
        return new NoAtendidoResponse(pedido.id(), pedido.packages(), pedido.deadline());
    }
}
