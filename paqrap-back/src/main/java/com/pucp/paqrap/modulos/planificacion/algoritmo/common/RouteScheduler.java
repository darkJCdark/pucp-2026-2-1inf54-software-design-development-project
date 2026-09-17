package com.pucp.paqrap.modulos.planificacion.algoritmo.common;
import com.pucp.paqrap.modulos.almacenes.entity.*;
import com.pucp.paqrap.modulos.flota.entity.*;
import com.pucp.paqrap.modulos.flota.service.*;
import com.pucp.paqrap.modulos.incidencias.entity.*;
import com.pucp.paqrap.modulos.incidencias.service.*;
import com.pucp.paqrap.modulos.pedidos.entity.*;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.*;
import com.pucp.paqrap.modulos.planificacion.entity.*;
import com.pucp.paqrap.modulos.redvial.entity.*;
import com.pucp.paqrap.modulos.redvial.service.*;

import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import com.pucp.paqrap.modulos.redvial.entity.RoadPath;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Transforms a logical route into timed legs, using the snapshot speed profile and active road blocks. */
public final class RouteScheduler {
    private final RoadNetwork roadNetwork;

    public RouteScheduler(RoadNetwork roadNetwork) {
        this.roadNetwork = Objects.requireNonNull(roadNetwork, "roadNetwork is required");
    }

    public ScheduledDeliveryRoute schedule(DeliveryRoute route, OperationalSnapshot snapshot, List<RoadBlock> blocks) {
        Objects.requireNonNull(route, "route is required");
        Objects.requireNonNull(snapshot, "snapshot is required");
        Objects.requireNonNull(blocks, "blocks are required");
        FleetProfile profile = snapshot.fleetProfile();
        double speed = profile.parametersFor(route.vehicle().type()).speedKmPerHour();
        Duration travelPerStreet = Duration.ofMillis(Math.round(3_600_000.0 / speed));

        List<ScheduledRouteStop> scheduledStops = new ArrayList<>();
        var currentLocation = route.startLocation();
        Instant currentTime = route.departureAt();
        int currentLoad = route.initialLoad();
        double distance = 0;
        // Refrigerio: una vez por turno, con >=1h de margen respecto a
        // cualquier cambio de turno (regla confirmada por el curso). Se
        // registra el INICIO del turno en el que ya se tomo, para detectar
        // el cambio de turno igual que ManejadorAverias/GraspPlanificador.
        Instant turnoConRefrigerioTomado = null;

        for (RouteStop stop : route.stops()) {
            RoadPath approach = roadNetwork.shortestPath(currentLocation, stop.location(), currentTime,
                            travelPerStreet, blocks)
                    .orElseThrow(() -> new IllegalStateException("No feasible road path to the next route stop"));
            Instant arrivedAt = approach.arrivesAt();

            ShiftSchedule.ShiftWindow turno = snapshot.shiftSchedule().shiftAt(arrivedAt);
            ShiftSchedule.ShiftWindow ventanaRefrigerio = snapshot.shiftSchedule().mealWindow(arrivedAt);
            if (!turno.startsAt().equals(turnoConRefrigerioTomado) && !arrivedAt.isBefore(ventanaRefrigerio.startsAt())) {
                arrivedAt = arrivedAt.plus(Duration.ofHours(1));
                turnoConRefrigerioTomado = turno.startsAt();
            }

            int loadBefore = currentLoad;
            if (stop instanceof DeliveryStop delivery) {
                currentLoad -= delivery.deliveredPackages();
                currentTime = arrivedAt.plus(DeliveryStop.SERVICE_TIME);
            } else if (stop instanceof WarehouseVisit visit) {
                currentLoad += visit.pickupPackages();
                currentTime = arrivedAt;
            } else {
                throw new IllegalStateException("Unknown route stop type");
            }
            scheduledStops.add(new ScheduledRouteStop(stop, approach, arrivedAt, currentTime,
                    loadBefore, currentLoad));
            currentLocation = stop.location();
            distance += approach.distanceKm();
        }
        double cost = distance * profile.parametersFor(route.vehicle().type()).costPerKm();
        return new ScheduledDeliveryRoute(route, scheduledStops, currentTime, distance, cost);
    }
}
