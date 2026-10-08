package com.pucp.paqrap.modulos.almacenes.repository;

import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<WarehouseEntity, String> {
}
