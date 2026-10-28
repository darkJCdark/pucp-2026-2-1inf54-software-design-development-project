package com.pucp.paqrap.modulos.almacenes.repository;

import com.pucp.paqrap.modulos.almacenes.persistence.InventoryMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovementEntity, Long> {

    Optional<InventoryMovementEntity> findByExecutionIdAndEventKey(long executionId, String eventKey);

    List<InventoryMovementEntity> findByWarehouseIdAndExecutionIdAndOccurredAtBetweenOrderByOccurredAtAscMovementIdAsc(
            String warehouseId, long executionId, Instant desde, Instant hasta);

    /** Momento de la última recarga del almacén en la ejecución, hasta {@code instante} (incluido). */
    @Query("""
            select max(m.occurredAt) from InventoryMovementEntity m
            where m.warehouseId = :almacenId and m.executionId = :ejecucionId
              and m.movementType = com.pucp.paqrap.modulos.almacenes.persistence.MovementType.RECHARGE
              and m.occurredAt <= :instante
            """)
    Optional<Instant> ultimaRecarga(@Param("almacenId") String almacenId, @Param("ejecucionId") long ejecucionId,
                                    @Param("instante") Instant instante);

    /** Paquetes despachados por el almacén en la ejecución durante (desde, hasta]; desde nulo = sin límite. */
    @Query("""
            select coalesce(sum(m.quantity), 0) from InventoryMovementEntity m
            where m.warehouseId = :almacenId and m.executionId = :ejecucionId
              and m.movementType = com.pucp.paqrap.modulos.almacenes.persistence.MovementType.DISPATCH
              and (:desde is null or m.occurredAt > :desde)
              and m.occurredAt <= :hasta
            """)
    long despachadoEntre(@Param("almacenId") String almacenId, @Param("ejecucionId") long ejecucionId,
                         @Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
