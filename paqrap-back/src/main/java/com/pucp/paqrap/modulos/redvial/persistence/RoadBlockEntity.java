package com.pucp.paqrap.modulos.redvial.persistence;

import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Fila de {@code road_blocks} con sus nodos. El dominio la consume como {@link RoadBlock}. */
@Entity
@Table(name = "road_blocks")
public class RoadBlockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "road_block_id")
    private Long roadBlockId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @ElementCollection
    @CollectionTable(name = "road_block_nodes", joinColumns = @JoinColumn(name = "road_block_id"))
    @OrderBy("sequenceNo")
    private List<RoadBlockNodeEmbeddable> nodes = new ArrayList<>();

    protected RoadBlockEntity() {
    }

    public static RoadBlockEntity desde(RoadBlock bloqueo) {
        RoadBlockEntity entity = new RoadBlockEntity();
        entity.startsAt = bloqueo.startsAt();
        entity.endsAt = bloqueo.endsAt();
        List<Location> nodos = bloqueo.nodes();
        for (int indice = 0; indice < nodos.size(); indice++) {
            entity.nodes.add(new RoadBlockNodeEmbeddable(indice + 1, nodos.get(indice)));
        }
        return entity;
    }

    public RoadBlock toDomain() {
        return new RoadBlock(startsAt, endsAt, nodes.stream().map(RoadBlockNodeEmbeddable::toDomain).toList());
    }

    public Long getRoadBlockId() { return roadBlockId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public List<RoadBlockNodeEmbeddable> getNodes() { return nodes; }
}
