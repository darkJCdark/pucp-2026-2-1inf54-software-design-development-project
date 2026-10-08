package com.pucp.paqrap.modulos.redvial.dto;

import com.pucp.paqrap.modulos.redvial.persistence.RoadBlockEntity;

import java.time.Instant;
import java.util.List;

/** Bloqueo vial: polilínea de nodos bloqueada entre {@code inicio} (incluido) y {@code fin} (excluido). */
public record BloqueoResponse(Long id, Instant inicio, Instant fin, List<Nodo> nodos) {

    public record Nodo(int x, int y) {
    }

    public static BloqueoResponse de(RoadBlockEntity entity) {
        List<Nodo> nodos = entity.getNodes().stream()
                .map(nodo -> new Nodo(nodo.getX(), nodo.getY()))
                .toList();
        return new BloqueoResponse(entity.getRoadBlockId(), entity.getStartsAt(), entity.getEndsAt(), nodos);
    }
}
