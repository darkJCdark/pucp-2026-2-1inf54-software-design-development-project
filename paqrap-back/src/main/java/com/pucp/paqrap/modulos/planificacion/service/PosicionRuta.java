package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Duration;
import java.time.Instant;

import com.pucp.paqrap.modulos.planificacion.dto.ParadaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.RutaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.TramoRutaResponse;
import com.pucp.paqrap.modulos.redvial.entity.Location;

/** Estimación sobre las mismas calles y tiempos enviados al mapa, sin reconstruir caminos en L. */
public final class PosicionRuta {
    private PosicionRuta() { }

    public static Location estimar(RutaResponse ruta, int xActual, int yActual, Instant instante) {
        Location confirmada = new Location(xActual, yActual);
        if (ruta.salida() == null || instante.isBefore(ruta.salida())) {
            return confirmada;
        }
        for (ParadaResponse parada : ruta.paradas()) {
            Instant arribo = parada.inicioRefrigerio() != null ? parada.inicioRefrigerio() : parada.llegada();
            if (arribo != null && parada.fin() != null && !instante.isBefore(arribo)
                    && instante.isBefore(parada.fin())) {
                return new Location(parada.x(), parada.y());
            }
        }
        for (TramoRutaResponse tramo : ruta.tramos()) {
            if (!instante.isBefore(tramo.llegada())) {
                continue;
            }
            if (!instante.isAfter(tramo.salida())) {
                // Espera por bloqueo, servicio o salida diferida: quedarse en el nodo real.
                return new Location(tramo.desdeX(), tramo.desdeY());
            }
            double progreso = (double) Duration.between(tramo.salida(), instante).toNanos()
                    / Duration.between(tramo.salida(), tramo.llegada()).toNanos();
            // El dominio almacena nodos enteros; el frontend mantiene el decimal para animar.
            return new Location(
                    (int) Math.round(tramo.desdeX() + (tramo.hastaX() - tramo.desdeX()) * progreso),
                    (int) Math.round(tramo.desdeY() + (tramo.hastaY() - tramo.desdeY()) * progreso));
        }
        if (!ruta.tramos().isEmpty()) {
            TramoRutaResponse ultimo = ruta.tramos().getLast();
            return new Location(ultimo.hastaX(), ultimo.hastaY());
        }
        // Una ruta antigua/sin geometría no autoriza inventar un camino entre paradas.
        return confirmada;
    }
}
