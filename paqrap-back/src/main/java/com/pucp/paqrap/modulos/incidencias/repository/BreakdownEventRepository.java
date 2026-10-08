package com.pucp.paqrap.modulos.incidencias.repository;

import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
import com.pucp.paqrap.modulos.incidencias.persistence.BreakdownEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BreakdownEventRepository extends JpaRepository<BreakdownEventEntity, Long> {

    @Query("""
            select b from BreakdownEventEntity b
            where (:vehiculoId is null or b.vehicleId = :vehiculoId)
              and (:tipo is null or b.breakdownType = :tipo)
              and (:ejecucionId is null or b.executionId = :ejecucionId)
              and (:desde is null or b.occurredAt >= :desde)
              and (:hasta is null or b.occurredAt < :hasta)
            order by b.occurredAt, b.breakdownId
            """)
    List<BreakdownEventEntity> buscar(@Param("vehiculoId") String vehiculoId,
                                      @Param("tipo") BreakdownType tipo,
                                      @Param("ejecucionId") Long ejecucionId,
                                      @Param("desde") Instant desde,
                                      @Param("hasta") Instant hasta);

    /** Última avería del vehículo ocurrida hasta {@code instante} (incluido). */
    Optional<BreakdownEventEntity> findFirstByVehicleIdAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
            String vehicleId, Instant instante);
}
