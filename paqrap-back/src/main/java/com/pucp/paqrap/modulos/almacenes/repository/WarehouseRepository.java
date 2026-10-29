package com.pucp.paqrap.modulos.almacenes.repository;

import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<WarehouseEntity, String> {

    /** Serializa las mutaciones del kardex de un mismo almacén. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WarehouseEntity w where w.warehouseId = :id")
    Optional<WarehouseEntity> findByIdForUpdate(@Param("id") String id);
}
