package com.pucp.paqrap.modulos.redvial.persistence;

import com.pucp.paqrap.modulos.redvial.entity.Location;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Fila de {@code road_block_nodes}: un vértice de la polilínea bloqueada. */
@Embeddable
public class RoadBlockNodeEmbeddable {

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "x", nullable = false)
    private Integer x;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "y", nullable = false)
    private Integer y;

    protected RoadBlockNodeEmbeddable() {
    }

    public RoadBlockNodeEmbeddable(int sequenceNo, Location location) {
        this.sequenceNo = sequenceNo;
        this.x = location.x();
        this.y = location.y();
    }

    public Location toDomain() {
        return new Location(x, y);
    }

    public Integer getSequenceNo() { return sequenceNo; }
    public Integer getX() { return x; }
    public Integer getY() { return y; }
}
