package com.pucp.paqrap.modulos.redvial.repository;

import com.pucp.paqrap.modulos.redvial.persistence.RoadBlockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RoadBlockRepository extends JpaRepository<RoadBlockEntity, Long> {

    /** Bloqueos cuyo intervalo [startsAt, endsAt) se cruza con [desde, hasta); los límites nulos no filtran. */
    @Query("""
            select distinct b from RoadBlockEntity b left join fetch b.nodes
            where (:desde is null or b.endsAt > :desde)
              and (:hasta is null or b.startsAt < :hasta)
            order by b.startsAt, b.roadBlockId
            """)
    List<RoadBlockEntity> buscarQueSeCruzan(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /** Cantidad de bloqueos que empiezan en (desde, hasta]. */
    long countByStartsAtGreaterThanAndStartsAtLessThanEqual(Instant desde, Instant hasta);

    /** Borra los bloqueos que empiezan en [desde, hasta); Hibernate borra antes sus nodos. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RoadBlockEntity b where b.startsAt >= :desde and b.startsAt < :hasta")
    int borrarQueEmpiezanEntre(@Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
