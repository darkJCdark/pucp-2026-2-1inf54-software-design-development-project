package com.pucp.paqrap.modulos.redvial.entity;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** A time-aware sequence of street legs. */
public final class RoadPath {
    private final Location origin;
    private final Location destination;
    private final Instant requestedDepartureAt;
    private final List<RoadLeg> legs;

    public RoadPath(Location origin, Location destination, Instant requestedDepartureAt, List<RoadLeg> legs) {
        this.origin = Objects.requireNonNull(origin, "origin is required");
        this.destination = Objects.requireNonNull(destination, "destination is required");
        this.requestedDepartureAt = Objects.requireNonNull(requestedDepartureAt, "requestedDepartureAt is required");
        this.legs = List.copyOf(Objects.requireNonNull(legs, "legs are required"));
        validateContinuity();
    }

    public Location origin() { return origin; }
    public Location destination() { return destination; }
    public List<RoadLeg> legs() { return legs; }
    public int distanceKm() { return legs.size(); }
    public Instant arrivesAt() { return legs.isEmpty() ? requestedDepartureAt : legs.getLast().arrivesAt(); }
    public Duration elapsedTime() { return Duration.between(requestedDepartureAt, arrivesAt()); }

    private void validateContinuity() {
        Location expectedFrom = origin;
        Instant previousArrival = requestedDepartureAt;
        for (RoadLeg leg : legs) {
            if (!leg.from().equals(expectedFrom) || leg.departsAt().isBefore(previousArrival)) {
                throw new IllegalArgumentException("Road path legs must be spatially and temporally continuous");
            }
            expectedFrom = leg.to();
            previousArrival = leg.arrivesAt();
        }
        if (!expectedFrom.equals(destination)) {
            throw new IllegalArgumentException("Road path must finish at its declared destination");
        }
    }
}
