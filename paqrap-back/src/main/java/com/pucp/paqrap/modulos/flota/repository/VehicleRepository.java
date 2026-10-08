package com.pucp.paqrap.modulos.flota.repository;

import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VehicleRepository extends JpaRepository<VehicleEntity, String> {

    @Query("""
            select v from VehicleEntity v
            where (:tipo is null or v.vehicleType = :tipo)
              and (:estado is null or v.operationalStatus = :estado)
            order by v.vehicleId
            """)
    List<VehicleEntity> buscar(@Param("tipo") VehicleType tipo, @Param("estado") VehicleOperationalStatus estado);
}
